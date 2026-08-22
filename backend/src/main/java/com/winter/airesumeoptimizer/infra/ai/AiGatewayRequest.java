package com.winter.airesumeoptimizer.infra.ai;

import java.util.regex.Pattern;

/** Controlled business request. The gateway, not callers, assigns chat roles. */
public record AiGatewayRequest(
        String policyId,
        String trustedPolicy,
        String untrustedData) {

    private static final Pattern POLICY_ID = Pattern.compile("[A-Z0-9_]{1,100}");

    public AiGatewayRequest {
        policyId = policyId == null ? "" : policyId.strip();
        trustedPolicy = trustedPolicy == null ? "" : trustedPolicy.strip();
        untrustedData = untrustedData == null ? "" : untrustedData.strip();
        if (!POLICY_ID.matcher(policyId).matches()
                || trustedPolicy.isBlank()
                || untrustedData.isBlank()) {
            throw new AiGatewayException(AiFailureCode.SCHEMA_INVALID, "AI input is invalid");
        }
    }
}
