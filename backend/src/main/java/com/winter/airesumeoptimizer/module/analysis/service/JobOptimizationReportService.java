package com.winter.airesumeoptimizer.module.analysis.service;

import com.winter.airesumeoptimizer.module.analysis.vo.JobOptimizationReportVO;

public interface JobOptimizationReportService {

    JobOptimizationReportVO getReport(Long userId, Long resumeId, Long jobDescriptionId);
}
