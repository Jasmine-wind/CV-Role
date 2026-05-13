package com.winter.airesumeoptimizer.module.job.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JobDescriptionParseResultDTO {

    private String jobTitle;

    private List<String> requiredSkills;

    private List<String> bonusSkills;

    private List<String> experienceSignals;

    private List<String> responsibilities;

    private List<String> keywords;

    private String summary;
}
