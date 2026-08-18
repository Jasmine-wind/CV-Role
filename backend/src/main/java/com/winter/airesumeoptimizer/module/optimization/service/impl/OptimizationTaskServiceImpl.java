package com.winter.airesumeoptimizer.module.optimization.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionSubmitDTO;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionService;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import com.winter.airesumeoptimizer.module.optimization.entity.JobTarget;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.JobTargetMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.optimization.service.OptimizationTaskService;
import com.winter.airesumeoptimizer.module.optimization.vo.OptimizationTaskVO;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OptimizationTaskServiceImpl implements OptimizationTaskService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String CONTENT_PENDING = "PENDING";
    private static final String CONTENT_READY = "READY";
    private static final String VERSION_SOURCE = "SOURCE";
    private static final String VERSION_TARGETED = "TARGETED";
    private static final String SOURCE_PARSED_UPLOAD = "PARSED_UPLOAD";
    private static final String SOURCE_JOB_DERIVATION = "JOB_DERIVATION";
    private static final String SOURCE_USER_INPUT = "USER_INPUT";
    private static final String DEFAULT_RULES_SNAPSHOT = "{}";
    private static final String DEFAULT_PROMPT_SNAPSHOT = "{}";
    private static final String DEFAULT_TEMPLATE_VERSION = "NOT_SELECTED";
    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    private final ResumeMapper resumeMapper;
    private final JobDescriptionService jobDescriptionService;
    private final JobDescriptionMapper jobDescriptionMapper;
    private final JobTargetMapper jobTargetMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final OptimizationTaskMapper optimizationTaskMapper;
    private final AiJobMatchResultMapper aiJobMatchResultMapper;
    private final ObjectMapper objectMapper;

    public OptimizationTaskServiceImpl(
            ResumeMapper resumeMapper,
            JobDescriptionService jobDescriptionService,
            JobDescriptionMapper jobDescriptionMapper,
            JobTargetMapper jobTargetMapper,
            ResumeVersionMapper resumeVersionMapper,
            OptimizationTaskMapper optimizationTaskMapper,
            AiJobMatchResultMapper aiJobMatchResultMapper,
            ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.jobDescriptionService = jobDescriptionService;
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.jobTargetMapper = jobTargetMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.optimizationTaskMapper = optimizationTaskMapper;
        this.aiJobMatchResultMapper = aiJobMatchResultMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public OptimizationTaskVO create(
            Long userId,
            Long resumeId,
            String jobTitle,
            String rawJobDescription,
            String providerSnapshot,
            String modelSnapshot) {
        getOwnedResume(userId, resumeId);
        JobDescriptionSubmitDTO submitRequest = new JobDescriptionSubmitDTO();
        submitRequest.setTitle(jobTitle);
        submitRequest.setRawText(rawJobDescription);
        JobDescriptionVO submittedJob = jobDescriptionService.submit(userId, submitRequest);
        return createFromLegacyJob(
                userId,
                resumeId,
                getOwnedJobDescription(userId, submittedJob.getId()),
                providerSnapshot,
                modelSnapshot);
    }

    @Override
    @Transactional
    public OptimizationTaskVO createFromExisting(
            Long userId,
            Long resumeId,
            Long jobDescriptionId,
            String providerSnapshot,
            String modelSnapshot) {
        return createFromLegacyJob(
                userId,
                resumeId,
                getOwnedJobDescription(userId, jobDescriptionId),
                providerSnapshot,
                modelSnapshot);
    }

    @Override
    public OptimizationTaskVO get(Long userId, Long optimizationTaskId) {
        OptimizationTask task = getOwnedTask(userId, optimizationTaskId);
        ResumeVersion sourceVersion = getOwnedVersion(userId, task.getSourceResumeVersionId());
        JobTarget jobTarget = getOwnedJobTarget(userId, task.getJobTargetId());
        Resume resume = getOwnedResume(userId, sourceVersion.getResumeId());
        return toVO(task, sourceVersion, jobTarget, resume);
    }

    @Override
    public OptimizationTaskVO findByLegacyInputs(Long userId, Long resumeId, Long jobDescriptionId) {
        getOwnedResume(userId, resumeId);
        getOwnedJobDescription(userId, jobDescriptionId);
        JobTarget jobTarget = jobTargetMapper.selectOne(new LambdaQueryWrapper<JobTarget>()
                .eq(JobTarget::getUserId, userId)
                .eq(JobTarget::getLegacyJobDescriptionId, jobDescriptionId));
        if (jobTarget == null) {
            throw new BusinessException(404, "优化任务不存在");
        }

        List<ResumeVersion> targetVersions = resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getUserId, userId)
                .eq(ResumeVersion::getResumeId, resumeId)
                .eq(ResumeVersion::getJobTargetId, jobTarget.getId())
                .eq(ResumeVersion::getVersionType, VERSION_TARGETED));
        if (targetVersions.isEmpty()) {
            throw new BusinessException(404, "优化任务不存在");
        }

        OptimizationTask task = optimizationTaskMapper.selectOne(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getUserId, userId)
                .in(OptimizationTask::getTargetResumeVersionId, targetVersions.stream().map(ResumeVersion::getId).toList())
                .orderByDesc(OptimizationTask::getCreatedAt)
                .last("LIMIT 1"));
        if (task == null) {
            throw new BusinessException(404, "优化任务不存在");
        }
        return get(userId, task.getId());
    }

    @Override
    public ExecutionContext getExecutionContext(Long userId, Long optimizationTaskId) {
        OptimizationTask task = getOwnedTask(userId, optimizationTaskId);
        ResumeVersion sourceVersion = getOwnedVersion(userId, task.getSourceResumeVersionId());
        ResumeVersion targetVersion = getOwnedVersion(userId, task.getTargetResumeVersionId());
        JobTarget jobTarget = getOwnedJobTarget(userId, task.getJobTargetId());
        if (!sourceVersion.getResumeId().equals(targetVersion.getResumeId())) {
            throw new BusinessException(500, "优化任务的简历版本关系不一致");
        }
        return new ExecutionContext(
                task.getId(),
                sourceVersion.getResumeId(),
                jobTarget.getLegacyJobDescriptionId(),
                jobTarget.getId(),
                sourceVersion.getId(),
                targetVersion.getId());
    }

    @Override
    @Transactional
    public void attachAsyncTask(Long userId, Long optimizationTaskId, Long asyncTaskId) {
        OptimizationTask task = getOwnedTask(userId, optimizationTaskId);
        if (STATUS_SUCCESS.equals(task.getStatus())) {
            throw new BusinessException(409, "已完成的优化任务不能重试");
        }
        if (asyncTaskId == null) {
            throw new BusinessException(400, "后台任务 ID 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        updateOwnedTask(userId, optimizationTaskId, new UpdateWrapper<OptimizationTask>()
                .set("async_task_id", asyncTaskId)
                .set("status", STATUS_PENDING)
                .set("error_code", null)
                .set("error_message", null)
                .set("finished_at", null)
                .set("updated_at", now));
    }

    @Override
    @Transactional
    public void captureResumeSnapshot(Long userId, Long optimizationTaskId, String structuredContent) {
        if (structuredContent == null || structuredContent.isBlank()) {
            throw new BusinessException(400, "简历结构化内容不能为空");
        }
        OptimizationTask task = getOwnedTask(userId, optimizationTaskId);
        LocalDateTime now = LocalDateTime.now();
        updateOwnedVersion(userId, task.getSourceResumeVersionId(), structuredContent, now);
        updateOwnedVersion(userId, task.getTargetResumeVersionId(), structuredContent, now);
        updateOwnedTask(userId, optimizationTaskId, new UpdateWrapper<OptimizationTask>()
                .set("resume_input_snapshot", structuredContent)
                .set("updated_at", now));
    }

    @Override
    @Transactional
    public void markRunning(Long userId, Long optimizationTaskId) {
        getOwnedTask(userId, optimizationTaskId);
        LocalDateTime now = LocalDateTime.now();
        updateOwnedTask(userId, optimizationTaskId, new UpdateWrapper<OptimizationTask>()
                .set("status", STATUS_RUNNING)
                .set("started_at", now)
                .set("updated_at", now));
    }

    @Override
    @Transactional
    public void markSuccess(
            Long userId,
            Long optimizationTaskId,
            JobDescriptionVO parsedJob,
            AiJobMatchResult matchResult) {
        OptimizationTask task = getOwnedTask(userId, optimizationTaskId);
        if (matchResult == null || matchResult.getId() == null || !STATUS_SUCCESS.equals(matchResult.getMatchStatus())) {
            throw new BusinessException(400, "岗位分析结果未成功，不能完成优化任务");
        }
        ResumeVersion sourceVersion = getOwnedVersion(userId, task.getSourceResumeVersionId());
        JobTarget jobTarget = getOwnedJobTarget(userId, task.getJobTargetId());
        if (!sourceVersion.getResumeId().equals(matchResult.getResumeId())
                || !jobTarget.getLegacyJobDescriptionId().equals(matchResult.getJobDescriptionId())) {
            throw new BusinessException(400, "岗位分析结果不属于当前优化任务");
        }
        if (task.getResumeInputSnapshot() == null || task.getResumeInputSnapshot().isBlank()) {
            throw new BusinessException(500, "优化任务缺少简历输入快照");
        }

        LocalDateTime now = LocalDateTime.now();
        String promptSnapshot = serializePromptSnapshot(parsedJob, matchResult);
        updateOwnedTask(userId, optimizationTaskId, new UpdateWrapper<OptimizationTask>()
                .set("status", STATUS_SUCCESS)
                .set("analysis_result_id", matchResult.getId())
                .set("prompt_snapshot", promptSnapshot)
                .set("model_snapshot", matchResult.getModelName())
                .set("error_code", null)
                .set("error_message", null)
                .set("finished_at", now)
                .set("updated_at", now));

        if (parsedJob != null && parsedJob.getTitle() != null && !parsedJob.getTitle().isBlank()) {
            updateOwnedJobTargetTitle(userId, task.getJobTargetId(), parsedJob.getTitle(), now);
        }
    }

    @Override
    @Transactional
    public void markFailed(Long userId, Long optimizationTaskId, String errorCode, String errorMessage) {
        OptimizationTask task = getOwnedTask(userId, optimizationTaskId);
        if (STATUS_SUCCESS.equals(task.getStatus())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        updateOwnedTask(userId, optimizationTaskId, new UpdateWrapper<OptimizationTask>()
                .set("status", STATUS_FAILED)
                .set("error_code", truncate(errorCode, 100))
                .set("error_message", truncate(LogSanitizer.sanitize(errorMessage), ERROR_MESSAGE_MAX_LENGTH))
                .set("finished_at", now)
                .set("updated_at", now));
    }

    @Override
    public AiJobMatchResult getAnalysisResult(Long userId, Long optimizationTaskId) {
        OptimizationTask task = getOwnedTask(userId, optimizationTaskId);
        if (task.getAnalysisResultId() == null) {
            throw new BusinessException(404, "岗位分析结果尚未生成");
        }
        AiJobMatchResult result = aiJobMatchResultMapper.selectById(task.getAnalysisResultId());
        if (result == null) {
            throw new BusinessException(404, "岗位分析结果不存在");
        }
        ExecutionContext context = getExecutionContext(userId, optimizationTaskId);
        if (!context.resumeId().equals(result.getResumeId())
                || !context.jobDescriptionId().equals(result.getJobDescriptionId())) {
            throw new BusinessException(500, "优化任务与岗位分析结果不一致");
        }
        return result;
    }

    private OptimizationTaskVO createFromLegacyJob(
            Long userId,
            Long resumeId,
            JobDescription legacyJob,
            String providerSnapshot,
            String modelSnapshot) {
        Resume resume = getOwnedResume(userId, resumeId);
        if (legacyJob.getRawText() == null || legacyJob.getRawText().isBlank()) {
            throw new BusinessException(400, "目标岗位 JD 原文不能为空");
        }

        LocalDateTime now = LocalDateTime.now();
        JobTarget jobTarget = findOrCreateJobTarget(userId, legacyJob, now);
        ResumeVersion sourceVersion = createSourceVersion(userId, resume.getId(), now);
        ResumeVersion targetVersion = createTargetVersion(
                userId,
                resume.getId(),
                sourceVersion.getId(),
                jobTarget.getId(),
                now);

        OptimizationTask task = new OptimizationTask();
        task.setUserId(userId);
        task.setSourceResumeVersionId(sourceVersion.getId());
        task.setTargetResumeVersionId(targetVersion.getId());
        task.setJobTargetId(jobTarget.getId());
        task.setStatus(STATUS_PENDING);
        task.setJobInputSnapshot(legacyJob.getRawText());
        task.setPromptSnapshot(DEFAULT_PROMPT_SNAPSHOT);
        task.setRulesSnapshot(DEFAULT_RULES_SNAPSHOT);
        task.setProviderSnapshot(normalizeBlank(providerSnapshot));
        task.setModelSnapshot(normalizeBlank(modelSnapshot));
        task.setTemplateVersion(DEFAULT_TEMPLATE_VERSION);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        insertTask(task);
        return toVO(task, sourceVersion, jobTarget, resume);
    }

    private JobTarget findOrCreateJobTarget(Long userId, JobDescription legacyJob, LocalDateTime now) {
        JobTarget existing = jobTargetMapper.selectOne(new LambdaQueryWrapper<JobTarget>()
                .eq(JobTarget::getLegacyJobDescriptionId, legacyJob.getId()));
        if (existing != null) {
            if (!userId.equals(existing.getUserId())) {
                throw new BusinessException(404, "目标岗位不存在");
            }
            return existing;
        }

        JobTarget jobTarget = new JobTarget();
        jobTarget.setUserId(userId);
        jobTarget.setLegacyJobDescriptionId(legacyJob.getId());
        jobTarget.setTitle(legacyJob.getTitle());
        jobTarget.setRawJd(legacyJob.getRawText());
        jobTarget.setSourceType(SOURCE_USER_INPUT);
        jobTarget.setCreatedAt(now);
        jobTarget.setUpdatedAt(now);
        int rows = jobTargetMapper.insert(jobTarget);
        if (rows != 1 || jobTarget.getId() == null) {
            throw new BusinessException(500, "目标岗位保存失败");
        }
        return jobTarget;
    }

    private ResumeVersion createSourceVersion(Long userId, Long resumeId, LocalDateTime now) {
        ResumeVersion version = new ResumeVersion();
        version.setUserId(userId);
        version.setResumeId(resumeId);
        version.setVersionType(VERSION_SOURCE);
        version.setSourceType(SOURCE_PARSED_UPLOAD);
        version.setContentStatus(CONTENT_PENDING);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        insertVersion(version);
        return version;
    }

    private ResumeVersion createTargetVersion(
            Long userId,
            Long resumeId,
            Long sourceVersionId,
            Long jobTargetId,
            LocalDateTime now) {
        ResumeVersion version = new ResumeVersion();
        version.setUserId(userId);
        version.setResumeId(resumeId);
        version.setSourceVersionId(sourceVersionId);
        version.setJobTargetId(jobTargetId);
        version.setVersionType(VERSION_TARGETED);
        version.setSourceType(SOURCE_JOB_DERIVATION);
        version.setContentStatus(CONTENT_PENDING);
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        insertVersion(version);
        return version;
    }

    private void insertVersion(ResumeVersion version) {
        int rows = resumeVersionMapper.insert(version);
        if (rows != 1 || version.getId() == null) {
            throw new BusinessException(500, "简历版本保存失败");
        }
    }

    private void insertTask(OptimizationTask task) {
        int rows = optimizationTaskMapper.insert(task);
        if (rows != 1 || task.getId() == null) {
            throw new BusinessException(500, "优化任务保存失败");
        }
    }

    private Resume getOwnedResume(Long userId, Long resumeId) {
        validateUserId(userId);
        if (resumeId == null || resumeId <= 0) {
            throw new BusinessException(400, "简历 ID 必须大于 0");
        }
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId));
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        return resume;
    }

    private JobDescription getOwnedJobDescription(Long userId, Long jobDescriptionId) {
        validateUserId(userId);
        if (jobDescriptionId == null || jobDescriptionId <= 0) {
            throw new BusinessException(400, "目标岗位 ID 必须大于 0");
        }
        JobDescription jobDescription = jobDescriptionMapper.selectOne(new LambdaQueryWrapper<JobDescription>()
                .eq(JobDescription::getId, jobDescriptionId)
                .eq(JobDescription::getUserId, userId));
        if (jobDescription == null) {
            throw new BusinessException(404, "目标岗位不存在");
        }
        return jobDescription;
    }

    private OptimizationTask getOwnedTask(Long userId, Long optimizationTaskId) {
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
        return task;
    }

    private ResumeVersion getOwnedVersion(Long userId, Long versionId) {
        ResumeVersion version = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, versionId)
                .eq(ResumeVersion::getUserId, userId));
        if (version == null) {
            throw new BusinessException(404, "简历版本不存在");
        }
        return version;
    }

    private JobTarget getOwnedJobTarget(Long userId, Long jobTargetId) {
        JobTarget target = jobTargetMapper.selectOne(new LambdaQueryWrapper<JobTarget>()
                .eq(JobTarget::getId, jobTargetId)
                .eq(JobTarget::getUserId, userId));
        if (target == null) {
            throw new BusinessException(404, "目标岗位不存在");
        }
        return target;
    }

    private void updateOwnedTask(Long userId, Long taskId, UpdateWrapper<OptimizationTask> update) {
        update.eq("id", taskId).eq("user_id", userId);
        if (optimizationTaskMapper.update(null, update) != 1) {
            throw new BusinessException(404, "优化任务不存在");
        }
    }

    private void updateOwnedVersion(Long userId, Long versionId, String structuredContent, LocalDateTime now) {
        int rows = resumeVersionMapper.update(null, new UpdateWrapper<ResumeVersion>()
                .eq("id", versionId)
                .eq("user_id", userId)
                .set("content_status", CONTENT_READY)
                .set("structured_content", structuredContent)
                .set("updated_at", now));
        if (rows != 1) {
            throw new BusinessException(404, "简历版本不存在");
        }
    }

    private void updateOwnedJobTargetTitle(Long userId, Long jobTargetId, String title, LocalDateTime now) {
        int rows = jobTargetMapper.update(null, new UpdateWrapper<JobTarget>()
                .eq("id", jobTargetId)
                .eq("user_id", userId)
                .set("title", truncate(title.strip(), 200))
                .set("updated_at", now));
        if (rows != 1) {
            throw new BusinessException(404, "目标岗位不存在");
        }
    }

    private String serializePromptSnapshot(JobDescriptionVO parsedJob, AiJobMatchResult matchResult) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "jobParsePromptVersion", valueOrEmpty(parsedJob == null ? null : parsedJob.getPromptVersion()),
                    "matchPromptVersion", valueOrEmpty(matchResult.getPromptVersion())));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "任务配置快照保存失败");
        }
    }

    private OptimizationTaskVO toVO(
            OptimizationTask task,
            ResumeVersion sourceVersion,
            JobTarget jobTarget,
            Resume resume) {
        return OptimizationTaskVO.builder()
                .optimizationTaskId(task.getId())
                .sourceResumeVersionId(task.getSourceResumeVersionId())
                .targetResumeVersionId(task.getTargetResumeVersionId())
                .jobTargetId(task.getJobTargetId())
                .asyncTaskId(task.getAsyncTaskId())
                .analysisResultId(task.getAnalysisResultId())
                .status(task.getStatus())
                .jobTitle(jobTarget.getTitle())
                .resumeName(resume.getOriginalFilename())
                .providerSnapshot(task.getProviderSnapshot())
                .modelSnapshot(task.getModelSnapshot())
                .templateVersion(task.getTemplateVersion())
                .errorCode(task.getErrorCode())
                .errorMessage(task.getErrorMessage())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
