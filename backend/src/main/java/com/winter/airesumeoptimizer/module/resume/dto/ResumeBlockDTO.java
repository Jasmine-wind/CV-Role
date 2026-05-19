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
public class ResumeBlockDTO {

    private Integer index;

    private Integer originalIndex;

    private Integer displayOrder;

    private String text;

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
