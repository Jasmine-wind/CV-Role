package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import java.util.List;

/** Optional cleaning seam that keeps visual source blocks while applying normal text rules. */
public interface ResumeLayoutAwareTextCleanService {

    ResumeTextCleanResultDTO cleanAndSplitSections(String extractedText, List<ResumeBlockDTO> sourceBlocks);
}
