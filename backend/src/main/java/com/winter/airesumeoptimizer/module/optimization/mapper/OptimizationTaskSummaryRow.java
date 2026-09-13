package com.winter.airesumeoptimizer.module.optimization.mapper;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** Lightweight projection used by the Home recent-task list. */
@Getter
@Setter
public class OptimizationTaskSummaryRow {

    private Long optimizationTaskId;
    private Long resumeId;
    private String status;
    private String jobTitle;
    private String resumeName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
