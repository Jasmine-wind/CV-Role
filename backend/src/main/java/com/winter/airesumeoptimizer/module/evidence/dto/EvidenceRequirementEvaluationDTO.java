package com.winter.airesumeoptimizer.module.evidence.dto;

import com.winter.airesumeoptimizer.module.evidence.enums.EvidenceMatchLevel;
import com.winter.airesumeoptimizer.module.evidence.enums.RequirementImportance;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EvidenceRequirementEvaluationDTO {

    private String requirementText;

    private RequirementImportance importance;

    private EvidenceMatchLevel matchLevel;

    private String conclusion;

    private String suggestion;

    private List<EvidenceQuoteDTO> evidences;
}
