package com.winter.airesumeoptimizer.module.evidence.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "正式岗位证据分析结果")
public class EvidenceAnalysisResultVO {

    @Schema(description = "正式分析 ID", example = "1")
    private Long evidenceAnalysisId;

    @Schema(description = "已有证据的要求数量", example = "3")
    private Integer matchedCount;

    @Schema(description = "有经历但表达不足的要求数量", example = "2")
    private Integer expressionGapCount;

    @Schema(description = "当前材料未提供证据的要求数量", example = "1")
    private Integer noEvidenceCount;

    @Schema(description = "逐条岗位要求的匹配结论")
    private List<EvidenceRequirementVO> requirements;
}
