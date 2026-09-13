package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextQualityResultDTO;

public interface ResumeTextQualityCheckService {

    ResumeTextQualityResultDTO check(String extractedText, String fileType);

    /**
     * Check text with optional PDF image evidence. Existing implementations remain source
     * compatible; callers that do not retain extraction metadata use the conservative legacy
     * two-argument result.
     */
    default ResumeTextQualityResultDTO check(
            String extractedText, String fileType, Boolean imageContentPresent) {
        return check(extractedText, fileType);
    }
}
