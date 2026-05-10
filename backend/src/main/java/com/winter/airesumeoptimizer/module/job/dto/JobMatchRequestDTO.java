package com.winter.airesumeoptimizer.module.job.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "岗位匹配请求")
public class JobMatchRequestDTO {

    @Schema(description = "岗位 ID", example = "1")
    @NotNull(message = "岗位 ID 不能为空")
    @Positive(message = "岗位 ID 必须大于 0")
    private Long jobId;
}
