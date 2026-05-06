package com.winter.airesumeoptimizer.module.resume.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResumeParseResultVO {

    private Long resumeId;

    private String parseStatus;

    private String extractedText;

    private String structuredJson;

    private String errorMessage;

    private LocalDateTime updatedAt;
}
