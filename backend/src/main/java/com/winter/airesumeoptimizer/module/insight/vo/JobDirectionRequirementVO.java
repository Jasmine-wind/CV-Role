package com.winter.airesumeoptimizer.module.insight.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "一个保守聚合的岗位要求分布")
public class JobDirectionRequirementVO {

    @Schema(description = "显示标签；技术锚点标签只表示岗位要求包含该词")
    private String label;

    @Schema(description = "出现该要求的不同岗位数", example = "6")
    private Integer occurrenceCount;

    @Schema(description = "当前 cohort 的不同岗位总数", example = "8")
    private Integer sampleSize;

    @Schema(description = "正式结果为 MATCHED 的岗位数", example = "3")
    private Integer matchedCount;

    @Schema(description = "正式结果为 PARTIAL_EVIDENCE 的岗位数", example = "2")
    private Integer partialEvidenceCount;

    @Schema(description = "正式结果为 NO_EVIDENCE 的岗位数", example = "1")
    private Integer noEvidenceCount;

    @Schema(description = "组成该聚合的原始 Requirement 与 Evidence 来源")
    private List<JobDirectionRequirementSourceVO> sources;
}
