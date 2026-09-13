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
@Schema(description = "结构化字段的原文行号引用")
public class ResumeSourceRefDTO {

    private Integer startLine;

    private Integer endLine;

    private String text;

    /** Stable source block identifiers covered by this reference (legacy alias). */
    private List<String> sourceBlockIds;

    /** Stable occurrence identifiers. Unlike a text match, these retain duplicate source rows. */
    private List<String> sourceOccurrenceIds;

    private Integer page;

    private Double x;

    private Double y;

    private Double width;

    private Double height;

    private Double fontSize;

    private String fontName;

    private Boolean boldHint;

    private Integer indent;

    private Boolean bulletHint;

    private ResumeSourceBlockRole role;

    private String sourceType;

}
