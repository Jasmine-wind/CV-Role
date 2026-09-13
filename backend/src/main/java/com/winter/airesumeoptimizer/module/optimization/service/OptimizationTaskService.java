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

    /** 删除当前用户的一条岗位优化记录，不删除源简历。 */
    void delete(Long userId, Long optimizationTaskId);

    java.util.List<OptimizationTaskVO> listRecent(Long userId, int limit);

    /** Returns only the task-frozen SOURCE document; never falls back to the resume's current canonical pointer. */
    String getFrozenSourceCanonicalDocument(Long userId, Long optimizationTaskId);

    OptimizationTaskVO findByLegacyInputs(Long userId, Long resumeId, Long jobDescriptionId);

    ExecutionContext getExecutionContext(Long userId, Long optimizationTaskId);

    /**
     * Locks the task's source Resume and then the task row for async submission. The caller
     * must keep its transaction open until async_tasks insertion and attachment commit.
     */
    void lockForAsyncSubmission(Long userId, Long optimizationTaskId);

    void attachAsyncTask(Long userId, Long optimizationTaskId, Long asyncTaskId);

    /**
     * Compatibility backfill for pre-Slice-A tasks. New tasks freeze their confirmed SOURCE at creation;
     * this method must never replace an already-populated SOURCE.
     */
    void captureResumeSnapshot(Long userId, Long optimizationTaskId, String structuredContent);

    void markRunning(Long userId, Long optimizationTaskId);

    /**
     * Execution-fenced callback used by the asynchronous analysis worker. The callback may
     * mutate the formal task only while this exact async execution is still attached and active.
     */
    default void markRunning(Long userId, Long optimizationTaskId, Long asyncTaskId) {
        markRunning(userId, optimizationTaskId);
    }

    void markSuccess(
            Long userId,
            Long optimizationTaskId,
            JobDescriptionVO parsedJob,
            EvidenceAnalysis evidenceAnalysis);

    /** Execution-fenced variant for asynchronous analysis callbacks. */
    default void markSuccess(
            Long userId,
            Long optimizationTaskId,
            Long asyncTaskId,
            JobDescriptionVO parsedJob,
            EvidenceAnalysis evidenceAnalysis) {
        markSuccess(userId, optimizationTaskId, parsedJob, evidenceAnalysis);
    }

    void markFailed(Long userId, Long optimizationTaskId, String errorCode, String errorMessage);

    /** Execution-fenced variant for asynchronous analysis callbacks. */
    default void markFailed(
            Long userId,
            Long optimizationTaskId,
            Long asyncTaskId,
            String errorCode,
            String errorMessage) {
        markFailed(userId, optimizationTaskId, errorCode, errorMessage);
    }

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
            AiSelectionSnapshot aiSelection,
            String frozenResumeSnapshot) {

        public ExecutionContext(
                Long optimizationTaskId,
                Long resumeId,
                Long jobDescriptionId,
                Long jobTargetId,
                Long sourceResumeVersionId,
                Long targetResumeVersionId,
                AiSelectionSnapshot aiSelection) {
            this(
                    optimizationTaskId,
                    resumeId,
                    jobDescriptionId,
                    jobTargetId,
                    sourceResumeVersionId,
                    targetResumeVersionId,
                    aiSelection,
                    null);
        }

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
                    null,
                    null);
        }
    }
}
