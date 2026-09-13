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
@Schema(description = "简历基础信息")
public class ResumeDocumentBasicsDTO {

    @Schema(description = "基础信息来源引用，仅用于追溯")
    private ResumeSourceRefDTO sourceRef;

    @Schema(description = "基础信息覆盖的来源 occurrence ID")
    private List<String> sourceOccurrenceIds;

    /** Field name → source reference; values are parser evidence, not editor authority. */
    @Schema(description = "基础信息字段级来源引用")
    private Map<String, ResumeSourceRefDTO> fieldSourceRefs;

    @Schema(description = "姓名", example = "张三")
    private String name;

    @Schema(description = "求职意向", example = "Java 后端开发工程师")
    private String jobIntention;

    @Schema(description = "最高学历", example = "本科")
    private String highestEducation;

    @Schema(description = "联系方式等基础字段")
    private List<ResumeDocumentContactDTO> contacts;
}
