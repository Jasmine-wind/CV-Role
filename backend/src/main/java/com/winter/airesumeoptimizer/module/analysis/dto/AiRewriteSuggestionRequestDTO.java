package com.winter.airesumeoptimizer.module.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "AI 局部改写建议请求")
public class AiRewriteSuggestionRequestDTO {

    @Schema(description = "改写对象类型", example = "PROJECT")
    @NotBlank(message = "改写对象类型不能为空")
    @Size(max = 30, message = "改写对象类型长度不能超过 30")
    private String rewriteType;

    @Schema(description = "目标简历部分", example = "项目经历")
    @NotBlank(message = "目标简历部分不能为空")
    @Size(max = 100, message = "目标简历部分长度不能超过 100")
    private String targetSection;

    @Schema(description = "用户选择的原文片段", example = "做了一个 AI 简历优化系统，负责后端开发。")
    @NotBlank(message = "原文片段不能为空")
    @Size(max = 3000, message = "原文片段长度不能超过 3000")
    private String originalText;

    @Schema(description = "目标岗位 ID，可选", example = "1")
    @Positive(message = "目标岗位 ID 必须大于 0")
    private Long jobDescriptionId;

    @Schema(description = "AI 匹配结果 ID，可选", example = "1")
    @Positive(message = "AI 匹配结果 ID 必须大于 0")
    private Long aiJobMatchResultId;

    @Schema(description = "AI 优化建议 ID，可选", example = "1")
    @Positive(message = "AI 优化建议 ID 必须大于 0")
    private Long aiResumeSuggestionId;
}
