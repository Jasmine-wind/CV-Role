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
@Schema(description = "成果与获奖")
public class ResumeAchievementDTO {

    private String title;

    private String level;

    private String competition;

    private String ranking;

    private String timeRange;

    private String date;

    private String sourceSectionId;

    private Integer parentExperienceIndex;

    private List<String> evidence;

    private ResumeSourceRefDTO sourceRef;

    private Double confidence;
}
