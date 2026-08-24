package com.winter.airesumeoptimizer.module.insight.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "正式 Requirement Evidence 的只读追溯信息")
public class JobDirectionEvidenceVO {

    private Long requirementEvidenceId;
    private String sectionLabel;
    private String evidenceText;
    private String supportLevel;
}
