package com.winter.airesumeoptimizer.module.embedding.service;

import com.winter.airesumeoptimizer.module.embedding.dto.RagContextDTO;

public interface ResumeRagService {

    RagContextDTO buildContext(Long userId, Long resumeId, Long jobDescriptionId, Integer topK);
}
