package com.winter.airesumeoptimizer.module.job.service;

import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionPromptDTO;

public interface JobDescriptionPromptService {

    String PROMPT_VERSION = "job_description_parse_v1";

    JobDescriptionPromptDTO buildPrompt(String rawText);
}
