package com.winter.airesumeoptimizer.module.history.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "历史详情中的解析结果")
public class HistoryParseResultVO {

    @Schema(description = "解析状态", example = "SUCCESS")
    private String parseStatus;

    @Schema(description = "解析文本预览")
    private String extractedTextPreview;

    @Schema(description = "解析错误信息")
    private String parseErrorMessage;

    @Schema(description = "解析更新时间")
    private LocalDateTime parseUpdatedAt;
}
