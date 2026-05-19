package com.winter.airesumeoptimizer.module.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "简历解析元数据")
public class ResumeParseMetaDTO {

    @Schema(description = "解析模式", example = "BALANCED")
    private String parseMode;

    @Schema(description = "解析器版本", example = "resume-parser-v2.9.17")
    private String parserVersion;

    @Schema(description = "AI 状态：USED/SKIPPED/FALLBACK/DISABLED")
    private String aiStatus;

    @Schema(description = "本次解析是否实际使用或采用了 AI 结果")
    private Boolean aiUsed;

    @Schema(description = "AI 未调用原因编码")
    private String aiSkippedReason;

    @Schema(description = "AI 是否发生失败降级")
    private Boolean aiFallbackOccurred;

    @Schema(description = "AI 失败降级原因")
    private String aiFallbackReason;

    @Schema(description = "AI 是否命中缓存，仅真实 AI 调用或复用 AI 结果时有意义")
    private Boolean aiCacheHit;

    @Schema(description = "AI 缓存 key 摘要")
    private String aiCacheKeyDigest;

    @Schema(description = "总解析耗时，毫秒")
    private Long totalParseDurationMs;

    @Schema(description = "规则解析耗时，毫秒")
    private Long ruleParseDurationMs;

    @Schema(description = "AI 章节归类耗时，毫秒")
    private Long aiSectionClassifyDurationMs;

    @Schema(description = "AI 结构化解析耗时，毫秒")
    private Long aiStructuredParseDurationMs;
}
