package com.winter.airesumeoptimizer.infra.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.infra.ai.AiClientException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleEmbeddingClientServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildRequestBodyShouldIncludeModelInputAndDimensions() throws Exception {
        EmbeddingClientProperties properties = siliconFlowProperties();
        OpenAiCompatibleEmbeddingClientService service = new OpenAiCompatibleEmbeddingClientService(
                properties,
                objectMapper);

        String requestBody = service.buildRequestBody("AI 简历优化与岗位匹配系统");

        Map<?, ?> json = objectMapper.readValue(requestBody, Map.class);
        assertThat(json.get("model")).isEqualTo("Qwen/Qwen3-Embedding-0.6B");
        assertThat(json.get("input")).isEqualTo("AI 简历优化与岗位匹配系统");
        assertThat(json.get("dimensions")).isEqualTo(1024);
    }

    @Test
    void extractEmbeddingShouldReturnVector() throws Exception {
        List<Double> vector = IntStream.range(0, 1024)
                .mapToObj(index -> index / 1000.0)
                .toList();
        OpenAiCompatibleEmbeddingClientService service = new OpenAiCompatibleEmbeddingClientService(
                siliconFlowProperties(),
                objectMapper);

        String responseBody = objectMapper.writeValueAsString(Map.of(
                "data", List.of(Map.of("embedding", vector))));

        List<Double> embedding = service.extractEmbedding(responseBody);
        service.validateEmbeddingDimension(embedding);

        assertThat(embedding).hasSize(1024);
    }

    @Test
    void extractEmbeddingShouldRejectMissingEmbedding() {
        OpenAiCompatibleEmbeddingClientService service = new OpenAiCompatibleEmbeddingClientService(
                new EmbeddingClientProperties(),
                objectMapper);

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
                objectMapper);

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
                objectMapper);

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
                objectMapper);

        assertThatThrownBy(() -> service.validateEmbeddingDimension(List.of(0.1, 0.2, 0.3)))
                .isInstanceOf(AiClientException.class)
                .hasMessage("Embedding 向量维度不一致，期望：4，实际：3");
    }

    @Test
    void validateConfigShouldRejectBlankApiKey() {
        EmbeddingClientProperties properties = siliconFlowProperties();
        properties.setApiKey("");
        OpenAiCompatibleEmbeddingClientService service = new OpenAiCompatibleEmbeddingClientService(
                properties,
                objectMapper);

        assertThatThrownBy(service::validateConfig)
                .isInstanceOf(AiClientException.class)
                .hasMessage("Embedding API Key 未配置");
    }

    @Test
    void buildHttpErrorExceptionShouldMapAuthErrors() {
        OpenAiCompatibleEmbeddingClientService service = new OpenAiCompatibleEmbeddingClientService(
                siliconFlowProperties(),
                objectMapper);

        assertThat(service.buildHttpErrorException(401))
                .hasMessage("Embedding API Key 无效或没有调用权限");
        assertThat(service.buildHttpErrorException(403))
                .hasMessage("Embedding API Key 无效或没有调用权限");
    }

    @Test
    void buildHttpErrorExceptionShouldMapRateLimit() {
        OpenAiCompatibleEmbeddingClientService service = new OpenAiCompatibleEmbeddingClientService(
                siliconFlowProperties(),
                objectMapper);

        assertThat(service.buildHttpErrorException(429))
                .hasMessage("Embedding 请求过于频繁或额度不足，请稍后重试");
    }

    @Test
    void buildHttpErrorExceptionShouldMapServerErrors() {
        OpenAiCompatibleEmbeddingClientService service = new OpenAiCompatibleEmbeddingClientService(
                siliconFlowProperties(),
                objectMapper);

        assertThat(service.buildHttpErrorException(500))
                .hasMessage("SiliconFlow Embedding 服务暂时不可用，请稍后重试");
    }

    private EmbeddingClientProperties siliconFlowProperties() {
        EmbeddingClientProperties properties = new EmbeddingClientProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://api.siliconflow.cn/v1");
        properties.setModel("Qwen/Qwen3-Embedding-0.6B");
        properties.setDimension(1024);
        properties.setTimeoutSeconds(120);
        properties.setMaxInputLength(8000);
        return properties;
    }
}
