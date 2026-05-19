package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeDisplayModelDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;

public interface ResumeDisplayModelService {

    ResumeDisplayModelDTO buildRuleDisplayModel(Long resumeId, ResumeStructuredContentDTO structuredContent);

    ResumeDisplayModelDTO buildAiDisplayModel(Long resumeId, ResumeStructuredContentDTO structuredContent);

    ResumeDisplayModelDTO getCachedAiDisplayModel(Long resumeId, ResumeStructuredContentDTO structuredContent);
}
