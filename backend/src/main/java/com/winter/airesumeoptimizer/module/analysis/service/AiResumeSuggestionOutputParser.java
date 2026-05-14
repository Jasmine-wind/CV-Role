package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionResultDTO;

public interface AiResumeSuggestionOutputParser {

    AiResumeSuggestionResultDTO parse(String aiOutput);
}
