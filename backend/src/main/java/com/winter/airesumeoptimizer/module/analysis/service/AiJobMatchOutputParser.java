package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchResultDTO;

public interface AiJobMatchOutputParser {

    AiJobMatchResultDTO parse(String aiOutput);
}
