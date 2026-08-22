package com.winter.airesumeoptimizer.infra.ai;

import java.util.Objects;

/** Explicit identity and task selection for one AI operation. */
public record AiInvocationContext(
        Long userId,
        Long taskId,
        String operation,
        AiSelectionSnapshot selection) {

    public AiInvocationContext {
        operation = operation == null || operation.isBlank() ? "UNSPECIFIED" : operation.strip();
    }

    public static AiInvocationContext user(Long userId, String operation, AiSelectionSnapshot selection) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("AI 调用必须绑定有效用户");
        }
        return new AiInvocationContext(userId, null, operation, selection);
    }

    public static AiInvocationContext task(
            Long userId,
            Long taskId,
            String operation,
            AiSelectionSnapshot selection) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("任务级 AI 调用必须绑定有效用户");
        }
        if (taskId == null || taskId <= 0) {
            throw new IllegalArgumentException("任务级 AI 调用必须绑定有效任务");
        }
        return new AiInvocationContext(userId, taskId, operation, Objects.requireNonNull(selection, "selection"));
    }

    public AiInvocationContext withOperation(String nextOperation) {
        return new AiInvocationContext(userId, taskId, nextOperation, selection);
    }

    public boolean isTaskBound() {
        return taskId != null;
    }
}
