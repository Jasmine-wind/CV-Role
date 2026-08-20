package com.winter.airesumeoptimizer.module.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
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

    public static final String SCHEMA_VERSION = "RESUME_DOCUMENT_V1";

    @Schema(description = "文档结构版本", example = "RESUME_DOCUMENT_V1")
    private String schemaVersion;

    @Schema(description = "基础信息")
    private ResumeDocumentBasicsDTO basics;

    @Schema(description = "简历章节列表，顺序即展示顺序")
    private List<ResumeDocumentSectionDTO> sections;
}
