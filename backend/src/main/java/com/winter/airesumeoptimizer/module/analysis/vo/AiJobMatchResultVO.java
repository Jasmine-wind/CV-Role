package com.winter.airesumeoptimizer.module.analysis.vo;

import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchEvidenceDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchItemDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchWeakExperienceDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "AI 岗位匹配结果")
public class AiJobMatchResultVO {

    @Schema(description = "AI 匹配结果 ID", example = "1")
    private Long matchId;

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "岗位描述 ID", example = "1")
    private Long jobDescriptionId;

    @Schema(description = "总体匹配分数", example = "82")
    private Integer overallScore;

    @Schema(description = "强匹配项")
    private List<AiJobMatchItemDTO> strongMatches;

    @Schema(description = "弱匹配项")
    private List<AiJobMatchItemDTO> weakMatches;

    @Schema(description = "缺失技能")
    private List<AiJobMatchItemDTO> missingSkills;

    @Schema(description = "表达较弱经历")
    private List<AiJobMatchWeakExperienceDTO> weakExperienceDescriptions;

    @Schema(description = "匹配依据")
    private List<AiJobMatchEvidenceDTO> evidence;

    @Schema(description = "风险提示")
    private List<String> riskNotes;

    @Schema(description = "模型名称", example = "deepseek-v4-flash")
    private String modelName;

    @Schema(description = "Prompt 版本", example = "ai_job_match_v1")
    private String promptVersion;

    @Schema(description = "匹配状态", example = "SUCCESS")
    private String matchStatus;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
