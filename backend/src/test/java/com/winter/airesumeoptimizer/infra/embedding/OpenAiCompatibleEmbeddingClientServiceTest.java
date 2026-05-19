package com.winter.airesumeoptimizer.infra.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.infra.ai.AiClientException;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleEmbeddingClientServiceTest {

    @Test
    void extractEmbeddingShouldReturnVector() {
        OpenAiCompatibleEmbeddingClientService service = new OpenAiCompatibleEmbeddingClientService(
                new EmbeddingClientProperties(),
                new ObjectMapper());

        String responseBody = """
                {"data":[{"embedding":[0.1,-0.2,0.3]}]}
                """;

        assertThat(service.extractEmbedding(responseBody)).containsExactly(0.1, -0.2, 0.3);
    }

    @Test
    void extractEmbeddingShouldRejectMissingEmbedding() {
        OpenAiCompatibleEmbeddingClientService service = new OpenAiCompatibleEmbeddingClientService(
                new EmbeddingClientProperties(),
                new ObjectMapper());

        String responseBody = """
                {"data":[{}]}
                """;

        assertThatThrownBy(() -> service.extractEmbedding(responseBody))
                .isInstanceOf(AiClientException.class)
                .hasMessage("Embedding 响应中缺少向量数据");
    }

    @Test
    void normalizeInputTextShouldRejectBlankText() {
        OpenAiCompatibleEmbeddingClientService service = new OpenAiCompatibleEmbeddingClientService(
                new EmbeddingClientProperties(),
                new ObjectMapper());

        assertThatThrownBy(() -> service.normalizeInputText("  "))
                .isInstanceOf(AiClientException.class)
                .hasMessage("Embedding 输入不能为空");
    }

    @Test
    void normalizeInputTextShouldRejectTooLongText() {
        EmbeddingClientProperties properties = new EmbeddingClientProperties();
        properties.setMaxInputLength(3);
        OpenAiCompatibleEmbeddingClientService service = new OpenAiCompatibleEmbeddingClientService(
                properties,
                new ObjectMapper());

        assertThatThrownBy(() -> service.normalizeInputText("abcd"))
                .isInstanceOf(AiClientException.class)
                .hasMessage("Embedding 输入过长，最大允许字符数：3");
    }

    @Test
    void validateEmbeddingDimensionShouldRejectMismatch() {
        EmbeddingClientProperties properties = new EmbeddingClientProperties();
        properties.setDimension(4);
        OpenAiCompatibleEmbeddingClientService service = new OpenAiCompatibleEmbeddingClientService(
                properties,
                new ObjectMapper());

        assertThatThrownBy(() -> service.validateEmbeddingDimension(List.of(0.1, 0.2, 0.3)))
                .isInstanceOf(AiClientException.class)
                .hasMessage("Embedding 向量维度不一致，期望：4，实际：3");
    }
}
