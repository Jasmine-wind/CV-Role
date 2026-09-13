package com.winter.airesumeoptimizer.module.resume.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeAiStructuredParseResultDTO {

    private Boolean aiEnabled;

    private Boolean applied;

    private Boolean aiInvoked;

    private String aiStatus;

    private String skippedReason;

    private Boolean fallbackOccurred;

    private String fallbackReason;

    private Long durationMs;

    private Boolean cacheHit;

    private String cacheKey;

    private ResumeStructuredContentDTO structuredContent;

    /** Candidate returned only as an unconfirmed reference; never a write/apply result. */
    private ResumeStructuredContentDTO referenceContent;

    private Boolean referenceOnly;

    private Double referenceConfidence;

    private List<String> qualityWarnings;

    /**
     * Automatic AI-to-canonical application is intentionally unavailable. AI output is either a
     * rule result or an explicitly requested, reference-only advisory candidate.
     */
    public boolean shouldApply() {
        return false;
    }
}
