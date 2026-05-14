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
import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.ResumeAiAnalysisMapper;
import com.winter.airesumeoptimizer.module.history.vo.HistoryDetailVO;
import com.winter.airesumeoptimizer.module.history.vo.HistoryPageVO;
import com.winter.airesumeoptimizer.module.job.entity.Job;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.entity.JobMatchResult;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.job.mapper.JobMapper;
import com.winter.airesumeoptimizer.module.job.mapper.JobMatchResultMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class HistoryServiceImplTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final ResumeParseResultMapper resumeParseResultMapper = mock(ResumeParseResultMapper.class);
    private final ResumeAiAnalysisMapper resumeAiAnalysisMapper = mock(ResumeAiAnalysisMapper.class);
    private final JobMatchResultMapper jobMatchResultMapper = mock(JobMatchResultMapper.class);
    private final AiJobMatchResultMapper aiJobMatchResultMapper = mock(AiJobMatchResultMapper.class);
    private final JobMapper jobMapper = mock(JobMapper.class);
    private final JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HistoryServiceImpl service = new HistoryServiceImpl(
            resumeMapper,
            resumeParseResultMapper,
            resumeAiAnalysisMapper,
            jobMatchResultMapper,
            aiJobMatchResultMapper,
            jobMapper,
            jobDescriptionMapper,
            objectMapper);

    @Test
    void listShouldReturnEmptyPageWhenUserHasNoResume() {
        when(resumeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        HistoryPageVO result = service.list(1L, null, null);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getPage()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotal()).isZero();
        assertThat(result.getTotalPages()).isZero();
    }

    @Test
    void listShouldAggregateAndSortByLatestUpdatedAt() {
        Resume oldResume = buildResume(100L, "old.pdf", time(1), time(1));
        Resume recentResume = buildResume(200L, "recent.pdf", time(2), time(2));
        ResumeParseResult oldParse = buildParseResult(100L, "SUCCESS", time(3));
        ResumeParseResult recentParse = buildParseResult(200L, "SUCCESS", time(4));
        ResumeAiAnalysis recentAnalysis = buildAnalysis(200L, 88, time(5));
        AiJobMatchResult recentMatch = buildAiMatch(400L, 200L, 10L, 91, time(6));
        JobDescription jobDescription = buildJobDescription(10L);

        when(resumeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(oldResume, recentResume));
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(oldParse, recentParse);
        when(resumeAiAnalysisMapper.selectOne(any(Wrapper.class))).thenReturn(null, recentAnalysis);
        when(jobMatchResultMapper.selectList(any(Wrapper.class))).thenReturn(List.of(), List.of());
        when(aiJobMatchResultMapper.selectList(any(Wrapper.class))).thenReturn(List.of(), List.of(recentMatch));
        when(jobDescriptionMapper.selectById(10L)).thenReturn(jobDescription);

        HistoryPageVO result = service.list(1L, 1, 10);

        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getResumeId()).isEqualTo(200L);
        assertThat(result.getRecords().get(0).getAnalysisScore()).isEqualTo(88);
        assertThat(result.getRecords().get(0).getLatestJobTitle()).isEqualTo("Java 后端开发工程师");
        assertThat(result.getRecords().get(0).getLatestJobDescriptionId()).isEqualTo(10L);
        assertThat(result.getRecords().get(0).getLatestMatchSource()).isEqualTo("AI_JOB_DESCRIPTION");
        assertThat(result.getRecords().get(0).getLatestMatchScore()).isEqualTo(91);
        assertThat(result.getRecords().get(1).getResumeId()).isEqualTo(100L);
    }

    @Test
    void detailShouldRejectOtherUsersResume() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.detail(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历不存在");
    }

    @Test
    void detailShouldReturnAggregatedRecord() {
        Resume resume = buildResume(100L, "demo.pdf", time(1), time(1));
        ResumeParseResult parseResult = buildParseResult(100L, "SUCCESS", time(2));
        ResumeAiAnalysis analysis = buildAnalysis(100L, 78, time(3));
        JobMatchResult matchResult = buildMatch(500L, 100L, 20L, 66, time(4));
        AiJobMatchResult aiMatchResult = buildAiMatch(600L, 100L, 10L, 82, time(5));

        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(parseResult);
        when(resumeAiAnalysisMapper.selectOne(any(Wrapper.class))).thenReturn(analysis);
        when(jobMatchResultMapper.selectList(any(Wrapper.class))).thenReturn(List.of(matchResult));
        when(aiJobMatchResultMapper.selectList(any(Wrapper.class))).thenReturn(List.of(aiMatchResult));
        when(jobMapper.selectById(20L)).thenReturn(buildJob(20L));
        when(jobDescriptionMapper.selectById(10L)).thenReturn(buildJobDescription(10L));

        HistoryDetailVO result = service.detail(1L, 100L);

        assertThat(result.getRecordId()).isEqualTo(100L);
        assertThat(result.getResume().getResumeName()).isEqualTo("demo.pdf");
        assertThat(result.getParseResult().getParseStatus()).isEqualTo("SUCCESS");
        assertThat(result.getAiAnalysis().getAnalysisScore()).isEqualTo(78);
        assertThat(result.getAiAnalysis().getSuggestionsSummary()).isEqualTo("补充项目量化结果");
        assertThat(result.getLatestMatch().getMatchScore()).isEqualTo(82);
        assertThat(result.getLatestMatch().getMatchSource()).isEqualTo("AI_JOB_DESCRIPTION");
        assertThat(result.getMatchResults()).hasSize(2);
        assertThat(result.getUpdatedAt()).isEqualTo(time(5));
    }

    private Resume buildResume(Long id, String filename, LocalDateTime createdAt, LocalDateTime updatedAt) {
        Resume resume = new Resume();
        resume.setId(id);
        resume.setUserId(1L);
        resume.setOriginalFilename(filename);
        resume.setFileType("PDF");
        resume.setFileSize(1024L);
        resume.setUploadStatus("UPLOADED");
        resume.setCreatedAt(createdAt);
        resume.setUpdatedAt(updatedAt);
        return resume;
    }

    private ResumeParseResult buildParseResult(Long resumeId, String status, LocalDateTime updatedAt) {
        ResumeParseResult parseResult = new ResumeParseResult();
        parseResult.setResumeId(resumeId);
        parseResult.setParseStatus(status);
        parseResult.setExtractedText("Java 后端开发经验，熟悉 Spring Boot 和 PostgreSQL。");
        parseResult.setUpdatedAt(updatedAt);
        return parseResult;
    }

    private ResumeAiAnalysis buildAnalysis(Long resumeId, Integer score, LocalDateTime updatedAt) {
        ResumeAiAnalysis analysis = new ResumeAiAnalysis();
        analysis.setResumeId(resumeId);
        analysis.setAnalysisStatus("SUCCESS");
        analysis.setScore(score);
        analysis.setStrengths("[\"Java 基础扎实\"]");
        analysis.setSuggestionsSummary("[\"补充项目量化结果\"]");
        analysis.setUpdatedAt(updatedAt);
        return analysis;
    }

    private JobMatchResult buildMatch(Long id, Long resumeId, Long jobId, Integer score, LocalDateTime updatedAt) {
        JobMatchResult matchResult = new JobMatchResult();
        matchResult.setId(id);
        matchResult.setResumeId(resumeId);
        matchResult.setJobId(jobId);
        matchResult.setMatchScore(score);
        matchResult.setMatchReason("技能匹配度较高");
        matchResult.setSuggestions("[{\"title\":\"补充 PostgreSQL 项目\"}]");
        matchResult.setUpdatedAt(updatedAt);
        return matchResult;
    }

    private AiJobMatchResult buildAiMatch(
            Long id,
            Long resumeId,
            Long jobDescriptionId,
            Integer score,
            LocalDateTime updatedAt) {
        AiJobMatchResult matchResult = new AiJobMatchResult();
        matchResult.setId(id);
        matchResult.setResumeId(resumeId);
        matchResult.setJobDescriptionId(jobDescriptionId);
        matchResult.setOverallScore(score);
        matchResult.setStrongMatches("[\"Spring Boot 匹配\"]");
        matchResult.setRiskNotes("[\"缺少高并发项目\"]");
        matchResult.setUpdatedAt(updatedAt);
        return matchResult;
    }

    private Job buildJob(Long id) {
        Job job = new Job();
        job.setId(id);
        job.setTitle("Java 后端开发工程师");
        job.setCompanyName("星河软件");
        job.setJobCategory("后端开发");
        return job;
    }

    private JobDescription buildJobDescription(Long id) {
        JobDescription jobDescription = new JobDescription();
        jobDescription.setId(id);
        jobDescription.setTitle("Java 后端开发工程师");
        return jobDescription;
    }

    private LocalDateTime time(int hour) {
        return LocalDateTime.of(2026, 5, 9, hour, 0);
    }
}
