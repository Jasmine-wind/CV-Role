package com.winter.airesumeoptimizer.module.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "技能证据")
public class ResumeSkillEvidenceDTO {

    private String skill;

    private String sourceSectionId;

    private String sourceText;

    /** Source-backed human-readable description; keyword tags remain separate. */
    private String description;

    private List<String> keywords;

    private ResumeSourceRefDTO sourceRef;
}
