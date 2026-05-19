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
@Schema(description = "项目经历")
public class ResumeProjectDTO {

    private String name;

    private String description;

    private String role;

    private String mentor;

    private String timeRange;

    private String environment;

    private List<String> techStack;

    private List<String> responsibilities;

    private String startDate;

    private String endDate;

    private String sourceType;

    private Integer parentExperienceIndex;

    private String sourceSectionId;

    private List<String> evidence;

    private ResumeSourceRefDTO sourceRef;

    private Double confidence;
}
