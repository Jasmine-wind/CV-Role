package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionResultDTO;

public interface AiRewriteSuggestionOutputParser {

    AiRewriteSuggestionResultDTO parse(String aiOutput);
}
