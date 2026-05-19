package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextQualityResultDTO;

public interface ResumeTextQualityCheckService {

    ResumeTextQualityResultDTO check(String extractedText, String fileType);
}
