package com.winter.airesumeoptimizer.module.task.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.infra.ai.AiClientException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageException;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskErrorCode;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskFailureHandler;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class AsyncTaskFailureHandlerImpl implements AsyncTaskFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskFailureHandlerImpl.class);

    private final AsyncTaskService asyncTaskService;

    public AsyncTaskFailureHandlerImpl(AsyncTaskService asyncTaskService) {
        this.asyncTaskService = asyncTaskService;
    }

    @Override
    public void markFailed(Long taskId, AsyncTaskErrorCode errorCode, Throwable exception) {
        AsyncTaskErrorCode resolvedCode = errorCode == null ? resolveErrorCode(exception) : errorCode;
        log.warn("Async task failed, taskId={}, errorCode={}, exceptionType={}, message={}",
                taskId,
                resolvedCode.name(),
                exception == null ? "unknown" : exception.getClass().getName(),
                exception == null ? null : LogSanitizer.sanitize(exception.getMessage()));
        asyncTaskService.markFailed(taskId, resolvedCode.name(), resolvedCode.getUserMessage());
    }

    @Override
    public AsyncTaskErrorCode resolveErrorCode(Throwable exception) {
        if (exception instanceof RejectedExecutionException) {
            return AsyncTaskErrorCode.TASK_REJECTED;
        }

        if (exception instanceof TimeoutException) {
            return AsyncTaskErrorCode.AI_TIMEOUT;
        }

        if (exception instanceof FileStorageException) {
            return AsyncTaskErrorCode.FILE_READ_FAILED;
        }

        if (exception instanceof AiClientException) {
            return AsyncTaskErrorCode.AI_SERVICE_UNAVAILABLE;
        }

        if (exception instanceof DataAccessException) {
            return AsyncTaskErrorCode.DATABASE_ERROR;
        }

        if (exception instanceof BusinessException businessException) {
            return resolveBusinessErrorCode(businessException);
        }

        return AsyncTaskErrorCode.UNKNOWN_ERROR;
    }

    private AsyncTaskErrorCode resolveBusinessErrorCode(BusinessException exception) {
        String message = exception.getMessage();
        if (message == null) {
            return AsyncTaskErrorCode.UNKNOWN_ERROR;
        }

        if (message.contains("无权限") || message.contains("未登录") || message.contains("请先登录")) {
            return AsyncTaskErrorCode.PERMISSION_DENIED;
        }

        if (message.contains("文件读取")) {
            return AsyncTaskErrorCode.FILE_READ_FAILED;
        }

        if (message.contains("文件") && (message.contains("解析") || message.contains("提取"))) {
            return AsyncTaskErrorCode.FILE_PARSE_FAILED;
        }

        if (message.contains("尚未解析")
                || message.contains("解析未成功")
                || message.contains("解析文本为空")) {
            return AsyncTaskErrorCode.PARSE_RESULT_NOT_FOUND;
        }

        if (message.contains("Embedding") || message.contains("向量")) {
            return AsyncTaskErrorCode.EMBEDDING_FAILED;
        }

        if (message.contains("JSON") || message.contains("格式") || message.contains("返回结果")) {
            return AsyncTaskErrorCode.AI_JSON_PARSE_FAILED;
        }

        if (message.contains("AI") || message.contains("模型")) {
            return AsyncTaskErrorCode.AI_RESPONSE_INVALID;
        }

        return AsyncTaskErrorCode.UNKNOWN_ERROR;
    }
}
