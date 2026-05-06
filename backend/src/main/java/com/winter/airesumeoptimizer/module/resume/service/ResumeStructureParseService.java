package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;

public interface ResumeStructureParseService {

    ResumeStructuredContentDTO parse(String rawText);
}
