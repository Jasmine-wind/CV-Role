package com.winter.airesumeoptimizer.infra.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.cache.redis")
public class RedisCacheProperties {

    private boolean enabled = true;

    private long defaultTtlSeconds = 86400;

    private long aiDisplayModelTtlSeconds = 86400;

    private String keyPrefix = "ai-resume-optimizer";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getDefaultTtlSeconds() {
        return defaultTtlSeconds;
    }

    public void setDefaultTtlSeconds(long defaultTtlSeconds) {
        this.defaultTtlSeconds = defaultTtlSeconds;
    }

    public long getAiDisplayModelTtlSeconds() {
        return aiDisplayModelTtlSeconds;
    }

    public void setAiDisplayModelTtlSeconds(long aiDisplayModelTtlSeconds) {
        this.aiDisplayModelTtlSeconds = aiDisplayModelTtlSeconds;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
