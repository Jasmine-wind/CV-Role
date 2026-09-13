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

    /** Logical source blocks retained during layout-aware parsing. */
    private List<ResumeBlockDTO> sourceBlocks;

    private String extractionCandidateType;

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
