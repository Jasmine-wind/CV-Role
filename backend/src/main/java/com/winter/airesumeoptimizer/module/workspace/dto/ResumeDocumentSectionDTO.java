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
@Schema(description = "简历章节")
public class ResumeDocumentSectionDTO {

    @Schema(description = "章节稳定 ID", example = "s-1")
    private String id;

    @Schema(description = "章节类型", example = "EXPERIENCE")
    private String kind;

    @Schema(description = "章节标题", example = "工作经历")
    private String title;

    @Schema(description = "章节条目列表，顺序即展示顺序")
    private List<ResumeDocumentEntryDTO> entries;
}
