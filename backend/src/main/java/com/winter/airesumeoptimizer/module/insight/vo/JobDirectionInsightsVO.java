package com.winter.airesumeoptimizer.module.insight.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "基于冻结岗位分析的只读方向洞察")
public class JobDirectionInsightsVO {

    @Schema(description = "达到样本门槛的简历基线洞察")
    private List<JobDirectionCohortVO> cohorts;
}
