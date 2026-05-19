package com.winter.airesumeoptimizer.module.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
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
@Schema(description = "简历结构化内容")
public class ResumeStructuredContentDTO {

    @Schema(description = "姓名", example = "张三")
    private String name;

    @Schema(description = "手机号", example = "13800000000")
    private String phone;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "基础个人信息")
    private Map<String, String> basicInfo;

    @Schema(description = "基础个人信息调试详情")
    private Map<String, ResumeBasicInfoFieldDTO> basicInfoDebug;

    @Schema(description = "原始简历章节结构")
    private List<ResumeRawSectionDTO> rawSections;

    @Schema(description = "清洗后文本行索引，用于 sourceRef 和 Pointer Extraction")
    private List<ResumeIndexedLineDTO> indexedLines;

    @Schema(description = "低耦合结构化数据")
    private ResumeStructuredDataDTO structuredData;

    @Schema(description = "解析元数据")
    private ResumeParseMetaDTO parseMeta;

    @Schema(description = "前端主视图展示模型，优先使用 AI 展示优化结果，失败时为规则降级结果")
    private ResumeDisplayModelDTO displayModel;

    @Schema(description = "AI 展示优化模型")
    private ResumeDisplayModelDTO aiDisplayModel;

    @Schema(description = "规则展示模型，作为 AI 展示优化失败降级结果")
    private ResumeDisplayModelDTO ruleDisplayModel;

    @Schema(description = "求职意向", example = "Java 后端开发工程师")
    private String jobIntention;

    @Schema(description = "最高学历", example = "本科")
    private String highestEducation;

    @Schema(description = "简历类型", example = "EXPERIENCED")
    private String resumeType;

    @Schema(description = "解析模式", example = "BALANCED")
    private String parseMode;

    @Schema(description = "解析器版本", example = "resume-parser-v2.9.16.7")
    private String parserVersion;

    @Schema(description = "教育经历")
    private List<String> education;

    @Schema(description = "技能列表")
    private List<String> skills;

    @Schema(description = "项目经历")
    private List<String> projects;

    @Schema(description = "工作经历")
    private List<String> workExperiences;

    @Schema(description = "实习经历")
    private List<String> internships;

    @Schema(description = "校园经历")
    private List<String> campusExperiences;

    @Schema(description = "获奖经历")
    private List<String> awards;

    @Schema(description = "证书")
    private List<String> certificates;

    @Schema(description = "个人总结")
    private String summary;

    @Schema(description = "未归类但有价值的内容")
    private List<String> others;

    @Schema(description = "解析质量提示")
    private List<String> qualityWarnings;

    @Schema(description = "调试信息")
    private Map<String, Object> debug;

    @Schema(description = "是否启用 AI 章节归类")
    private Boolean aiSectionClassifyEnabled;

    @Schema(description = "AI 章节归类是否应用")
    private Boolean aiSectionClassifyApplied;

    @Schema(description = "AI 章节归类降级原因")
    private String aiSectionClassifyFallbackReason;

    @Schema(description = "AI 章节归类耗时，毫秒")
    private Long aiSectionClassifyDurationMs;

    @Schema(description = "AI 章节归类是否命中缓存")
    private Boolean aiSectionClassifyCacheHit;

    @Schema(description = "AI 章节归类缓存 key")
    private String aiSectionClassifyCacheKey;

    @Schema(description = "是否启用 AI 结构化补全")
    private Boolean aiStructuredParseEnabled;

    @Schema(description = "AI 结构化补全是否应用")
    private Boolean aiStructuredParseApplied;

    @Schema(description = "AI 结构化补全降级原因")
    private String aiStructuredParseFallbackReason;

    @Schema(description = "AI 结构化补全耗时，毫秒")
    private Long aiStructuredParseDurationMs;

    @Schema(description = "AI 结构化补全是否命中缓存")
    private Boolean aiStructuredParseCacheHit;

    @Schema(description = "AI 结构化补全缓存 key")
    private String aiStructuredParseCacheKey;

    @Schema(description = "文本提取耗时，毫秒")
    private Long textExtractDurationMs;

    @Schema(description = "规则解析耗时，毫秒")
    private Long ruleParseDurationMs;

    @Schema(description = "总解析耗时，毫秒")
    private Long totalParseDurationMs;

    @Schema(description = "章节识别结果")
    private List<ResumeTextSectionDTO> sections;

    @Schema(description = "原始简历文本")
    private String rawText;
}
