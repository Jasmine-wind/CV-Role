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
@Schema(description = "简历条目要点")
public class ResumeDocumentBulletDTO {

    @Schema(description = "要点稳定 ID", example = "b-1")
    private String id;

    @Schema(description = "要点内容")
    private String text;
}
