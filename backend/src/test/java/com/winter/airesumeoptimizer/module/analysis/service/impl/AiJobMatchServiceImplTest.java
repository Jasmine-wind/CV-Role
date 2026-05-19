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
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchItemDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchResultDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchOutputParser;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchPromptService;
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

class AiJobMatchServiceImplTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final ResumeParseResultMapper resumeParseResultMapper = mock(ResumeParseResultMapper.class);
    private final JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
    private final AiJobMatchResultMapper aiJobMatchResultMapper = mock(AiJobMatchResultMapper.class);
    private final AiJobMatchPromptService aiJobMatchPromptService = mock(AiJobMatchPromptService.class);
    private final AiJobMatchOutputParser aiJobMatchOutputParser = mock(AiJobMatchOutputParser.class);
    private final AiClientService aiClientService = mock(AiClientService.class);
    private final ResumeRagService resumeRagService = mock(ResumeRagService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AiJobMatchServiceImpl service = new AiJobMatchServiceImpl(
            resumeMapper,
            resumeParseResultMapper,
            jobDescriptionMapper,
            aiJobMatchResultMapper,
            aiJobMatchPromptService,
            aiJobMatchOutputParser,
            aiClientService,
            objectMapper,
            resumeRagService);

    @Test
    void matchShouldSaveSuccessResult() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildParseResult("SUCCESS"));
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription("SUCCESS"));
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(resumeRagService.buildContext(1L, 10L, 20L, 3)).thenReturn(buildRagContext(true));
        when(aiJobMatchPromptService.buildPrompt(
                "{\"skills\":[\"Java\"]}",
                "{\"requiredSkills\":[\"Java\"]}",
                "Java 项目",
                "RAG 上下文"))
                .thenReturn(AiJobMatchPromptDTO.builder()
                        .promptVersion("ai_job_match_v1")
                        .prompt("prompt")
                        .build());
        when(aiClientService.modelName()).thenReturn("qwen-plus");
        when(aiClientService.complete("prompt")).thenReturn("{\"overallScore\":82}");
        when(aiJobMatchOutputParser.parse("{\"overallScore\":82}")).thenReturn(AiJobMatchResultDTO.builder()
                .overallScore(82)
                .strongMatches(List.of(AiJobMatchItemDTO.builder()
                        .item("Java")
                        .reason("双方都出现 Java")
                        .build()))
                .weakMatches(List.of())
                .missingSkills(List.of())
                .weakExperienceDescriptions(List.of())
                .evidence(List.of())
                .riskNotes(List.of("部分技能缺少项目支撑"))
                .build());

        AiJobMatchResult result = service.match(1L, 10L, 20L);

        assertThat(result.getMatchStatus()).isEqualTo("SUCCESS");
        assertThat(result.getOverallScore()).isEqualTo(82);
        assertThat(result.getStrongMatches()).contains("Java");
        assertThat(result.getRiskNotes()).contains("部分技能缺少项目支撑");
        assertThat(result.getModelName()).isEqualTo("qwen-plus");
        assertThat(result.getPromptVersion()).isEqualTo("ai_job_match_v1");
        verify(aiJobMatchResultMapper).insert(any(AiJobMatchResult.class));
    }

    @Test
    void matchShouldUpdateExistingResult() {
        AiJobMatchResult existing = new AiJobMatchResult();
        existing.setId(99L);
        existing.setResumeId(10L);
        existing.setJobDescriptionId(20L);
        existing.setMatchStatus("FAILED");
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildParseResult("SUCCESS"));
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription("SUCCESS"));
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(resumeRagService.buildContext(1L, 10L, 20L, 3)).thenReturn(buildRagContext(false));
        when(aiJobMatchPromptService.buildPrompt(any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(AiJobMatchPromptDTO.builder()
                        .promptVersion("ai_job_match_v1")
                        .prompt("prompt")
                        .build());
        when(aiClientService.modelName()).thenReturn("qwen-plus");
        when(aiClientService.complete("prompt")).thenReturn("{\"overallScore\":70}");
        when(aiJobMatchOutputParser.parse(any(String.class))).thenReturn(AiJobMatchResultDTO.builder()
                .overallScore(70)
                .strongMatches(List.of())
                .weakMatches(List.of())
                .missingSkills(List.of())
                .weakExperienceDescriptions(List.of())
                .evidence(List.of())
                .riskNotes(List.of())
                .build());

        service.match(1L, 10L, 20L);

        verify(aiJobMatchResultMapper).updateById(existing);
    }

    @Test
    void matchShouldSaveFailedResultWhenAiOutputInvalid() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildParseResult("SUCCESS"));
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription("SUCCESS"));
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(resumeRagService.buildContext(1L, 10L, 20L, 3)).thenReturn(buildRagContext(false));
        when(aiJobMatchPromptService.buildPrompt(any(String.class), any(String.class), any(String.class), any(String.class)))
                .thenReturn(AiJobMatchPromptDTO.builder()
                        .promptVersion("ai_job_match_v1")
                        .prompt("prompt")
                        .build());
        when(aiClientService.modelName()).thenReturn("qwen-plus");
        when(aiClientService.complete("prompt")).thenReturn("not json");
        when(aiJobMatchOutputParser.parse("not json"))
                .thenThrow(new BusinessException(502, "AI 匹配结果不是合法 JSON"));

        service.match(1L, 10L, 20L);

        ArgumentCaptor<AiJobMatchResult> captor = ArgumentCaptor.forClass(AiJobMatchResult.class);
        verify(aiJobMatchResultMapper).insert(captor.capture());
        assertThat(captor.getValue().getMatchStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getOverallScore()).isNull();
        assertThat(captor.getValue().getStrongMatches()).isNull();
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("AI 匹配结果不是合法 JSON");
    }

    @Test
    void matchShouldRejectUnparsedResumeAndJobDescription() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildParseResult("FAILED"));

        assertThatThrownBy(() -> service.match(1L, 10L, 20L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历解析未成功，不能进行匹配分析");
        verify(aiClientService, never()).complete(any(String.class));

        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildParseResult("SUCCESS"));
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription("FAILED"));

        assertThatThrownBy(() -> service.match(1L, 10L, 20L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("目标岗位解析未成功，不能进行匹配分析");
        verify(aiClientService, never()).complete(any(String.class));
    }

    @Test
    void matchShouldRejectOtherUsersResume() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.match(1L, 10L, 20L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历不存在");
        verify(aiClientService, never()).complete(any(String.class));
    }

    @Test
    void listByResumeShouldReturnOwnedResumeMatchResults() {
        AiJobMatchResult matchResult = buildMatchResult();
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(aiJobMatchResultMapper.selectList(any(Wrapper.class))).thenReturn(List.of(matchResult));

        List<AiJobMatchResult> results = service.listByResume(1L, 10L);

        assertThat(results).containsExactly(matchResult);
    }

    @Test
    void getByResumeAndJobDescriptionShouldReturnSpecificMatchResult() {
        AiJobMatchResult matchResult = buildMatchResult();
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription("SUCCESS"));
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(matchResult);

        AiJobMatchResult result = service.getByResumeAndJobDescription(1L, 10L, 20L);

        assertThat(result).isSameAs(matchResult);
    }

    @Test
    void getByResumeAndJobDescriptionShouldRejectMissingResult() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription("SUCCESS"));
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getByResumeAndJobDescription(1L, 10L, 20L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("匹配分析结果不存在");
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
        jobDescription.setStructuredContent("{\"requiredSkills\":[\"Java\"]}");
        return jobDescription;
    }

    private AiJobMatchResult buildMatchResult() {
        AiJobMatchResult matchResult = new AiJobMatchResult();
        matchResult.setId(30L);
        matchResult.setResumeId(10L);
        matchResult.setJobDescriptionId(20L);
        matchResult.setMatchStatus("SUCCESS");
        matchResult.setOverallScore(82);
        return matchResult;
    }

    private RagContextDTO buildRagContext(boolean used) {
        return RagContextDTO.builder()
                .used(used)
                .matchCount(used ? 1 : 0)
                .contextText(used ? "RAG 上下文" : "未使用 RAG 上下文：没有可用语义相似片段")
                .note(used ? "已使用" : "没有可用语义相似片段")
                .build();
    }
}
