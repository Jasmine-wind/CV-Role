package com.winter.airesumeoptimizer.module.embedding.service;

import com.winter.airesumeoptimizer.module.embedding.vo.JobDescriptionEmbeddingSummaryVO;

public interface JobDescriptionEmbeddingService {

    JobDescriptionEmbeddingSummaryVO generate(Long userId, Long jobDescriptionId);

    JobDescriptionEmbeddingSummaryVO getSummary(Long userId, Long jobDescriptionId);
}
