package com.winter.airesumeoptimizer.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleAiClientServiceTest {

    @Test
    void extractContentShouldReturnAssistantContent() {
        OpenAiCompatibleAiClientService service = new OpenAiCompatibleAiClientService(
                new AiClientProperties(),
                new ObjectMapper());

        String responseBody = """
                {"choices":[{"message":{"content":"基础 AI 调用成功"}}]}
                """;

        assertThat(service.extractContent(responseBody)).isEqualTo("基础 AI 调用成功");
    }

    @Test
    void completeShouldRejectMissingApiKey() {
        AiClientProperties properties = new AiClientProperties();
        properties.setBaseUrl("http://localhost:8080/v1");
        properties.setModel("test-model");

        OpenAiCompatibleAiClientService service = new OpenAiCompatibleAiClientService(
                properties,
                new ObjectMapper());

        assertThatThrownBy(() -> service.complete("测试输入"))
                .isInstanceOf(AiClientException.class)
                .hasMessage("AI API Key 未配置");
    }
}
