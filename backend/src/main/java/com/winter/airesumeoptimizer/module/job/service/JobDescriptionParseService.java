package com.winter.airesumeoptimizer.module.job.service;

import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;

public interface JobDescriptionParseService {

    JobDescriptionVO parse(Long userId, Long jobDescriptionId);

    default JobDescriptionVO parse(
            Long userId,
            Long jobDescriptionId,
            AiSelectionSnapshot selection) {
        return parse(userId, jobDescriptionId);
    }

    /** Binds a formal analysis Provider attempt to its OptimizationTask when available. */
    default JobDescriptionVO parse(
            Long userId,
            Long jobDescriptionId,
            AiSelectionSnapshot selection,
            Long optimizationTaskId) {
        return parse(userId, jobDescriptionId, selection);
    }
}
