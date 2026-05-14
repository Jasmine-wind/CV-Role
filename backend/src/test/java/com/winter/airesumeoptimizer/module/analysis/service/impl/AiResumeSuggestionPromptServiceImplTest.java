package com.winter.airesumeoptimizer.module.analysis.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.service.AiResumeSuggestionPromptService;
import org.junit.jupiter.api.Test;

class AiResumeSuggestionPromptServiceImplTest {

    private final AiResumeSuggestionPromptServiceImpl service = new AiResumeSuggestionPromptServiceImpl();

    @Test
    void buildPromptShouldIncludeVersionInputsAndSafetyRules() {
        AiResumeSuggestionPromptDTO result = service.buildPrompt(
                "{\"skills\":[\"Java\"]}",
                "{\"requiredSkills\":[\"Docker\"]}",
                "{\"missingSkills\":[{\"item\":\"Docker\"}]}");

        assertThat(result.getPromptVersion()).isEqualTo(AiResumeSuggestionPromptService.PROMPT_VERSION);
        assertThat(result.getPrompt()).contains("Prompt 版本：resume_suggestion_v1");
        assertThat(result.getPrompt()).contains("不得编造简历中不存在的经历");
        assertThat(result.getPrompt()).contains("不得生成完整定制版简历");
        assertThat(result.getPrompt()).contains("SKILL_GAP");
        assertThat(result.getPrompt()).contains("{\"skills\":[\"Java\"]}");
        assertThat(result.getPrompt()).contains("{\"requiredSkills\":[\"Docker\"]}");
        assertThat(result.getPrompt()).contains("\"missingSkills\"");
    }

    @Test
    void buildPromptShouldRejectMissingInputs() {
        assertThatThrownBy(() -> service.buildPrompt("", "{\"requiredSkills\":[\"Java\"]}", "{}"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历结构化解析结果不能为空");

        assertThatThrownBy(() -> service.buildPrompt("{}", "", "{}"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位描述结构化解析结果不能为空");

        assertThatThrownBy(() -> service.buildPrompt("{}", "{}", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 匹配结果不能为空");
    }
}
