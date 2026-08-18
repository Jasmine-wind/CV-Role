package com.winter.airesumeoptimizer.module.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "岗位分析启动结果")
public class JobAnalysisStartVO {

    @Schema(description = "后台执行任务 ID", example = "1001")
    private Long taskId;

    @Schema(description = "正式优化任务 ID", example = "2001")
    private Long optimizationTaskId;

    @Schema(description = "本次输入简历版本 ID", example = "3001")
    private Long sourceResumeVersionId;

    @Schema(description = "岗位定向简历版本 ID", example = "3002")
    private Long targetResumeVersionId;

    @Schema(description = "正式目标岗位 ID", example = "4001")
    private Long jobTargetId;

    @Deprecated
    @Schema(description = "兼容字段：V1 简历 ID", example = "1", deprecated = true)
    private Long resumeId;

    @Deprecated
    @Schema(description = "兼容字段：V1 目标岗位 ID", example = "10", deprecated = true)
    private Long jobDescriptionId;
}
