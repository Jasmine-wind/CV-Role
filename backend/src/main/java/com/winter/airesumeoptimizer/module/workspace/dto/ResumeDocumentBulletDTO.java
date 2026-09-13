package com.winter.airesumeoptimizer.module.workspace.dto;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
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
@Schema(description = "简历条目要点")
public class ResumeDocumentBulletDTO {

    @Schema(description = "要点来源引用，仅用于追溯")
    private ResumeSourceRefDTO sourceRef;

    @Schema(description = "要点覆盖的来源 occurrence ID")
    private List<String> sourceOccurrenceIds;

    @Schema(description = "要点稳定 ID", example = "b-1")
    private String id;

    @Schema(description = "要点内容")
    private String text;
}
