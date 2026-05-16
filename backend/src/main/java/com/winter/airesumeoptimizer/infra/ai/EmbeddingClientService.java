package com.winter.airesumeoptimizer.infra.ai;

import java.util.List;

public interface EmbeddingClientService {

    List<Double> embed(String text);

    String modelName();

    Integer dimension();
}
