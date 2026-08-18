package com.winter.airesumeoptimizer.module.evidence.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EvidenceMatchOutcomeDTO {

    private List<EvidenceRequirementEvaluationDTO> requirements;
}
