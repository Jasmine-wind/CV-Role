package com.winter.airesumeoptimizer.module.analysis.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "岗位分析启动结果")
public class JobAnalysisStartVO {

    @Schema(description = "后台任务 ID", example = "1001")
    private Long taskId;

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "已保存的目标岗位 ID", example = "10")
    private Long jobDescriptionId;
}
