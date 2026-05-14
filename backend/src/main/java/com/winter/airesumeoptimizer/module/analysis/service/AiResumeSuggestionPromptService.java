package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionPromptDTO;

public interface AiResumeSuggestionPromptService {

    String PROMPT_VERSION = "resume_suggestion_v1";

    AiResumeSuggestionPromptDTO buildPrompt(
            String resumeStructuredContent,
            String jobStructuredContent,
            String aiMatchResult);
}
