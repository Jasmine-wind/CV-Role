package com.winter.airesumeoptimizer.module.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "AI 岗位匹配请求")
public class AiJobMatchRequestDTO {

    @Schema(description = "岗位描述 ID", example = "1")
    @NotNull(message = "岗位描述 ID 不能为空")
    @Positive(message = "岗位描述 ID 必须大于 0")
    private Long jobDescriptionId;
}
