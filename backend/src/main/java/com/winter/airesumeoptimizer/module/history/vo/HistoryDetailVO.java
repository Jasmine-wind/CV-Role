package com.winter.airesumeoptimizer.module.history.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "历史记录详情")
public class HistoryDetailVO {

    @Schema(description = "记录 ID", example = "1")
    private Long recordId;

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "简历信息")
    private HistoryResumeVO resume;

    @Schema(description = "解析结果")
    private HistoryParseResultVO parseResult;

    @Schema(description = "AI 分析结果")
    private HistoryAiAnalysisVO aiAnalysis;

    @Schema(description = "最近一次岗位匹配")
    private HistoryMatchResultVO latestMatch;

    @Schema(description = "岗位匹配结果列表")
    private List<HistoryMatchResultVO> matchResults;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
