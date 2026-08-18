package com.winter.airesumeoptimizer.module.optimization.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "岗位定向优化任务")
public class OptimizationTaskVO {

    private Long optimizationTaskId;

    private Long sourceResumeVersionId;

    private Long targetResumeVersionId;

    private Long jobTargetId;

    private Long asyncTaskId;

    private Long analysisResultId;

    private String status;

    private String jobTitle;

    private String resumeName;

    private String providerSnapshot;

    private String modelSnapshot;

    private String templateVersion;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
