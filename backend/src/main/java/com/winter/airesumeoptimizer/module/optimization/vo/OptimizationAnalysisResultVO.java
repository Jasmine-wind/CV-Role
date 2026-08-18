package com.winter.airesumeoptimizer.module.optimization.vo;

import com.winter.airesumeoptimizer.module.analysis.vo.AiJobMatchResultVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "正式优化任务的岗位分析结果")
public class OptimizationAnalysisResultVO {

    private Long optimizationTaskId;

    private Long sourceResumeVersionId;

    private Long targetResumeVersionId;

    private Long jobTargetId;

    private String status;

    private String jobTitle;

    private String resumeName;

    private AiJobMatchResultVO analysis;
}
