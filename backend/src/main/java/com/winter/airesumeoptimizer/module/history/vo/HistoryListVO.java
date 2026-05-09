package com.winter.airesumeoptimizer.module.history.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HistoryListVO {

    private Long recordId;

    private Long resumeId;

    private String resumeName;

    private String fileType;

    private Long fileSize;

    private String uploadStatus;

    private LocalDateTime uploadTime;

    private String parseStatus;

    private String analysisStatus;

    private Integer analysisScore;

    private Long latestJobId;

    private String latestJobTitle;

    private String latestCompanyName;

    private Integer latestMatchScore;

    private LocalDateTime updatedAt;
}
