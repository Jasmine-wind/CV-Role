package com.winter.airesumeoptimizer.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.infra.ai.transport.PinnedHttpTransport;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class RealAiClientSmokeTest {

    @Test
    @EnabledIfSystemProperty(named = "realAi", matches = "true")
    void completeShouldCallRealAiApi() {
        AiClientProperties properties = new AiClientProperties();
        properties.setApiKey(requiredProperty("realAi.apiKey"));
        properties.setBaseUrl(propertyOrDefault("realAi.baseUrl", "https://www.openclaudecode.cn/v1"));
        properties.setModel(propertyOrDefault("realAi.model", "deepseek/deepseek-v4-flash"));
        properties.setTimeoutSeconds(Integer.parseInt(propertyOrDefault("realAi.timeoutSeconds", "30")));
        properties.setTemperature(Double.parseDouble(propertyOrDefault("realAi.temperature", "0.2")));

        OpenAiCompatibleAiClientService service = new OpenAiCompatibleAiClientService(
                new ObjectMapper(),
                new PinnedHttpTransport());

        String response = service.complete(new AiProviderRequest(
                properties.getApiKey(),
                properties.getBaseUrl(),
                properties.getModel(),
                properties.getTemperature(),
                properties.getMaxTokens(),
                Duration.ofSeconds(properties.getTimeoutSeconds()),
                List.of(
                        AiChatMessage.system("只遵循服务端 JSON 输出要求。"),
                        AiChatMessage.user("请只回复 JSON：{\"ok\":true}")))).text();

        assertThat(response).isNotBlank();
        System.out.println("AI smoke response: " + response);
    }

    private String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少 JVM 参数：" + name);
        }
        return value;
    }

    private String propertyOrDefault(String name, String defaultValue) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
