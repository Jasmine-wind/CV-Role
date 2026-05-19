package com.winter.airesumeoptimizer.module.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
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
@Schema(description = "原始简历章节")
public class ResumeRawSectionDTO {

    private String id;

    private String originalTitle;

    private String normalizedSection;

    private String displayName;

    private String iconType;

    private Double confidence;

    private String source;

    private Integer originalOrder;

    private Integer displayOrder;

    private List<ResumeRawSectionBlockDTO> blocks;
}
