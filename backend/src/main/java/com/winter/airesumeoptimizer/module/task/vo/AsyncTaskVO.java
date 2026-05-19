package com.winter.airesumeoptimizer.module.task.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "异步任务状态")
public class AsyncTaskVO {

    @Schema(description = "任务 ID", example = "1001")
    private Long taskId;

    @Schema(description = "任务类型", example = "RESUME_PARSE")
    private String taskType;

    @Schema(description = "业务对象类型", example = "RESUME")
    private String bizType;

    @Schema(description = "业务对象 ID", example = "12")
    private Long bizId;

    @Schema(description = "任务状态", example = "RUNNING")
    private String status;

    @Schema(description = "任务进度，0 到 100", example = "40")
    private Integer progress;

    @Schema(description = "当前阶段提示", example = "正在调用 AI 模型")
    private String message;

    @Schema(description = "结果类型", example = "RESUME_PARSE")
    private String resultType;

    @Schema(description = "结果记录 ID", example = "2001")
    private Long resultId;

    @Schema(description = "结果摘要")
    private String resultSummary;

    @Schema(description = "错误码", example = "AI_TIMEOUT")
    private String errorCode;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "结束时间")
    private LocalDateTime finishedAt;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
