package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;
import com.winter.airesumeoptimizer.module.analysis.service.ResumeAnalysisService;
import com.winter.airesumeoptimizer.module.embedding.service.ResumeEmbeddingService;
import com.winter.airesumeoptimizer.module.embedding.vo.ResumeEmbeddingRecordVO;
import com.winter.airesumeoptimizer.module.embedding.vo.ResumeEmbeddingSummaryVO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseOptionsDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAsyncTaskService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeParseResultVO;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskErrorCode;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskType;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskFailureHandler;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskService;
import com.winter.airesumeoptimizer.module.task.vo.AsyncTaskVO;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ResumeAsyncTaskServiceImpl implements ResumeAsyncTaskService {

    private static final String BIZ_TYPE_RESUME = "RESUME";
    private static final String STATUS_SUCCESS = "SUCCESS";

    private final ResumeService resumeService;
    private final ResumeAnalysisService resumeAnalysisService;
    private final ResumeEmbeddingService resumeEmbeddingService;
    private final AsyncTaskService asyncTaskService;
    private final AsyncTaskFailureHandler asyncTaskFailureHandler;
    private final TaskExecutor taskExecutor;

    public ResumeAsyncTaskServiceImpl(
            ResumeService resumeService,
            ResumeAnalysisService resumeAnalysisService,
            ResumeEmbeddingService resumeEmbeddingService,
            AsyncTaskService asyncTaskService,
            AsyncTaskFailureHandler asyncTaskFailureHandler,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.resumeService = resumeService;
        this.resumeAnalysisService = resumeAnalysisService;
        this.resumeEmbeddingService = resumeEmbeddingService;
        this.asyncTaskService = asyncTaskService;
        this.asyncTaskFailureHandler = asyncTaskFailureHandler;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public AsyncTaskVO submitParseTask(Long userId, Long resumeId, ResumeParseOptionsDTO options) {
        resumeService.getDetail(userId, resumeId);
        return submitTask(userId, resumeId, AsyncTaskType.RESUME_PARSE,
                taskId -> runParseTask(taskId, userId, resumeId, options));
    }

    @Override
    public AsyncTaskVO submitDiagnosisTask(Long userId, Long resumeId) {
        resumeService.getDetail(userId, resumeId);
        return submitTask(userId, resumeId, AsyncTaskType.RESUME_DIAGNOSIS,
                taskId -> runDiagnosisTask(taskId, userId, resumeId));
    }

    @Override
    public AsyncTaskVO submitEmbeddingTask(Long userId, Long resumeId) {
        resumeService.getDetail(userId, resumeId);
        return submitTask(userId, resumeId, AsyncTaskType.RESUME_EMBEDDING,
                taskId -> runEmbeddingTask(taskId, userId, resumeId));
    }

    private AsyncTaskVO submitTask(Long userId, Long resumeId, AsyncTaskType taskType, TaskRunner runner) {
        AsyncTaskVO activeTask = asyncTaskService.findActiveTask(userId, taskType, BIZ_TYPE_RESUME, resumeId);
        if (activeTask != null) {
            return activeTask;
        }

        Long taskId = asyncTaskService.createTask(userId, taskType, BIZ_TYPE_RESUME, resumeId);
        try {
            taskExecutor.execute(() -> runner.run(taskId));
        } catch (RejectedExecutionException exception) {
            asyncTaskFailureHandler.markFailed(taskId, AsyncTaskErrorCode.TASK_REJECTED, exception);
        }
        return asyncTaskService.getTask(taskId, userId);
    }

    private void runParseTask(Long taskId, Long userId, Long resumeId, ResumeParseOptionsDTO options) {
        try {
            asyncTaskService.markRunning(taskId, "简历解析任务已启动");
            asyncTaskService.updateStage(taskId, "正在读取简历文件");
            asyncTaskService.updateStage(taskId, "正在提取、清洗和结构化解析");
            ResumeParseResultVO result = resumeService.parse(userId, resumeId, options);
            if (!STATUS_SUCCESS.equals(result.getParseStatus())) {
                asyncTaskService.markFailed(
                        taskId,
                        AsyncTaskErrorCode.FILE_PARSE_FAILED.name(),
                        firstPresent(result.getErrorMessage(), "简历解析失败"));
                return;
            }
            asyncTaskService.updateStage(taskId, "正在保存解析结果");
            asyncTaskService.markSuccess(taskId, "RESUME_PARSE_RESULT", resumeId, "简历解析完成");
        } catch (RuntimeException exception) {
            asyncTaskFailureHandler.markFailed(taskId, null, exception);
        }
    }

    private void runDiagnosisTask(Long taskId, Long userId, Long resumeId) {
        try {
            asyncTaskService.markRunning(taskId, "简历诊断任务已启动");
            asyncTaskService.updateProgress(taskId, 20, "正在准备简历上下文");
            asyncTaskService.updateProgress(taskId, 50, "正在调用 AI 模型");
            ResumeAiAnalysis analysis = resumeAnalysisService.analyze(userId, resumeId);
            if (!STATUS_SUCCESS.equals(analysis.getAnalysisStatus())) {
                asyncTaskService.markFailed(
                        taskId,
                        AsyncTaskErrorCode.AI_RESPONSE_INVALID.name(),
                        firstPresent(analysis.getErrorMessage(), "简历诊断失败"));
                return;
            }
            asyncTaskService.updateProgress(taskId, 90, "正在保存诊断结果");
            asyncTaskService.markSuccess(taskId, "RESUME_AI_ANALYSIS", analysis.getId(), "简历诊断完成");
        } catch (RuntimeException exception) {
            asyncTaskFailureHandler.markFailed(taskId, null, exception);
        }
    }

    private void runEmbeddingTask(Long taskId, Long userId, Long resumeId) {
        try {
            asyncTaskService.markRunning(taskId, "简历向量生成任务已启动");
            asyncTaskService.updateProgress(taskId, 20, "正在读取解析结果");
            asyncTaskService.updateProgress(taskId, 50, "正在调用 Embedding 模型");
            ResumeEmbeddingSummaryVO summary = resumeEmbeddingService.generate(userId, resumeId);
            if (!STATUS_SUCCESS.equals(summary.getEmbeddingStatus())) {
                asyncTaskService.markFailed(
                        taskId,
                        AsyncTaskErrorCode.EMBEDDING_FAILED.name(),
                        buildEmbeddingFailureMessage(summary));
                return;
            }
            asyncTaskService.updateProgress(taskId, 90, "正在保存向量结果");
            asyncTaskService.markSuccess(taskId, "RESUME_EMBEDDING", resumeId, "简历向量生成完成");
        } catch (RuntimeException exception) {
            asyncTaskFailureHandler.markFailed(taskId, null, exception);
        }
    }

    private String firstPresent(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String buildEmbeddingFailureMessage(ResumeEmbeddingSummaryVO summary) {
        List<ResumeEmbeddingRecordVO> records = summary.getRecords();
        if (records != null) {
            for (ResumeEmbeddingRecordVO record : records) {
                if ("FAILED".equals(record.getEmbeddingStatus())
                        && record.getErrorMessage() != null
                        && !record.getErrorMessage().isBlank()) {
                    return firstPresent(record.getErrorMessage(), "简历向量生成失败");
                }
            }
        }
        return "简历向量生成未完全成功";
    }

    @FunctionalInterface
    private interface TaskRunner {
        void run(Long taskId);
    }
}
