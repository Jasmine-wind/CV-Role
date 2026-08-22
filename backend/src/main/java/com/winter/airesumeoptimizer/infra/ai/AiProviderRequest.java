package com.winter.airesumeoptimizer.infra.ai;

import java.time.Duration;
import java.util.List;

/** Internal adapter request. Contains the decrypted key only inside infra/ai. */
public record AiProviderRequest(
        String apiKey,
        String baseUrl,
        String model,
        Double temperature,
        Integer maxTokens,
        Duration timeout,
        List<AiChatMessage> messages) {

    public AiProviderRequest {
        timeout = timeout == null ? Duration.ofSeconds(90) : timeout;
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
