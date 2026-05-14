package com.winter.airesumeoptimizer.module.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "AI 简历优化建议触发响应")
public class AiResumeSuggestionTriggerVO {

    @Schema(description = "优化建议结果 ID", example = "1")
    private Long suggestionId;

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "岗位描述 ID", example = "1")
    private Long jobDescriptionId;

    @Schema(description = "AI 匹配结果 ID", example = "1")
    private Long aiJobMatchResultId;

    @Schema(description = "建议生成状态", example = "SUCCESS")
    private String suggestionStatus;

    @Schema(description = "建议数量", example = "3")
    private Integer suggestionCount;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
