package com.winter.airesumeoptimizer.module.insight.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "方向洞察中可回溯的一条正式岗位要求")
public class JobDirectionRequirementSourceVO {

    private Long optimizationTaskId;
    private Long evidenceRequirementId;
    private String requirementText;
    private String matchLevel;
    private List<JobDirectionEvidenceVO> evidences;
}
