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
@Schema(description = "简历基础信息")
public class ResumeDocumentBasicsDTO {

    @Schema(description = "姓名", example = "张三")
    private String name;

    @Schema(description = "联系方式等基础字段")
    private List<ResumeDocumentContactDTO> contacts;
}
