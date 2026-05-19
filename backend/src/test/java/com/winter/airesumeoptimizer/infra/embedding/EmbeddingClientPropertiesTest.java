package com.winter.airesumeoptimizer.infra.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

class EmbeddingClientPropertiesTest {

    @Test
    void shouldBindEmbeddingProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.ai.embedding-compatible.api-key", "test-key")
                .withProperty("app.ai.embedding-compatible.base-url", "http://localhost:8000/v1")
                .withProperty("app.ai.embedding-compatible.model", "Qwen3-Embedding-0.6B")
                .withProperty("app.ai.embedding-compatible.dimension", "1024")
                .withProperty("app.ai.embedding-compatible.timeout", "30")
                .withProperty("app.ai.embedding-compatible.max-input-length", "8192");

        EmbeddingClientProperties properties = Binder.get(environment)
                .bind("app.ai.embedding-compatible", EmbeddingClientProperties.class)
                .orElseThrow(() -> new IllegalStateException("Embedding 配置绑定失败"));

        assertThat(properties.getApiKey()).isEqualTo("test-key");
        assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:8000/v1");
        assertThat(properties.getModel()).isEqualTo("Qwen3-Embedding-0.6B");
        assertThat(properties.getDimension()).isEqualTo(1024);
        assertThat(properties.getTimeout()).isEqualTo(30);
        assertThat(properties.getMaxInputLength()).isEqualTo(8192);
    }
}
