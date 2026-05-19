package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSectionClassifyResultDTO;
import java.util.List;

public interface ResumeAiSectionClassifier {

    ResumeSectionClassifyResultDTO classify(List<ResumeBlockDTO> blocks);

    ResumeSectionClassifyResultDTO classify(List<ResumeBlockDTO> blocks, Boolean enabledOverride);

    ResumeSectionClassifyResultDTO classify(Long resumeId, List<ResumeBlockDTO> blocks, Boolean enabledOverride);
}
