package com.winter.airesumeoptimizer.infra.ai;

public record AiCompletionResult(
        String text,
        AiSource source,
        String providerType,
        String model,
        Long credentialId,
        Long credentialRevision,
        AiUsageMetrics usage) {

    public AiCompletionResult {
        if (text == null) {
            text = "";
        }
        source = source == null ? AiSource.SYSTEM_DEFAULT : source;
        providerType = providerType == null || providerType.isBlank()
                ? AiSelectionSnapshot.OPENAI_COMPATIBLE
                : providerType;
        model = model == null ? "" : model;
        usage = usage == null ? AiUsageMetrics.empty(0, 1) : usage;
    }

    public static AiCompletionResult legacy(String text, String model) {
        return new AiCompletionResult(
                text,
                AiSource.SYSTEM_DEFAULT,
                AiSelectionSnapshot.OPENAI_COMPATIBLE,
                model,
                null,
                null,
                AiUsageMetrics.empty(0, 1));
    }
}
