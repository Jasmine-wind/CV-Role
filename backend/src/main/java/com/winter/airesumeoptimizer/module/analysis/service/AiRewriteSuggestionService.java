package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import com.winter.airesumeoptimizer.module.analysis.vo.RewriteContextVO;
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
            Long aiResumeSuggestionId,
            String rewriteGoal,
            List<String> jobKeywords,
            String tone,
            Integer lengthLimit);

    RewriteContextVO getRewriteContext(Long userId, Long aiResumeSuggestionId, Integer suggestionIndex);

    List<AiRewriteSuggestion> listByResume(Long userId, Long resumeId, String rewriteType, String acceptStatus);

    AiRewriteSuggestion updateAcceptStatus(Long userId, Long rewriteId, String acceptStatus);
}
