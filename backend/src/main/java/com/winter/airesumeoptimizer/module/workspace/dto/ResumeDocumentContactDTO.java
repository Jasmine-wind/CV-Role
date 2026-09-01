package com.winter.airesumeoptimizer.module.workspace.dto;

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
@Schema(description = "简历联系方式，必须携带显式类型")
public class ResumeDocumentContactDTO {

    @Schema(description = "字段稳定 ID", example = "c-1")
    private String id;

    @Schema(description = "联系方式类型", example = "PHONE")
    private String type;

    @Schema(description = "展示名，缺省时由类型派生", example = "电话")
    private String label;

    @Schema(description = "字段值", example = "13800000000")
    private String value;
}
