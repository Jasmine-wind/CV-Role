package com.winter.airesumeoptimizer.module.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "匹配分析触发响应")
public class AiJobMatchTriggerVO {

    @Schema(description = "匹配分析结果 ID", example = "1")
    private Long matchId;

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "目标岗位 ID", example = "1")
    private Long jobDescriptionId;

    @Schema(description = "总体匹配分数", example = "82")
    private Integer overallScore;

    @Schema(description = "匹配状态", example = "SUCCESS")
    private String matchStatus;

    @Schema(description = "模型名称", example = "deepseek-v4-flash")
    private String modelName;

    @Schema(description = "Prompt 版本", example = "ai_job_match_v1")
    private String promptVersion;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
