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
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionItemDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionResultDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiResumeSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.service.AiResumeSuggestionOutputParser;
import com.winter.airesumeoptimizer.module.analysis.service.AiResumeSuggestionPromptService;
import com.winter.airesumeoptimizer.module.embedding.dto.RagContextDTO;
import com.winter.airesumeoptimizer.module.embedding.service.ResumeRagService;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiResumeSuggestionServiceImplTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final ResumeParseResultMapper resumeParseResultMapper = mock(ResumeParseResultMapper.class);
    private final JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
    private final AiJobMatchResultMapper aiJobMatchResultMapper = mock(AiJobMatchResultMapper.class);
    private final AiResumeSuggestionMapper aiResumeSuggestionMapper = mock(AiResumeSuggestionMapper.class);
    private final AiResumeSuggestionPromptService aiResumeSuggestionPromptService = mock(AiResumeSuggestionPromptService.class);
    private final AiResumeSuggestionOutputParser aiResumeSuggestionOutputParser = mock(AiResumeSuggestionOutputParser.class);
    private final AiClientService aiClientService = mock(AiClientService.class);
    private final ResumeRagService resumeRagService = mock(ResumeRagService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiResumeSuggestionServiceImpl service = new AiResumeSuggestionServiceImpl(
            resumeMapper,
            resumeParseResultMapper,
            jobDescriptionMapper,
            aiJobMatchResultMapper,
            aiResumeSuggestionMapper,
            aiResumeSuggestionPromptService,
            aiResumeSuggestionOutputParser,
            aiClientService,
            objectMapper,
            resumeRagService);

    @Test
    void generateShouldSaveSuccessSuggestion() {
        mockValidInputs();
        when(aiResumeSuggestionMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(aiResumeSuggestionPromptService.buildPrompt(any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(AiResumeSuggestionPromptDTO.builder()
                        .promptVersion("resume_suggestion_v1")
                        .prompt("prompt")
                        .build());
        when(aiClientService.modelName()).thenReturn("qwen-plus");
        when(aiClientService.complete("prompt")).thenReturn("{\"suggestions\":[]}");
        when(aiResumeSuggestionOutputParser.parse("{\"suggestions\":[]}")).thenReturn(AiResumeSuggestionResultDTO.builder()
                .suggestions(List.of(AiResumeSuggestionItemDTO.builder()
                        .type("SKILL_GAP")
                        .priority("HIGH")
                        .targetSection("技能")
                        .issue("缺少 Docker")
                        .suggestion("如果真实掌握 Docker，补充项目实践。")
                        .evidence(List.of("岗位要求 Docker"))
                        .caution("不要虚构技能")
                        .relatedItems(List.of("Docker"))
                        .build()))
                .build());

        AiResumeSuggestion result = service.generate(1L, 10L, 20L, 30L);

        assertThat(result.getSuggestionStatus()).isEqualTo("SUCCESS");
        assertThat(result.getSuggestions()).contains("SKILL_GAP");
        assertThat(result.getModelName()).isEqualTo("qwen-plus");
        assertThat(result.getPromptVersion()).isEqualTo("resume_suggestion_v1");
        assertThat(result.getResumeId()).isEqualTo(10L);
        assertThat(result.getJobDescriptionId()).isEqualTo(20L);
        assertThat(result.getAiJobMatchResultId()).isEqualTo(30L);
        verify(aiResumeSuggestionMapper).insert(any(AiResumeSuggestion.class));
    }

    @Test
    void generateShouldUpdateExistingSuggestion() {
        AiResumeSuggestion existing = new AiResumeSuggestion();
        existing.setId(99L);
        existing.setResumeId(10L);
        existing.setJobDescriptionId(20L);
        existing.setAiJobMatchResultId(30L);
        existing.setSuggestionStatus("FAILED");
        mockValidInputs();
        when(aiResumeSuggestionMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(aiResumeSuggestionPromptService.buildPrompt(any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(AiResumeSuggestionPromptDTO.builder()
                        .promptVersion("resume_suggestion_v1")
                        .prompt("prompt")
                        .build());
        when(aiClientService.modelName()).thenReturn("qwen-plus");
        when(aiClientService.complete("prompt")).thenReturn("{\"suggestions\":[]}");
        when(aiResumeSuggestionOutputParser.parse(any(String.class))).thenReturn(AiResumeSuggestionResultDTO.builder()
                .suggestions(List.of())
                .build());

        service.generate(1L, 10L, 20L, 30L);

        verify(aiResumeSuggestionMapper).updateById(existing);
    }

    @Test
    void generateShouldSaveFailedSuggestionWhenAiOutputInvalid() {
        mockValidInputs();
        when(aiResumeSuggestionMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(aiResumeSuggestionPromptService.buildPrompt(any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(AiResumeSuggestionPromptDTO.builder()
                        .promptVersion("resume_suggestion_v1")
                        .prompt("prompt")
                        .build());
        when(aiClientService.modelName()).thenReturn("qwen-plus");
        when(aiClientService.complete("prompt")).thenReturn("not json");
        when(aiResumeSuggestionOutputParser.parse("not json"))
                .thenThrow(new BusinessException(502, "AI 优化建议结果不是合法 JSON"));

        service.generate(1L, 10L, 20L, 30L);

        ArgumentCaptor<AiResumeSuggestion> captor = ArgumentCaptor.forClass(AiResumeSuggestion.class);
        verify(aiResumeSuggestionMapper).insert(captor.capture());
        assertThat(captor.getValue().getSuggestionStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getSuggestions()).isNull();
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("AI 优化建议结果不是合法 JSON");
    }

    @Test
    void generateShouldRejectFailedMatchResult() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildParseResult("SUCCESS"));
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription("SUCCESS"));
        AiJobMatchResult matchResult = buildMatchResult("FAILED");
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(matchResult);

        assertThatThrownBy(() -> service.generate(1L, 10L, 20L, 30L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 岗位匹配未成功，不能生成优化建议");
        verify(aiClientService, never()).complete(any(String.class));
    }

    @Test
    void generateShouldRejectOtherUsersResume() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.generate(1L, 10L, 20L, 30L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历不存在");
        verify(aiClientService, never()).complete(any(String.class));
    }

    @Test
    void listByResumeShouldReturnOwnedResumeSuggestions() {
        AiResumeSuggestion suggestion = buildSuggestion();
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(aiResumeSuggestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(suggestion));

        List<AiResumeSuggestion> results = service.listByResume(1L, 10L);

        assertThat(results).containsExactly(suggestion);
    }

    @Test
    void getByResumeAndJobDescriptionShouldReturnSpecificSuggestion() {
        AiResumeSuggestion suggestion = buildSuggestion();
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription("SUCCESS"));
        when(aiResumeSuggestionMapper.selectOne(any(Wrapper.class))).thenReturn(suggestion);

        AiResumeSuggestion result = service.getByResumeAndJobDescription(1L, 10L, 20L);

        assertThat(result).isSameAs(suggestion);
    }

    @Test
    void getByResumeAndMatchResultShouldReturnSpecificSuggestion() {
        AiResumeSuggestion suggestion = buildSuggestion();
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(aiResumeSuggestionMapper.selectOne(any(Wrapper.class))).thenReturn(suggestion);

        AiResumeSuggestion result = service.getByResumeAndMatchResult(1L, 10L, 30L);

        assertThat(result).isSameAs(suggestion);
    }

    @Test
    void getSuggestionShouldRejectMissingSuggestion() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription("SUCCESS"));
        when(aiResumeSuggestionMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getByResumeAndJobDescription(1L, 10L, 20L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 优化建议结果不存在");
    }

    private void mockValidInputs() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildParseResult("SUCCESS"));
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription("SUCCESS"));
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildMatchResult("SUCCESS"));
        when(resumeRagService.buildContext(1L, 10L, 20L, 3)).thenReturn(RagContextDTO.builder()
                .used(true)
                .matchCount(1)
                .contextText("RAG 上下文")
                .note("已使用")
                .build());
    }

    private Resume buildResume() {
        Resume resume = new Resume();
        resume.setId(10L);
        resume.setUserId(1L);
        return resume;
    }

    private ResumeParseResult buildParseResult(String status) {
        ResumeParseResult parseResult = new ResumeParseResult();
        parseResult.setId(11L);
        parseResult.setResumeId(10L);
        parseResult.setParseStatus(status);
        parseResult.setStructuredJson("{\"skills\":[\"Java\"]}");
        parseResult.setExtractedText("Java 项目");
        return parseResult;
    }

    private JobDescription buildJobDescription(String status) {
        JobDescription jobDescription = new JobDescription();
        jobDescription.setId(20L);
        jobDescription.setUserId(1L);
        jobDescription.setParseStatus(status);
        jobDescription.setStructuredContent("{\"requiredSkills\":[\"Docker\"]}");
        return jobDescription;
    }

    private AiJobMatchResult buildMatchResult(String status) {
        AiJobMatchResult matchResult = new AiJobMatchResult();
        matchResult.setId(30L);
        matchResult.setResumeId(10L);
        matchResult.setJobDescriptionId(20L);
        matchResult.setMatchStatus(status);
        matchResult.setOverallScore(70);
        matchResult.setStrongMatches("[]");
        matchResult.setWeakMatches("[]");
        matchResult.setMissingSkills("[{\"item\":\"Docker\",\"reason\":\"岗位要求 Docker\"}]");
        matchResult.setWeakExperienceDescriptions("[]");
        matchResult.setEvidence("[]");
        matchResult.setRiskNotes("[]");
        return matchResult;
    }

    private AiResumeSuggestion buildSuggestion() {
        AiResumeSuggestion suggestion = new AiResumeSuggestion();
        suggestion.setId(40L);
        suggestion.setResumeId(10L);
        suggestion.setJobDescriptionId(20L);
        suggestion.setAiJobMatchResultId(30L);
        suggestion.setSuggestionStatus("SUCCESS");
        suggestion.setSuggestions("[]");
        return suggestion;
    }
}
