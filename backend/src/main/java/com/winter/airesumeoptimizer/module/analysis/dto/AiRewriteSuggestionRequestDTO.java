package com.winter.airesumeoptimizer.module.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "AI 局部改写建议请求")
public class AiRewriteSuggestionRequestDTO {

    @Schema(description = "改写对象类型", example = "PROJECT")
    @NotBlank(message = "改写对象类型不能为空")
    @Size(max = 30, message = "改写对象类型长度不能超过 30")
    private String rewriteType;

    @Schema(description = "目标简历部分", example = "项目经历")
    @NotBlank(message = "目标简历部分不能为空")
    @Size(max = 100, message = "目标简历部分长度不能超过 100")
    private String targetSection;

    @Schema(description = "用户选择的原文片段", example = "做了一个 AI 简历优化系统，负责后端开发。")
    @Size(max = 3000, message = "原文片段长度不能超过 3000")
    private String originalText;

    @Schema(description = "用户确认的候选原文片段，兼容建议驱动改写入口")
    @Size(max = 3000, message = "原文片段长度不能超过 3000")
    private String sourceText;

    @Schema(description = "目标岗位 ID，可选", example = "1")
    @Positive(message = "目标岗位 ID 必须大于 0")
    private Long jobDescriptionId;

    @Schema(description = "AI 匹配结果 ID，可选", example = "1")
    @Positive(message = "AI 匹配结果 ID 必须大于 0")
    private Long aiJobMatchResultId;

    @Schema(description = "AI 匹配结果 ID，可选，兼容前端建议驱动入口", example = "1")
    @Positive(message = "AI 匹配结果 ID 必须大于 0")
    private Long matchId;

    @Schema(description = "AI 优化建议 ID，可选", example = "1")
    @Positive(message = "AI 优化建议 ID 必须大于 0")
    private Long aiResumeSuggestionId;

    @Schema(description = "AI 优化建议 ID，可选，兼容建议驱动入口", example = "1")
    @Positive(message = "AI 优化建议 ID 必须大于 0")
    private Long suggestionId;

    @Schema(description = "改写目标", example = "突出岗位关键词并补充技术细节")
    @Size(max = 200, message = "改写目标长度不能超过 200")
    private String rewriteGoal;

    @Schema(description = "岗位关键词")
    @Size(max = 20, message = "岗位关键词最多 20 个")
    private List<String> jobKeywords;

    @Schema(description = "表达风格", example = "简洁专业")
    @Size(max = 40, message = "表达风格长度不能超过 40")
    private String tone;

    @Schema(description = "期望改写长度上限", example = "180")
    @Positive(message = "期望改写长度上限必须大于 0")
    private Integer lengthLimit;

    public String resolvedOriginalText() {
        if (sourceText != null && !sourceText.isBlank()) {
            return sourceText;
        }
        return originalText;
    }

    public Long resolvedAiJobMatchResultId() {
        return matchId == null ? aiJobMatchResultId : matchId;
    }

    public Long resolvedAiResumeSuggestionId() {
        return suggestionId == null ? aiResumeSuggestionId : suggestionId;
    }
}
