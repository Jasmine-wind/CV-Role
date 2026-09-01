package com.winter.airesumeoptimizer.module.resume.enums;

/**
 * 解析结果的交付质量状态（Slice A）。
 * parse SUCCESS 只表示解析流程跑完，不代表内容可安全投递；
 * 是否可安全使用由该状态裁决，SoT 在 resume_parse_results。
 */
public enum ResumeQualityStatus {

    /** 解析进行中或刚重置，尚未完成确定性验证。 */
    PENDING,

    /** canonical 文档满足进入 Workspace / Preview / Export 的最低结构要求。 */
    READY,

    /** 内容已保留，但存在无法安全确定的结构或字段，必须由用户确认。 */
    NEEDS_REVIEW,

    /** 无法形成可编辑文档。 */
    FAILED;

    public static final String QUALITY_READY = READY.name();
    public static final String QUALITY_NEEDS_REVIEW = NEEDS_REVIEW.name();
    public static final String QUALITY_FAILED = FAILED.name();
    public static final String QUALITY_PENDING = PENDING.name();
}
