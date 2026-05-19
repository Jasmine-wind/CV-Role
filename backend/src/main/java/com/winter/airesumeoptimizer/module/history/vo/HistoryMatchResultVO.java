package com.winter.airesumeoptimizer.module.history.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "历史详情中的岗位匹配结果")
public class HistoryMatchResultVO {

    @Schema(description = "匹配结果 ID", example = "1")
    private Long matchId;

    @Schema(description = "岗位 ID", example = "1")
    private Long jobId;

    @Schema(description = "目标岗位 ID", example = "1")
    private Long jobDescriptionId;

    @Schema(description = "匹配来源", example = "AI_JOB_DESCRIPTION")
    private String matchSource;

    @Schema(description = "岗位标题")
    private String jobTitle;

    @Schema(description = "公司名称")
    private String companyName;

    @Schema(description = "岗位分类")
    private String jobCategory;

    @Schema(description = "匹配分数", example = "80")
    private Integer matchScore;

    @Schema(description = "匹配原因")
    private String matchReason;

    @Schema(description = "建议预览")
    private String suggestionsPreview;

    @Schema(description = "匹配更新时间")
    private LocalDateTime matchUpdatedAt;
}
