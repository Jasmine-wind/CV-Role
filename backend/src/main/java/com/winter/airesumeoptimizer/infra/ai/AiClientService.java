package com.winter.airesumeoptimizer.infra.ai;

public interface AiClientService {

    String complete(String prompt);

    String modelName();
}
