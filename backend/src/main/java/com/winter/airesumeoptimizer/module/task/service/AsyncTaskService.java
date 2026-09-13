package com.winter.airesumeoptimizer.module.task.service;

import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskType;
import com.winter.airesumeoptimizer.module.task.vo.AsyncTaskVO;

public interface AsyncTaskService {

    Long createTask(Long userId, AsyncTaskType taskType, String bizType, Long bizId);

    void markRunning(Long taskId, String message);

    void updateStage(Long taskId, String message);

    void updateProgress(Long taskId, int progress, String message);

    void markSuccess(Long taskId, String resultType, Long resultId, String resultSummary);

    void markFailed(Long taskId, String errorCode, String errorMessage);

    /**
     * Durably fences work which belongs to a resource that is being deleted.
     * This is an internal lifecycle seam; it is intentionally not exposed as a
     * user-facing task cancellation API.
     */
    void cancelActiveTasks(Long userId, String bizType, Long bizId);

    /**
     * Returns whether a worker may still perform work for the owned task.
     * Terminal and missing tasks are inactive.
     */
    boolean isActive(Long userId, Long taskId);

    AsyncTaskVO getTask(Long taskId, Long currentUserId);

    AsyncTaskVO findActiveTask(Long userId, AsyncTaskType taskType, String bizType, Long bizId);
}
