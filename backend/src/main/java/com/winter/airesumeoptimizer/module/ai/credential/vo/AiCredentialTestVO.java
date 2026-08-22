package com.winter.airesumeoptimizer.module.ai.credential.vo;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiCredentialTestVO {

    private final boolean success;
    private final String failureCode;
    private final String message;
}
