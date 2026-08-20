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
@Schema(description = "简历章节条目，如一段经历、一个项目或一条技能")
public class ResumeDocumentEntryDTO {

    @Schema(description = "条目稳定 ID", example = "e-1")
    private String id;

    @Schema(description = "条目标题，如公司、学校或项目名", example = "某科技有限公司")
    private String heading;

    @Schema(description = "条目辅助信息，如时间范围或职位", example = "2022.07 - 至今 · Java 后端")
    private String meta;

    @Schema(description = "条目要点列表，顺序即展示顺序")
    private List<ResumeDocumentBulletDTO> bullets;
}
