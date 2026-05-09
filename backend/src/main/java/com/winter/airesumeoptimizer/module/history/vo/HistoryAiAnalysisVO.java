package com.winter.airesumeoptimizer.module.history.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HistoryAiAnalysisVO {

    private String analysisStatus;

    private Integer analysisScore;

    private String strengthsPreview;

    private String problemsPreview;

    private String suggestionsSummary;

    private String analysisErrorMessage;

    private LocalDateTime analysisUpdatedAt;
}
