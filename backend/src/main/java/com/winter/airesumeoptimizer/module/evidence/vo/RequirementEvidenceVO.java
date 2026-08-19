package com.winter.airesumeoptimizer.module.evidence.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "岗位要求对应的简历证据")
public class RequirementEvidenceVO {

    @Schema(description = "证据 ID", example = "1")
    private Long requirementEvidenceId;

    @Schema(description = "证据所在简历章节", example = "项目经历")
    private String sectionLabel;

    @Schema(description = "简历原文引用")
    private String evidenceText;

    @Schema(description = "当前材料对岗位要求的支持程度", example = "SUFFICIENT")
    private String supportLevel;
}
