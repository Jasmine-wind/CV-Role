package com.winter.airesumeoptimizer.module.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
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
@Schema(description = "技能标签集合")
public class ResumeSkillSetDTO {

    private List<String> keywords;

    private Map<String, List<String>> groups;

    private List<ResumeSkillEvidenceDTO> evidence;
}
