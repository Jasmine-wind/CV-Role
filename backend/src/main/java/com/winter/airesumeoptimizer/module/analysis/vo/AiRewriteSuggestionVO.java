package com.winter.airesumeoptimizer.module.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "AI 局部改写建议结果")
public class AiRewriteSuggestionVO {

    @Schema(description = "局部改写建议 ID", example = "1")
    private Long rewriteId;

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "目标岗位 ID", example = "1")
    private Long jobDescriptionId;

    @Schema(description = "AI 匹配结果 ID", example = "1")
    private Long aiJobMatchResultId;

    @Schema(description = "AI 优化建议 ID", example = "1")
    private Long aiResumeSuggestionId;

    @Schema(description = "改写对象类型", example = "PROJECT")
    private String rewriteType;

    @Schema(description = "目标简历部分", example = "项目经历")
    private String targetSection;

    @Schema(description = "原文片段")
    private String originalText;

    @Schema(description = "改写建议文本")
    private String rewrittenText;

    @Schema(description = "改写理由")
    private String rewriteReason;

    @Schema(description = "注意事项")
    private String caution;

    @Schema(description = "采纳状态", example = "PENDING")
    private String acceptStatus;

    @Schema(description = "生成状态", example = "SUCCESS")
    private String rewriteStatus;

    @Schema(description = "模型名称", example = "deepseek-v4-flash")
    private String modelName;

    @Schema(description = "Prompt 版本", example = "rewrite_suggestion_v1")
    private String promptVersion;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
