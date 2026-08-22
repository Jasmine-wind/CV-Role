package com.winter.airesumeoptimizer.infra.ai.transport;

public class OutboundTransportException extends RuntimeException {

    public enum Kind {
        UNSAFE_URL,
        TIMEOUT,
        RESPONSE_TOO_LARGE,
        NETWORK,
        INTERRUPTED
    }

    private final Kind kind;

    public OutboundTransportException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
