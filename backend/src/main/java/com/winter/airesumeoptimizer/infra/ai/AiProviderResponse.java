package com.winter.airesumeoptimizer.infra.ai;

public record AiProviderResponse(
        String text,
        Long inputTokens,
        Long outputTokens) {

    public AiProviderResponse {
        text = text == null ? "" : text;
    }
}
