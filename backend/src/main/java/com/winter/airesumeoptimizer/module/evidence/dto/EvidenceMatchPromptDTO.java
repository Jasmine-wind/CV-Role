package com.winter.airesumeoptimizer.module.evidence.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EvidenceMatchPromptDTO {

    private String promptVersion;

    private String prompt;

    private String systemPrompt;

    private String userPrompt;
}
