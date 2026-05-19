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
public class ResumeTextCleanResultDTO {

    private String cleanedText;

    private List<ResumeTextSectionDTO> sections;

    private Integer duplicateLineCount;

    private Integer invalidLineCount;

    private List<String> sectionConflictWarnings;

    private Boolean aiSectionClassifyEnabled;

    private Boolean aiSectionClassifyApplied;

    private String aiSectionClassifyFallbackReason;

    private Long aiSectionClassifyDurationMs;

    private Boolean aiSectionClassifyCacheHit;

    private String aiSectionClassifyCacheKey;
}
