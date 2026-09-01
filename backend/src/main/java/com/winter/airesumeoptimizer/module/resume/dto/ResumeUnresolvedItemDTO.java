package com.winter.airesumeoptimizer.module.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 解析阶段无法可靠判定归属的候选内容（Slice A unresolved sidecar）。
 * 它是审查态数据，不是简历内容：不进入 SOURCE/TARGET、不进入任务快照、不参与证据分析。
 * 用户确认（接受/删除）后才允许进入或离开 canonical 文档。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "待用户确认的解析候选项")
public class ResumeUnresolvedItemDTO {

    /** 候选联系方式。 */
    public static final String KIND_CONTACT_CANDIDATE = "CONTACT_CANDIDATE";
    /** 必填联系方式缺失时的手动补录候选，只接受电话或邮箱。 */
    public static final String KIND_REQUIRED_CONTACT_CANDIDATE = "REQUIRED_CONTACT_CANDIDATE";
    /** 候选姓名：原文候选仅供确认，无法识别时允许用户手动补录。 */
    public static final String KIND_NAME_CANDIDATE = "NAME_CANDIDATE";
    /** 候选条目（经历/项目/教育）。 */
    public static final String KIND_ENTRY_CANDIDATE = "ENTRY_CANDIDATE";
    /** 无法判定归属的游离文本。 */
    public static final String KIND_TEXT_FRAGMENT = "TEXT_FRAGMENT";

    @Schema(description = "候选项稳定 ID", example = "u-1")
    private String id;

    @Schema(description = "候选类型", example = "TEXT_FRAGMENT")
    private String kind;

    @Schema(description = "候选内容的 canonical 片段 JSON")
    private String canonicalDraft;

    @Schema(description = "人类可读的原因说明", example = "该行无法确认属于哪个章节")
    private String reason;

    @Schema(description = "来源行引用，仅用于内部定位，不向用户展示")
    private String sourceRef;
}
