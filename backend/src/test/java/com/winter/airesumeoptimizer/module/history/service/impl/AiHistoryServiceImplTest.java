package com.winter.airesumeoptimizer.module.history.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiResumeSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiRewriteSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.ResumeAiAnalysisMapper;
import com.winter.airesumeoptimizer.module.history.vo.AiResultDetailVO;
import com.winter.airesumeoptimizer.module.history.vo.AiResultPageVO;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiHistoryServiceImplTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
    private final ResumeAiAnalysisMapper resumeAiAnalysisMapper = mock(ResumeAiAnalysisMapper.class);
    private final AiJobMatchResultMapper aiJobMatchResultMapper = mock(AiJobMatchResultMapper.class);
    private final AiResumeSuggestionMapper aiResumeSuggestionMapper = mock(AiResumeSuggestionMapper.class);
    private final AiRewriteSuggestionMapper aiRewriteSuggestionMapper = mock(AiRewriteSuggestionMapper.class);
    private final AiHistoryServiceImpl service = new AiHistoryServiceImpl(
            resumeMapper,
            jobDescriptionMapper,
            resumeAiAnalysisMapper,
            aiJobMatchResultMapper,
            aiResumeSuggestionMapper,
            aiRewriteSuggestionMapper,
            new ObjectMapper());

    @Test
    void listShouldAggregateAiResultsAndSortByUpdatedAt() {
        mockOwnedResources();
        when(resumeAiAnalysisMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildAnalysis()));
        when(aiJobMatchResultMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildMatch()));
        when(aiResumeSuggestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildSuggestion()));
        when(aiRewriteSuggestionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildRewrite()));

        AiResultPageVO result = service.list(1L, null, null, null, "SUCCESS", 1, 10);

        assertThat(result.getTotal()).isEqualTo(5L);
        assertThat(result.getRecords()).extracting("resultType")
                .containsExactly(
                        "LOCAL_REWRITE",
                        "JOB_OPTIMIZATION_SUGGESTION",
                        "MATCH_ANALYSIS",
                        "TARGET_JOB_PARSE",
                        "RESUME_DIAGNOSIS");
        assertThat(result.getRecords().getFirst().getTitle()).isEqualTo("局部改写 - 项目经历");
        assertThat(result.getRecords().getFirst().getResumeName()).isEqualTo("resume.pdf");
        assertThat(result.getRecords().getFirst().getJobTitle()).isEqualTo("Java 后端开发");
    }

    @Test
    void listShouldFilterByResultTypeResumeAndJobDescription() {
        mockOwnedResources();
        when(aiJobMatchResultMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildMatch()));

        AiResultPageVO result = service.list(1L, "MATCH_ANALYSIS", 100L, 10L, null, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords().getFirst().getResultType()).isEqualTo("MATCH_ANALYSIS");
        assertThat(result.getRecords().getFirst().getRecordId()).isEqualTo(400L);
    }

    @Test
    void listShouldReturnEmptyPageForUnownedFilters() {
        mockOwnedResources();

        AiResultPageVO result = service.list(1L, null, 999L, null, null, 1, 10);

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    void listShouldRejectUnsupportedResultType() {
        assertThatThrownBy(() -> service.list(1L, "UNKNOWN", null, null, null, 1, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 结果类型不支持");
    }

    @Test
    void detailShouldReturnMatchAnalysisContent() {
        when(aiJobMatchResultMapper.selectById(400L)).thenReturn(buildMatch());
        when(resumeMapper.selectById(100L)).thenReturn(buildResume());
        when(jobDescriptionMapper.selectById(10L)).thenReturn(buildJobDescription());

        AiResultDetailVO result = service.detail(1L, "MATCH_ANALYSIS", 400L);

        assertThat(result.getRecordId()).isEqualTo(400L);
        assertThat(result.getResultType()).isEqualTo("MATCH_ANALYSIS");
        assertThat(result.getResumeName()).isEqualTo("resume.pdf");
        assertThat(result.getJobTitle()).isEqualTo("Java 后端开发");
        assertThat(result.getContent()).containsEntry("overallScore", 86);
        assertThat(result.getContent()).containsKey("riskNotes");
    }

    @Test
    void detailShouldRejectUnownedResult() {
        Resume otherResume = buildResume();
        otherResume.setUserId(2L);
        when(resumeAiAnalysisMapper.selectById(300L)).thenReturn(buildAnalysis());
        when(resumeMapper.selectById(100L)).thenReturn(otherResume);

        assertThatThrownBy(() -> service.detail(1L, "RESUME_DIAGNOSIS", 300L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 结果不存在");
    }

    private void mockOwnedResources() {
        when(resumeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildResume()));
        when(jobDescriptionMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildJobDescription()));
    }

    private Resume buildResume() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        resume.setOriginalFilename("resume.pdf");
        return resume;
    }

    private JobDescription buildJobDescription() {
        JobDescription jobDescription = new JobDescription();
        jobDescription.setId(10L);
        jobDescription.setUserId(1L);
        jobDescription.setTitle("Java 后端开发");
        jobDescription.setParseStatus("SUCCESS");
        jobDescription.setStructuredContent("{\"skills\":[\"Java\"]}");
        jobDescription.setModelName("deepseek-v4-flash");
        jobDescription.setPromptVersion("job_description_parse_v1");
        jobDescription.setCreatedAt(LocalDateTime.of(2026, 5, 15, 10, 0));
        jobDescription.setUpdatedAt(LocalDateTime.of(2026, 5, 15, 10, 0));
        return jobDescription;
    }

    private ResumeAiAnalysis buildAnalysis() {
        ResumeAiAnalysis analysis = new ResumeAiAnalysis();
        analysis.setId(300L);
        analysis.setResumeId(100L);
        analysis.setAnalysisStatus("SUCCESS");
        analysis.setSuggestionsSummary("[\"补充项目成果\"]");
        analysis.setModelName("deepseek-v4-flash");
        analysis.setPromptVersion("resume_analysis_v1");
        analysis.setCreatedAt(LocalDateTime.of(2026, 5, 15, 9, 0));
        analysis.setUpdatedAt(LocalDateTime.of(2026, 5, 15, 9, 0));
        return analysis;
    }

    private AiJobMatchResult buildMatch() {
        AiJobMatchResult match = new AiJobMatchResult();
        match.setId(400L);
        match.setResumeId(100L);
        match.setJobDescriptionId(10L);
        match.setMatchStatus("SUCCESS");
        match.setOverallScore(86);
        match.setRiskNotes("[\"学历存在筛选风险\"]");
        match.setModelName("deepseek-v4-flash");
        match.setPromptVersion("ai_job_match_v1");
        match.setCreatedAt(LocalDateTime.of(2026, 5, 15, 11, 0));
        match.setUpdatedAt(LocalDateTime.of(2026, 5, 15, 11, 0));
        return match;
    }

    private AiResumeSuggestion buildSuggestion() {
        AiResumeSuggestion suggestion = new AiResumeSuggestion();
        suggestion.setId(500L);
        suggestion.setResumeId(100L);
        suggestion.setJobDescriptionId(10L);
        suggestion.setAiJobMatchResultId(400L);
        suggestion.setSuggestionStatus("SUCCESS");
        suggestion.setSuggestions("[{\"title\":\"项目描述优化\"}]");
        suggestion.setModelName("deepseek-v4-flash");
        suggestion.setPromptVersion("resume_suggestion_v1");
        suggestion.setCreatedAt(LocalDateTime.of(2026, 5, 15, 12, 0));
        suggestion.setUpdatedAt(LocalDateTime.of(2026, 5, 15, 12, 0));
        return suggestion;
    }

    private AiRewriteSuggestion buildRewrite() {
        AiRewriteSuggestion rewrite = new AiRewriteSuggestion();
        rewrite.setId(600L);
        rewrite.setResumeId(100L);
        rewrite.setJobDescriptionId(10L);
        rewrite.setAiJobMatchResultId(400L);
        rewrite.setAiResumeSuggestionId(500L);
        rewrite.setRewriteStatus("SUCCESS");
        rewrite.setTargetSection("项目经历");
        rewrite.setRewrittenText("负责后端接口开发与性能优化。");
        rewrite.setModelName("deepseek-v4-flash");
        rewrite.setPromptVersion("rewrite_suggestion_v1");
        rewrite.setCreatedAt(LocalDateTime.of(2026, 5, 15, 13, 0));
        rewrite.setUpdatedAt(LocalDateTime.of(2026, 5, 15, 13, 0));
        return rewrite;
    }
}
