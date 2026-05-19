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
@Schema(description = "带连续编号的简历文本行")
public class ResumeIndexedLineDTO {

    private Integer lineId;

    private Integer page;

    private String text;

    private String normalizedText;

    private String sourceType;

    private String rawSectionId;

    private String sectionHint;

    private Double sectionConfidence;

    private Boolean isNoise;
}
