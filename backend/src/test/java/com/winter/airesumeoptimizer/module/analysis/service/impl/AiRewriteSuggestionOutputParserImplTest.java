package com.winter.airesumeoptimizer.module.analysis.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionResultDTO;
import org.junit.jupiter.api.Test;

class AiRewriteSuggestionOutputParserImplTest {

    private final AiRewriteSuggestionOutputParserImpl parser = new AiRewriteSuggestionOutputParserImpl(new ObjectMapper());

    @Test
    void parseShouldReadValidRewriteResult() {
        AiRewriteSuggestionResultDTO result = parser.parse("""
                {"rewrittenText":"负责简历上传模块后端开发。","rewriteReason":"表达更具体。","caution":"确认职责真实。","needUserSupplement":false,"supplementQuestions":[]}
                """);

        assertThat(result.getRewrittenText()).isEqualTo("负责简历上传模块后端开发。");
        assertThat(result.getRewriteReason()).isEqualTo("表达更具体。");
        assertThat(result.getCaution()).isEqualTo("确认职责真实。");
        assertThat(result.getNeedUserSupplement()).isFalse();
        assertThat(result.getSupplementQuestions()).isEmpty();
    }

    @Test
    void parseShouldExtractJsonFromCodeFence() {
        AiRewriteSuggestionResultDTO result = parser.parse("""
                ```json
                {"rewrittenText":"熟悉 Java 后端开发。","rewriteReason":"保留事实并优化表达。","caution":"不要补充未掌握技能。","needUserSupplement":true,"supplementQuestions":["是否有真实项目场景？"]}
                ```
                """);

        assertThat(result.getNeedUserSupplement()).isTrue();
        assertThat(result.getSupplementQuestions()).containsExactly("是否有真实项目场景？");
    }

    @Test
    void parseShouldRejectMissingRequiredFields() {
        assertThatThrownBy(() -> parser.parse("{\"rewrittenText\":\"改写文本\"}"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 局部改写结果缺少 rewriteReason");
    }

    @Test
    void parseShouldRejectSupplementFlagWithoutQuestions() {
        assertThatThrownBy(() -> parser.parse("""
                {"rewrittenText":"改写文本","rewriteReason":"原因","caution":"注意","needUserSupplement":true,"supplementQuestions":[]}
                """))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 局部改写结果需要补充信息时必须提供 supplementQuestions");
    }
}
