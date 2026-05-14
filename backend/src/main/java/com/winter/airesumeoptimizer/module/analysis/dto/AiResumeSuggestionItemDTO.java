package com.winter.airesumeoptimizer.module.analysis.dto;

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
public class AiResumeSuggestionItemDTO {

    private String type;

    private String priority;

    private String targetSection;

    private String issue;

    private String suggestion;

    private List<String> evidence;

    private String caution;

    private List<String> relatedItems;
}
