package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseQualityResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextQualityResultDTO;

public interface ResumeParseQualityCheckService {

    ResumeParseQualityResultDTO check(
            ResumeStructuredContentDTO structuredContent,
            ResumeTextCleanResultDTO cleanResult,
            ResumeTextQualityResultDTO textQualityResult);
}
