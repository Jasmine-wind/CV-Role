package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionPromptDTO;

public interface AiResumeSuggestionPromptService {

    String PROMPT_VERSION = "resume_suggestion_v1";

    default AiResumeSuggestionPromptDTO buildPrompt(
            String resumeStructuredContent,
            String jobStructuredContent,
            String aiMatchResult) {
        return buildPrompt(resumeStructuredContent, jobStructuredContent, aiMatchResult, null);
    }

    AiResumeSuggestionPromptDTO buildPrompt(
            String resumeStructuredContent,
            String jobStructuredContent,
            String aiMatchResult,
            String ragContext);
}
