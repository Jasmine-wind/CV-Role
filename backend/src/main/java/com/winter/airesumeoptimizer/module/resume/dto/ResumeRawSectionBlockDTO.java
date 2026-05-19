package com.winter.airesumeoptimizer.module.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "原始章节文本块")
public class ResumeRawSectionBlockDTO {

    private Integer index;

    private String text;

    private String iconType;

    private Integer originalIndex;

    private Integer displayOrder;
}
