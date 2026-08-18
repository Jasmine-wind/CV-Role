package com.winter.airesumeoptimizer.module.optimization.service;

import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import com.winter.airesumeoptimizer.module.optimization.vo.OptimizationTaskVO;

public interface OptimizationTaskService {

    OptimizationTaskVO create(
            Long userId,
            Long resumeId,
            String jobTitle,
            String rawJobDescription,
            String providerSnapshot,
            String modelSnapshot);

    OptimizationTaskVO createFromExisting(
            Long userId,
            Long resumeId,
            Long jobDescriptionId,
            String providerSnapshot,
            String modelSnapshot);

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
            AiJobMatchResult matchResult);

    void markFailed(Long userId, Long optimizationTaskId, String errorCode, String errorMessage);

    AiJobMatchResult getAnalysisResult(Long userId, Long optimizationTaskId);

    record ExecutionContext(
            Long optimizationTaskId,
            Long resumeId,
            Long jobDescriptionId,
            Long jobTargetId,
            Long sourceResumeVersionId,
            Long targetResumeVersionId) {
    }
}
