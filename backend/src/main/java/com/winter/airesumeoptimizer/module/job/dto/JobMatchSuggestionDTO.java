package com.winter.airesumeoptimizer.module.job.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "岗位匹配优化建议")
public class JobMatchSuggestionDTO {

    @Schema(description = "建议类型", example = "SKILL_GAP")
    private String type;

    @Schema(description = "优先级", example = "HIGH")
    private String priority;

    @Schema(description = "建议标题")
    private String title;

    @Schema(description = "建议内容")
    private String content;

    @Schema(description = "关联技能或条目")
    private String relatedItem;
}
