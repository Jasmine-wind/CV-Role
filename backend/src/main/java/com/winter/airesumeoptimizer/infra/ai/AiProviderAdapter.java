package com.winter.airesumeoptimizer.infra.ai;

public interface AiProviderAdapter {

    AiProviderResponse complete(AiProviderRequest request);
}
