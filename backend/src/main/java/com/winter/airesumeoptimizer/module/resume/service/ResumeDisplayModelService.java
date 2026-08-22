package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeDisplayModelDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;

public interface ResumeDisplayModelService {

    ResumeDisplayModelDTO buildRuleDisplayModel(Long resumeId, ResumeStructuredContentDTO structuredContent);

    ResumeDisplayModelDTO buildAiDisplayModel(Long resumeId, ResumeStructuredContentDTO structuredContent);

    ResumeDisplayModelDTO getCachedAiDisplayModel(Long resumeId, ResumeStructuredContentDTO structuredContent);

    default ResumeDisplayModelDTO getCachedAiDisplayModel(
            Long resumeId,
            ResumeStructuredContentDTO structuredContent,
            AiSelectionSnapshot selection) {
        return getCachedAiDisplayModel(resumeId, structuredContent);
    }

    default ResumeDisplayModelDTO getCachedAiDisplayModel(
            Long userId,
            Long resumeId,
            ResumeStructuredContentDTO structuredContent,
            AiSelectionSnapshot selection) {
        return getCachedAiDisplayModel(resumeId, structuredContent, selection);
    }

    default ResumeDisplayModelDTO buildAiDisplayModel(
            Long resumeId,
            ResumeStructuredContentDTO structuredContent,
            AiSelectionSnapshot selection) {
        return buildAiDisplayModel(resumeId, structuredContent);
    }
}
