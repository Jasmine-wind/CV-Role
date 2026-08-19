package com.winter.airesumeoptimizer.module.evidence.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "单条岗位要求的匹配结论")
public class EvidenceRequirementVO {

    @Schema(description = "岗位要求 ID", example = "1")
    private Long evidenceRequirementId;

    @Schema(description = "岗位要求原文表述")
    private String requirementText;

    @Schema(description = "要求重要程度", example = "REQUIRED")
    private String importance;

    @Schema(description = "当前材料支持情况", example = "PARTIAL_EVIDENCE")
    private String matchLevel;

    @Schema(description = "匹配结论")
    private String conclusion;

    @Schema(description = "面向用户的处理建议")
    private String suggestion;

    @Schema(description = "简历中的真实证据")
    private List<RequirementEvidenceVO> evidences;
}
