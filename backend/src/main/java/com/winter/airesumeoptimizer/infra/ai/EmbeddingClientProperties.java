package com.winter.airesumeoptimizer.infra.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.ai.embedding-compatible")
public class EmbeddingClientProperties {

    private String apiKey;

    private String baseUrl;

    private String model;

    private Integer dimension;

    private Integer timeout = 30;

    private Integer maxInputLength = 8192;

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

    public Integer getTimeout() {
        return timeout;
    }

    public void setTimeout(Integer timeout) {
        this.timeout = timeout;
    }

    public Integer getMaxInputLength() {
        return maxInputLength;
    }

    public void setMaxInputLength(Integer maxInputLength) {
        this.maxInputLength = maxInputLength;
    }
}
