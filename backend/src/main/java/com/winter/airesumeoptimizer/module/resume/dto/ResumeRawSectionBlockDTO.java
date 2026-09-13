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
@Schema(description = "原始章节文本块")
public class ResumeRawSectionBlockDTO {

    /** Stable logical source identifier when one is available. */
    private String id;

    private Integer index;

    private String text;

    private String iconType;

    private Integer originalIndex;

    private Integer displayOrder;

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

    private List<String> sourceBlockIds;

    /** Stable occurrence IDs; unlike text they distinguish identical rows. */
    private List<String> sourceOccurrenceIds;

    private String sourceType;
}
