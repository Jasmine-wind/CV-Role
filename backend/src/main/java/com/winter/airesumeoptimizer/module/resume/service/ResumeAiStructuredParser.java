package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAiStructuredParseResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import java.util.List;

public interface ResumeAiStructuredParser {

    ResumeAiStructuredParseResultDTO parse(
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings);

    ResumeAiStructuredParseResultDTO parse(
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride);

    default ResumeAiStructuredParseResultDTO parse(
            Long userId,
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride,
            AiSelectionSnapshot selection) {
        return parse(blocks, ruleStructuredContent, qualityWarnings, enabledOverride);
    }
}
