package com.winter.airesumeoptimizer.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

class RealEmbeddingClientSmokeTest {

    @Test
    @EnabledIfSystemProperty(named = "realEmbedding", matches = "true")
    void embedShouldCallRealEmbeddingApi() {
        EmbeddingClientProperties properties = new EmbeddingClientProperties();
        properties.setApiKey(requiredPropertyOrEnv("realEmbedding.apiKey", "EMBEDDING_API_KEY"));
        properties.setBaseUrl(requiredPropertyOrEnv("realEmbedding.baseUrl", "EMBEDDING_BASE_URL"));
        properties.setModel(propertyOrEnv("realEmbedding.model", "EMBEDDING_MODEL", "Qwen3-Embedding-0.6B"));
        properties.setDimension(Integer.parseInt(propertyOrEnv("realEmbedding.dimension", "EMBEDDING_DIMENSION", "1024")));
        properties.setTimeout(Integer.parseInt(propertyOrEnv("realEmbedding.timeout", "EMBEDDING_TIMEOUT", "30")));
        properties.setMaxInputLength(Integer.parseInt(propertyOrEnv(
                "realEmbedding.maxInputLength",
                "EMBEDDING_MAX_INPUT_LENGTH",
                "8192")));

        OpenAiCompatibleEmbeddingClientService service = new OpenAiCompatibleEmbeddingClientService(
                properties,
                new ObjectMapper());

        List<Double> embedding = service.embed("这是一个用于验证本地 Embedding 服务的短文本。");

        assertThat(embedding).hasSize(properties.getDimension());
        assertThat(embedding).allSatisfy(value -> assertThat(value).isFinite());
        System.out.println("Embedding smoke succeeded: model="
                + properties.getModel()
                + ", dimension="
                + embedding.size());
    }

    private String requiredPropertyOrEnv(String propertyName, String envName) {
        String value = propertyOrEnv(propertyName, envName, "");
        if (value.isBlank()) {
            throw new IllegalStateException("缺少配置：" + propertyName + " 或 " + envName);
        }
        return value;
    }

    private String propertyOrEnv(String propertyName, String envName, String defaultValue) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return defaultValue;
    }
}
