package com.winter.airesumeoptimizer.module.evidence.service;

import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchPromptDTO;

public interface EvidenceMatchPromptService {

    String PROMPT_VERSION = "evidence_match_v3";

    EvidenceMatchPromptDTO buildPrompt(String jobStructuredContent, String resumeStructuredContent);
}
