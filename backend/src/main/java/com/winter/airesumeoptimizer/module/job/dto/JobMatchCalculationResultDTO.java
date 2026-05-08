package com.winter.airesumeoptimizer.module.job.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobMatchCalculationResultDTO {

    private Integer matchScore;

    private List<String> matchedItems;

    private List<String> missingItems;

    private String matchReason;
}
