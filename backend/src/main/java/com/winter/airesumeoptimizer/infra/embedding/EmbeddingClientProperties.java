package com.winter.airesumeoptimizer.infra.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.embedding-compatible")
public class EmbeddingClientProperties {

    private String apiKey;

    private String baseUrl;

    private String model;

    private Integer dimension;

    private Integer timeoutSeconds = 120;

    private Integer maxInputLength = 8000;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Integer getTimeout() {
        return timeoutSeconds;
    }

    public void setTimeout(Integer timeout) {
        this.timeoutSeconds = timeout;
    }

    public Integer getMaxInputLength() {
        return maxInputLength;
    }

    public void setMaxInputLength(Integer maxInputLength) {
        this.maxInputLength = maxInputLength;
    }
}
