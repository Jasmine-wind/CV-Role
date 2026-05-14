package com.winter.airesumeoptimizer.module.analysis.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiJobMatchPromptDTO {

    private String promptVersion;

    private String prompt;
}
