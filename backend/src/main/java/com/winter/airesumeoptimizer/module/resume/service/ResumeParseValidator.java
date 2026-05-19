package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import java.util.List;

public interface ResumeParseValidator {

    ResumeStructuredContentDTO validateAndMerge(
            ResumeStructuredContentDTO aiContent,
            ResumeStructuredContentDTO ruleContent,
            List<String> qualityWarnings);
}
