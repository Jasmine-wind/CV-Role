package com.winter.airesumeoptimizer.module.resume.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "简历解析确认视图")
public class ResumeReviewVO {

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "交付质量状态", example = "NEEDS_REVIEW")
    private String qualityStatus;

    @Schema(description = "确定性验证问题 JSON")
    private String qualityIssues;

    @Schema(description = "待确认候选项 JSON")
    private String unresolvedItems;

    @Schema(description = "canonical 交付文档 JSON（RESUME_DOCUMENT_V1）")
    private String canonicalDocument;
}
