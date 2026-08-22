package com.winter.airesumeoptimizer.infra.ai;

public record AiUsageMetrics(
        Long inputTokens,
        Long outputTokens,
        long latencyMs,
        int attempts) {

    public static AiUsageMetrics empty(long latencyMs, int attempts) {
        return new AiUsageMetrics(null, null, Math.max(0, latencyMs), Math.max(1, attempts));
    }
}
