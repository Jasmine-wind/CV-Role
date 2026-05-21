package com.winter.airesumeoptimizer.module.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "岗位优化建议驱动的局部改写上下文")
public class RewriteContextVO {

    @Schema(description = "AI 优化建议结果 ID", example = "12")
    private Long suggestionId;

    @Schema(description = "建议条目索引", example = "0")
    private Integer suggestionIndex;

    @Schema(description = "简历 ID", example = "3")
    private Long resumeId;

    @Schema(description = "目标岗位 ID", example = "8")
    private Long jobDescriptionId;

    @Schema(description = "AI 匹配结果 ID", example = "20")
    private Long matchId;

    @Schema(description = "建议标题")
    private String suggestionTitle;

    @Schema(description = "建议正文")
    private String suggestionText;

    @Schema(description = "建议原因")
    private String suggestionReason;

    @Schema(description = "推荐候选简历片段")
    private List<RecommendedRewriteSectionVO> recommendedSections;

    @Schema(description = "岗位关键词")
    private List<String> jobKeywords;

    @Schema(description = "可选改写目标")
    private List<String> rewriteGoals;

    @Schema(description = "默认改写目标")
    private String defaultRewriteGoal;

    @Schema(description = "可选表达风格")
    private List<String> tones;
}
