package com.winter.airesumeoptimizer.module.insight.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "同一冻结简历基线上的岗位样本")
public class JobDirectionCohortVO {

    @Schema(description = "所属简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "简历显示名称", example = "我的简历.pdf")
    private String resumeName;

    @Schema(description = "纳入的不同岗位数量", example = "8")
    private Integer sampleSize;

    @Schema(description = "固定的最小样本数", example = "8")
    private Integer minimumSampleSize;

    @Schema(description = "滚动样本窗口起点")
    private LocalDateTime windowStart;

    @Schema(description = "样本中最近一次完成分析时间")
    private LocalDateTime newestAnalysisAt;

    @Schema(description = "常见岗位要求及其正式 Evidence 分布")
    private List<JobDirectionRequirementVO> commonRequirements;
}
