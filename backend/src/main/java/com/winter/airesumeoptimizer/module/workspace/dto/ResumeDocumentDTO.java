package com.winter.airesumeoptimizer.module.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
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
@Schema(description = "结构化简历文档，岗位版本编辑与后续渲染的唯一业务数据源")
public class ResumeDocumentDTO {

    /** RESUME_DOCUMENT_V1 remains the canonical business document schema. */
    public static final String SCHEMA_VERSION = "RESUME_DOCUMENT_V1";

    @Schema(description = "文档结构版本", example = "RESUME_DOCUMENT_V1")
    private String schemaVersion;

    /** Optional immutable origin metadata for the parser-produced SOURCE projection. */
    @Schema(description = "整份文档的来源引用，仅用于追溯，不作为编辑后的事实证明")
    private ResumeSourceRefDTO sourceRef;

    /** Occurrence identities are retained separately from display text so duplicates survive. */
    @Schema(description = "整份文档覆盖的来源 occurrence ID")
    private List<String> sourceOccurrenceIds;

    /** Per-occurrence source text is the authenticated root-manifest association for child refs. */
    @Schema(description = "来源 occurrence 到原始文本的只读映射")
    private Map<String, String> sourceOccurrenceTexts;

    /** Maps aliases to one logical occurrence so repeated source rows remain distinguishable. */
    @Schema(description = "来源 occurrence 到逻辑 occurrence 的只读映射")
    private Map<String, String> sourceOccurrencePrimaryIds;

    @Schema(description = "基础信息")
    private ResumeDocumentBasicsDTO basics;

    @Schema(description = "简历章节列表，顺序即展示顺序")
    private List<ResumeDocumentSectionDTO> sections;
}
