package com.winter.airesumeoptimizer.module.resume.dto;

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
public class ResumeAiStructuredParseResultDTO {

    private Boolean aiEnabled;

    private Boolean applied;

    private Boolean aiInvoked;

    private String aiStatus;

    private String skippedReason;

    private Boolean fallbackOccurred;

    private String fallbackReason;

    private Long durationMs;

    private Boolean cacheHit;

    private String cacheKey;

    private ResumeStructuredContentDTO structuredContent;

    private List<String> qualityWarnings;

    public boolean shouldApply() {
        return Boolean.TRUE.equals(aiEnabled)
                && Boolean.TRUE.equals(applied)
                && structuredContent != null;
    }
}
