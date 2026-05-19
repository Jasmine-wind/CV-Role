package com.winter.airesumeoptimizer.module.job.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "目标岗位详情")
public class JobDescriptionVO {

    @Schema(description = "目标岗位 ID", example = "1")
    private Long id;

    @Schema(description = "目标岗位标题", example = "Java 后端开发工程师")
    private String title;

    @Schema(description = "岗位来源类型", example = "USER_INPUT")
    private String sourceType;

    @Schema(description = "目标岗位 JD 原文")
    private String rawText;

    @Schema(description = "解析状态", example = "PENDING")
    private String parseStatus;

    @Schema(description = "结构化解析结果 JSON")
    private String structuredContent;

    @Schema(description = "模型名称", example = "qwen-plus")
    private String modelName;

    @Schema(description = "Prompt 版本", example = "job_description_parse_v1")
    private String promptVersion;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
