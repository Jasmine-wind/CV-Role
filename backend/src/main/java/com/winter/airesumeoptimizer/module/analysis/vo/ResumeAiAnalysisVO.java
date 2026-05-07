package com.winter.airesumeoptimizer.module.analysis.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResumeAiAnalysisVO {

    private Long resumeId;

    private String analysisStatus;

    private Integer score;

    private List<String> strengths;

    private List<String> problems;

    private List<String> suggestionsSummary;

    private String modelName;

    private String promptVersion;

    private String errorMessage;

    private LocalDateTime updatedAt;
}
