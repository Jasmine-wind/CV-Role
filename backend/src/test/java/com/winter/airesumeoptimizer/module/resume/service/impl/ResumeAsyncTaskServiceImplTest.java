package com.winter.airesumeoptimizer.module.resume.service.impl;

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
import org.springframework.core.task.SyncTaskExecutor;

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
            new SyncTaskExecutor());

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
    }
}
