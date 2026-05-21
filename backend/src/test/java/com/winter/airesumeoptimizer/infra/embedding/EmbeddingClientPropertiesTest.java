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
                .withProperty("app.ai.embedding-compatible.base-url", "https://api.siliconflow.cn/v1")
                .withProperty("app.ai.embedding-compatible.model", "Qwen/Qwen3-Embedding-0.6B")
                .withProperty("app.ai.embedding-compatible.dimension", "1024")
                .withProperty("app.ai.embedding-compatible.timeout-seconds", "120")
                .withProperty("app.ai.embedding-compatible.max-input-length", "8000");

        EmbeddingClientProperties properties = Binder.get(environment)
                .bind("app.ai.embedding-compatible", EmbeddingClientProperties.class)
                .orElseThrow(() -> new IllegalStateException("Embedding 配置绑定失败"));

        assertThat(properties.getApiKey()).isEqualTo("test-key");
        assertThat(properties.getBaseUrl()).isEqualTo("https://api.siliconflow.cn/v1");
        assertThat(properties.getModel()).isEqualTo("Qwen/Qwen3-Embedding-0.6B");
        assertThat(properties.getDimension()).isEqualTo(1024);
        assertThat(properties.getTimeoutSeconds()).isEqualTo(120);
        assertThat(properties.getMaxInputLength()).isEqualTo(8000);
    }
}
