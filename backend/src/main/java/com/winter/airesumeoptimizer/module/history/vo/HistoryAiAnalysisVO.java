package com.winter.airesumeoptimizer.module.history.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "历史详情中的 AI 分析结果")
public class HistoryAiAnalysisVO {

    @Schema(description = "分析状态", example = "SUCCESS")
    private String analysisStatus;

    @Schema(description = "分析分数", example = "85")
    private Integer analysisScore;

    @Schema(description = "优势预览")
    private String strengthsPreview;

    @Schema(description = "问题预览")
    private String problemsPreview;

    @Schema(description = "建议摘要预览")
    private String suggestionsSummary;

    @Schema(description = "分析错误信息")
    private String analysisErrorMessage;

    @Schema(description = "分析更新时间")
    private LocalDateTime analysisUpdatedAt;
}
