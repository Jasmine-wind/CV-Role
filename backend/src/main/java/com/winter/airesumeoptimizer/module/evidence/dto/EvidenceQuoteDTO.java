package com.winter.airesumeoptimizer.module.evidence.dto;

import com.winter.airesumeoptimizer.module.evidence.enums.EvidenceSupportLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EvidenceQuoteDTO {

    private String sectionLabel;

    private String quote;

    private EvidenceSupportLevel supportLevel;
}
