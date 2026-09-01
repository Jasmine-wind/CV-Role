package com.winter.airesumeoptimizer.module.resume.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "简历解析结果")
public class ResumeParseResultVO {

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "解析状态", example = "SUCCESS")
    private String parseStatus;

    @Schema(description = "提取出的简历文本")
    private String extractedText;

    @Schema(description = "清洗后的简历文本")
    private String cleanedText;

    @Schema(description = "章节识别结果 JSON")
    private String sectionResult;

    @Schema(description = "结构化解析 JSON")
    private String structuredJson;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "文本提取质量状态", example = "GOOD")
    private String textQualityStatus;

    @Schema(description = "文本提取质量问题 JSON")
    private String textQualityIssues;

    @Schema(description = "文本提取质量提示")
    private String textQualityMessage;

    @Schema(description = "结构化解析质量状态", example = "GOOD")
    private String parseQualityStatus;

    @Schema(description = "结构化解析质量警告 JSON")
    private String parseQualityWarnings;

    @Schema(description = "结构化解析质量提示")
    private String parseQualityMessage;

    @Schema(description = "结构化解析质量分数", example = "85")
    private Integer parseQualityScore;

    @Schema(description = "交付质量状态", example = "READY")
    private String qualityStatus;

    @Schema(description = "确定性验证问题 JSON")
    private String qualityIssues;

    @Schema(description = "待用户确认的候选项 JSON")
    private String unresolvedItems;

    @Schema(description = "canonical 交付文档 JSON（RESUME_DOCUMENT_V1）")
    private String canonicalDocument;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
