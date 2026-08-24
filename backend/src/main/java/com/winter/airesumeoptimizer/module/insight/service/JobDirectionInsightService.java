package com.winter.airesumeoptimizer.module.insight.service;

import com.winter.airesumeoptimizer.module.insight.vo.JobDirectionInsightsVO;

/**
 * Read-only aggregation of retained formal Evidence analyses. It never creates
 * capability facts or changes any task, resume version, or evidence result.
 */
public interface JobDirectionInsightService {

    JobDirectionInsightsVO getInsights(Long userId);
}
