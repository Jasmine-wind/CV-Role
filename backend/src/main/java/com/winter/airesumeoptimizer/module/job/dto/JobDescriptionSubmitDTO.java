package com.winter.airesumeoptimizer.module.job.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "目标岗位提交请求，来源固定为用户粘贴 JD")
public class JobDescriptionSubmitDTO {

    @Schema(description = "目标岗位标题", example = "Java 后端开发工程师")
    @NotBlank(message = "目标岗位标题不能为空")
    @Size(max = 200, message = "目标岗位标题不能超过 200 个字符")
    private String title;

    @Schema(description = "目标岗位 JD 原文")
    @NotBlank(message = "目标岗位 JD 原文不能为空")
    @Size(max = 10000, message = "目标岗位 JD 原文不能超过 10000 个字符")
    private String rawText;
}
