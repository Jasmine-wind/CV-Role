package com.winter.airesumeoptimizer.infra.ai.transport;

import java.util.Map;

public record OutboundResponse(
        int statusCode,
        Map<String, String> headers,
        String body) {

    public OutboundResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? "" : body;
    }
}
