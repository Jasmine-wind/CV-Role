package com.winter.airesumeoptimizer.module.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiRewriteSuggestionPromptDTO {

    private String promptVersion;

    private String prompt;

    private String systemPrompt;

    private String userPrompt;
}
