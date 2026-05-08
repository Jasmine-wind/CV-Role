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
import com.winter.airesumeoptimizer.module.job.dto.JobMatchCalculationResultDTO;
import com.winter.airesumeoptimizer.module.job.dto.JobMatchSuggestionDTO;
import com.winter.airesumeoptimizer.module.job.entity.Job;
import com.winter.airesumeoptimizer.module.job.entity.JobMatchResult;
import com.winter.airesumeoptimizer.module.job.mapper.JobMapper;
import com.winter.airesumeoptimizer.module.job.mapper.JobMatchResultMapper;
import com.winter.airesumeoptimizer.module.job.service.JobMatchService;
import com.winter.airesumeoptimizer.module.job.service.JobMatchSuggestionService;
import com.winter.airesumeoptimizer.module.job.vo.JobMatchResultVO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobMatchResultServiceImplTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final ResumeParseResultMapper resumeParseResultMapper = mock(ResumeParseResultMapper.class);
    private final JobMapper jobMapper = mock(JobMapper.class);
    private final JobMatchResultMapper jobMatchResultMapper = mock(JobMatchResultMapper.class);
    private final JobMatchService jobMatchService = mock(JobMatchService.class);
    private final JobMatchSuggestionService jobMatchSuggestionService = mock(JobMatchSuggestionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JobMatchResultServiceImpl service = new JobMatchResultServiceImpl(
            resumeMapper,
            resumeParseResultMapper,
            jobMapper,
            jobMatchResultMapper,
            jobMatchService,
            jobMatchSuggestionService,
            objectMapper);

    @Test
    void matchShouldSaveNewResult() throws Exception {
        Resume resume = buildResume();
        ResumeStructuredContentDTO structuredContent = ResumeStructuredContentDTO.builder()
                .skills(List.of("Java"))
                .build();
        ResumeParseResult parseResult = buildParseResult(objectMapper.writeValueAsString(structuredContent));
        Job job = buildJob("ENABLED");
        JobMatchCalculationResultDTO calculationResult = JobMatchCalculationResultDTO.builder()
                .matchScore(50)
                .matchedItems(List.of("Java"))
                .missingItems(List.of("PostgreSQL"))
                .matchReason("命中 1 项技能")
                .build();
        List<JobMatchSuggestionDTO> suggestions = List.of(JobMatchSuggestionDTO.builder()
                .type("SKILL_GAP")
                .priority("HIGH")
                .title("补充缺失技能：PostgreSQL")
                .content("补充 PostgreSQL")
                .relatedItem("PostgreSQL")
                .build());

        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(parseResult);
        when(jobMapper.selectById(2L)).thenReturn(job);
        when(jobMatchService.calculateMatch(any(ResumeStructuredContentDTO.class), any(Job.class)))
                .thenReturn(calculationResult);
        when(jobMatchSuggestionService.generateSuggestions(calculationResult, job)).thenReturn(suggestions);
        when(jobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(jobMatchResultMapper.insert(any(JobMatchResult.class))).thenAnswer(invocation -> {
            JobMatchResult result = invocation.getArgument(0);
            result.setId(10L);
            return 1;
        });

        JobMatchResultVO result = service.match(1L, 100L, 2L);

        assertThat(result.getMatchId()).isEqualTo(10L);
        assertThat(result.getResumeId()).isEqualTo(100L);
        assertThat(result.getJobId()).isEqualTo(2L);
        assertThat(result.getMatchScore()).isEqualTo(50);
        assertThat(result.getMatchedItems()).containsExactly("Java");
        assertThat(result.getMissingItems()).containsExactly("PostgreSQL");
        assertThat(result.getSuggestions()).hasSize(1);
        verify(jobMatchResultMapper).insert(any(JobMatchResult.class));
        verify(jobMatchResultMapper, never()).updateById(any(JobMatchResult.class));
    }

    @Test
    void matchShouldUpdateExistingResult() throws Exception {
        Resume resume = buildResume();
        ResumeParseResult parseResult = buildParseResult(objectMapper.writeValueAsString(
                ResumeStructuredContentDTO.builder().skills(List.of("Java")).build()));
        Job job = buildJob("ENABLED");
        JobMatchResult existingResult = new JobMatchResult();
        existingResult.setId(20L);
        existingResult.setResumeId(100L);
        existingResult.setJobId(2L);
        JobMatchCalculationResultDTO calculationResult = JobMatchCalculationResultDTO.builder()
                .matchScore(100)
                .matchedItems(List.of("Java"))
                .missingItems(List.of())
                .matchReason("全部命中")
                .build();

        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(resume);
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(parseResult);
        when(jobMapper.selectById(2L)).thenReturn(job);
        when(jobMatchService.calculateMatch(any(ResumeStructuredContentDTO.class), any(Job.class)))
                .thenReturn(calculationResult);
        when(jobMatchSuggestionService.generateSuggestions(calculationResult, job)).thenReturn(List.of());
        when(jobMatchResultMapper.selectOne(any(Wrapper.class))).thenReturn(existingResult);

        JobMatchResultVO result = service.match(1L, 100L, 2L);

        assertThat(result.getMatchId()).isEqualTo(20L);
        assertThat(result.getMatchScore()).isEqualTo(100);
        verify(jobMatchResultMapper).updateById(existingResult);
        verify(jobMatchResultMapper, never()).insert(any(JobMatchResult.class));
    }

    @Test
    void matchShouldRejectUnparsedResume() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.match(1L, 100L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先完成简历解析");
    }

    @Test
    void matchShouldRejectDisabledJob() throws Exception {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(resumeParseResultMapper.selectOne(any(Wrapper.class))).thenReturn(buildParseResult(objectMapper.writeValueAsString(
                ResumeStructuredContentDTO.builder().skills(List.of("Java")).build())));
        when(jobMapper.selectById(2L)).thenReturn(buildJob("DISABLED"));

        assertThatThrownBy(() -> service.match(1L, 100L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位不可用");
    }

    @Test
    void listByResumeShouldReturnMatchResults() throws Exception {
        JobMatchResult matchResult = buildSavedMatchResult();

        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobMatchResultMapper.selectList(any(Wrapper.class))).thenReturn(List.of(matchResult));
        when(jobMapper.selectById(2L)).thenReturn(buildJob("ENABLED"));

        List<JobMatchResultVO> results = service.listByResume(1L, 100L);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getMatchId()).isEqualTo(30L);
        assertThat(results.getFirst().getJobTitle()).isEqualTo("Java 后端开发工程师");
        assertThat(results.getFirst().getMatchedItems()).containsExactly("Java");
        assertThat(results.getFirst().getMissingItems()).containsExactly("PostgreSQL");
        assertThat(results.getFirst().getSuggestions()).hasSize(1);
    }

    @Test
    void listByResumeShouldReturnEmptyListWhenNoResults() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(buildResume());
        when(jobMatchResultMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        List<JobMatchResultVO> results = service.listByResume(1L, 100L);

        assertThat(results).isEmpty();
    }

    @Test
    void listByResumeShouldRejectOtherUsersResume() {
        when(resumeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.listByResume(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历不存在");
    }

    private Resume buildResume() {
        Resume resume = new Resume();
        resume.setId(100L);
        resume.setUserId(1L);
        return resume;
    }

    private ResumeParseResult buildParseResult(String structuredJson) {
        ResumeParseResult parseResult = new ResumeParseResult();
        parseResult.setResumeId(100L);
        parseResult.setParseStatus("SUCCESS");
        parseResult.setStructuredJson(structuredJson);
        return parseResult;
    }

    private Job buildJob(String status) {
        Job job = new Job();
        job.setId(2L);
        job.setTitle("Java 后端开发工程师");
        job.setCompanyName("星河软件");
        job.setStatus(status);
        job.setRequiredSkills("[\"Java\"]");
        return job;
    }

    private JobMatchResult buildSavedMatchResult() throws Exception {
        JobMatchResult matchResult = new JobMatchResult();
        matchResult.setId(30L);
        matchResult.setResumeId(100L);
        matchResult.setJobId(2L);
        matchResult.setMatchScore(50);
        matchResult.setMatchedItems(objectMapper.writeValueAsString(List.of("Java")));
        matchResult.setMissingItems(objectMapper.writeValueAsString(List.of("PostgreSQL")));
        matchResult.setMatchReason("命中 1 项技能");
        matchResult.setSuggestions(objectMapper.writeValueAsString(List.of(JobMatchSuggestionDTO.builder()
                .type("SKILL_GAP")
                .priority("HIGH")
                .title("补充缺失技能：PostgreSQL")
                .content("补充 PostgreSQL")
                .relatedItem("PostgreSQL")
                .build())));
        return matchResult;
    }
}
