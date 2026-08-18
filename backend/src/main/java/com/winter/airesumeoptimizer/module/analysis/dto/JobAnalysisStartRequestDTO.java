package com.winter.airesumeoptimizer.module.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "开始岗位分析请求")
public class JobAnalysisStartRequestDTO {

    @NotNull(message = "请选择简历")
    @Positive(message = "简历 ID 必须大于 0")
    @Schema(description = "用于岗位分析的简历 ID", example = "1")
    private Long resumeId;

    @NotBlank(message = "目标岗位 JD 不能为空")
    @Size(max = 10000, message = "目标岗位 JD 不能超过 10000 个字符")
    @Schema(description = "用户粘贴的目标岗位 JD 原文")
    private String jobDescription;
}
