package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;

public interface ResumeAnalysisService {

    ResumeAiAnalysis analyze(Long userId, Long resumeId);

    ResumeAiAnalysis getAnalysis(Long userId, Long resumeId);
}
