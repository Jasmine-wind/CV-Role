package com.winter.airesumeoptimizer.module.resume.dto;

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
public class ResumeTextSectionDTO {

    private String sectionType;

    private String heading;

    private String sourceSectionConfidence;

    private String iconType;

    private List<String> lines;

    private List<ResumeBlockDTO> blocks;
}
