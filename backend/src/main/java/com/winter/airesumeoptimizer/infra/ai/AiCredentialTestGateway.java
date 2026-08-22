package com.winter.airesumeoptimizer.infra.ai;

import java.util.Map;

/** Infrastructure-only transient Credential test; the key is never persisted or returned. */
public interface AiCredentialTestGateway {

    AiCredentialTestResult test(
            Long userId,
            String apiKey,
            String baseUrl,
            String model,
            Map<String, Object> config);
}
