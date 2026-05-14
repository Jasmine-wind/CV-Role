package com.winter.airesumeoptimizer.module.analysis.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiJobMatchResultDTO {

    private Integer overallScore;

    private List<AiJobMatchItemDTO> strongMatches;

    private List<AiJobMatchItemDTO> weakMatches;

    private List<AiJobMatchItemDTO> missingSkills;

    private List<AiJobMatchWeakExperienceDTO> weakExperienceDescriptions;

    private List<AiJobMatchEvidenceDTO> evidence;

    private List<String> riskNotes;
}
