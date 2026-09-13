package com.winter.airesumeoptimizer.module.resume.dto;

import java.util.List;

/** Deterministic, order-neutral health metrics for a structured resume candidate. */
public record ResumeStructureHealthEvaluation(
        int healthScore,
        int sourceCoverage,
        int meaningfulSourceCount,
        int representedSourceCount,
        int orphanContentCount,
        int duplicateSourceCount,
        int entryBoundaryViolations,
        int unresolvedGeneralCount,
        int unresolvedGeneralRatio,
        int fragmentedLineCount,
        int suspiciousEntryCollapse,
        int sectionConsistency,
        int entryCount,
        boolean hardInvariantPass,
        List<String> hardInvariantViolations,
        String candidateType) {

    public ResumeStructureHealthEvaluation {
        hardInvariantViolations = hardInvariantViolations == null ? List.of() : List.copyOf(hardInvariantViolations);
        candidateType = candidateType == null ? "UNKNOWN" : candidateType;
    }

    public boolean improvedOver(ResumeStructureHealthEvaluation baseline, int threshold) {
        if (baseline == null || !hardInvariantPass) {
            return false;
        }
        // Zero is not a meaningful improvement margin: equal-scoring candidates must never
        // replace the stable extraction. Positive thresholds retain the inclusive frozen margin.
        return threshold <= 0
                ? healthScore > baseline.healthScore()
                : healthScore >= baseline.healthScore() + threshold;
    }

    /** A conservative trigger for an optional, reference-only repair attempt. */
    public boolean requiresReferenceRepair() {
        return !hardInvariantPass
                || sourceCoverage < 90
                || orphanContentCount > 0
                || duplicateSourceCount > 0
                || entryBoundaryViolations > 0
                || fragmentedLineCount > 0
                || suspiciousEntryCollapse > 0;
    }
}
