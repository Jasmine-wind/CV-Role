package com.winter.airesumeoptimizer.module.resume.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeBlockDTO {

    /** Stable source-backed identifier for a logical block. */
    private String id;

    private Integer index;

    private Integer originalIndex;

    private Integer displayOrder;

    private String text;

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

    /** IDs of the visual source lines represented by this logical block. */
    private List<String> sourceBlockIds;

    /** Stable logical occurrences represented by this block; preserves repeated source rows. */
    private List<String> sourceOccurrenceIds;

    private String prevText;

    private String nextText;

    private String sourceType;

    private String iconType;

    private String sourceSection;

    private String ruleSection;

    private Double ruleConfidence;

    private String sourceSectionConfidence;

    private String lockedLevel;

    private String resumeTypeHint;

    private String parseMode;

    private String finalSectionSource;

    private Boolean sectionLocked;
}
