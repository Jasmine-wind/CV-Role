package com.winter.airesumeoptimizer.module.analysis.suggestion.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionResultDTO;
import org.junit.jupiter.api.Test;

class AiResumeSuggestionOutputParserImplTest {

    private final AiResumeSuggestionOutputParserImpl parser = new AiResumeSuggestionOutputParserImpl(new ObjectMapper());

    @Test
    void parseShouldReturnValidatedSuggestions() {
        AiResumeSuggestionResultDTO result = parser.parse("""
                {
                  "suggestions": [
                    {
                      "type": "SKILL_GAP",
                      "priority": "HIGH",
                      "targetSection": "技能",
                      "issue": "岗位要求 Docker，但简历中未体现 Docker",
                      "suggestion": "如果你确实掌握 Docker，建议补充真实实践。",
                      "evidence": ["岗位要求 Docker", "匹配结果显示 Docker 缺失"],
                      "caution": "不要虚构技能。",
                      "relatedItems": ["Docker"]
                    }
                  ]
                }
                """);

        assertThat(result.getSuggestions()).hasSize(1);
        assertThat(result.getSuggestions().getFirst().getType()).isEqualTo("SKILL_GAP");
        assertThat(result.getSuggestions().getFirst().getPriority()).isEqualTo("HIGH");
        assertThat(result.getSuggestions().getFirst().getEvidence()).containsExactly("岗位要求 Docker", "匹配结果显示 Docker 缺失");
    }

    @Test
    void parseShouldRejectInvalidTypePriorityAndMissingEvidence() {
        assertThatThrownBy(() -> parser.parse("""
                {"suggestions":[{"type":"WRONG","priority":"HIGH","issue":"问题","suggestion":"建议","evidence":["依据"]}]}
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessage("优化建议 type 不合法");

        assertThatThrownBy(() -> parser.parse("""
                {"suggestions":[{"type":"GENERAL","priority":"URGENT","issue":"问题","suggestion":"建议","evidence":["依据"]}]}
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessage("优化建议 priority 不合法");

        assertThatThrownBy(() -> parser.parse("""
                {"suggestions":[{"type":"GENERAL","priority":"LOW","issue":"问题","suggestion":"建议","evidence":[]}]}
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessage("优化建议缺少 evidence");
    }

    @Test
    void parseShouldExtractJsonFromMarkdownFence() {
        AiResumeSuggestionResultDTO result = parser.parse("""
                ```json
                {"suggestions":[]}
                ```
                """);

        assertThat(result.getSuggestions()).isEmpty();
    }
}
