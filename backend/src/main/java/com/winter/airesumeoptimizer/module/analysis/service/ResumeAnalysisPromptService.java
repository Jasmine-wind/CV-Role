package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.dto.ResumeAnalysisPromptDTO;

public interface ResumeAnalysisPromptService {

    String PROMPT_VERSION = "resume_analysis_v1";

    ResumeAnalysisPromptDTO buildPrompt(String extractedText, String structuredJson);
}
