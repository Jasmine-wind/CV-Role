package com.winter.airesumeoptimizer.module.resume.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResumeDisplayNameUpdateRequestDTO {

    @NotBlank(message = "简历名称不能为空")
    @Size(max = 255, message = "简历名称不能超过 255 个字符")
    private String displayName;
}
