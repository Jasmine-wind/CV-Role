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
@Schema(description = "简历基础信息字段")
public class ResumeDocumentContactDTO {

    @Schema(description = "字段稳定 ID", example = "c-1")
    private String id;

    @Schema(description = "字段名", example = "电话")
    private String label;

    @Schema(description = "字段值", example = "13800000000")
    private String value;
}
