package com.winter.airesumeoptimizer.module.task.service;

import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskErrorCode;

public interface AsyncTaskFailureHandler {

    void markFailed(Long taskId, AsyncTaskErrorCode errorCode, Throwable exception);

    AsyncTaskErrorCode resolveErrorCode(Throwable exception);
}
