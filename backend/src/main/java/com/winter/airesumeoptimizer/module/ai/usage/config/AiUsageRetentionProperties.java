package com.winter.airesumeoptimizer.module.ai.usage.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.ai.usage.retention")
public class AiUsageRetentionProperties {

    /** Provider-attempt metadata is operationally useful but is not a permanent activity history. */
    @Min(1)
    private int days = 90;

    private boolean enabled = true;
}
