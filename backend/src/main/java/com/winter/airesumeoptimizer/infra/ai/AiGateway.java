package com.winter.airesumeoptimizer.infra.ai;

/**
 * The sole business-facing Chat AI seam.
 * Provider credentials and HTTP details never appear in this interface.
 */
public interface AiGateway {

    AiCompletionResult complete(AiInvocationContext context, AiGatewayRequest request);

    String modelName(AiInvocationContext context);

    /** Captures the selection used by a newly created asynchronous task. */
    default AiSelectionSnapshot selectionForNewTask(Long userId) {
        throw new AiGatewayException(AiFailureCode.CONFIGURATION_INVALID, "AI Gateway 不支持任务选择快照");
    }
}
