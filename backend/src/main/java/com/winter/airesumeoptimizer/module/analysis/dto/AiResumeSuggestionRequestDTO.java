package com.winter.airesumeoptimizer.module.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "岗位优化建议请求")
public class AiResumeSuggestionRequestDTO {

    @Schema(description = "目标岗位 ID", example = "1")
    @NotNull(message = "目标岗位 ID 不能为空")
    @Positive(message = "目标岗位 ID 必须大于 0")
    private Long jobDescriptionId;

    @Schema(description = "AI 匹配结果 ID，可选", example = "1")
    @Positive(message = "AI 匹配结果 ID 必须大于 0")
    private Long aiJobMatchResultId;
}
