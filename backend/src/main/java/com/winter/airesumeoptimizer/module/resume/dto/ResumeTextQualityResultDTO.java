package com.winter.airesumeoptimizer.module.resume.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResumeTextQualityResultDTO {

    private String status;

    private List<String> issues;

    private String message;

    public boolean failed() {
        return "FAILED".equals(status);
    }
}
