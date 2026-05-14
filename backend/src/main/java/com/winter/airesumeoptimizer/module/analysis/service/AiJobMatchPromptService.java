package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchPromptDTO;

public interface AiJobMatchPromptService {

    String PROMPT_VERSION = "ai_job_match_v1";

    AiJobMatchPromptDTO buildPrompt(String resumeStructuredContent, String jobStructuredContent, String resumeRawTextSummary);
}
