package com.winter.airesumeoptimizer.module.analysis.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.module.analysis.dto.JobAnalysisStartRequestDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchService;
import com.winter.airesumeoptimizer.module.analysis.vo.JobAnalysisStartVO;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionParseService;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import com.winter.airesumeoptimizer.module.optimization.service.OptimizationTaskService;
import com.winter.airesumeoptimizer.module.optimization.service.OptimizationTaskService.ExecutionContext;
import com.winter.airesumeoptimizer.module.optimization.vo.OptimizationTaskVO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeDetailVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeParseResultVO;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskErrorCode;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskType;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskFailureHandler;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.SyncTaskExecutor;

class JobAnalysisServiceImplTest {

    private final ResumeService resumeService = mock(ResumeService.class);
    private final JobDescriptionParseService jobDescriptionParseService = mock(JobDescriptionParseService.class);
    private final AiJobMatchService aiJobMatchService = mock(AiJobMatchService.class);
    private final OptimizationTaskService optimizationTaskService = mock(OptimizationTaskService.class);
    private final AsyncTaskService asyncTaskService = mock(AsyncTaskService.class);
    private final AsyncTaskFailureHandler asyncTaskFailureHandler = mock(AsyncTaskFailureHandler.class);
    private final AiClientService aiClientService = mock(AiClientService.class);
    private final JobAnalysisServiceImpl service = new JobAnalysisServiceImpl(
            resumeService,
            jobDescriptionParseService,
            aiJobMatchService,
            optimizationTaskService,
            asyncTaskService,
            asyncTaskFailureHandler,
            aiClientService,
            new SyncTaskExecutor());

    @BeforeEach
    void setUp() {
        when(resumeService.getDetail(1L, 10L)).thenReturn(ResumeDetailVO.builder().id(10L).build());
        when(aiClientService.modelName()).thenReturn("test-model");
        when(optimizationTaskService.create(any(), any(), any(), any(), any(), any()))
                .thenReturn(taskVO());
        when(optimizationTaskService.get(1L, 50L)).thenReturn(taskVO());
        when(optimizationTaskService.getExecutionContext(1L, 50L)).thenReturn(context());
        when(asyncTaskService.createTask(1L, AsyncTaskType.MATCH_ANALYSIS, "OPTIMIZATION_TASK", 50L))
                .thenReturn(100L);
    }

    @Test
    void startShouldRejectBlankJobDescriptionAtModuleInterface() {
        assertThatThrownBy(() -> service.start(1L, request(" ")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("目标岗位 JD 不能为空");
        verify(optimizationTaskService, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void startShouldUseFormalTaskAndCaptureVersionSnapshotBeforeMatching() {
        prepareSuccessfulAnalysis();

        JobAnalysisStartVO result = service.start(1L, request("Java 后端工程师\n负责 Spring Boot 开发"));

        assertThat(result.getTaskId()).isEqualTo(100L);
        assertThat(result.getOptimizationTaskId()).isEqualTo(50L);
        assertThat(result.getSourceResumeVersionId()).isEqualTo(40L);
        assertThat(result.getTargetResumeVersionId()).isEqualTo(41L);
        assertThat(result.getJobTargetId()).isEqualTo(30L);
        verify(optimizationTaskService).attachAsyncTask(1L, 50L, 100L);
        verify(optimizationTaskService).captureResumeSnapshot(1L, 50L, "{\"skills\":[\"Java\"]}");
        verify(optimizationTaskService).markSuccess(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(50L),
                any(JobDescriptionVO.class),
                any(AiJobMatchResult.class));
        verify(asyncTaskService).markSuccess(100L, "OPTIMIZATION_TASK", 50L, "Java 后端工程师");

        ArgumentCaptor<String> titleCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jdCaptor = ArgumentCaptor.forClass(String.class);
        verify(optimizationTaskService).create(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(10L),
                titleCaptor.capture(),
                jdCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("SYSTEM_DEFAULT_OPENAI_COMPATIBLE"),
                org.mockito.ArgumentMatchers.eq("test-model"));
        assertThat(titleCaptor.getValue()).isEqualTo("Java 后端工程师");
        assertThat(jdCaptor.getValue()).contains("Spring Boot");
    }

    @Test
    void retryShouldReuseFormalTaskInputsWithoutCreatingNewVersions() {
        prepareSuccessfulAnalysis();

        JobAnalysisStartVO result = service.retry(1L, 50L);

        assertThat(result.getOptimizationTaskId()).isEqualTo(50L);
        verify(optimizationTaskService, never()).create(any(), any(), any(), any(), any(), any());
        verify(optimizationTaskService).getExecutionContext(1L, 50L);
        verify(optimizationTaskService).attachAsyncTask(1L, 50L, 100L);
    }

    @Test
    void legacyRetryShouldResolveMigratedFormalTask() {
        when(optimizationTaskService.findByLegacyInputs(1L, 10L, 20L)).thenReturn(taskVO());
        prepareSuccessfulAnalysis();

        JobAnalysisStartVO result = service.retryLegacy(1L, 10L, 20L);

        assertThat(result.getOptimizationTaskId()).isEqualTo(50L);
        verify(optimizationTaskService).findByLegacyInputs(1L, 10L, 20L);
    }

    @Test
    void legacyRetryShouldCreateFormalTaskForPreMigrationFailureWithoutMatchResult() {
        when(optimizationTaskService.findByLegacyInputs(1L, 10L, 20L))
                .thenThrow(new BusinessException(404, "优化任务不存在"));
        when(optimizationTaskService.createFromExisting(
                1L,
                10L,
                20L,
                "SYSTEM_DEFAULT_OPENAI_COMPATIBLE",
                "test-model"))
                .thenReturn(taskVO());
        prepareSuccessfulAnalysis();

        JobAnalysisStartVO result = service.retryLegacy(1L, 10L, 20L);

        assertThat(result.getOptimizationTaskId()).isEqualTo(50L);
        verify(optimizationTaskService).createFromExisting(
                1L,
                10L,
                20L,
                "SYSTEM_DEFAULT_OPENAI_COMPATIBLE",
                "test-model");
    }

    @Test
    void retryShouldNotStartSecondExecutionWhileTaskIsActive() {
        when(optimizationTaskService.get(1L, 50L)).thenReturn(OptimizationTaskVO.builder()
                .optimizationTaskId(50L)
                .asyncTaskId(99L)
                .status("RUNNING")
                .build());

        assertThatThrownBy(() -> service.retry(1L, 50L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位分析正在进行中");

        verify(asyncTaskService, never()).createTask(any(), any(), any(), any());
    }

    @Test
    void retryShouldRejectTaskNotOwnedByCurrentUser() {
        when(optimizationTaskService.get(2L, 50L))
                .thenThrow(new BusinessException(404, "优化任务不存在"));

        assertThatThrownBy(() -> service.retry(2L, 50L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("优化任务不存在");

        verify(asyncTaskService, never()).createTask(any(), any(), any(), any());
    }

    @Test
    void startShouldPrepareLegacyResumeWhenNoParseResultExists() {
        when(resumeService.getParseResult(1L, 10L))
                .thenThrow(new BusinessException(404, "简历尚未解析"));
        when(resumeService.parse(1L, 10L)).thenReturn(successfulResumeParse());
        when(jobDescriptionParseService.parse(1L, 20L)).thenReturn(successfulJob());
        when(aiJobMatchService.match(1L, 10L, 20L)).thenReturn(successfulMatch());

        service.start(1L, request("目标岗位 JD"));

        verify(resumeService).parse(1L, 10L);
        verify(optimizationTaskService).captureResumeSnapshot(1L, 50L, "{\"skills\":[\"Java\"]}");
        verify(aiJobMatchService).match(1L, 10L, 20L);
    }

    @Test
    void startShouldKeepBothTaskStatesFailedWhenResumePreparationFails() {
        when(resumeService.getParseResult(1L, 10L)).thenReturn(ResumeParseResultVO.builder()
                .resumeId(10L)
                .parseStatus("FAILED")
                .errorMessage("无法读取简历")
                .build());
        when(resumeService.parse(1L, 10L)).thenReturn(ResumeParseResultVO.builder()
                .resumeId(10L)
                .parseStatus("FAILED")
                .errorMessage("无法读取简历")
                .build());

        service.start(1L, request("目标岗位 JD"));

        verify(optimizationTaskService).markFailed(
                1L,
                50L,
                AsyncTaskErrorCode.FILE_PARSE_FAILED.name(),
                "无法读取简历");
        verify(asyncTaskService).markFailed(
                100L,
                AsyncTaskErrorCode.FILE_PARSE_FAILED.name(),
                "无法读取简历");
        verify(jobDescriptionParseService, never()).parse(1L, 20L);
        verify(aiJobMatchService, never()).match(1L, 10L, 20L);
    }

    private void prepareSuccessfulAnalysis() {
        when(resumeService.getParseResult(1L, 10L)).thenReturn(successfulResumeParse());
        when(jobDescriptionParseService.parse(1L, 20L)).thenReturn(successfulJob());
        when(aiJobMatchService.match(1L, 10L, 20L)).thenReturn(successfulMatch());
    }

    private ResumeParseResultVO successfulResumeParse() {
        return ResumeParseResultVO.builder()
                .resumeId(10L)
                .parseStatus("SUCCESS")
                .structuredJson("{\"skills\":[\"Java\"]}")
                .build();
    }

    private JobDescriptionVO successfulJob() {
        return JobDescriptionVO.builder()
                .id(20L)
                .title("Java 后端工程师")
                .parseStatus("SUCCESS")
                .promptVersion("job-v1")
                .build();
    }

    private AiJobMatchResult successfulMatch() {
        AiJobMatchResult match = new AiJobMatchResult();
        match.setId(60L);
        match.setMatchStatus("SUCCESS");
        match.setModelName("test-model");
        match.setPromptVersion("match-v1");
        return match;
    }

    private OptimizationTaskVO taskVO() {
        return OptimizationTaskVO.builder()
                .optimizationTaskId(50L)
                .sourceResumeVersionId(40L)
                .targetResumeVersionId(41L)
                .jobTargetId(30L)
                .status("PENDING")
                .build();
    }

    private ExecutionContext context() {
        return new ExecutionContext(50L, 10L, 20L, 30L, 40L, 41L);
    }

    private JobAnalysisStartRequestDTO request(String jobDescription) {
        JobAnalysisStartRequestDTO request = new JobAnalysisStartRequestDTO();
        request.setResumeId(10L);
        request.setJobDescription(jobDescription);
        return request;
    }
}
