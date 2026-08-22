package com.winter.airesumeoptimizer.module.job.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobDescriptionPromptDTO {

    private String promptVersion;

    private String prompt;

    private String systemPrompt;

    private String userPrompt;
}
