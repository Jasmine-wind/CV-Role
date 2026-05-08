package com.winter.airesumeoptimizer.module.job.dto;

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
public class JobMatchSuggestionDTO {

    private String type;

    private String priority;

    private String title;

    private String content;

    private String relatedItem;
}
