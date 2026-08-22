package com.winter.airesumeoptimizer.infra.ai;

public record AiCredentialTestResult(
        boolean success,
        AiFailureCode failureCode,
        String message,
        String model) {

    public AiCredentialTestResult {
        failureCode = success ? null : (failureCode == null ? AiFailureCode.PROVIDER_UNAVAILABLE : failureCode);
        message = message == null || message.isBlank()
                ? (success ? "AI Provider 连接测试成功" : "AI Provider 连接测试失败")
                : message;
        model = model == null ? "" : model;
    }
}
