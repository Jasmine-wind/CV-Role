package com.winter.airesumeoptimizer.module.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "推荐局部改写候选片段")
public class RecommendedRewriteSectionVO {

    @Schema(description = "片段类型", example = "PROJECTS")
    private String sectionType;

    @Schema(description = "片段标题", example = "AI 简历优化系统")
    private String sectionTitle;

    @Schema(description = "候选原文片段")
    private String sourceText;

    @Schema(description = "推荐原因")
    private String reason;

    @Schema(description = "推荐置信度", example = "0.82")
    private Double confidence;

    @Schema(description = "命中的岗位关键词")
    private List<String> matchedKeywords;

    @Schema(description = "原文定位")
    private RewriteSourceRefVO sourceRef;
}
