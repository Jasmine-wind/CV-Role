package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredParsePromptDTO;
import java.util.List;

public interface ResumeStructuredParsePromptService {

    ResumeStructuredParsePromptDTO buildPrompt(
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings);
}
