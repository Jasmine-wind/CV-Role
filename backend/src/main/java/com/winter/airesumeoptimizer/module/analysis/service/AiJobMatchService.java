package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import java.util.List;

public interface AiJobMatchService {

    AiJobMatchResult match(Long userId, Long resumeId, Long jobDescriptionId);

    List<AiJobMatchResult> listByResume(Long userId, Long resumeId);

    AiJobMatchResult getByResumeAndJobDescription(Long userId, Long resumeId, Long jobDescriptionId);
}
