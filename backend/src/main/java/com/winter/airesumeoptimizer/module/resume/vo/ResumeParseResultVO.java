package com.winter.airesumeoptimizer.module.resume.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "简历解析结果")
public class ResumeParseResultVO {

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "解析状态", example = "SUCCESS")
    private String parseStatus;

    @Schema(description = "提取出的简历文本")
    private String extractedText;

    @Schema(description = "结构化解析 JSON")
    private String structuredJson;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
