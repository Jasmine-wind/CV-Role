package com.winter.airesumeoptimizer.config.async;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.async")
public class AsyncTaskProperties {

    @Min(1)
    private int corePoolSize = 4;

    @Min(1)
    private int maxPoolSize = 8;

    @Min(1)
    private int queueCapacity = 100;

    @NotBlank
    private String threadNamePrefix = "ai-resume-task-";

    @Min(1)
    private int awaitTerminationSeconds = 30;
}
