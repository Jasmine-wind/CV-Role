package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBasicInfoFieldDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResumeStructuredParsePromptServiceImplTest {

    private final ResumeStructuredParsePromptServiceImpl service =
            new ResumeStructuredParsePromptServiceImpl(new ObjectMapper());

    @Test
    void buildPromptShouldContainSchemaBlocksAndRuleResult() {
        var prompt = service.buildPrompt(
                List.of(ResumeBlockDTO.builder()
                        .index(0)
                        .text("专业技能 Java Spring Boot")
                        .sourceType("cleanedText")
                        .sourceSection("SKILLS")
                        .build()),
                ResumeStructuredContentDTO.builder()
                        .name("张三")
                        .skills(List.of("Java"))
                        .build(),
                List.of("SKILLS_MISSING"));

        assertThat(prompt.getPromptVersion()).isEqualTo("resume-structured-parse-v2");
        assertThat(prompt.getPrompt())
                .contains("schema")
                .contains("classifiedBlocks")
                .contains("ruleStructuredContent")
                .contains("qualityWarnings")
                .contains("不得编造")
                .contains("只返回有把握补全或纠错的字段")
                .contains("专业技能 Java Spring Boot")
                .contains("missingFields")
                .contains("stableFieldsOmitted")
                .doesNotContain("张三");
    }

    @Test
    void buildPromptShouldOmitStableRuleStructuredContent() {
        List<String> longProjects = List.of(
                "项目1：" + "负责接口设计和性能优化".repeat(30),
                "项目2：" + "负责接口设计和性能优化".repeat(30),
                "项目3：" + "负责接口设计和性能优化".repeat(30),
                "项目4：" + "负责接口设计和性能优化".repeat(30),
                "项目5：" + "负责接口设计和性能优化".repeat(30),
                "项目6：" + "负责接口设计和性能优化".repeat(30),
                "项目7：" + "负责接口设计和性能优化".repeat(30),
                "项目8：" + "负责接口设计和性能优化".repeat(30),
                "项目9：" + "负责接口设计和性能优化".repeat(30),
                "项目10：" + "负责接口设计和性能优化".repeat(30),
                "项目11：" + "负责接口设计和性能优化".repeat(30),
                "项目12：" + "负责接口设计和性能优化".repeat(30),
                "项目13：" + "不应进入 Prompt");

        var prompt = service.buildPrompt(
                List.of(ResumeBlockDTO.builder()
                        .index(0)
                        .text("项目经历")
                        .sourceType("cleanedText")
                        .sourceSection("PROJECTS")
                        .build()),
                ResumeStructuredContentDTO.builder()
                        .projects(longProjects)
                        .build(),
                List.of());

        assertThat(prompt.getPrompt()).contains("missingFields");
        assertThat(prompt.getPrompt()).doesNotContain("项目12");
        assertThat(prompt.getPrompt()).doesNotContain("项目13");
        assertThat(prompt.getPrompt()).doesNotContain("负责接口设计和性能优化".repeat(20));
    }

    @Test
    void buildPromptShouldIncludeMoreContextForAccurateMode() {
        var prompt = service.buildPrompt(
                List.of(ResumeBlockDTO.builder()
                        .index(0)
                        .text("本人具有较强学习能力")
                        .sourceType("cleanedText")
                        .sourceSection("GENERAL")
                        .build()),
                ResumeStructuredContentDTO.builder()
                        .parseMode("ACCURATE")
                        .basicInfoDebug(Map.of("name", ResumeBasicInfoFieldDTO.builder()
                                .value("")
                                .confidence(0.2)
                                .source("RULE")
                                .evidence("本人具有较强学习能力")
                                .status("REJECTED")
                                .rejectReason("命中姓名黑名单")
                                .build()))
                        .build(),
                List.of());

        assertThat(prompt.getPrompt())
                .contains("\"parseMode\":\"ACCURATE\"")
                .contains("ACCURATE_MORE_CONTEXT")
                .contains("basicInfoDebug")
                .contains("命中姓名黑名单");
    }
}
