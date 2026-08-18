package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.dto.JobAnalysisStartRequestDTO;
import com.winter.airesumeoptimizer.module.analysis.vo.JobAnalysisStartVO;

/**
 * Owns the complete default flow from a pasted JD to a persisted job analysis result.
 */
public interface JobAnalysisService {

    JobAnalysisStartVO start(Long userId, JobAnalysisStartRequestDTO request);

    JobAnalysisStartVO retry(Long userId, Long resumeId, Long jobDescriptionId);
}
