package com.winter.airesumeoptimizer.module.job.service;

import com.winter.airesumeoptimizer.module.job.dto.JobMatchCalculationResultDTO;
import com.winter.airesumeoptimizer.module.job.entity.Job;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;

public interface JobMatchService {

    JobMatchCalculationResultDTO calculateMatch(ResumeStructuredContentDTO resumeContent, Job job);
}
