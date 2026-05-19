package com.winter.airesumeoptimizer.module.analysis.vo;

import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchItemDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchEvidenceDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionItemDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "岗位优化报告")
public class JobOptimizationReportVO {

    @Schema(description = "简历 ID", example = "1")
    private Long resumeId;

    @Schema(description = "简历文件名或展示名称", example = "Java后端实习简历.pdf")
    private String resumeName;

    @Schema(description = "岗位描述 ID", example = "2")
    private Long jobDescriptionId;

    @Schema(description = "目标岗位标题", example = "Java后端开发实习生")
    private String jobTitle;

    @Schema(description = "岗位匹配分数，来自已有 AI 匹配结果", example = "82")
    private Integer matchScore;

    @Schema(description = "匹配等级，仅在有匹配分数时返回 HIGH、MEDIUM 或 LOW", example = "HIGH")
    private String matchLevel;

    @Schema(description = "强匹配项，来自已有 AI 岗位匹配结果")
    private List<AiJobMatchItemDTO> strongMatches;

    @Schema(description = "弱匹配项，来自已有 AI 岗位匹配结果")
    private List<AiJobMatchItemDTO> weakMatches;

    @Schema(description = "缺失技能，来自已有 AI 岗位匹配结果")
    private List<AiJobMatchItemDTO> missingSkills;

    @Schema(description = "风险提示，来自已有 AI 岗位匹配结果")
    private List<String> riskTips;

    @Schema(description = "匹配依据，来自已有 AI 岗位匹配结果")
    private List<AiJobMatchEvidenceDTO> matchEvidence;

    @Schema(description = "优化建议数量摘要")
    private SuggestionSummaryVO suggestionSummary;

    @Schema(description = "高优先级优化建议")
    private List<AiResumeSuggestionItemDTO> highPrioritySuggestions;

    @Schema(description = "中优先级优化建议")
    private List<AiResumeSuggestionItemDTO> mediumPrioritySuggestions;

    @Schema(description = "低优先级优化建议")
    private List<AiResumeSuggestionItemDTO> lowPrioritySuggestions;

    @Schema(description = "全部局部改写建议")
    private List<RewriteSuggestionItemVO> rewriteSuggestions;

    @Schema(description = "已采纳的局部改写建议")
    private List<RewriteSuggestionItemVO> acceptedRewriteSuggestions;

    @Schema(description = "待处理的局部改写建议")
    private List<RewriteSuggestionItemVO> pendingRewriteSuggestions;

    @Schema(description = "已拒绝的局部改写建议")
    private List<RewriteSuggestionItemVO> rejectedRewriteSuggestions;

    @Schema(description = "下一步操作清单，由已有匹配、建议和改写状态聚合生成")
    private List<NextStepItemVO> nextStepChecklist;

    @Schema(description = "相关 AI 结果的模型和 Prompt 信息")
    private List<ModelInfoVO> modelInfo;

    @Schema(description = "报告生成时间")
    private LocalDateTime generatedAt;

    @Schema(description = "报告聚合过程中的提示信息")
    private List<WarningVO> warnings;

    @Getter
    @Builder
    @Schema(description = "优化建议数量摘要")
    public static class SuggestionSummaryVO {

        @Schema(description = "建议总数", example = "8")
        private Integer totalCount;

        @Schema(description = "高优先级建议数量", example = "3")
        private Integer highPriorityCount;

        @Schema(description = "中优先级建议数量", example = "4")
        private Integer mediumPriorityCount;

        @Schema(description = "低优先级建议数量", example = "1")
        private Integer lowPriorityCount;
    }

    @Getter
    @Builder
    @Schema(description = "岗位报告中的局部改写建议")
    public static class RewriteSuggestionItemVO {

        @Schema(description = "局部改写建议 ID", example = "1")
        private Long rewriteId;

        @Schema(description = "改写对象类型", example = "PROJECT")
        private String rewriteType;

        @Schema(description = "目标简历部分", example = "项目经历")
        private String targetSection;

        @Schema(description = "原文片段")
        private String originalText;

        @Schema(description = "改写建议文本")
        private String rewrittenText;

        @Schema(description = "改写理由")
        private String rewriteReason;

        @Schema(description = "注意事项")
        private String caution;

        @Schema(description = "采纳状态", example = "PENDING")
        private String acceptStatus;

        @Schema(description = "关联的优化建议 ID", example = "1")
        private Long aiResumeSuggestionId;

        @Schema(description = "更新时间")
        private LocalDateTime updatedAt;
    }

    @Getter
    @Builder
    @Schema(description = "岗位优化报告下一步操作项")
    public static class NextStepItemVO {

        @Schema(description = "操作项标识", example = "REVIEW_HIGH_PRIORITY_SUGGESTIONS")
        private String key;

        @Schema(description = "操作项说明")
        private String text;

        @Schema(description = "操作项来源", example = "SUGGESTION")
        private String source;

        @Schema(description = "操作项状态", example = "PENDING")
        private String status;
    }

    @Getter
    @Builder
    @Schema(description = "岗位优化报告关联的模型信息")
    public static class ModelInfoVO {

        @Schema(description = "来源类型", example = "MATCH")
        private String sourceType;

        @Schema(description = "来源记录 ID", example = "1")
        private Long sourceId;

        @Schema(description = "模型名称", example = "deepseek-v4-flash")
        private String modelName;

        @Schema(description = "Prompt 版本", example = "ai_job_match_v1")
        private String promptVersion;

        @Schema(description = "来源结果状态", example = "SUCCESS")
        private String status;

        @Schema(description = "来源结果更新时间")
        private LocalDateTime updatedAt;
    }

    @Getter
    @Builder
    @Schema(description = "岗位优化报告提示信息")
    public static class WarningVO {

        @Schema(description = "提示编码", example = "MATCH_RESULT_MISSING")
        private String code;

        @Schema(description = "提示文案")
        private String message;

        @Schema(description = "提示来源", example = "MATCH")
        private String source;
    }
}
