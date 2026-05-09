package com.winter.airesumeoptimizer.module.history.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HistoryParseResultVO {

    private String parseStatus;

    private String extractedTextPreview;

    private String parseErrorMessage;

    private LocalDateTime parseUpdatedAt;
}
