package com.winter.airesumeoptimizer.module.job.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.job.dto.JobMatchCalculationResultDTO;
import com.winter.airesumeoptimizer.module.job.dto.JobMatchSuggestionDTO;
import com.winter.airesumeoptimizer.module.job.entity.Job;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobMatchSuggestionServiceImplTest {

    private final JobMatchSuggestionServiceImpl service = new JobMatchSuggestionServiceImpl();

    @Test
    void generateSuggestionsShouldIncludeSkillGapsAndStrengths() {
        JobMatchCalculationResultDTO matchResult = JobMatchCalculationResultDTO.builder()
                .matchScore(67)
                .matchedItems(List.of("Java", "Spring Boot"))
                .missingItems(List.of("PostgreSQL"))
                .matchReason("已命中 2 项技能")
                .build();
        Job job = buildJob("Java 后端开发工程师", "Java 后端");

        List<JobMatchSuggestionDTO> suggestions = service.generateSuggestions(matchResult, job);

        assertThat(suggestions).extracting(JobMatchSuggestionDTO::getType)
                .contains("GENERAL", "SKILL_GAP", "HIGHLIGHT_STRENGTH", "PROJECT_DESCRIPTION");
        assertThat(suggestions).anySatisfy(suggestion -> {
            assertThat(suggestion.getType()).isEqualTo("SKILL_GAP");
            assertThat(suggestion.getPriority()).isEqualTo("HIGH");
            assertThat(suggestion.getRelatedItem()).isEqualTo("PostgreSQL");
            assertThat(suggestion.getContent()).contains("PostgreSQL");
        });
        assertThat(suggestions).anySatisfy(suggestion -> {
            assertThat(suggestion.getType()).isEqualTo("HIGHLIGHT_STRENGTH");
            assertThat(suggestion.getRelatedItem()).isEqualTo("Java");
        });
    }

    @Test
    void generateSuggestionsShouldHandleNoMissingItems() {
        JobMatchCalculationResultDTO matchResult = JobMatchCalculationResultDTO.builder()
                .matchScore(100)
                .matchedItems(List.of("Java"))
                .missingItems(List.of())
                .build();
        Job job = buildJob("Java 后端开发工程师", "Java 后端");

        List<JobMatchSuggestionDTO> suggestions = service.generateSuggestions(matchResult, job);

        assertThat(suggestions).anySatisfy(suggestion -> {
            assertThat(suggestion.getType()).isEqualTo("SKILL_GAP");
            assertThat(suggestion.getPriority()).isEqualTo("LOW");
            assertThat(suggestion.getTitle()).isEqualTo("保持技能覆盖");
        });
    }

    @Test
    void generateSuggestionsShouldHandleLowScore() {
        JobMatchCalculationResultDTO matchResult = JobMatchCalculationResultDTO.builder()
                .matchScore(20)
                .matchedItems(List.of())
                .missingItems(List.of("Vue 3", "TypeScript"))
                .build();
        Job job = buildJob("前端开发工程师", "前端开发");

        List<JobMatchSuggestionDTO> suggestions = service.generateSuggestions(matchResult, job);

        assertThat(suggestions.getFirst().getType()).isEqualTo("GENERAL");
        assertThat(suggestions.getFirst().getPriority()).isEqualTo("HIGH");
        assertThat(suggestions.getFirst().getContent()).contains("匹配度偏低");
    }

    @Test
    void generateSuggestionsShouldRejectNullMatchResult() {
        assertThatThrownBy(() -> service.generateSuggestions(null, buildJob("Java 后端开发工程师", "Java 后端")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位匹配结果不能为空");
    }

    private Job buildJob(String title, String jobCategory) {
        Job job = new Job();
        job.setTitle(title);
        job.setJobCategory(jobCategory);
        return job;
    }
}
