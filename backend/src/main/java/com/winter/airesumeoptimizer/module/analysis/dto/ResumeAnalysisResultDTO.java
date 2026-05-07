package com.winter.airesumeoptimizer.module.analysis.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResumeAnalysisResultDTO {

    private Integer score;

    private List<String> strengths;

    private List<String> problems;

    private List<String> suggestionsSummary;
}
