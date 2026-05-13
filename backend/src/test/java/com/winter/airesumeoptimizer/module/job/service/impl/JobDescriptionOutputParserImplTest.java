package com.winter.airesumeoptimizer.module.job.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionParseResultDTO;
import org.junit.jupiter.api.Test;

class JobDescriptionOutputParserImplTest {

    private final JobDescriptionOutputParserImpl parser = new JobDescriptionOutputParserImpl(new ObjectMapper());

    @Test
    void parseShouldReadStructuredJobDescriptionJson() {
        JobDescriptionParseResultDTO result = parser.parse("""
                {
                  "jobTitle": "Java 后端开发工程师",
                  "requiredSkills": ["Java", "Spring Boot"],
                  "bonusSkills": ["Redis"],
                  "experienceSignals": ["有后端项目经验"],
                  "responsibilities": ["负责业务接口开发"],
                  "keywords": ["Java", "后端开发"],
                  "summary": "岗位侧重 Java 后端开发"
                }
                """);

        assertThat(result.getJobTitle()).isEqualTo("Java 后端开发工程师");
        assertThat(result.getRequiredSkills()).containsExactly("Java", "Spring Boot");
        assertThat(result.getBonusSkills()).containsExactly("Redis");
        assertThat(result.getExperienceSignals()).containsExactly("有后端项目经验");
        assertThat(result.getResponsibilities()).containsExactly("负责业务接口开发");
        assertThat(result.getKeywords()).containsExactly("Java", "后端开发");
        assertThat(result.getSummary()).isEqualTo("岗位侧重 Java 后端开发");
    }

    @Test
    void parseShouldRejectInvalidJson() {
        assertThatThrownBy(() -> parser.parse("不是 JSON"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位描述解析结果不是合法 JSON");
    }

    @Test
    void parseShouldRejectNonObjectJson() {
        assertThatThrownBy(() -> parser.parse("[\"Java\"]"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位描述解析结果必须是 JSON 对象");
    }

    @Test
    void parseShouldLimitListSizeAndItemLength() {
        String longText = "非常长的技能描述".repeat(20);
        JobDescriptionParseResultDTO result = parser.parse("""
                {
                  "jobTitle": "Java 后端开发工程师",
                  "requiredSkills": ["%s", "1", "2", "3", "4", "5", "6", "7", "8"],
                  "bonusSkills": [],
                  "experienceSignals": [],
                  "responsibilities": [],
                  "keywords": [],
                  "summary": ""
                }
                """.formatted(longText));

        assertThat(result.getRequiredSkills()).hasSize(8);
        assertThat(result.getRequiredSkills().getFirst()).hasSize(80);
    }
}
