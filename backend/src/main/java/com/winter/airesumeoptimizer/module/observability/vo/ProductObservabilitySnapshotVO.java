package com.winter.airesumeoptimizer.module.observability.vo;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Aggregate counters only; no content, identifiers, URLs, or secrets are retained here. */
@Getter
@Setter
public class ProductObservabilitySnapshotVO {

    private LocalDateTime fromInclusive;
    private LocalDateTime toExclusive;
    private Long registrations;
    private Long uploadedResumes;
    private Long resumePreparationSuccesses;
    private Long resumePreparationFailures;
    private Long analysisSuccesses;
    private Long analysisFailures;
    private Long successfulExports;
    private Long analysesWithExport;
    private Long averageFirstSuccessfulAnalysisMs;
    private Long providerAttempts;
    private Long providerFailures;
    private Long reportedTotalTokens;
}
