package com.winter.airesumeoptimizer.module.job.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "岗位描述提交请求")
public class JobDescriptionSubmitDTO {

    @Schema(description = "岗位描述标题", example = "Java 后端开发工程师")
    @NotBlank(message = "岗位描述标题不能为空")
    @Size(max = 200, message = "岗位描述标题不能超过 200 个字符")
    private String title;

    @Schema(description = "岗位描述原文")
    @NotBlank(message = "岗位描述原文不能为空")
    @Size(max = 10000, message = "岗位描述原文不能超过 10000 个字符")
    private String rawText;
}
