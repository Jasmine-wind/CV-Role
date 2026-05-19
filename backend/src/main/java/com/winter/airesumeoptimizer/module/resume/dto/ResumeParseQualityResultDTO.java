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
public class ResumeParseQualityResultDTO {

    private String status;

    private List<String> warnings;

    private String message;

    private Integer score;

    public boolean failed() {
        return "FAILED".equals(status);
    }
}
