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
@Schema(description = "统一经历")
public class ResumeExperienceDTO {

    private String type;

    private String organization;

    private String role;

    private String startDate;

    private String endDate;

    private String description;

    private List<String> bullets;

    private String sourceSectionId;

    private String sourceTitle;

    private List<String> evidence;

    private ResumeSourceRefDTO sourceRef;

    private Double confidence;
}
