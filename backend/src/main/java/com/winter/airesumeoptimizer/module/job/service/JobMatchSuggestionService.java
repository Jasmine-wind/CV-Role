package com.winter.airesumeoptimizer.module.job.service;

import com.winter.airesumeoptimizer.module.job.dto.JobMatchCalculationResultDTO;
import com.winter.airesumeoptimizer.module.job.dto.JobMatchSuggestionDTO;
import com.winter.airesumeoptimizer.module.job.entity.Job;
import java.util.List;

public interface JobMatchSuggestionService {

    List<JobMatchSuggestionDTO> generateSuggestions(JobMatchCalculationResultDTO matchResult, Job job);
}
