package com.winter.airesumeoptimizer.infra.ai;

import java.util.List;

/**
 * Compatibility bridge used while legacy unit doubles still implement AiClientService.
 * Real production gateways always take the context-aware branch.
 */
public final class AiGatewaySupport {

    private AiGatewaySupport() {
    }

    public static AiCompletionResult complete(
            AiGateway gateway,
            AiInvocationContext context,
            AiGatewayRequest request) {
        if (gateway == null) {
            throw new AiGatewayException(AiFailureCode.CONFIGURATION_INVALID, "AI Gateway 未配置");
        }
        if (request == null) {
            throw new AiGatewayException(AiFailureCode.SCHEMA_INVALID, "AI 输入不能为空");
        }
        if (gateway instanceof AiClientService legacy && !(gateway instanceof ContextAwareAiGateway)) {
            List<AiChatMessage> messages = List.of(
                    AiChatMessage.system(request.trustedPolicy()),
                    AiChatMessage.user(request.untrustedData()));
            // Preserve the old test/compatibility seam while production callers
            // always take the context-aware Gateway branch. Legacy single-prompt
            // doubles historically stubbed complete(String); the Phase 5 bullet
            // seam explicitly depends on role-separated List messages.
            if (context != null && "BULLET_REWRITE".equals(context.operation())) {
                return AiCompletionResult.legacy(legacy.complete(messages), legacy.modelName());
            }
            if (context != null && "EVIDENCE_MATCH".equals(context.operation())) {
                return AiCompletionResult.legacy(
                        legacy.complete(request.trustedPolicy() + "\n" + request.untrustedData()),
                        legacy.modelName());
            }
            return AiCompletionResult.legacy(legacy.complete(request.untrustedData()), legacy.modelName());
        }
        return gateway.complete(context, request);
    }

    public static String completeText(
            AiGateway gateway,
            AiInvocationContext context,
            AiGatewayRequest request) {
        return complete(gateway, context, request).text();
    }

    public static String modelName(AiGateway gateway, AiInvocationContext context) {
        if (gateway instanceof AiClientService legacy && !(gateway instanceof ContextAwareAiGateway)) {
            return legacy.modelName();
        }
        return gateway.modelName(context);
    }

    /** Compatibility-safe selection for old unit doubles; real gateways never return null. */
    public static AiSelectionSnapshot selectionForNewTask(
            AiGateway gateway,
            Long userId,
            String operation) {
        AiSelectionSnapshot selection = gateway.selectionForNewTask(userId);
        if (selection != null) {
            return selection;
        }
        String model;
        try {
            model = modelName(gateway, new AiInvocationContext(userId, null, operation, null));
        } catch (RuntimeException exception) {
            model = "unknown";
        }
        return new AiSelectionSnapshot(
                AiSource.SYSTEM_DEFAULT,
                AiSelectionSnapshot.OPENAI_COMPATIBLE,
                null,
                null,
                "",
                model == null || model.isBlank() ? "unknown" : model,
                "{}",
                null);
    }

    /** Marker implemented only by the real context-aware Gateway. */
    public interface ContextAwareAiGateway extends AiGateway {
    }
}
