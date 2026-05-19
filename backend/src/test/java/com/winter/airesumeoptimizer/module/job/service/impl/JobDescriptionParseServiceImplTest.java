package com.winter.airesumeoptimizer.module.job.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionParseResultDTO;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionPromptDTO;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionOutputParser;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionPromptService;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobDescriptionParseServiceImplTest {

    private final JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
    private final JobDescriptionPromptService jobDescriptionPromptService = mock(JobDescriptionPromptService.class);
    private final JobDescriptionOutputParser jobDescriptionOutputParser = mock(JobDescriptionOutputParser.class);
    private final AiClientService aiClientService = mock(AiClientService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JobDescriptionParseServiceImpl service = new JobDescriptionParseServiceImpl(
            jobDescriptionMapper,
            jobDescriptionPromptService,
            jobDescriptionOutputParser,
            aiClientService,
            objectMapper);

    @Test
    void parseShouldSaveSuccessResult() {
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription());
        when(jobDescriptionPromptService.buildPrompt("招聘 Java 后端")).thenReturn(JobDescriptionPromptDTO.builder()
                .promptVersion("job_description_parse_v1")
                .prompt("prompt")
                .build());
        when(aiClientService.modelName()).thenReturn("qwen-plus");
        when(aiClientService.complete("prompt")).thenReturn("{\"jobTitle\":\"Java 后端开发工程师\"}");
        when(jobDescriptionOutputParser.parse(any(String.class))).thenReturn(JobDescriptionParseResultDTO.builder()
                .jobTitle("Java 后端开发工程师")
                .requiredSkills(List.of("Java"))
                .bonusSkills(List.of())
                .experienceSignals(List.of("有后端项目经验"))
                .responsibilities(List.of("负责接口开发"))
                .keywords(List.of("Java"))
                .summary("岗位侧重 Java 后端")
                .build());

        JobDescriptionVO result = service.parse(1L, 10L);

        assertThat(result.getParseStatus()).isEqualTo("SUCCESS");
        assertThat(result.getModelName()).isEqualTo("qwen-plus");
        assertThat(result.getPromptVersion()).isEqualTo("job_description_parse_v1");
        assertThat(result.getStructuredContent()).contains("Java 后端开发工程师");
        assertThat(result.getErrorMessage()).isNull();
        verify(jobDescriptionMapper).updateById(any(JobDescription.class));
    }

    @Test
    void parseShouldSaveFailedResultWhenAiCallFails() {
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription());
        when(jobDescriptionPromptService.buildPrompt("招聘 Java 后端")).thenReturn(JobDescriptionPromptDTO.builder()
                .promptVersion("job_description_parse_v1")
                .prompt("prompt")
                .build());
        when(aiClientService.modelName()).thenReturn("qwen-plus");
        when(aiClientService.complete("prompt")).thenThrow(new BusinessException(502, "AI 服务调用失败"));

        JobDescriptionVO result = service.parse(1L, 10L);

        assertThat(result.getParseStatus()).isEqualTo("FAILED");
        assertThat(result.getStructuredContent()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("AI 服务调用失败");
    }

    @Test
    void parseShouldClearDirtyResultWhenOutputParserFails() {
        JobDescription jobDescription = buildJobDescription();
        jobDescription.setStructuredContent("{\"old\":true}");
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(jobDescription);
        when(jobDescriptionPromptService.buildPrompt("招聘 Java 后端")).thenReturn(JobDescriptionPromptDTO.builder()
                .promptVersion("job_description_parse_v1")
                .prompt("prompt")
                .build());
        when(aiClientService.modelName()).thenReturn("qwen-plus");
        when(aiClientService.complete("prompt")).thenReturn("not json");
        when(jobDescriptionOutputParser.parse("not json"))
                .thenThrow(new BusinessException(502, "目标岗位解析结果不是合法 JSON"));

        service.parse(1L, 10L);

        ArgumentCaptor<JobDescription> captor = ArgumentCaptor.forClass(JobDescription.class);
        verify(jobDescriptionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getParseStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getStructuredContent()).isNull();
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("目标岗位解析结果不是合法 JSON");
    }

    @Test
    void parseShouldRejectOtherUsersJobDescription() {
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.parse(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("目标岗位不存在");
        verify(aiClientService, never()).complete(any(String.class));
    }

    private JobDescription buildJobDescription() {
        JobDescription jobDescription = new JobDescription();
        jobDescription.setId(10L);
        jobDescription.setUserId(1L);
        jobDescription.setTitle("Java 后端开发工程师");
        jobDescription.setRawText("招聘 Java 后端");
        jobDescription.setParseStatus("PENDING");
        return jobDescription;
    }
}
