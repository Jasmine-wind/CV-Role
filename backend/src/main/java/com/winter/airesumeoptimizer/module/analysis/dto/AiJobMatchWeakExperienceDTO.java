package com.winter.airesumeoptimizer.module.analysis.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiJobMatchWeakExperienceDTO {

    private String section;

    private String issue;
}
