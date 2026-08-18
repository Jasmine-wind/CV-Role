package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.JobAnalysisStartRequestDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchService;
import com.winter.airesumeoptimizer.module.analysis.service.JobAnalysisService;
import com.winter.airesumeoptimizer.module.analysis.vo.JobAnalysisStartVO;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionSubmitDTO;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionParseService;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionService;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeParseResultVO;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskErrorCode;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskType;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskFailureHandler;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskService;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class JobAnalysisServiceImpl implements JobAnalysisService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String BIZ_TYPE_JOB_ANALYSIS = "JOB_ANALYSIS";
    private static final int TITLE_MAX_LENGTH = 200;

    private final ResumeService resumeService;
    private final JobDescriptionService jobDescriptionService;
    private final JobDescriptionParseService jobDescriptionParseService;
    private final AiJobMatchService aiJobMatchService;
    private final AsyncTaskService asyncTaskService;
    private final AsyncTaskFailureHandler asyncTaskFailureHandler;
    private final TaskExecutor taskExecutor;

    public JobAnalysisServiceImpl(
            ResumeService resumeService,
            JobDescriptionService jobDescriptionService,
            JobDescriptionParseService jobDescriptionParseService,
            AiJobMatchService aiJobMatchService,
            AsyncTaskService asyncTaskService,
            AsyncTaskFailureHandler asyncTaskFailureHandler,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor) {
        this.resumeService = resumeService;
        this.jobDescriptionService = jobDescriptionService;
        this.jobDescriptionParseService = jobDescriptionParseService;
        this.aiJobMatchService = aiJobMatchService;
        this.asyncTaskService = asyncTaskService;
        this.asyncTaskFailureHandler = asyncTaskFailureHandler;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public JobAnalysisStartVO start(Long userId, JobAnalysisStartRequestDTO request) {
        if (request == null) {
            throw new BusinessException(400, "岗位分析请求不能为空");
        }
        validateResumeId(request.getResumeId());
        if (request.getJobDescription() == null || request.getJobDescription().isBlank()) {
            throw new BusinessException(400, "目标岗位 JD 不能为空");
        }
        if (request.getJobDescription().length() > 10000) {
            throw new BusinessException(400, "目标岗位 JD 不能超过 10000 个字符");
        }
        resumeService.getDetail(userId, request.getResumeId());

        JobDescriptionSubmitDTO jobRequest = new JobDescriptionSubmitDTO();
        jobRequest.setTitle(deriveTitle(request.getJobDescription()));
        jobRequest.setRawText(request.getJobDescription());
        JobDescriptionVO jobDescription = jobDescriptionService.submit(userId, jobRequest);

        return submitAnalysis(userId, request.getResumeId(), jobDescription.getId());
    }

    @Override
    public JobAnalysisStartVO retry(Long userId, Long resumeId, Long jobDescriptionId) {
        validateResumeId(resumeId);
        if (jobDescriptionId == null || jobDescriptionId <= 0) {
            throw new BusinessException(400, "目标岗位 ID 必须大于 0");
        }
        resumeService.getDetail(userId, resumeId);
        jobDescriptionService.getDetail(userId, jobDescriptionId);
        return submitAnalysis(userId, resumeId, jobDescriptionId);
    }

    private JobAnalysisStartVO submitAnalysis(Long userId, Long resumeId, Long jobDescriptionId) {
        Long taskId = asyncTaskService.createTask(
                userId,
                AsyncTaskType.MATCH_ANALYSIS,
                BIZ_TYPE_JOB_ANALYSIS,
                jobDescriptionId);
        try {
            taskExecutor.execute(() -> runAnalysis(taskId, userId, resumeId, jobDescriptionId));
        } catch (RejectedExecutionException exception) {
            asyncTaskFailureHandler.markFailed(taskId, AsyncTaskErrorCode.TASK_REJECTED, exception);
        }

        return JobAnalysisStartVO.builder()
                .taskId(taskId)
                .resumeId(resumeId)
                .jobDescriptionId(jobDescriptionId)
                .build();
    }

    private void runAnalysis(Long taskId, Long userId, Long resumeId, Long jobDescriptionId) {
        try {
            asyncTaskService.markRunning(taskId, "正在读取岗位要求");
            asyncTaskService.updateStage(taskId, "正在准备简历内容");
            ResumeParseResultVO resumeParseResult = ensureResumeReady(userId, resumeId);
            if (!STATUS_SUCCESS.equals(resumeParseResult.getParseStatus())) {
                asyncTaskService.markFailed(
                        taskId,
                        AsyncTaskErrorCode.FILE_PARSE_FAILED.name(),
                        firstPresent(resumeParseResult.getErrorMessage(), "未能读取简历内容"));
                return;
            }

            asyncTaskService.updateStage(taskId, "正在理解岗位要求");
            JobDescriptionVO parsedJob = jobDescriptionParseService.parse(userId, jobDescriptionId);
            if (!STATUS_SUCCESS.equals(parsedJob.getParseStatus())) {
                asyncTaskService.markFailed(
                        taskId,
                        AsyncTaskErrorCode.AI_RESPONSE_INVALID.name(),
                        firstPresent(parsedJob.getErrorMessage(), "未能理解岗位要求"));
                return;
            }

            asyncTaskService.updateStage(taskId, "正在对比岗位与简历");
            AiJobMatchResult matchResult = aiJobMatchService.match(userId, resumeId, jobDescriptionId);
            if (!STATUS_SUCCESS.equals(matchResult.getMatchStatus())) {
                asyncTaskService.markFailed(
                        taskId,
                        AsyncTaskErrorCode.AI_RESPONSE_INVALID.name(),
                        firstPresent(matchResult.getErrorMessage(), "岗位分析失败"));
                return;
            }

            asyncTaskService.updateStage(taskId, "正在整理分析结果");
            asyncTaskService.markSuccess(
                    taskId,
                    "JOB_ANALYSIS_RESULT",
                    matchResult.getId(),
                    firstPresent(parsedJob.getTitle(), "岗位分析完成"));
        } catch (RuntimeException exception) {
            asyncTaskFailureHandler.markFailed(taskId, null, exception);
        }
    }

    private ResumeParseResultVO ensureResumeReady(Long userId, Long resumeId) {
        try {
            ResumeParseResultVO existing = resumeService.getParseResult(userId, resumeId);
            if (STATUS_SUCCESS.equals(existing.getParseStatus())) {
                return existing;
            }
        } catch (BusinessException exception) {
            if (exception.getCode() != 404) {
                throw exception;
            }
        }
        return resumeService.parse(userId, resumeId);
    }

    private void validateResumeId(Long resumeId) {
        if (resumeId == null || resumeId <= 0) {
            throw new BusinessException(400, "请选择简历");
        }
    }

    private String deriveTitle(String jobDescription) {
        String normalized = jobDescription == null ? "" : jobDescription.strip();
        if (normalized.isEmpty()) {
            return "目标岗位";
        }
        String firstLine = normalized.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse("目标岗位");
        return firstLine.length() <= TITLE_MAX_LENGTH
                ? firstLine
                : firstLine.substring(0, TITLE_MAX_LENGTH);
    }

    private String firstPresent(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
