package com.winter.airesumeoptimizer.module.analysis.vo;

import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionItemDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "岗位优化建议结果")
public class AiResumeSuggestionVO {

    @Schema(description = "优化建议结果 ID", example = "1")
    private Long suggestionId;

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "目标岗位 ID", example = "1")
    private Long jobDescriptionId;

    @Schema(description = "匹配分析结果 ID", example = "1")
    private Long aiJobMatchResultId;

    @Schema(description = "建议状态", example = "SUCCESS")
    private String suggestionStatus;

    @Schema(description = "优化建议列表")
    private List<AiResumeSuggestionItemDTO> suggestions;

    @Schema(description = "模型名称", example = "deepseek-v4-flash")
    private String modelName;

    @Schema(description = "Prompt 版本", example = "resume_suggestion_v1")
    private String promptVersion;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
