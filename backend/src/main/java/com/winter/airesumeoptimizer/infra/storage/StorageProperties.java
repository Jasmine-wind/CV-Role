package com.winter.airesumeoptimizer.infra.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String type = "local";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
