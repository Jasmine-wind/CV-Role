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
@Schema(description = "服务后续分析的低耦合结构化数据")
public class ResumeStructuredDataDTO {

    private List<String> education;

    private List<ResumeSourceRefDTO> educationSourceRefs;

    private ResumeSkillSetDTO skills;

    private List<ResumeExperienceDTO> experiences;

    private List<ResumeProjectDTO> projects;

    private List<ResumeAchievementDTO> achievements;

    private List<String> certificates;

    private String summary;

    private ResumeSourceRefDTO summarySourceRef;

    private List<String> others;
}
