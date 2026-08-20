package com.winter.airesumeoptimizer.module.workspace.dto;

import com.winter.airesumeoptimizer.module.workspace.enums.RewriteFactViolationCode;

/**
 * Rewrite 事实校验结果。passed 为 false 时候选不得展示为可采纳建议。
 */
public record RewriteFactValidationResult(
        boolean passed,
        RewriteFactViolationCode code,
        String message) {

    public static RewriteFactValidationResult pass() {
        return new RewriteFactValidationResult(true, RewriteFactViolationCode.OK, null);
    }

    public static RewriteFactValidationResult fail(RewriteFactViolationCode code, String message) {
        return new RewriteFactValidationResult(false, code, message);
    }
}
