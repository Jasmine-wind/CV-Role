package com.winter.airesumeoptimizer.module.analysis.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winter.airesumeoptimizer.module.analysis.dto.JobAnalysisStartRequestDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchService;
import com.winter.airesumeoptimizer.module.analysis.vo.JobAnalysisStartVO;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionSubmitDTO;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionParseService;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionService;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
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
    private final JobDescriptionService jobDescriptionService = mock(JobDescriptionService.class);
    private final JobDescriptionParseService jobDescriptionParseService = mock(JobDescriptionParseService.class);
    private final AiJobMatchService aiJobMatchService = mock(AiJobMatchService.class);
    private final AsyncTaskService asyncTaskService = mock(AsyncTaskService.class);
    private final AsyncTaskFailureHandler asyncTaskFailureHandler = mock(AsyncTaskFailureHandler.class);
    private final JobAnalysisServiceImpl service = new JobAnalysisServiceImpl(
            resumeService,
            jobDescriptionService,
            jobDescriptionParseService,
            aiJobMatchService,
            asyncTaskService,
            asyncTaskFailureHandler,
            new SyncTaskExecutor());

    @BeforeEach
    void setUp() {
        when(resumeService.getDetail(1L, 10L)).thenReturn(ResumeDetailVO.builder().id(10L).build());
        when(jobDescriptionService.submit(any(), any(JobDescriptionSubmitDTO.class)))
                .thenReturn(JobDescriptionVO.builder().id(20L).title("Java 后端工程师").parseStatus("PENDING").build());
        when(asyncTaskService.createTask(1L, AsyncTaskType.MATCH_ANALYSIS, "JOB_ANALYSIS", 20L))
                .thenReturn(100L);
    }

    @Test
    void startShouldRejectBlankJobDescriptionAtModuleInterface() {
        assertThatThrownBy(() -> service.start(1L, request(" ")))
                .isInstanceOf(com.winter.airesumeoptimizer.common.exception.BusinessException.class)
                .hasMessage("目标岗位 JD 不能为空");
        verify(jobDescriptionService, never()).submit(any(), any());
    }

    @Test
    void startShouldCompleteDefaultFlowAndReusePreparedResume() {
        when(resumeService.getParseResult(1L, 10L)).thenReturn(ResumeParseResultVO.builder()
                .resumeId(10L)
                .parseStatus("SUCCESS")
                .build());
        when(jobDescriptionParseService.parse(1L, 20L)).thenReturn(JobDescriptionVO.builder()
                .id(20L)
                .title("Java 后端工程师")
                .parseStatus("SUCCESS")
                .build());
        AiJobMatchResult match = new AiJobMatchResult();
        match.setId(30L);
        match.setMatchStatus("SUCCESS");
        when(aiJobMatchService.match(1L, 10L, 20L)).thenReturn(match);

        JobAnalysisStartVO result = service.start(1L, request("Java 后端工程师\n负责 Spring Boot 开发"));

        assertThat(result.getTaskId()).isEqualTo(100L);
        assertThat(result.getResumeId()).isEqualTo(10L);
        assertThat(result.getJobDescriptionId()).isEqualTo(20L);
        verify(resumeService, never()).parse(1L, 10L);
        verify(asyncTaskService).markSuccess(100L, "JOB_ANALYSIS_RESULT", 30L, "Java 后端工程师");

        ArgumentCaptor<JobDescriptionSubmitDTO> requestCaptor = ArgumentCaptor.forClass(JobDescriptionSubmitDTO.class);
        verify(jobDescriptionService).submit(org.mockito.ArgumentMatchers.eq(1L), requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTitle()).isEqualTo("Java 后端工程师");
        assertThat(requestCaptor.getValue().getRawText()).contains("Spring Boot");
    }

    @Test
    void retryShouldReuseSavedInputsWithoutCreatingAnotherJobDescription() {
        when(jobDescriptionService.getDetail(1L, 20L)).thenReturn(JobDescriptionVO.builder()
                .id(20L)
                .title("Java 后端工程师")
                .parseStatus("FAILED")
                .build());
        when(resumeService.getParseResult(1L, 10L)).thenReturn(ResumeParseResultVO.builder()
                .resumeId(10L)
                .parseStatus("SUCCESS")
                .build());
        when(jobDescriptionParseService.parse(1L, 20L)).thenReturn(JobDescriptionVO.builder()
                .id(20L)
                .title("Java 后端工程师")
                .parseStatus("SUCCESS")
                .build());
        AiJobMatchResult match = new AiJobMatchResult();
        match.setId(30L);
        match.setMatchStatus("SUCCESS");
        when(aiJobMatchService.match(1L, 10L, 20L)).thenReturn(match);

        JobAnalysisStartVO result = service.retry(1L, 10L, 20L);

        assertThat(result.getTaskId()).isEqualTo(100L);
        assertThat(result.getResumeId()).isEqualTo(10L);
        assertThat(result.getJobDescriptionId()).isEqualTo(20L);
        verify(jobDescriptionService, never()).submit(any(), any());
        verify(jobDescriptionService).getDetail(1L, 20L);
        verify(asyncTaskService).markSuccess(100L, "JOB_ANALYSIS_RESULT", 30L, "Java 后端工程师");
    }

    @Test
    void retryShouldRejectJobDescriptionNotOwnedByCurrentUser() {
        when(jobDescriptionService.getDetail(1L, 20L))
                .thenThrow(new com.winter.airesumeoptimizer.common.exception.BusinessException(404, "目标岗位不存在"));

        assertThatThrownBy(() -> service.retry(1L, 10L, 20L))
                .isInstanceOf(com.winter.airesumeoptimizer.common.exception.BusinessException.class)
                .hasMessage("目标岗位不存在");

        verify(asyncTaskService, never()).createTask(any(), any(), any(), any());
    }

    @Test
    void startShouldPrepareLegacyResumeWhenNoParseResultExists() {
        when(resumeService.getParseResult(1L, 10L))
                .thenThrow(new com.winter.airesumeoptimizer.common.exception.BusinessException(404, "简历尚未解析"));
        when(resumeService.parse(1L, 10L)).thenReturn(ResumeParseResultVO.builder()
                .resumeId(10L)
                .parseStatus("SUCCESS")
                .build());
        when(jobDescriptionParseService.parse(1L, 20L)).thenReturn(JobDescriptionVO.builder()
                .id(20L)
                .title("目标岗位")
                .parseStatus("SUCCESS")
                .build());
        AiJobMatchResult match = new AiJobMatchResult();
        match.setId(30L);
        match.setMatchStatus("SUCCESS");
        when(aiJobMatchService.match(1L, 10L, 20L)).thenReturn(match);

        service.start(1L, request("目标岗位 JD"));

        verify(resumeService).parse(1L, 10L);
        verify(aiJobMatchService).match(1L, 10L, 20L);
    }

    @Test
    void startShouldStopWhenResumePreparationFails() {
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

        verify(asyncTaskService).markFailed(100L, AsyncTaskErrorCode.FILE_PARSE_FAILED.name(), "无法读取简历");
        verify(jobDescriptionParseService, never()).parse(1L, 20L);
        verify(aiJobMatchService, never()).match(1L, 10L, 20L);
    }

    private JobAnalysisStartRequestDTO request(String jobDescription) {
        JobAnalysisStartRequestDTO request = new JobAnalysisStartRequestDTO();
        request.setResumeId(10L);
        request.setJobDescription(jobDescription);
        return request;
    }
}
