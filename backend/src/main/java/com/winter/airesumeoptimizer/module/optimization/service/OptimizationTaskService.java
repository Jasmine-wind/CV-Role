package com.winter.airesumeoptimizer.module.optimization.service;

import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.evidence.entity.EvidenceAnalysis;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import com.winter.airesumeoptimizer.module.optimization.vo.OptimizationTaskVO;

public interface OptimizationTaskService {

    /** Legacy source-compatible overload. New callers pass an immutable AI selection. */
    OptimizationTaskVO create(
            Long userId,
            Long resumeId,
            String jobTitle,
            String rawJobDescription,
            String providerSnapshot,
            String modelSnapshot);

    OptimizationTaskVO create(
            Long userId,
            Long resumeId,
            String jobTitle,
            String rawJobDescription,
            AiSelectionSnapshot selection);

    /** Legacy source-compatible overload. New callers pass an immutable AI selection. */
    OptimizationTaskVO createFromExisting(
            Long userId,
            Long resumeId,
            Long jobDescriptionId,
            String providerSnapshot,
            String modelSnapshot);

    OptimizationTaskVO createFromExisting(
            Long userId,
            Long resumeId,
            Long jobDescriptionId,
            AiSelectionSnapshot selection);

    OptimizationTaskVO get(Long userId, Long optimizationTaskId);

    OptimizationTaskVO findByLegacyInputs(Long userId, Long resumeId, Long jobDescriptionId);

    ExecutionContext getExecutionContext(Long userId, Long optimizationTaskId);

    void attachAsyncTask(Long userId, Long optimizationTaskId, Long asyncTaskId);

    void captureResumeSnapshot(Long userId, Long optimizationTaskId, String structuredContent);

    void markRunning(Long userId, Long optimizationTaskId);

    void markSuccess(
            Long userId,
            Long optimizationTaskId,
            JobDescriptionVO parsedJob,
            EvidenceAnalysis evidenceAnalysis);

    void markFailed(Long userId, Long optimizationTaskId, String errorCode, String errorMessage);

    /**
     * 兼容读取：返回任务关联的 V1 匹配结果。Phase 3 起新任务不再生成该结果，
     * 正式结果以 EvidenceAnalysis 为准。
     */
    AiJobMatchResult getLegacyAnalysisResult(Long userId, Long optimizationTaskId);

    record ExecutionContext(
            Long optimizationTaskId,
            Long resumeId,
            Long jobDescriptionId,
            Long jobTargetId,
            Long sourceResumeVersionId,
            Long targetResumeVersionId,
            AiSelectionSnapshot aiSelection) {

        public ExecutionContext(
                Long optimizationTaskId,
                Long resumeId,
                Long jobDescriptionId,
                Long jobTargetId,
                Long sourceResumeVersionId,
                Long targetResumeVersionId) {
            this(
                    optimizationTaskId,
                    resumeId,
                    jobDescriptionId,
                    jobTargetId,
                    sourceResumeVersionId,
                    targetResumeVersionId,
                    null);
        }
    }
}
