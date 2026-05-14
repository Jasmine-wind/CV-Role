package com.winter.airesumeoptimizer.module.analysis.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiRewriteSuggestionResultDTO {

    private String rewrittenText;

    private String rewriteReason;

    private String caution;

    private Boolean needUserSupplement;

    private List<String> supplementQuestions;
}
