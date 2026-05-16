package com.winter.airesumeoptimizer.module.embedding.service;

import com.winter.airesumeoptimizer.module.embedding.vo.ResumeEmbeddingSummaryVO;

public interface ResumeEmbeddingService {

    ResumeEmbeddingSummaryVO generate(Long userId, Long resumeId);

    ResumeEmbeddingSummaryVO getSummary(Long userId, Long resumeId);
}
