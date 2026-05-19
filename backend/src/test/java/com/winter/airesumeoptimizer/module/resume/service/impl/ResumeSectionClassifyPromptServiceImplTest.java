package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeSectionClassifyPromptServiceImplTest {

    private final ResumeSectionClassifyPromptServiceImpl service = new ResumeSectionClassifyPromptServiceImpl(new ObjectMapper());

    @Test
    void buildPromptShouldContainSafetyConstraintsAndBlocks() {
        var prompt = service.buildPrompt(List.of(ResumeBlockDTO.builder()
                .index(0)
                .originalIndex(10)
                .displayOrder(2)
                .text("教育背景 示例大学 本科")
                .prevText("张三 13800000000")
                .nextText("专业技能 Java Spring Boot")
                .sourceType("cleanedText")
                .sourceSection("GENERAL")
                .ruleSection("OTHERS")
                .ruleConfidence(0.35)
                .sourceSectionConfidence("LOW")
                .lockedLevel("LOW")
                .resumeTypeHint("INTERN")
                .parseMode("BALANCED")
                .build()));

        assertThat(prompt.getPromptVersion()).isEqualTo("resume-section-classify-v2");
        assertThat(prompt.getPrompt())
                .contains("no fabrication")
                .contains("no rewriting")
                .contains("return JSON only")
                .contains("output fields only: items[].index, items[].section, items[].confidence, items[].reasonCode")
                .contains("BASIC_INFO")
                .contains("WORK_EXPERIENCES")
                .contains("教育背景 示例大学 本科")
                .contains("张三 13800000000")
                .contains("专业技能 Java Spring Boot")
                .contains("ruleSection")
                .contains("ruleConfidence")
                .contains("sourceSectionConfidence")
                .contains("lockedLevel")
                .contains("resumeTypeHint")
                .contains("\"parseMode\":\"BALANCED\"")
                .doesNotContain("originalIndex")
                .doesNotContain("displayOrder")
                .doesNotContain("sourceType")
                .doesNotContain("不得丢弃任何有价值 block")
                .doesNotContain("输出格式");
        assertThat(prompt.getPrompt().length()).isLessThan(1800);
    }
}
