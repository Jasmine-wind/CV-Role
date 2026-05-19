package com.winter.airesumeoptimizer.module.resume.dto;

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
public class ResumeSectionClassificationDTO {

    private Integer index;

    private String section;

    private Double confidence;

    private String reasonCode;
}
