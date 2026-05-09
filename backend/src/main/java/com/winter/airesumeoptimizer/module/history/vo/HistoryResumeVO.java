package com.winter.airesumeoptimizer.module.history.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HistoryResumeVO {

    private Long resumeId;

    private String resumeName;

    private String fileType;

    private Long fileSize;

    private String uploadStatus;

    private LocalDateTime uploadTime;
}
