package com.winter.airesumeoptimizer.module.job.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.job.dto.JobMatchCalculationResultDTO;
import com.winter.airesumeoptimizer.module.job.entity.Job;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobMatchServiceImplTest {

    private final JobMatchServiceImpl service = new JobMatchServiceImpl(new ObjectMapper());

    @Test
    void calculateMatchShouldReturnMatchedAndMissingSkills() {
        ResumeStructuredContentDTO resumeContent = ResumeStructuredContentDTO.builder()
                .skills(List.of("Java", "Spring Boot", "Git"))
                .build();
        Job job = buildJob("[\"Java\",\"Spring Boot\",\"PostgreSQL\",\"Git\"]");

        JobMatchCalculationResultDTO result = service.calculateMatch(resumeContent, job);

        assertThat(result.getMatchScore()).isEqualTo(75);
        assertThat(result.getMatchedItems()).containsExactly("Java", "Spring Boot", "Git");
        assertThat(result.getMissingItems()).containsExactly("PostgreSQL");
        assertThat(result.getMatchReason()).contains("已命中 3 项技能");
    }

    @Test
    void calculateMatchShouldHandleBlankResumeSkills() {
        ResumeStructuredContentDTO resumeContent = ResumeStructuredContentDTO.builder()
                .skills(List.of())
                .build();
        Job job = buildJob("[\"Java\",\"Spring Boot\"]");

        JobMatchCalculationResultDTO result = service.calculateMatch(resumeContent, job);

        assertThat(result.getMatchScore()).isZero();
        assertThat(result.getMatchedItems()).isEmpty();
        assertThat(result.getMissingItems()).containsExactly("Java", "Spring Boot");
        assertThat(result.getMatchReason()).isEqualTo("简历技能信息不足，暂未命中岗位要求，建议先补充技能关键词。");
    }

    @Test
    void calculateMatchShouldSupportCommaSeparatedJobSkills() {
        ResumeStructuredContentDTO resumeContent = ResumeStructuredContentDTO.builder()
                .skills(List.of("Vue 3", "TypeScript"))
                .build();
        Job job = buildJob("Vue 3, TypeScript, Axios");

        JobMatchCalculationResultDTO result = service.calculateMatch(resumeContent, job);

        assertThat(result.getMatchScore()).isEqualTo(67);
        assertThat(result.getMatchedItems()).containsExactly("Vue 3", "TypeScript");
        assertThat(result.getMissingItems()).containsExactly("Axios");
    }

    @Test
    void calculateMatchShouldRejectBlankJobSkills() {
        ResumeStructuredContentDTO resumeContent = ResumeStructuredContentDTO.builder()
                .skills(List.of("Java"))
                .build();
        Job job = buildJob(" ");

        assertThatThrownBy(() -> service.calculateMatch(resumeContent, job))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位技能要求不能为空");
    }

    private Job buildJob(String requiredSkills) {
        Job job = new Job();
        job.setRequiredSkills(requiredSkills);
        return job;
    }
}
