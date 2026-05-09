package com.winter.airesumeoptimizer.module.history.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HistoryDetailVO {

    private Long recordId;

    private Long resumeId;

    private HistoryResumeVO resume;

    private HistoryParseResultVO parseResult;

    private HistoryAiAnalysisVO aiAnalysis;

    private HistoryMatchResultVO latestMatch;

    private List<HistoryMatchResultVO> matchResults;

    private LocalDateTime updatedAt;
}
