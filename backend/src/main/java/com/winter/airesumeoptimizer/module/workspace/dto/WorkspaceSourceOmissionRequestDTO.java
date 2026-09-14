package com.winter.airesumeoptimizer.module.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "按冻结来源 occurrence 确认或撤销 intentional omission")
public record WorkspaceSourceOmissionRequestDTO(
        @NotNull(message = "缺少内容版本号")
        @PositiveOrZero(message = "内容版本号不能为负数")
        Long expectedRevision,
        @NotEmpty(message = "来源 occurrence ID 不能为空")
        @Size(max = 500, message = "来源 occurrence ID 数量超出上限")
        List<@NotNull(message = "来源 occurrence ID 不能为空") String> sourceOccurrenceIds) {
}
