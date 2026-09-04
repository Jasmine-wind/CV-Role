package com.winter.airesumeoptimizer.module.optimization.vo;

import com.winter.airesumeoptimizer.module.analysis.vo.AiJobMatchResultVO;
import com.winter.airesumeoptimizer.module.evidence.vo.EvidenceAnalysisResultVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "正式优化任务的岗位分析结果")
public class OptimizationAnalysisResultVO {

    private Long optimizationTaskId;

    private Long resumeId;

    private Long sourceResumeVersionId;

    private Long targetResumeVersionId;

    private Long jobTargetId;

    private String status;

    private String jobTitle;

    private String resumeName;

    @Schema(description = "结果模式：EVIDENCE 为正式证据分析，LEGACY_COMPAT 为旧匹配结果兼容读取", example = "EVIDENCE")
    private String analysisMode;

    @Schema(description = "正式岗位证据分析结果，仅 EVIDENCE 模式存在")
    private EvidenceAnalysisResultVO evidenceAnalysis;

    @Schema(description = "旧匹配结果，仅历史任务兼容读取时存在")
    private AiJobMatchResultVO legacyAnalysis;
}
