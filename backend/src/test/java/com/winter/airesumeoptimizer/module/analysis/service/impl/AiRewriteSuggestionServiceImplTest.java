package com.winter.airesumeoptimizer.module.analysis.service.impl;

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
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionResultDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiResumeSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiRewriteSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionOutputParser;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionPromptService;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiRewriteSuggestionServiceImplTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
    private final AiJobMatchResultMapper aiJobMatchResultMapper = mock(AiJobMatchResultMapper.class);
    private final AiResumeSuggestionMapper aiResumeSuggestionMapper = mock(AiResumeSuggestionMapper.class);
    private final AiRewriteSuggestionMapper aiRewriteSuggestionMapper = mock(AiRewriteSuggestionMapper.class);
    private final AiRewriteSuggestionPromptService aiRewriteSuggestionPromptService = mock(AiRewriteSuggestionPromptService.class);
    private final AiRewriteSuggestionOutputParser aiRewriteSuggestionOutputParser = mock(AiRewriteSuggestionOutputParser.class);
    private final AiClientService aiClientService = mock(AiClientService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiRewriteSuggestionServiceImpl service = new AiRewriteSuggestionServiceImpl(
            resumeMapper,
            jobDescriptionMapper,
            aiJobMatchResultMapper,
            aiResumeSuggestionMapper,
            aiRewriteSuggestionMapper,
            aiRewriteSuggestionPromptService,
            aiRewriteSuggestionOutputParser,
            aiClientService,
            objectMapper);

    @Test
    void generateShouldSaveSuccessRewriteSuggestion() {
        mockValidInputs();
        when(aiRewriteSuggestionPromptService.buildPrompt(any(), any(), any(), any(), any(), any()))
                .thenReturn(AiRewriteSuggestionPromptDTO.builder()
                        .promptVersion("rewrite_suggestion_v1")
                        .prompt("prompt")
                        .build());
        when(aiClientService.modelName()).thenReturn("qwen-plus");
        when(aiClientService.complete("prompt")).thenReturn("{\"rewrittenText\":\"改写文本\"}");
        when(aiRewriteSuggestionOutputParser.parse(any(String.class))).thenReturn(AiRewriteSuggestionResultDTO.builder()
                .rewrittenText("负责简历上传模块后端开发。")
                .rewriteReason("表达更具体。")
                .caution("确认职责真实。")
                .needUserSupplement(false)
                .supplementQuestions(List.of())
                .build());

        AiRewriteSuggestion result = service.generate(
                1L,
                10L,
                "project",
                "项目经历",
                "负责简历上传模块",
                20L,
                30L,
                40L);

        assertThat(result.getRewriteStatus()).isEqualTo("SUCCESS");
        assertThat(result.getAcceptStatus()).isEqualTo("PENDING");
        assertThat(result.getRewriteType()).isEqualTo("PROJECT");
        assertThat(result.getRewrittenText()).isEqualTo("负责简历上传模块后端开发。");
        assertThat(result.getRewriteReason()).isEqualTo("表达更具体。");
        assertThat(result.getCaution()).isEqualTo("确认职责真实。");
        assertThat(result.getModelName()).isEqualTo("qwen-plus");
        assertThat(result.getPromptVersion()).isEqualTo("rewrite_suggestion_v1");
        verify(aiRewriteSuggestionMapper).insert(any(AiRewriteSuggestion.class));
    }

    @Test
    void generateShouldAppendSupplementQuestionsToCaution() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(aiRewriteSuggestionPromptService.buildPrompt(any(), any(), any(), any(), any(), any()))
                .thenReturn(AiRewriteSuggestionPromptDTO.builder()
                        .promptVersion("rewrite_suggestion_v1")
                        .prompt("prompt")
                        .build());
        when(aiClientService.modelName()).thenReturn("qwen-plus");
        when(aiClientService.complete("prompt")).thenReturn("{}");
        when(aiRewriteSuggestionOutputParser.parse(any(String.class))).thenReturn(AiRewriteSuggestionResultDTO.builder()
                .rewrittenText("熟悉 Java 后端开发。")
                .rewriteReason("原文较短，保守优化表达。")
                .caution("需要确认真实项目场景。")
                .needUserSupplement(true)
                .supplementQuestions(List.of("是否有真实项目？", "是否使用 Spring Boot？"))
                .build());

        AiRewriteSuggestion result = service.generate(1L, 10L, "SKILL", "技能", "熟悉 Java", null, null, null);

        assertThat(result.getCaution()).contains("需要用户补充：是否有真实项目？；是否使用 Spring Boot？");
    }

    @Test
    void generateShouldSaveFailedSuggestionWhenAiOutputInvalid() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(aiRewriteSuggestionPromptService.buildPrompt(any(), any(), any(), any(), any(), any()))
                .thenReturn(AiRewriteSuggestionPromptDTO.builder()
                        .promptVersion("rewrite_suggestion_v1")
                        .prompt("prompt")
                        .build());
        when(aiClientService.modelName()).thenReturn("qwen-plus");
        when(aiClientService.complete("prompt")).thenReturn("not json");
        when(aiRewriteSuggestionOutputParser.parse("not json"))
                .thenThrow(new BusinessException(502, "AI 局部改写结果不是合法 JSON"));

        service.generate(1L, 10L, "PROJECT", "项目经历", "负责接口开发", null, null, null);

        ArgumentCaptor<AiRewriteSuggestion> captor = ArgumentCaptor.forClass(AiRewriteSuggestion.class);
        verify(aiRewriteSuggestionMapper).insert(captor.capture());
        assertThat(captor.getValue().getRewriteStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getRewrittenText()).isNull();
        assertThat(captor.getValue().getRewriteReason()).isNull();
        assertThat(captor.getValue().getCaution()).isNull();
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("AI 局部改写结果不是合法 JSON");
    }

    @Test
    void generateShouldRejectOtherUsersResume() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.generate(1L, 10L, "PROJECT", "项目经历", "负责接口开发", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历不存在");
        verify(aiClientService, never()).complete(any(String.class));
    }

    @Test
    void generateShouldRejectInvalidRewriteType() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());

        assertThatThrownBy(() -> service.generate(1L, 10L, "UNKNOWN", "项目经历", "负责接口开发", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("改写对象类型不合法");
        verify(aiClientService, never()).complete(any(String.class));
    }

    @Test
    void generateShouldRejectFailedMatchResult() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildMatchResult("FAILED"));

        assertThatThrownBy(() -> service.generate(1L, 10L, "PROJECT", "项目经历", "负责接口开发", null, 30L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 岗位匹配未成功，不能用于局部改写");
        verify(aiClientService, never()).complete(any(String.class));
    }

    @Test
    void updateAcceptStatusShouldAcceptOwnedRewriteSuggestion() {
        AiRewriteSuggestion suggestion = buildRewriteSuggestion();
        when(aiRewriteSuggestionMapper.selectById(50L)).thenReturn(suggestion);
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());

        AiRewriteSuggestion result = service.updateAcceptStatus(1L, 50L, "accepted");

        assertThat(result.getAcceptStatus()).isEqualTo("ACCEPTED");
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(aiRewriteSuggestionMapper).updateById(suggestion);
    }

    @Test
    void updateAcceptStatusShouldRejectRejectedStatus() {
        AiRewriteSuggestion suggestion = buildRewriteSuggestion();
        when(aiRewriteSuggestionMapper.selectById(50L)).thenReturn(suggestion);
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());

        AiRewriteSuggestion result = service.updateAcceptStatus(1L, 50L, "REJECTED");

        assertThat(result.getAcceptStatus()).isEqualTo("REJECTED");
        verify(aiRewriteSuggestionMapper).updateById(suggestion);
    }

    @Test
    void updateAcceptStatusShouldRejectPendingStatus() {
        AiRewriteSuggestion suggestion = buildRewriteSuggestion();
        when(aiRewriteSuggestionMapper.selectById(50L)).thenReturn(suggestion);
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());

        assertThatThrownBy(() -> service.updateAcceptStatus(1L, 50L, "PENDING"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("采纳状态只能为 ACCEPTED 或 REJECTED");
    }

    @Test
    void updateAcceptStatusShouldRejectOtherUsersRewriteSuggestion() {
        when(aiRewriteSuggestionMapper.selectById(50L)).thenReturn(buildRewriteSuggestion());
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.updateAcceptStatus(1L, 50L, "ACCEPTED"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历不存在");
        verify(aiRewriteSuggestionMapper, never()).updateById(any(AiRewriteSuggestion.class));
    }

    private void mockValidInputs() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription("SUCCESS"));
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildMatchResult("SUCCESS"));
        when(aiResumeSuggestionMapper.selectOne(any(Wrapper.class))).thenReturn(buildResumeSuggestion("SUCCESS"));
    }

    private Resume buildResume() {
        Resume resume = new Resume();
        resume.setId(10L);
        resume.setUserId(1L);
        return resume;
    }

    private JobDescription buildJobDescription(String status) {
        JobDescription jobDescription = new JobDescription();
        jobDescription.setId(20L);
        jobDescription.setUserId(1L);
        jobDescription.setParseStatus(status);
        jobDescription.setStructuredContent("{\"requiredSkills\":[\"Spring Boot\"]}");
        return jobDescription;
    }

    private AiJobMatchResult buildMatchResult(String status) {
        AiJobMatchResult matchResult = new AiJobMatchResult();
        matchResult.setId(30L);
        matchResult.setResumeId(10L);
        matchResult.setJobDescriptionId(20L);
        matchResult.setMatchStatus(status);
        matchResult.setOverallScore(80);
        matchResult.setStrongMatches("[]");
        matchResult.setWeakMatches("[]");
        matchResult.setMissingSkills("[]");
        matchResult.setWeakExperienceDescriptions("[]");
        matchResult.setEvidence("[]");
        matchResult.setRiskNotes("[]");
        return matchResult;
    }

    private AiResumeSuggestion buildResumeSuggestion(String status) {
        AiResumeSuggestion suggestion = new AiResumeSuggestion();
        suggestion.setId(40L);
        suggestion.setResumeId(10L);
        suggestion.setJobDescriptionId(20L);
        suggestion.setAiJobMatchResultId(30L);
        suggestion.setSuggestionStatus(status);
        suggestion.setSuggestions("[{\"type\":\"PROJECT_DESCRIPTION\"}]");
        return suggestion;
    }

    private AiRewriteSuggestion buildRewriteSuggestion() {
        AiRewriteSuggestion suggestion = new AiRewriteSuggestion();
        suggestion.setId(50L);
        suggestion.setResumeId(10L);
        suggestion.setRewriteType("PROJECT");
        suggestion.setTargetSection("项目经历");
        suggestion.setOriginalText("负责接口开发");
        suggestion.setRewrittenText("负责后端接口开发。");
        suggestion.setAcceptStatus("PENDING");
        suggestion.setRewriteStatus("SUCCESS");
        return suggestion;
    }
}
