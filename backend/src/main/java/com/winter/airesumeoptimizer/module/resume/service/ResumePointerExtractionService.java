package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseMode;
import com.winter.airesumeoptimizer.module.resume.dto.ResumePointerExtractionResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumePointerExtractorType;
import java.util.List;

public interface ResumePointerExtractionService {

    ResumePointerExtractionResultDTO extract(
            Long resumeId,
            List<ResumeIndexedLineDTO> indexedLines,
            ResumeParseMode parseMode,
            ResumePointerExtractorType extractorType);

    default ResumePointerExtractionResultDTO extract(
            Long userId,
            Long resumeId,
            List<ResumeIndexedLineDTO> indexedLines,
            ResumeParseMode parseMode,
            ResumePointerExtractorType extractorType,
            AiSelectionSnapshot selection) {
        return extract(resumeId, indexedLines, parseMode, extractorType);
    }
}
