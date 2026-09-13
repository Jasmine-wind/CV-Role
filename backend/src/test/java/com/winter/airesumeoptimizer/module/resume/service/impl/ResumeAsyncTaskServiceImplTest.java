package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;
import com.winter.airesumeoptimizer.module.analysis.service.ResumeAnalysisService;
import com.winter.airesumeoptimizer.module.embedding.service.ResumeEmbeddingService;
import com.winter.airesumeoptimizer.module.embedding.vo.ResumeEmbeddingRecordVO;
import com.winter.airesumeoptimizer.module.embedding.vo.ResumeEmbeddingSummaryVO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeDetailVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeParseResultVO;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskErrorCode;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskType;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskFailureHandler;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskService;
import com.winter.airesumeoptimizer.module.task.vo.AsyncTaskVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import com.winter.airesumeoptimizer.module.task.service.CommittedTaskDispatcher;
import java.sql.Connection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

class ResumeAsyncTaskServiceImplTest {

    private final ResumeService resumeService = mock(ResumeService.class);
    private final ResumeAnalysisService resumeAnalysisService = mock(ResumeAnalysisService.class);
    private final ResumeEmbeddingService resumeEmbeddingService = mock(ResumeEmbeddingService.class);
    private final AsyncTaskService asyncTaskService = mock(AsyncTaskService.class);
    private final AsyncTaskFailureHandler asyncTaskFailureHandler = mock(AsyncTaskFailureHandler.class);
    private final ResumeAsyncTaskServiceImpl service = new ResumeAsyncTaskServiceImpl(
            resumeService,
            resumeAnalysisService,
            resumeEmbeddingService,
            asyncTaskService,
            asyncTaskFailureHandler,
            worker -> CompletableFuture.runAsync(worker).join());

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = AsyncTaskType.class,
            names = {"RESUME_PARSE", "RESUME_DIAGNOSIS", "RESUME_EMBEDDING"})
    void submissionMustWaitForCommit(AsyncTaskType type) {
        mockResumeDetail();
        mockCreateTask(100L, type);
        // Stop at the worker fence: this test concerns dispatch, not provider behavior.
        when(asyncTaskService.isActive(1L, 100L)).thenReturn(false);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            switch (type) {
                case RESUME_PARSE -> service.submitParseTask(1L, 10L, null);
                case RESUME_DIAGNOSIS -> service.submitDiagnosisTask(1L, 10L);
                default -> service.submitEmbeddingTask(1L, 10L);
            }
            verify(asyncTaskService).createTask(1L, type, "RESUME", 10L);
            verify(asyncTaskService, never()).markRunning(anyLong(), anyString());
            org.springframework.transaction.support.TransactionSynchronizationUtils.triggerAfterCommit();
            verify(asyncTaskService, never()).markRunning(anyLong(), anyString());
            org.springframework.transaction.support.TransactionSynchronizationUtils.triggerAfterCompletion(0);
            verify(asyncTaskService).markRunning(eq(100L), anyString());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clear();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 2})
    void rolledBackOrUnknownSubmissionNeverDispatches(int completionStatus) {
        mockResumeDetail();
        mockCreateTask(100L, AsyncTaskType.RESUME_PARSE);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.submitParseTask(1L, 10L, null);
            TransactionSynchronizationUtils.triggerAfterCompletion(completionStatus);
            verify(asyncTaskService, never()).markRunning(anyLong(), anyString());
            verify(resumeService, never()).parse(eq(1L), eq(10L), isNull());
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void rejectionAfterCommitUsesFreshTransactionAndRespectsCancellation(boolean canceled) throws Exception {
        mockResumeDetail();
        mockCreateTask(100L, AsyncTaskType.RESUME_PARSE);
        DataSource dataSource = mock(DataSource.class);
        Connection submission = mock(Connection.class);
        Connection failure = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(submission, failure);
        when(submission.getAutoCommit()).thenReturn(true);
        when(failure.getAutoCommit()).thenReturn(true);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        RejectedExecutionException rejection = new RejectedExecutionException("full");
        when(asyncTaskService.isActive(1L, 100L)).thenAnswer(invocation -> {
            verify(submission).commit();
            assertThat(((ConnectionHolder) TransactionSynchronizationManager.getResource(dataSource))
                    .getConnection()).isSameAs(failure);
            return !canceled;
        });
        ResumeAsyncTaskServiceImpl transactionalService = new ResumeAsyncTaskServiceImpl(
                resumeService, resumeAnalysisService, resumeEmbeddingService, asyncTaskService,
                asyncTaskFailureHandler, worker -> { throw rejection; }, new CommittedTaskDispatcher(manager));

        new TransactionTemplate(manager).executeWithoutResult(status -> {
            transactionalService.submitParseTask(1L, 10L, null);
            verify(resumeService).lockForAsyncTaskSubmission(1L, 10L);
            verify(asyncTaskFailureHandler, never()).markFailed(any(), any(), any());
            verify(asyncTaskService, never()).isActive(1L, 100L);
        });

        verify(failure).commit();
        verify(asyncTaskService, never()).markRunning(anyLong(), anyString());
        if (canceled) {
            verify(asyncTaskFailureHandler, never()).markFailed(any(), any(), any());
        } else {
            verify(asyncTaskFailureHandler).markFailed(100L, AsyncTaskErrorCode.TASK_REJECTED, rejection);
        }
        assertThat(TransactionSynchronizationManager.hasResource(dataSource)).isFalse();
    }

    @Test
    void submitParseTaskShouldRunAndMarkSuccess() {
        mockResumeDetail();
        mockCreateTask(100L, AsyncTaskType.RESUME_PARSE);
        when(resumeService.parse(eq(1L), eq(10L), isNull()))
                .thenReturn(ResumeParseResultVO.builder()
                        .resumeId(10L)
                        .parseStatus("SUCCESS")
                        .build());

        service.submitParseTask(1L, 10L, null);

        verify(asyncTaskService).markRunning(100L, "简历解析任务已启动");
        verify(asyncTaskService).markSuccess(100L, "RESUME_PARSE_RESULT", 10L, "简历解析完成");
    }

    @Test
    void submitParseTaskShouldMarkFailedWhenParseResultFailed() {
        mockResumeDetail();
        mockCreateTask(100L, AsyncTaskType.RESUME_PARSE);
        when(resumeService.parse(eq(1L), eq(10L), isNull()))
                .thenReturn(ResumeParseResultVO.builder()
                        .resumeId(10L)
                        .parseStatus("FAILED")
                        .errorMessage("简历文本为空")
                        .build());

        service.submitParseTask(1L, 10L, null);

        verify(asyncTaskService).markFailed(100L, AsyncTaskErrorCode.FILE_PARSE_FAILED.name(), "简历文本为空");
    }

    @Test
    void submitDiagnosisTaskShouldRunAndMarkSuccess() {
        mockResumeDetail();
        mockCreateTask(101L, AsyncTaskType.RESUME_DIAGNOSIS);
        ResumeAiAnalysis analysis = new ResumeAiAnalysis();
        analysis.setId(201L);
        analysis.setAnalysisStatus("SUCCESS");
        when(resumeAnalysisService.analyze(1L, 10L)).thenReturn(analysis);

        service.submitDiagnosisTask(1L, 10L);

        verify(asyncTaskService).updateStage(101L, "正在准备简历上下文");
        verify(asyncTaskService).updateStage(101L, "正在调用 AI 模型");
        verify(asyncTaskService).updateStage(101L, "正在保存诊断结果");
        verify(asyncTaskService, never()).updateProgress(anyLong(), org.mockito.ArgumentMatchers.anyInt(), anyString());
        verify(asyncTaskService).markSuccess(101L, "RESUME_AI_ANALYSIS", 201L, "简历诊断完成");
    }

    @Test
    void submitEmbeddingTaskShouldMarkFailedWhenSummaryNotSuccess() {
        mockResumeDetail();
        mockCreateTask(102L, AsyncTaskType.RESUME_EMBEDDING);
        when(resumeEmbeddingService.generate(1L, 10L))
                .thenReturn(ResumeEmbeddingSummaryVO.builder()
                        .resumeId(10L)
                        .embeddingStatus("PARTIAL_SUCCESS")
                        .build());

        service.submitEmbeddingTask(1L, 10L);

        verify(asyncTaskService).updateStage(102L, "正在读取解析结果");
        verify(asyncTaskService).updateStage(102L, "正在调用 Embedding 模型");
        verify(asyncTaskService, never()).updateProgress(anyLong(), org.mockito.ArgumentMatchers.anyInt(), anyString());
        verify(asyncTaskService).markFailed(102L, AsyncTaskErrorCode.EMBEDDING_FAILED.name(), "简历向量生成未完全成功");
    }

    @Test
    void submitEmbeddingTaskShouldExposeChunkFailureReason() {
        mockResumeDetail();
        mockCreateTask(102L, AsyncTaskType.RESUME_EMBEDDING);
        when(resumeEmbeddingService.generate(1L, 10L))
                .thenReturn(ResumeEmbeddingSummaryVO.builder()
                        .resumeId(10L)
                        .embeddingStatus("FAILED")
                        .records(List.of(ResumeEmbeddingRecordVO.builder()
                                .embeddingStatus("FAILED")
                                .errorMessage("Embedding base-url 未配置")
                                .build()))
                        .build());

        service.submitEmbeddingTask(1L, 10L);

        verify(asyncTaskService).markFailed(102L, AsyncTaskErrorCode.EMBEDDING_FAILED.name(), "Embedding base-url 未配置");
    }

    @Test
    void canceledTaskShouldNotStartResumeWork() {
        mockResumeDetail();
        mockCreateTask(100L, AsyncTaskType.RESUME_PARSE);
        when(asyncTaskService.isActive(1L, 100L)).thenReturn(false);

        service.submitParseTask(1L, 10L, null);

        verify(resumeService, never()).parse(eq(1L), eq(10L), isNull());
        verify(asyncTaskService, never()).markSuccess(anyLong(), anyString(), anyLong(), anyString());
    }

    @Test
    void cancellationAfterParsePreventsLateSuccessCallback() {
        mockResumeDetail();
        mockCreateTask(100L, AsyncTaskType.RESUME_PARSE);
        when(asyncTaskService.isActive(1L, 100L)).thenReturn(true, true, false);
        when(resumeService.parse(eq(1L), eq(10L), isNull()))
                .thenReturn(ResumeParseResultVO.builder()
                        .resumeId(10L)
                        .parseStatus("SUCCESS")
                        .build());

        service.submitParseTask(1L, 10L, null);

        verify(resumeService).parse(eq(1L), eq(10L), isNull());
        verify(asyncTaskService, never()).markSuccess(anyLong(), anyString(), anyLong(), anyString());
    }

    @Test
    void submitTaskShouldReturnActiveTaskWhenRunningTaskExists() {
        mockResumeDetail();
        AsyncTaskVO activeTask = AsyncTaskVO.builder()
                .taskId(100L)
                .taskType("RESUME_PARSE")
                .bizType("RESUME")
                .bizId(10L)
                .status("RUNNING")
                .progress(30)
                .build();
        when(asyncTaskService.findActiveTask(1L, AsyncTaskType.RESUME_PARSE, "RESUME", 10L))
                .thenReturn(activeTask);

        service.submitParseTask(1L, 10L, null);

        verify(asyncTaskService, never()).createTask(1L, AsyncTaskType.RESUME_PARSE, "RESUME", 10L);
        verify(resumeService, never()).parse(eq(1L), eq(10L), isNull());
    }

    private void mockResumeDetail() {
        when(resumeService.getDetail(1L, 10L))
                .thenReturn(ResumeDetailVO.builder()
                        .id(10L)
                        .build());
    }

    private void mockCreateTask(Long taskId, AsyncTaskType taskType) {
        when(asyncTaskService.findActiveTask(1L, taskType, "RESUME", 10L)).thenReturn(null);
        when(asyncTaskService.createTask(1L, taskType, "RESUME", 10L)).thenReturn(taskId);
        when(asyncTaskService.getTask(taskId, 1L))
                .thenReturn(AsyncTaskVO.builder()
                        .taskId(taskId)
                        .taskType(taskType.name())
                        .bizType("RESUME")
                        .bizId(10L)
                        .status("PENDING")
                        .progress(0)
                        .build());
        when(asyncTaskService.isActive(1L, taskId)).thenReturn(true);
    }
}
