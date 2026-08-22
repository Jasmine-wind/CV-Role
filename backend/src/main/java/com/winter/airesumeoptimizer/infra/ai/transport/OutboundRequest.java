package com.winter.airesumeoptimizer.infra.ai.transport;

import java.time.Duration;
import java.util.Map;

public record OutboundRequest(
        String method,
        String baseUrl,
        String endpointPath,
        Map<String, String> headers,
        String body,
        Duration timeout) {

    public OutboundRequest {
        method = method == null || method.isBlank() ? "POST" : method.strip().toUpperCase();
        baseUrl = baseUrl == null ? "" : baseUrl.strip();
        endpointPath = endpointPath == null || endpointPath.isBlank() ? "/" : endpointPath.strip();
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? "" : body;
        timeout = timeout == null ? Duration.ofSeconds(90) : timeout;
    }
}
