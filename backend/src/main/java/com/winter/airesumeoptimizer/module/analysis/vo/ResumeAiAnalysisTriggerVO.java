package com.winter.airesumeoptimizer.module.analysis.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResumeAiAnalysisTriggerVO {

    private Long resumeId;

    private String analysisStatus;

    private Integer score;

    private String modelName;

    private String promptVersion;

    private String errorMessage;

    private LocalDateTime updatedAt;
}
