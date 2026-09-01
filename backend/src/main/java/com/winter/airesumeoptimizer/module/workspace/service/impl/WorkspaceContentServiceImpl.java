package com.winter.airesumeoptimizer.module.workspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.optimization.entity.JobTarget;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.JobTargetMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.service.ResumeCanonicalDocumentService;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceContentSaveRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.service.ResumeDocumentConverter;
import com.winter.airesumeoptimizer.module.workspace.service.WorkspaceContentService;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentSaveResultVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentVO;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceContentServiceImpl implements WorkspaceContentService {

    private static final String TASK_STATUS_SUCCESS = "SUCCESS";
    private static final String VERSION_SOURCE = "SOURCE";
    private static final String VERSION_TARGETED = "TARGETED";
    private static final String CONTENT_READY = "READY";
    private static final long PRISTINE_REVISION = 0L;

    private final OptimizationTaskMapper optimizationTaskMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final JobTargetMapper jobTargetMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeDocumentConverter resumeDocumentConverter;
    private final ResumeCanonicalDocumentService resumeCanonicalDocumentService;
    private final ObjectMapper objectMapper;

    public WorkspaceContentServiceImpl(
            OptimizationTaskMapper optimizationTaskMapper,
            ResumeVersionMapper resumeVersionMapper,
            JobTargetMapper jobTargetMapper,
            ResumeMapper resumeMapper,
            ResumeDocumentConverter resumeDocumentConverter,
            ResumeCanonicalDocumentService resumeCanonicalDocumentService,
            ObjectMapper objectMapper) {
        this.optimizationTaskMapper = optimizationTaskMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.jobTargetMapper = jobTargetMapper;
        this.resumeMapper = resumeMapper;
        this.resumeDocumentConverter = resumeDocumentConverter;
        this.resumeCanonicalDocumentService = resumeCanonicalDocumentService;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkspaceContentVO getContent(Long userId, Long optimizationTaskId) {
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        ResumeVersion target = context.target();
        long revision = revisionOf(target);

        ResumeDocumentDTO document;
        if (revision > PRISTINE_REVISION) {
            document = readPersistedDocument(target);
        } else {
            // revision 0：TARGET 仍是分析时冻结内容的原始副本，按冻结快照确定性地生成编辑文档。
            document = documentFromFrozenSnapshot(resolveFrozenSnapshot(context));
        }
        return WorkspaceContentVO.builder()
                .optimizationTaskId(context.task().getId())
                .revision(revision)
                .document(document)
                .build();
    }

    @Override
    public WorkspaceContentVO getPersistedContentForRender(Long userId, Long optimizationTaskId) {
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        ResumeVersion target = context.target();
        long revision = revisionOf(target);
        if (revision == PRISTINE_REVISION) {
            throw new BusinessException(409, "请先保存当前简历内容，再进行预览或导出");
        }
        return WorkspaceContentVO.builder()
                .optimizationTaskId(context.task().getId())
                .revision(revision)
                .document(readPersistedDocument(target))
                .build();
    }

    @Override
    @Transactional
    public WorkspaceContentSaveResultVO saveContent(
            Long userId, Long optimizationTaskId, WorkspaceContentSaveRequestDTO request) {
        if (request == null || request.getExpectedRevision() == null) {
            throw new BusinessException(400, "缺少内容版本号");
        }
        validateExpectedRevision(request.getExpectedRevision());
        if (request.getDocument() == null) {
            throw new BusinessException(400, "简历内容不能为空");
        }
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        ResumeDocumentDTO normalized = resumeDocumentConverter.normalize(request.getDocument());
        return writeTargetContent(context, normalized, request.getExpectedRevision());
    }

    @Override
    @Transactional
    public WorkspaceContentSaveResultVO restorePreOptimizationContent(
            Long userId, Long optimizationTaskId, Long expectedRevision) {
        if (expectedRevision == null) {
            throw new BusinessException(400, "缺少内容版本号");
        }
        validateExpectedRevision(expectedRevision);
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        // 恢复只读取任务冻结快照重新生成文档；SOURCE、快照与证据分析不被回写。
        ResumeDocumentDTO restored = documentFromFrozenSnapshot(resolveFrozenSnapshot(context));
        return writeTargetContent(context, restored, expectedRevision);
    }

    /**
     * 服务端从任务解析 SOURCE / TARGET 并校验完整版本链，调用方不能指定可写版本。
     */
    private EditableTaskContext resolveEditableTarget(Long userId, Long optimizationTaskId) {
        validateUserId(userId);
        if (optimizationTaskId == null || optimizationTaskId <= 0) {
            throw new BusinessException(400, "优化任务 ID 必须大于 0");
        }
        OptimizationTask task = optimizationTaskMapper.selectOne(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getId, optimizationTaskId)
                .eq(OptimizationTask::getUserId, userId));
        if (task == null) {
            throw new BusinessException(404, "优化任务不存在");
        }
        if (!TASK_STATUS_SUCCESS.equals(task.getStatus())) {
            throw new BusinessException(409, "岗位分析尚未完成，暂不能编辑简历内容");
        }

        ResumeVersion source = getOwnedVersion(userId, task.getSourceResumeVersionId());
        ResumeVersion target = getOwnedVersion(userId, task.getTargetResumeVersionId());
        JobTarget jobTarget = getOwnedJobTarget(userId, task.getJobTargetId());
        Resume resume = getOwnedResume(userId, source.getResumeId());

        if (!VERSION_SOURCE.equals(source.getVersionType())
                || source.getSourceVersionId() != null
                || source.getJobTargetId() != null
                || !CONTENT_READY.equals(source.getContentStatus())
                || source.getStructuredContent() == null
                || source.getStructuredContent().isBlank()) {
            throw new BusinessException(500, "优化任务的简历版本关系不一致");
        }
        if (!VERSION_TARGETED.equals(target.getVersionType())
                || !task.getSourceResumeVersionId().equals(target.getSourceVersionId())
                || !task.getJobTargetId().equals(target.getJobTargetId())
                || !source.getResumeId().equals(target.getResumeId())
                || !resume.getId().equals(target.getResumeId())
                || !jobTarget.getId().equals(target.getJobTargetId())
                || !CONTENT_READY.equals(target.getContentStatus())
                || target.getStructuredContent() == null
                || target.getStructuredContent().isBlank()) {
            throw new BusinessException(500, "优化任务的简历版本关系不一致");
        }
        long sourceRevision = revisionOf(source);
        long targetRevision = revisionOf(target);
        String snapshot = task.getResumeInputSnapshot();
        if (sourceRevision != PRISTINE_REVISION
                || (snapshot != null && !snapshot.isBlank() && !snapshot.equals(source.getStructuredContent()))
                || (targetRevision == PRISTINE_REVISION
                        && !target.getStructuredContent().equals(source.getStructuredContent()))) {
            throw new BusinessException(500, "优化任务的冻结简历内容不一致");
        }
        Long targetUseCount = optimizationTaskMapper.selectCount(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getTargetResumeVersionId, target.getId()));
        if (targetUseCount == null || targetUseCount != 1L) {
            throw new BusinessException(500, "岗位版本被多个优化任务引用，不能安全编辑");
        }
        return new EditableTaskContext(task, source, target);
    }

    private ResumeVersion getOwnedVersion(Long userId, Long versionId) {
        if (versionId == null) {
            throw new BusinessException(500, "优化任务的简历版本关系不一致");
        }
        ResumeVersion version = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, versionId)
                .eq(ResumeVersion::getUserId, userId));
        if (version == null) {
            throw new BusinessException(404, "简历版本不存在");
        }
        return version;
    }

    private JobTarget getOwnedJobTarget(Long userId, Long jobTargetId) {
        if (jobTargetId == null) {
            throw new BusinessException(500, "优化任务的岗位关系不一致");
        }
        JobTarget target = jobTargetMapper.selectOne(new LambdaQueryWrapper<JobTarget>()
                .eq(JobTarget::getId, jobTargetId)
                .eq(JobTarget::getUserId, userId));
        if (target == null) {
            throw new BusinessException(404, "目标岗位不存在");
        }
        return target;
    }

    private Resume getOwnedResume(Long userId, Long resumeId) {
        if (resumeId == null) {
            throw new BusinessException(500, "优化任务的简历关系不一致");
        }
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId));
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        return resume;
    }

    /**
     * 仅当 expectedRevision 与服务端当前 revision 一致时原子写入并递增；
     * 条件更新保证同 revision 的并发保存只有一个成功。
     */
    private WorkspaceContentSaveResultVO writeTargetContent(
            EditableTaskContext context, ResumeDocumentDTO document, long expectedRevision) {
        ResumeVersion target = context.target();
        String serialized = serialize(document);
        LocalDateTime now = LocalDateTime.now();
        int rows = resumeVersionMapper.update(null, new UpdateWrapper<ResumeVersion>()
                .eq("id", target.getId())
                .eq("user_id", target.getUserId())
                .eq("version_type", VERSION_TARGETED)
                .eq("source_version_id", context.source().getId())
                .eq("job_target_id", context.task().getJobTargetId())
                .eq("content_status", CONTENT_READY)
                .eq("content_revision", expectedRevision)
                .set("structured_content", serialized)
                .set("content_revision", expectedRevision + 1)
                .set("updated_at", now));
        if (rows == 1) {
            return WorkspaceContentSaveResultVO.builder()
                    .saved(true)
                    .conflict(false)
                    .revision(expectedRevision + 1)
                    .document(document)
                    .build();
        }

        ResumeVersion current = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, target.getId())
                .eq(ResumeVersion::getUserId, target.getUserId()));
        if (current == null) {
            throw new BusinessException(404, "简历版本不存在");
        }
        return WorkspaceContentSaveResultVO.builder()
                .saved(false)
                .conflict(true)
                .revision(revisionOf(current))
                .build();
    }

    private ResumeDocumentDTO readPersistedDocument(ResumeVersion target) {
        String content = target.getStructuredContent();
        if (content == null || content.isBlank()) {
            throw new BusinessException(500, "简历内容格式不正确");
        }
        // V1 语义内容直接归一化，Slice A 之前的 generic V1 内容确定性升级；损坏内容在升级器内 fail closed。
        return resumeDocumentConverter.upgradeLegacyDocument(content);
    }

    /**
     * 编辑文档的原始输入优先使用任务冻结的 resume_input_snapshot；
     * 历史回填任务可能没有快照，此时退回该任务 SOURCE 的冻结结构化内容。
     */
    private ResumeDocumentDTO documentFromFrozenSnapshot(String snapshot) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(snapshot);
            String schemaVersion = root == null ? null : root.path("schemaVersion").asText(null);
            if (ResumeDocumentDTO.SCHEMA_VERSION.equals(schemaVersion)) {
                return resumeDocumentConverter.upgradeLegacyDocument(snapshot);
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历内容格式不正确，请重新解析");
        }
        // 历史任务冻结的是旧 structured_json 候选；只读确定性投影，不能作为新任务输入。
        return resumeCanonicalDocumentService.buildFromStructuredJson(snapshot).document();
    }

    private String resolveFrozenSnapshot(EditableTaskContext context) {
        String snapshot = context.task().getResumeInputSnapshot();
        if (snapshot != null && !snapshot.isBlank()) {
            return snapshot;
        }
        String sourceContent = context.source().getStructuredContent();
        if (sourceContent == null || sourceContent.isBlank()) {
            throw new BusinessException(409, "简历内容尚未就绪，请先完成简历解析");
        }
        return sourceContent;
    }

    private String serialize(ResumeDocumentDTO document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历内容保存失败");
        }
    }

    private long revisionOf(ResumeVersion version) {
        long revision = version.getContentRevision() == null ? PRISTINE_REVISION : version.getContentRevision();
        if (revision < PRISTINE_REVISION) {
            throw new BusinessException(500, "简历内容版本号不正确");
        }
        return revision;
    }

    private void validateExpectedRevision(long expectedRevision) {
        if (expectedRevision < PRISTINE_REVISION || expectedRevision == Long.MAX_VALUE) {
            throw new BusinessException(400, "内容版本号不正确");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
    }

    private record EditableTaskContext(OptimizationTask task, ResumeVersion source, ResumeVersion target) {
    }
}
