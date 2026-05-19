package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;

public interface ResumeTextCleanService {

    ResumeTextCleanResultDTO cleanAndSplitSections(String extractedText);
}
