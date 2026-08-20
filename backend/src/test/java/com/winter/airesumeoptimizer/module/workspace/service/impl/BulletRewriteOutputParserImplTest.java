package com.winter.airesumeoptimizer.module.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.workspace.dto.BulletRewriteOutputDTO;
import com.winter.airesumeoptimizer.module.workspace.service.BulletRewriteRefusedException;
import org.junit.jupiter.api.Test;

/**
 * AI 输出解析：malformed / empty / oversized / truncated / refusal 全部 fail closed。
 */
class BulletRewriteOutputParserImplTest {

    private final BulletRewriteOutputParserImpl parser =
            new BulletRewriteOutputParserImpl(new ObjectMapper());

    @Test
    void shouldParseValidOutput() {
        BulletRewriteOutputDTO result = parser.parse(
                "{\"suggestedText\":\"承担订单服务开发\",\"reason\":\"表达更完整\"}");

        assertThat(result.suggestedText()).isEqualTo("承担订单服务开发");
        assertThat(result.reason()).isEqualTo("表达更完整");
    }

    @Test
    void markdownFencedOutputShouldFailClosed() {
        assertThatThrownBy(() -> parser.parse(
                "```json\n{\"suggestedText\":\"x\",\"reason\":\"y\"}\n```"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void prosePrefixShouldFailClosed() {
        assertThatThrownBy(() -> parser.parse(
                "好的，结果如下：{\"suggestedText\":\"x\",\"reason\":\"y\"}"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void truncatedJsonShouldFailClosed() {
        assertThatThrownBy(() -> parser.parse(
                "{\"suggestedText\":\"承担订单服务开发\",\"reason\":\"表达更完整\""))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void missingSuggestedTextShouldFailClosed() {
        assertThatThrownBy(() -> parser.parse("{\"reason\":\"只有原因\"}"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void blankOutputShouldFailClosed() {
        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void emptySuggestedTextShouldBeTreatedAsRefusal() {
        assertThatThrownBy(() -> parser.parse("{\"suggestedText\":\"\",\"reason\":\"无法改写\"}"))
                .isInstanceOf(BulletRewriteRefusedException.class);
    }

    @Test
    void refusalProseInsideSuggestedTextShouldBeTreatedAsRefusal() {
        assertThatThrownBy(() -> parser.parse(
                "{\"suggestedText\":\"抱歉，我无法在不新增事实的情况下改写\",\"reason\":\"\"}"))
                .isInstanceOf(BulletRewriteRefusedException.class);
    }

    @Test
    void oversizedSuggestedTextShouldFailClosed() {
        String oversized = "负".repeat(4001);
        assertThatThrownBy(() -> parser.parse(
                "{\"suggestedText\":\"" + oversized + "\",\"reason\":\"\"}"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void oversizedReasonShouldBeTruncatedNotRejected() {
        BulletRewriteOutputDTO result = parser.parse(
                "{\"suggestedText\":\"承担订单服务开发\",\"reason\":\"" + "因".repeat(500) + "\"}");

        assertThat(result.reason()).hasSize(200);
    }
}
