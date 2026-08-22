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
}
