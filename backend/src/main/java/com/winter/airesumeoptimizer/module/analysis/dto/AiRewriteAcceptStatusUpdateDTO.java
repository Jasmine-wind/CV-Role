package com.winter.airesumeoptimizer.module.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "AI 局部改写采纳状态更新请求")
public class AiRewriteAcceptStatusUpdateDTO {

    @Schema(description = "采纳状态", example = "ACCEPTED")
    @NotBlank(message = "采纳状态不能为空")
    @Size(max = 20, message = "采纳状态长度不能超过 20")
    private String acceptStatus;
}
