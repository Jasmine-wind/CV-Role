package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAiStructuredParseResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import java.util.List;

public interface ResumeAiStructuredParser {

    ResumeAiStructuredParseResultDTO parse(
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings);

    ResumeAiStructuredParseResultDTO parse(
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride);

    default ResumeAiStructuredParseResultDTO parse(
            Long userId,
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride,
            AiSelectionSnapshot selection) {
        return parse(blocks, ruleStructuredContent, qualityWarnings, enabledOverride);
    }

    /** Selection-aware overload carrying the owning resume identity for cache isolation. */
    default ResumeAiStructuredParseResultDTO parse(
            Long userId,
            Long resumeId,
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride,
            AiSelectionSnapshot selection) {
        return parse(userId, blocks, ruleStructuredContent, qualityWarnings, enabledOverride, selection);
    }

    /**
     * Legacy reference-only overload. It has no owning resume identity, so it is deliberately
     * fail-closed instead of giving an implementation a cache scope that can span requests.
     * Callers that want reference repair must use the overload carrying {@code resumeId}.
     */
    default ResumeAiStructuredParseResultDTO parseReferenceOnly(
            Long userId,
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride,
            AiSelectionSnapshot selection) {
        return referenceOnlySkipped(
                ruleStructuredContent,
                Boolean.TRUE.equals(enabledOverride),
                "AI_REFERENCE_RESUME_ID_REQUIRED");
    }

    /**
     * Reference-only overload carrying the owning resume identity for cache isolation. Concrete
     * implementations must opt in to this identity-aware seam; the default remains fail-closed.
     */
    default ResumeAiStructuredParseResultDTO parseReferenceOnly(
            Long userId,
            Long resumeId,
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride,
            AiSelectionSnapshot selection) {
        if (resumeId == null || resumeId <= 0) {
            return referenceOnlySkipped(
                    ruleStructuredContent,
                    Boolean.TRUE.equals(enabledOverride),
                    "AI_REFERENCE_RESUME_ID_REQUIRED");
        }
        return referenceOnlySkipped(
                ruleStructuredContent,
                Boolean.TRUE.equals(enabledOverride),
                "REFERENCE_ONLY_UNSUPPORTED");
    }

    private static ResumeAiStructuredParseResultDTO referenceOnlySkipped(
            ResumeStructuredContentDTO ruleStructuredContent,
            boolean enabled,
            String reason) {
        return ResumeAiStructuredParseResultDTO.builder()
                .aiEnabled(enabled)
                .applied(false)
                .aiInvoked(false)
                .aiStatus("SKIPPED")
                .skippedReason(reason)
                .fallbackOccurred(false)
                .structuredContent(ruleStructuredContent)
                .referenceOnly(true)
                .referenceConfidence(0.35d)
                .qualityWarnings(List.of(reason))
                .build();
    }
}
