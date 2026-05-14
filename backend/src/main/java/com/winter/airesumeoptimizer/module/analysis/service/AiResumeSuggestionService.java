package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import java.util.List;

public interface AiResumeSuggestionService {

    AiResumeSuggestion generate(Long userId, Long resumeId, Long jobDescriptionId, Long aiJobMatchResultId);

    AiResumeSuggestion getByResumeAndJobDescription(Long userId, Long resumeId, Long jobDescriptionId);

    AiResumeSuggestion getByResumeAndMatchResult(Long userId, Long resumeId, Long aiJobMatchResultId);

    List<AiResumeSuggestion> listByResume(Long userId, Long resumeId);
}
