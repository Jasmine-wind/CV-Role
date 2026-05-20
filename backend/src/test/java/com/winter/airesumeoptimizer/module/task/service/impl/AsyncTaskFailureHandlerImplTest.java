package com.winter.airesumeoptimizer.module.task.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.mock;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.AiClientException;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskErrorCode;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class AsyncTaskFailureHandlerImplTest {

    private final AsyncTaskService asyncTaskService = mock(AsyncTaskService.class);
    private final AsyncTaskFailureHandlerImpl handler = new AsyncTaskFailureHandlerImpl(asyncTaskService);

    @Test
    void markFailedShouldStoreFriendlyMessage() {
        RuntimeException exception = new RuntimeException("connect timeout to internal host");

        handler.markFailed(100L, AsyncTaskErrorCode.AI_SERVICE_UNAVAILABLE, exception);

        verify(asyncTaskService).markFailed(
                100L,
                "AI_SERVICE_UNAVAILABLE",
                "AI 服务暂时不可用，请稍后重试");
        verifyNoMoreInteractions(asyncTaskService);
    }

    @Test
    void resolveErrorCodeShouldClassifyCommonFailures() {
        assertThat(handler.resolveErrorCode(new RejectedExecutionException()))
                .isEqualTo(AsyncTaskErrorCode.TASK_REJECTED);
        assertThat(handler.resolveErrorCode(new TimeoutException()))
                .isEqualTo(AsyncTaskErrorCode.AI_TIMEOUT);
        assertThat(handler.resolveErrorCode(new AiClientException("bad response")))
                .isEqualTo(AsyncTaskErrorCode.AI_SERVICE_UNAVAILABLE);
        assertThat(handler.resolveErrorCode(new BusinessException(502, "AI JSON 解析失败")))
                .isEqualTo(AsyncTaskErrorCode.AI_JSON_PARSE_FAILED);
        assertThat(handler.resolveErrorCode(new BusinessException(401, "请先登录")))
                .isEqualTo(AsyncTaskErrorCode.PERMISSION_DENIED);
        assertThat(handler.resolveErrorCode(new BusinessException(400, "简历尚未解析，不能生成向量")))
                .isEqualTo(AsyncTaskErrorCode.PARSE_RESULT_NOT_FOUND);
        assertThat(handler.resolveErrorCode(new BusinessException(400, "向量生成失败")))
                .isEqualTo(AsyncTaskErrorCode.EMBEDDING_FAILED);
    }
}
