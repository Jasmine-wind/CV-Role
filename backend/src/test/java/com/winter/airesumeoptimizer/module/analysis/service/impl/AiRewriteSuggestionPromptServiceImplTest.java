package com.winter.airesumeoptimizer.module.analysis.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionPromptService;
import org.junit.jupiter.api.Test;

class AiRewriteSuggestionPromptServiceImplTest {

    private final AiRewriteSuggestionPromptServiceImpl service = new AiRewriteSuggestionPromptServiceImpl();

    @Test
    void buildPromptShouldIncludeVersionInputsOutputSchemaAndSafetyRules() {
        AiRewriteSuggestionPromptDTO result = service.buildPrompt(
                "负责简历上传模块开发",
                "PROJECT",
                "项目经历",
                "{\"requiredSkills\":[\"Spring Boot\"]}",
                "{\"missingSkills\":[]}",
                "{\"suggestions\":[{\"type\":\"PROJECT_DESCRIPTION\"}]}");

        assertThat(result.getPromptVersion()).isEqualTo(AiRewriteSuggestionPromptService.PROMPT_VERSION);
        assertThat(result.getPrompt()).contains("Prompt 版本：rewrite_suggestion_v1");
        assertThat(result.getPrompt()).contains("负责简历上传模块开发");
        assertThat(result.getPrompt()).contains("PROJECT");
        assertThat(result.getPrompt()).contains("项目经历");
        assertThat(result.getPrompt()).contains("rewrittenText");
        assertThat(result.getPrompt()).contains("rewriteReason");
        assertThat(result.getPrompt()).contains("needUserSupplement");
        assertThat(result.getPrompt()).contains("supplementQuestions");
        assertThat(result.getPrompt()).contains("不得编造原文中不存在的项目");
        assertThat(result.getPrompt()).contains("不得代填接口调用量");
        assertThat(result.getPrompt()).contains("不得生成完整简历");
    }

    @Test
    void buildPromptShouldAllowOptionalReferenceInputs() {
        AiRewriteSuggestionPromptDTO result = service.buildPrompt(
                "熟悉 Java",
                "SKILL",
                "技能",
                null,
                "",
                "   ");

        assertThat(result.getPrompt()).contains("岗位描述结构化结果：\n未提供");
        assertThat(result.getPrompt()).contains("AI 匹配结果：\n未提供");
        assertThat(result.getPrompt()).contains("AI 优化建议：\n未提供");
    }

    @Test
    void buildPromptShouldRejectMissingRequiredInputs() {
        assertThatThrownBy(() -> service.buildPrompt("", "PROJECT", "项目经历", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("原文片段不能为空");

        assertThatThrownBy(() -> service.buildPrompt("负责接口开发", "", "项目经历", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("改写对象类型不能为空");

        assertThatThrownBy(() -> service.buildPrompt("负责接口开发", "PROJECT", "", null, null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("目标简历部分不能为空");
    }
}
