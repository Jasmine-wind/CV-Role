package com.winter.airesumeoptimizer.module.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "基础信息字段调试信息")
public class ResumeBasicInfoFieldDTO {

    @Schema(description = "字段值")
    private String value;

    @Schema(description = "置信度，0 到 1")
    private Double confidence;

    @Schema(description = "来源，例如 REGEX、RULE、AI、MERGED")
    private String source;

    @Schema(description = "匹配证据")
    private String evidence;

    @Schema(description = "字段状态，例如 CONFIRMED、REJECTED、EMPTY、LOW_CONFIDENCE")
    private String status;

    @Schema(description = "拒绝原因")
    private String rejectReason;
}
