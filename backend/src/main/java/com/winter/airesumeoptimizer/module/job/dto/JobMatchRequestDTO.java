package com.winter.airesumeoptimizer.module.job.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobMatchRequestDTO {

    @NotNull(message = "岗位 ID 不能为空")
    private Long jobId;
}
