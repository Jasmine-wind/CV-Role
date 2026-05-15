package com.winter.airesumeoptimizer.module.history.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "AI 历史结果详情")
public class AiResultDetailVO {

    @Schema(description = "来源表记录 ID", example = "1")
    private Long recordId;

    @Schema(description = "AI 结果类型", example = "MATCH_ANALYSIS")
    private String resultType;

    @Schema(description = "结果标题", example = "Java 后端开发 - 匹配分析")
    private String title;

    @Schema(description = "处理状态", example = "SUCCESS")
    private String status;

    @Schema(description = "结构化结果内容")
    private Map<String, Object> content;

    @Schema(description = "关联简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "关联简历名称", example = "winter-resume.pdf")
    private String resumeName;

    @Schema(description = "关联目标岗位 ID", example = "1")
    private Long jobDescriptionId;

    @Schema(description = "关联目标岗位标题", example = "Java 后端开发")
    private String jobTitle;

    @Schema(description = "模型名称", example = "deepseek-v4-flash")
    private String modelName;

    @Schema(description = "Prompt 版本", example = "ai_job_match_v1")
    private String promptVersion;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
