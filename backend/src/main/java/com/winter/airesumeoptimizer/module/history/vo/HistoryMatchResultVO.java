package com.winter.airesumeoptimizer.module.history.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HistoryMatchResultVO {

    private Long matchId;

    private Long jobId;

    private String jobTitle;

    private String companyName;

    private String jobCategory;

    private Integer matchScore;

    private String matchReason;

    private String suggestionsPreview;

    private LocalDateTime matchUpdatedAt;
}
