package com.winter.airesumeoptimizer.module.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "局部改写候选片段原文定位")
public class RewriteSourceRefVO {

    @Schema(description = "起始行号", example = "32")
    private Integer startLine;

    @Schema(description = "结束行号", example = "40")
    private Integer endLine;

    @Schema(description = "来源原文")
    private String text;
}
