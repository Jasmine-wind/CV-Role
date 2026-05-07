package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.dto.ResumeAnalysisResultDTO;

public interface ResumeAnalysisOutputParser {

    ResumeAnalysisResultDTO parse(String aiOutput);
}
