package com.winter.airesumeoptimizer.module.workspace.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 简历章节条目。Slice A 起按章节语义携带结构化字段：
 * 工作/项目经历使用 organization/role，教育经历使用 school/degree/major，
 * 日期一律保留原文字符串，不解析、不猜测。
 * SUMMARY/ACHIEVEMENT/CERTIFICATE/OTHER/CUSTOM 等通用章节仅使用 bullets。
 * 技能组条目使用 group + skillItems，不再伪装成普通 bullet。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "简历章节条目，如一段经历、一个教育条目或一个技能组")
public class ResumeDocumentEntryDTO {

    @Schema(description = "条目稳定 ID", example = "e-1")
    private String id;

    /** V1 generic fields are read-only compatibility input and are never persisted by Slice A. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(hidden = true)
    private String heading;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(hidden = true)
    private String meta;

    @Schema(description = "公司或机构名", example = "某科技有限公司")
    private String organization;

    @Schema(description = "职位或角色", example = "Java 后端工程师")
    private String role;

    @Schema(description = "学校名", example = "某大学")
    private String school;

    @Schema(description = "学历或学位", example = "本科")
    private String degree;

    @Schema(description = "专业", example = "计算机科学与技术")
    private String major;

    @Schema(description = "开始时间，原文字符串", example = "2022.07")
    private String startDate;

    @Schema(description = "结束时间，原文字符串", example = "至今")
    private String endDate;

    @Schema(description = "地点", example = "上海")
    private String location;

    @Schema(description = "技能组名称", example = "后端技术")
    private String group;

    @Schema(description = "技能组条目列表")
    private List<String> skillItems;

    @Schema(description = "条目要点列表，顺序即展示顺序")
    private List<ResumeDocumentBulletDTO> bullets;
}
