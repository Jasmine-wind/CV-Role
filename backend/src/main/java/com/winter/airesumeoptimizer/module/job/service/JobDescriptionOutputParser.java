package com.winter.airesumeoptimizer.module.job.service;

import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionParseResultDTO;

public interface JobDescriptionOutputParser {

    JobDescriptionParseResultDTO parse(String aiOutput);
}
