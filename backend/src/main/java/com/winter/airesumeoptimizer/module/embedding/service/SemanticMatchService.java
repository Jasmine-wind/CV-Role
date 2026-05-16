package com.winter.airesumeoptimizer.module.embedding.service;

import com.winter.airesumeoptimizer.module.embedding.vo.SemanticMatchResultVO;

public interface SemanticMatchService {

    SemanticMatchResultVO match(Long userId, Long resumeId, Long jobDescriptionId, Integer topK);
}
