package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.infra.ai.AiGateway;
import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.resume.config.ResumeParseProperties;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSectionClassifyResultDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAiSectionClassifier;
import com.winter.airesumeoptimizer.module.resume.service.ResumeSectionClassifyPromptService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Compatibility implementation for the retired AI section-classification seam.
 *
 * <p>Section ownership is a deterministic parser concern. Keeping an AI classifier in the normal
 * parse path makes a provider response capable of changing source ownership (and used to create
 * one call per batch), so this seam is intentionally rules-only. The constructor keeps the old
 * dependency shape for custom/test wiring; none of those dependencies are used to dispatch AI.</p>
 */
@Service
public class ResumeAiSectionClassifierImpl implements ResumeAiSectionClassifier {

    private static final String AI_STATUS_SKIPPED = "SKIPPED";
    private static final String AI_STATUS_DISABLED = "DISABLED";
    private static final String RULES_CANONICAL_REASON = "AI_SECTION_CLASSIFY_RULES_CANONICAL";

    private final ResumeParseProperties properties;

    public ResumeAiSectionClassifierImpl(
            ResumeParseProperties properties,
            ResumeSectionClassifyPromptService promptService,
            AiGateway aiGateway,
            ObjectMapper objectMapper) {
        this.properties = properties;
    }

    @Override
    public ResumeSectionClassifyResultDTO classify(List<ResumeBlockDTO> blocks) {
        return classify(null, blocks, null);
    }

    @Override
    public ResumeSectionClassifyResultDTO classify(List<ResumeBlockDTO> blocks, Boolean enabledOverride) {
        return classify(null, blocks, enabledOverride);
    }

    @Override
    public ResumeSectionClassifyResultDTO classify(
            Long resumeId,
            List<ResumeBlockDTO> blocks,
            Boolean enabledOverride) {
        return classify(null, resumeId, blocks, enabledOverride, null);
    }

    @Override
    public ResumeSectionClassifyResultDTO classify(
            Long userId,
            Long resumeId,
            List<ResumeBlockDTO> blocks,
            Boolean enabledOverride,
            AiSelectionSnapshot selection) {
        long startedAt = System.nanoTime();
        boolean configured = enabledOverride == null
                ? properties == null || properties.aiSectionClassifyEnabled()
                : Boolean.TRUE.equals(enabledOverride);
        if (!configured) {
            return ResumeSectionClassifyResultDTO.builder()
                    .aiEnabled(false)
                    .applied(false)
                    .aiInvoked(false)
                    .aiStatus(AI_STATUS_DISABLED)
                    .skippedReason("AI_SECTION_CLASSIFY_DISABLED")
                    .fallbackOccurred(false)
                    .durationMs(elapsedMs(startedAt))
                    .cacheHit(false)
                    .classifications(List.of())
                    .build();
        }
        return ResumeSectionClassifyResultDTO.builder()
                .aiEnabled(false)
                .applied(false)
                .aiInvoked(false)
                .aiStatus(AI_STATUS_SKIPPED)
                .skippedReason(RULES_CANONICAL_REASON)
                .fallbackOccurred(false)
                .durationMs(elapsedMs(startedAt))
                .cacheHit(false)
                .classifications(List.of())
                .build();
    }

    private long elapsedMs(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
