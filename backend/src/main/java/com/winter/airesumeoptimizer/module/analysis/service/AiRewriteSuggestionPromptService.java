package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionPromptDTO;
import java.util.List;

public interface AiRewriteSuggestionPromptService {

    String PROMPT_VERSION = "rewrite_suggestion_v1";

    AiRewriteSuggestionPromptDTO buildPrompt(
            String originalText,
            String rewriteType,
            String targetSection,
            String jobStructuredContent,
            String aiMatchResult,
            String aiSuggestion,
            String rewriteGoal,
            List<String> jobKeywords,
            String tone,
            Integer lengthLimit);
}
