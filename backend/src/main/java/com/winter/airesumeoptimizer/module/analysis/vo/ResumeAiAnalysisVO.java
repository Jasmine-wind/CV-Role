package com.winter.airesumeoptimizer.module.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "AI 分析详情")
public class ResumeAiAnalysisVO {

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "分析状态", example = "SUCCESS")
    private String analysisStatus;

    @Schema(description = "AI 评分", example = "85")
    private Integer score;

    @Schema(description = "简历优势列表")
    private List<String> strengths;

    @Schema(description = "问题列表")
    private List<String> problems;

    @Schema(description = "优化建议摘要")
    private List<String> suggestionsSummary;

    @Schema(description = "模型名称", example = "deepseek-v4-flash")
    private String modelName;

    @Schema(description = "Prompt 版本", example = "v1")
    private String promptVersion;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
