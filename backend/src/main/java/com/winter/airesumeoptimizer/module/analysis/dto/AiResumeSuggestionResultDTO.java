package com.winter.airesumeoptimizer.module.analysis.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AiResumeSuggestionResultDTO {

    private List<AiResumeSuggestionItemDTO> suggestions;
}
