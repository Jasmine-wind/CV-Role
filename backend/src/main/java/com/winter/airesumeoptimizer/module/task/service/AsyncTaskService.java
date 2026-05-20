package com.winter.airesumeoptimizer.module.task.service;

import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskType;
import com.winter.airesumeoptimizer.module.task.vo.AsyncTaskVO;

public interface AsyncTaskService {

    Long createTask(Long userId, AsyncTaskType taskType, String bizType, Long bizId);

    void markRunning(Long taskId, String message);

    void updateProgress(Long taskId, int progress, String message);

    void markSuccess(Long taskId, String resultType, Long resultId, String resultSummary);

    void markFailed(Long taskId, String errorCode, String errorMessage);

    AsyncTaskVO getTask(Long taskId, Long currentUserId);

    AsyncTaskVO findActiveTask(Long userId, AsyncTaskType taskType, String bizType, Long bizId);
}
