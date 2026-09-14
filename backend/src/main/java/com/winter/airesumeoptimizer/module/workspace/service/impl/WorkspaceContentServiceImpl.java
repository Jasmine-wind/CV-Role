package com.winter.airesumeoptimizer.module.workspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
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
import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceSourceOmissionRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.service.ResumeDocumentConverter;
import com.winter.airesumeoptimizer.module.workspace.service.WorkspaceContentService;
import com.winter.airesumeoptimizer.module.workspace.service.WorkspaceSourceReferenceAssembler;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentSaveResultVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourcePdfVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO.SourceBlock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private final WorkspaceSourceReferenceAssembler sourceReferenceAssembler;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public WorkspaceContentServiceImpl(
            OptimizationTaskMapper optimizationTaskMapper,
            ResumeVersionMapper resumeVersionMapper,
            JobTargetMapper jobTargetMapper,
            ResumeMapper resumeMapper,
            ResumeDocumentConverter resumeDocumentConverter,
            ResumeCanonicalDocumentService resumeCanonicalDocumentService,
            WorkspaceSourceReferenceAssembler sourceReferenceAssembler,
            FileStorageService fileStorageService,
            ObjectMapper objectMapper) {
        this.optimizationTaskMapper = optimizationTaskMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.jobTargetMapper = jobTargetMapper;
        this.resumeMapper = resumeMapper;
        this.resumeDocumentConverter = resumeDocumentConverter;
        this.resumeCanonicalDocumentService = resumeCanonicalDocumentService;
        this.sourceReferenceAssembler = sourceReferenceAssembler;
        this.fileStorageService = fileStorageService;
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
    public WorkspaceSourceReferenceVO getSourceReference(Long userId, Long optimizationTaskId) {
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        long revision = revisionOf(context.target());
        ResumeDocumentDTO source = documentFromFrozenSnapshot(resolveFrozenSnapshot(context));
        ResumeDocumentDTO target = revision > PRISTINE_REVISION
                ? readPersistedDocument(context.target()) : source;
        Resume resume = context.resume();
        return sourceReferenceAssembler.assemble(
                context.task().getId(), context.source().getId(), context.target().getId(), revision,
                resume.getOriginalFilename(), isPdf(resume), source, target);
    }

    @Override
    public WorkspaceSourcePdfVO getSourcePdf(Long userId, Long optimizationTaskId) {
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        Resume resume = context.resume();
        if (!isPdf(resume)) {
            throw new BusinessException(409, "原始文件不是 PDF，请使用原文视图核对");
        }
        if (resume.getObjectKey() == null || resume.getObjectKey().isBlank()) {
            throw new BusinessException(404, "原始文件不存在");
        }
        byte[] bytes;
        try {
            bytes = fileStorageService.loadAsBytes(resume.getObjectKey());
        } catch (RuntimeException exception) {
            throw new BusinessException(500, "原始文件读取失败，请稍后重试");
        }
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                || bytes[3] != 'F' || bytes[4] != '-') {
            throw new BusinessException(500, "原始 PDF 文件格式不正确");
        }
        return new WorkspaceSourcePdfVO(bytes, resume.getOriginalFilename());
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
        long currentRevision = revisionOf(context.target());
        if (currentRevision != request.getExpectedRevision()) {
            return conflictResult(currentRevision);
        }
        ResumeDocumentDTO frozen = documentFromFrozenSnapshot(resolveFrozenSnapshot(context));
        ResumeDocumentDTO current = currentRevision == PRISTINE_REVISION
                ? frozen : readPersistedDocument(context.target());
        ResumeDocumentDTO normalized = resumeDocumentConverter.normalizeWorkspaceSave(
                request.getDocument(), current, frozen);
        canonicalizeConfirmedOmissions(frozen, normalized);
        return writeTargetContent(context, normalized, request.getExpectedRevision());
    }

    @Override
    @Transactional
    public WorkspaceContentSaveResultVO confirmSourceOmissions(
            Long userId, Long optimizationTaskId, WorkspaceSourceOmissionRequestDTO request) {
        return mutateSourceOmissions(userId, optimizationTaskId, request, true);
    }

    @Override
    @Transactional
    public WorkspaceContentSaveResultVO unconfirmSourceOmissions(
            Long userId, Long optimizationTaskId, WorkspaceSourceOmissionRequestDTO request) {
        return mutateSourceOmissions(userId, optimizationTaskId, request, false);
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
        restored.setConfirmedSourceOmissionIds(List.of());
        return writeTargetContent(context, restored, expectedRevision);
    }

    private WorkspaceContentSaveResultVO mutateSourceOmissions(
            Long userId,
            Long optimizationTaskId,
            WorkspaceSourceOmissionRequestDTO request,
            boolean confirm) {
        if (request == null || request.expectedRevision() == null) {
            throw new BusinessException(400, "缺少内容版本号");
        }
        validateExpectedRevision(request.expectedRevision());
        List<String> requested = validateRequestedOccurrenceIds(request.sourceOccurrenceIds());
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        long currentRevision = revisionOf(context.target());
        if (currentRevision != request.expectedRevision()) {
            return conflictResult(currentRevision);
        }
        ResumeDocumentDTO frozen = documentFromFrozenSnapshot(resolveFrozenSnapshot(context));
        ResumeDocumentDTO current = currentRevision == PRISTINE_REVISION
                ? frozen : readPersistedDocument(context.target());
        Set<String> frozenIds = new LinkedHashSet<>(safeStrings(frozen.getSourceOccurrenceIds()));
        if (confirm && !frozenIds.containsAll(requested)) {
            throw new BusinessException(400, "来源 occurrence 不属于当前冻结简历");
        }

        // Inspect the persisted state before normalizing it. In particular, a malformed partial alias
        // group must remain removable even though the assembler correctly refuses to call it confirmed.
        Set<String> persistedIds = canonicalCandidateOccurrenceIds(current.getConfirmedSourceOmissionIds());
        Set<String> persistedMentions = mentionedOccurrenceIds(current.getConfirmedSourceOmissionIds());
        WorkspaceSourceReferenceVO fidelity = sourceReferenceAssembler.assemble(
                context.task().getId(), context.source().getId(), context.target().getId(), currentRevision,
                context.resume().getOriginalFilename(), isPdf(context.resume()), frozen, current);
        if (hasFidelityIssue(fidelity, "SOURCE_MANIFEST_INVALID")
                || hasFidelityIssue(fidelity, "SOURCE_MANIFEST_UNAVAILABLE")) {
            throw new BusinessException(400, "冻结原文清单不允许确认省略");
        }

        // Existing decisions have no authority merely because they were persisted. Rebuild only complete,
        // currently eligible alias/boundary groups; this drops unknown, duplicate, mapped, ambiguous,
        // ineligible, and unrelated partial stale state without promoting a partial alias to its closure.
        LinkedHashSet<String> next = canonicalConfirmedOmissionIds(fidelity.sourceBlocks(), persistedIds);
        LinkedHashSet<SourceBlock> boundaryBlocks = new LinkedHashSet<>();
        for (String requestedId : requested) {
            if (!frozenIds.contains(requestedId)) {
                if (confirm || !persistedMentions.contains(requestedId)) {
                    throw new BusinessException(400, "来源 occurrence 不属于当前冻结简历");
                }
                // An exact task-local stale value can authorize only its own CAS-backed removal.
                continue;
            }
            SourceBlock requestedBlock = resolveRequestedBlock(requestedId, fidelity.sourceBlocks());
            if (confirm && !isConfirmableOmission(requestedBlock)) {
                throw new BusinessException(400, "来源 occurrence 当前不能确认为省略");
            }
            List<SourceBlock> requestedBoundary = omissionBoundary(requestedBlock, fidelity.sourceBlocks());
            if (!confirm && requestedBoundary.stream()
                    .flatMap(block -> block.occurrenceIds().stream())
                    .noneMatch(persistedMentions::contains)) {
                throw new BusinessException(400, "来源 occurrence 当前未确认省略");
            }
            boundaryBlocks.addAll(requestedBoundary);
        }
        if (confirm && boundaryBlocks.stream().anyMatch(block -> !isConfirmableOmission(block))) {
            throw new BusinessException(400, "来源边界未完整省略，当前不能确认");
        }

        // SourceBlock.occurrenceIds is the complete physical alias closure. Applying the enclosing
        // Project/Bullet boundary after block resolution makes either operation authoritative and atomic.
        LinkedHashSet<String> authoritativeIds = boundaryBlocks.stream()
                .flatMap(block -> block.occurrenceIds().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (confirm) next.addAll(authoritativeIds);
        else next.removeAll(authoritativeIds);
        current.setConfirmedSourceOmissionIds(orderedFrozenIds(frozen, next));

        // A malformed confirmation list may be repaired, but no mutation may persist a confirmation state
        // that the assembler still considers invalid. This keeps confirm fail closed when rebuilding is unsafe.
        WorkspaceSourceReferenceVO rebuilt = sourceReferenceAssembler.assemble(
                context.task().getId(), context.source().getId(), context.target().getId(), currentRevision,
                context.resume().getOriginalFilename(), isPdf(context.resume()), frozen, current);
        Set<String> rebuiltIds = canonicalCandidateOccurrenceIds(current.getConfirmedSourceOmissionIds());
        if (hasFidelityIssue(rebuilt, "CONFIRMED_OMISSION_INVALID")
                || !canonicalConfirmedOmissionIds(rebuilt.sourceBlocks(), rebuiltIds).equals(rebuiltIds)) {
            throw new BusinessException(400, "已确认省略状态无法安全规范化");
        }
        return writeTargetContent(context, current, request.expectedRevision());
    }

    private List<String> validateRequestedOccurrenceIds(List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 500) {
            throw new BusinessException(400, "来源 occurrence ID 不能为空或数量超出上限");
        }
        List<String> normalized = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank() || !id.equals(id.strip()) || !unique.add(id)) {
                throw new BusinessException(400, "来源 occurrence ID 无效或重复");
            }
            normalized.add(id);
        }
        return normalized;
    }

    private void canonicalizeConfirmedOmissions(ResumeDocumentDTO frozen, ResumeDocumentDTO target) {
        WorkspaceSourceReferenceVO fidelity = sourceReferenceAssembler.assemble(
                null, null, null, 0L, null, false, frozen, target);
        Set<String> existing = canonicalCandidateOccurrenceIds(target.getConfirmedSourceOmissionIds());
        Set<String> canonical = canonicalConfirmedOmissionIds(fidelity.sourceBlocks(), existing);
        target.setConfirmedSourceOmissionIds(orderedFrozenIds(frozen, canonical));
    }

    private LinkedHashSet<String> canonicalConfirmedOmissionIds(
            List<SourceBlock> sourceBlocks, Set<String> existing) {
        LinkedHashSet<String> canonical = new LinkedHashSet<>();
        for (SourceBlock block : sourceBlocks) {
            if (!isConfirmableOmission(block)) continue;
            List<SourceBlock> boundary = omissionBoundary(block, sourceBlocks);
            boolean complete = boundary.stream().allMatch(candidate -> isConfirmableOmission(candidate)
                    && existing.containsAll(candidate.occurrenceIds()));
            if (complete) {
                boundary.stream().flatMap(candidate -> candidate.occurrenceIds().stream())
                        .forEach(canonical::add);
            }
        }
        return canonical;
    }

    private SourceBlock resolveRequestedBlock(String requestedId, List<SourceBlock> sourceBlocks) {
        List<SourceBlock> matches = sourceBlocks.stream()
                .filter(candidate -> candidate.occurrenceIds().contains(requestedId))
                .toList();
        if (matches.size() != 1) {
            throw new BusinessException(400, "来源 occurrence 不属于当前冻结简历");
        }
        return matches.get(0);
    }

    /**
     * Return only exact, unique persisted IDs that may retain authority. Malformed values are
     * never trimmed or deduplicated into a valid confirmation; every repeated ID is removed.
     */
    private Set<String> canonicalCandidateOccurrenceIds(List<String> ids) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        LinkedHashSet<String> duplicates = new LinkedHashSet<>();
        for (String id : safeStrings(ids)) {
            if (id == null || id.isBlank() || !id.equals(id.strip())) continue;
            if (!candidates.add(id)) duplicates.add(id);
        }
        candidates.removeAll(duplicates);
        return candidates;
    }

    /** A malformed stored value may authorize only its own removal, never confirmation. */
    private Set<String> mentionedOccurrenceIds(List<String> ids) {
        LinkedHashSet<String> mentions = new LinkedHashSet<>();
        for (String id : safeStrings(ids)) {
            if (id != null && !id.isBlank()) mentions.add(id.strip());
        }
        return mentions;
    }

    private List<String> orderedFrozenIds(ResumeDocumentDTO frozen, Set<String> ids) {
        return safeStrings(frozen.getSourceOccurrenceIds()).stream()
                .filter(ids::contains)
                .distinct()
                .toList();
    }

    private boolean hasFidelityIssue(WorkspaceSourceReferenceVO fidelity, String code) {
        return fidelity.fidelityIssues().stream().anyMatch(issue -> code.equals(issue.code()));
    }

    private boolean isConfirmableOmission(SourceBlock block) {
        return block.status()
                == com.winter.airesumeoptimizer.module.workspace.enums.WorkspaceSourceMappingStatus.UNMAPPED
                && block.omissionEligible();
    }

    /** Resolve omission units only from authenticated frozen-owner metadata returned by the server assembler. */
    private List<SourceBlock> omissionBoundary(SourceBlock anchor, List<SourceBlock> allBlocks) {
        if ("PROJECT".equalsIgnoreCase(anchor.sourceSectionKind())
                && hasBoundaryId(anchor.sourceSectionId()) && hasBoundaryId(anchor.sourceEntryId())) {
            return allBlocks.stream().filter(candidate ->
                    "PROJECT".equalsIgnoreCase(candidate.sourceSectionKind())
                            && anchor.sourceSectionId().equals(candidate.sourceSectionId())
                            && anchor.sourceEntryId().equals(candidate.sourceEntryId()))
                    .toList();
        }
        if ("BULLET".equals(anchor.sourceNodeType())
                && hasBoundaryId(anchor.sourceSectionId()) && hasBoundaryId(anchor.sourceEntryId())
                && hasBoundaryId(anchor.sourceBulletId())) {
            return allBlocks.stream().filter(candidate ->
                    "BULLET".equals(candidate.sourceNodeType())
                            && anchor.sourceSectionId().equals(candidate.sourceSectionId())
                            && anchor.sourceEntryId().equals(candidate.sourceEntryId())
                            && anchor.sourceBulletId().equals(candidate.sourceBulletId()))
                    .toList();
        }
        return List.of(anchor);
    }

    private boolean hasBoundaryId(String value) {
        return value != null && !value.isBlank();
    }

    private List<String> safeStrings(List<String> values) {
        return values == null ? List.of() : values;
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
        return new EditableTaskContext(task, source, target, resume);
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
    private WorkspaceContentSaveResultVO conflictResult(long currentRevision) {
        return WorkspaceContentSaveResultVO.builder()
                .saved(false)
                .conflict(true)
                .revision(currentRevision)
                .build();
    }

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
        return conflictResult(revisionOf(current));
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

    private boolean isPdf(Resume resume) {
        return resume != null && resume.getFileType() != null
                && "PDF".equalsIgnoreCase(resume.getFileType().strip());
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
    }

    private record EditableTaskContext(
            OptimizationTask task, ResumeVersion source, ResumeVersion target, Resume resume) {
    }
}
