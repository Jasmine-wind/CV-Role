package com.winter.airesumeoptimizer.module.analysis.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchEvidenceDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchItemDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionItemDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiResumeSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiRewriteSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.vo.JobOptimizationReportVO;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobOptimizationReportServiceImplTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
    private final AiJobMatchResultMapper aiJobMatchResultMapper = mock(AiJobMatchResultMapper.class);
    private final AiResumeSuggestionMapper aiResumeSuggestionMapper = mock(AiResumeSuggestionMapper.class);
    private final AiRewriteSuggestionMapper aiRewriteSuggestionMapper = mock(AiRewriteSuggestionMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JobOptimizationReportServiceImpl service = new JobOptimizationReportServiceImpl(
            resumeMapper,
            jobDescriptionMapper,
            aiJobMatchResultMapper,
            aiResumeSuggestionMapper,
            aiRewriteSuggestionMapper,
            objectMapper);

    @Test
    void getReportShouldAggregateMatchSuggestionAndRewriteResults() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription());
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildMatchResult("SUCCESS"));
        when(aiResumeSuggestionMapper.selectOne(any(Wrapper.class))).thenReturn(buildSuggestion("SUCCESS"));
        when(aiRewriteSuggestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                buildRewriteSuggestion(50L, "ACCEPTED", "SUCCESS"),
                buildRewriteSuggestion(51L, "PENDING", "SUCCESS"),
                buildRewriteSuggestion(52L, "REJECTED", "SUCCESS")));

        JobOptimizationReportVO report = service.getReport(1L, 10L, 20L);

        assertThat(report.getResumeId()).isEqualTo(10L);
        assertThat(report.getResumeName()).isEqualTo("java-resume.pdf");
        assertThat(report.getJobDescriptionId()).isEqualTo(20L);
        assertThat(report.getJobTitle()).isEqualTo("Java后端开发");
        assertThat(report.getMatchScore()).isEqualTo(82);
        assertThat(report.getMatchLevel()).isEqualTo("HIGH");
        assertThat(report.getStrongMatches()).extracting(AiJobMatchItemDTO::getItem).containsExactly("Java");
        assertThat(report.getWeakMatches()).extracting(AiJobMatchItemDTO::getItem).containsExactly("项目表达");
        assertThat(report.getMissingSkills()).extracting(AiJobMatchItemDTO::getItem).containsExactly("Docker");
        assertThat(report.getRiskTips()).containsExactly("不要虚构 Docker 经验");
        assertThat(report.getMatchEvidence()).extracting(AiJobMatchEvidenceDTO::getContent)
                .containsExactly("简历提到 Java", "岗位要求 Java");
        assertThat(report.getSuggestionSummary().getTotalCount()).isEqualTo(3);
        assertThat(report.getSuggestionSummary().getHighPriorityCount()).isEqualTo(1);
        assertThat(report.getSuggestionSummary().getMediumPriorityCount()).isEqualTo(1);
        assertThat(report.getSuggestionSummary().getLowPriorityCount()).isEqualTo(1);
        assertThat(report.getHighPrioritySuggestions()).extracting(AiResumeSuggestionItemDTO::getIssue).containsExactly("缺少 Docker");
        assertThat(report.getMediumPrioritySuggestions()).extracting(AiResumeSuggestionItemDTO::getIssue).containsExactly("项目描述偏弱");
        assertThat(report.getLowPrioritySuggestions()).extracting(AiResumeSuggestionItemDTO::getIssue).containsExactly("自我评价较泛");
        assertThat(report.getRewriteSuggestions()).hasSize(3);
        assertThat(report.getAcceptedRewriteSuggestions()).extracting(JobOptimizationReportVO.RewriteSuggestionItemVO::getRewriteId).containsExactly(50L);
        assertThat(report.getPendingRewriteSuggestions()).extracting(JobOptimizationReportVO.RewriteSuggestionItemVO::getRewriteId).containsExactly(51L);
        assertThat(report.getRejectedRewriteSuggestions()).extracting(JobOptimizationReportVO.RewriteSuggestionItemVO::getRewriteId).containsExactly(52L);
        assertThat(report.getNextStepChecklist()).extracting(JobOptimizationReportVO.NextStepItemVO::getKey)
                .contains(
                        "REVIEW_HIGH_PRIORITY_SUGGESTIONS",
                        "REVIEW_MISSING_SKILLS",
                        "IMPROVE_WEAK_MATCHES",
                        "REVIEW_PENDING_REWRITES",
                        "REVIEW_RISK_TIPS");
        assertThat(report.getModelInfo()).extracting(JobOptimizationReportVO.ModelInfoVO::getSourceType)
                .contains("MATCH", "SUGGESTION", "REWRITE");
        assertThat(report.getWarnings()).isEmpty();
    }

    @Test
    void getReportShouldWarnWhenEvidenceReasonOrCautionMissing() {
        AiJobMatchResult matchResult = buildMatchResult("SUCCESS");
        matchResult.setEvidence(null);
        matchResult.setStrongMatches(json(List.of(AiJobMatchItemDTO.builder()
                .item("Java")
                .reason("")
                .build())));

        AiResumeSuggestion suggestion = buildSuggestion("SUCCESS");
        suggestion.setSuggestions(json(List.of(AiResumeSuggestionItemDTO.builder()
                .type("CONTENT")
                .priority("HIGH")
                .targetSection("项目经历")
                .issue("项目描述偏弱")
                .suggestion("优化项目表达")
                .evidence(List.of())
                .caution(null)
                .relatedItems(List.of("Java"))
                .build())));

        AiRewriteSuggestion rewriteSuggestion = buildRewriteSuggestion(50L, "PENDING", "SUCCESS");
        rewriteSuggestion.setRewriteReason(null);
        rewriteSuggestion.setCaution("");

        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription());
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(matchResult);
        when(aiResumeSuggestionMapper.selectOne(any(Wrapper.class))).thenReturn(suggestion);
        when(aiRewriteSuggestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(rewriteSuggestion));

        JobOptimizationReportVO report = service.getReport(1L, 10L, 20L);

        assertThat(report.getWarnings()).extracting(JobOptimizationReportVO.WarningVO::getCode)
                .contains(
                        "MATCH_EVIDENCE_MISSING",
                        "MATCH_REASON_MISSING",
                        "SUGGESTION_EVIDENCE_MISSING",
                        "SUGGESTION_CAUTION_MISSING",
                        "REWRITE_REASON_MISSING",
                        "REWRITE_CAUTION_MISSING");
    }

    @Test
    void getReportShouldReturnEmptySuggestionsAndWarningsWhenOptionalResultsMissing() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription());
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildMatchResult("SUCCESS"));
        when(aiResumeSuggestionMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(aiRewriteSuggestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        JobOptimizationReportVO report = service.getReport(1L, 10L, 20L);

        assertThat(report.getSuggestionSummary().getTotalCount()).isZero();
        assertThat(report.getHighPrioritySuggestions()).isEmpty();
        assertThat(report.getRewriteSuggestions()).isEmpty();
        assertThat(report.getNextStepChecklist()).extracting(JobOptimizationReportVO.NextStepItemVO::getKey)
                .contains("GENERATE_REWRITE_SUGGESTIONS");
        assertThat(report.getWarnings()).extracting(JobOptimizationReportVO.WarningVO::getCode)
                .contains("SUGGESTION_RESULT_MISSING", "REWRITE_RESULT_MISSING");
    }

    @Test
    void getReportShouldRejectMissingMatchResult() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription());
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getReport(1L, 10L, 20L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("匹配分析结果不存在，请先生成匹配分析结果");
    }

    @Test
    void getReportShouldRejectFailedMatchResult() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobDescriptionMapper.selectOne(any(Wrapper.class))).thenReturn(buildJobDescription());
        when(aiJobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildMatchResult("FAILED"));

        assertThatThrownBy(() -> service.getReport(1L, 10L, 20L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("匹配分析未成功，不能生成岗位优化报告");
    }

    @Test
    void getReportShouldRejectOtherUsersResume() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.getReport(1L, 10L, 20L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历不存在");
    }

    private Resume buildResume() {
        Resume resume = new Resume();
        resume.setId(10L);
        resume.setUserId(1L);
        resume.setOriginalFilename("java-resume.pdf");
        return resume;
    }

    private JobDescription buildJobDescription() {
        JobDescription jobDescription = new JobDescription();
        jobDescription.setId(20L);
        jobDescription.setUserId(1L);
        jobDescription.setTitle("Java后端开发");
        return jobDescription;
    }

    private AiJobMatchResult buildMatchResult(String status) {
        AiJobMatchResult matchResult = new AiJobMatchResult();
        matchResult.setId(30L);
        matchResult.setResumeId(10L);
        matchResult.setJobDescriptionId(20L);
        matchResult.setMatchStatus(status);
        matchResult.setOverallScore(82);
        matchResult.setStrongMatches(json(List.of(AiJobMatchItemDTO.builder()
                .item("Java")
                .reason("简历与岗位均包含 Java")
                .build())));
        matchResult.setWeakMatches(json(List.of(AiJobMatchItemDTO.builder()
                .item("项目表达")
                .reason("项目职责不够突出")
                .build())));
        matchResult.setMissingSkills(json(List.of(AiJobMatchItemDTO.builder()
                .item("Docker")
                .reason("岗位要求 Docker")
                .build())));
        matchResult.setEvidence(json(List.of(
                AiJobMatchEvidenceDTO.builder()
                        .source("resume")
                        .content("简历提到 Java")
                        .build(),
                AiJobMatchEvidenceDTO.builder()
                        .source("job")
                        .content("岗位要求 Java")
                        .build())));
        matchResult.setRiskNotes(json(List.of("不要虚构 Docker 经验")));
        matchResult.setModelName("deepseek-v4-flash");
        matchResult.setPromptVersion("ai_job_match_v1");
        matchResult.setUpdatedAt(LocalDateTime.now());
        return matchResult;
    }

    private AiResumeSuggestion buildSuggestion(String status) {
        AiResumeSuggestion suggestion = new AiResumeSuggestion();
        suggestion.setId(40L);
        suggestion.setResumeId(10L);
        suggestion.setJobDescriptionId(20L);
        suggestion.setAiJobMatchResultId(30L);
        suggestion.setSuggestionStatus(status);
        suggestion.setSuggestions(json(List.of(
                buildSuggestionItem("HIGH", "缺少 Docker"),
                buildSuggestionItem("MEDIUM", "项目描述偏弱"),
                buildSuggestionItem("LOW", "自我评价较泛"))));
        suggestion.setModelName("deepseek-v4-flash");
        suggestion.setPromptVersion("resume_suggestion_v1");
        suggestion.setUpdatedAt(LocalDateTime.now());
        return suggestion;
    }

    private AiResumeSuggestionItemDTO buildSuggestionItem(String priority, String issue) {
        return AiResumeSuggestionItemDTO.builder()
                .type("CONTENT")
                .priority(priority)
                .targetSection("项目经历")
                .issue(issue)
                .suggestion("优化项目表达")
                .evidence(List.of("岗位要求"))
                .caution("不要虚构经历")
                .relatedItems(List.of("Java"))
                .build();
    }

    private AiRewriteSuggestion buildRewriteSuggestion(Long id, String acceptStatus, String rewriteStatus) {
        AiRewriteSuggestion suggestion = new AiRewriteSuggestion();
        suggestion.setId(id);
        suggestion.setResumeId(10L);
        suggestion.setJobDescriptionId(20L);
        suggestion.setAiJobMatchResultId(30L);
        suggestion.setAiResumeSuggestionId(40L);
        suggestion.setRewriteType("PROJECT");
        suggestion.setTargetSection("项目经历");
        suggestion.setOriginalText("负责接口开发");
        suggestion.setRewrittenText("负责核心接口开发与联调");
        suggestion.setRewriteReason("突出岗位相关职责");
        suggestion.setCaution("不得虚构指标");
        suggestion.setAcceptStatus(acceptStatus);
        suggestion.setRewriteStatus(rewriteStatus);
        suggestion.setModelName("deepseek-v4-flash");
        suggestion.setPromptVersion("rewrite_suggestion_v1");
        suggestion.setUpdatedAt(LocalDateTime.now());
        return suggestion;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
