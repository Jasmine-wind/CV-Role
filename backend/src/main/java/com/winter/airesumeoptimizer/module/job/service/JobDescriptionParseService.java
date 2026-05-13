package com.winter.airesumeoptimizer.module.job.service;

import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;

public interface JobDescriptionParseService {

    JobDescriptionVO parse(Long userId, Long jobDescriptionId);
}
