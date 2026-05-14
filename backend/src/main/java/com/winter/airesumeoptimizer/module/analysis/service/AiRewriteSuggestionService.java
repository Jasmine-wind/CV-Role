package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import java.util.List;

public interface AiRewriteSuggestionService {

    AiRewriteSuggestion generate(
            Long userId,
            Long resumeId,
            String rewriteType,
            String targetSection,
            String originalText,
            Long jobDescriptionId,
            Long aiJobMatchResultId,
            Long aiResumeSuggestionId);

    List<AiRewriteSuggestion> listByResume(Long userId, Long resumeId, String rewriteType, String acceptStatus);

    AiRewriteSuggestion updateAcceptStatus(Long userId, Long rewriteId, String acceptStatus);
}
