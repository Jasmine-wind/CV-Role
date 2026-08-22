package com.winter.airesumeoptimizer.infra.ai;

/** Safe, provider-independent failure. Never stores a raw provider body or cause. */
public class AiGatewayException extends RuntimeException {

    private final AiFailureCode failureCode;
    private final boolean retryable;
    private final long retryAfterMillis;

    public AiGatewayException(AiFailureCode failureCode, String safeMessage) {
        this(failureCode, safeMessage, false, 0L);
    }

    public AiGatewayException(AiFailureCode failureCode, String safeMessage, boolean retryable) {
        this(failureCode, safeMessage, retryable, 0L);
    }

    public AiGatewayException(
            AiFailureCode failureCode,
            String safeMessage,
            boolean retryable,
            long retryAfterMillis) {
        super(safeMessage == null || safeMessage.isBlank() ? "AI 服务调用失败" : safeMessage);
        this.failureCode = failureCode == null ? AiFailureCode.PROVIDER_UNAVAILABLE : failureCode;
        this.retryable = retryable;
        this.retryAfterMillis = Math.max(0L, Math.min(2000L, retryAfterMillis));
    }

    public AiFailureCode getFailureCode() {
        return failureCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }
}
