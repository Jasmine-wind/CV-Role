package com.winter.airesumeoptimizer.module.resume.dto;

import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 未决候选项处理请求。用户只面对 canonical 层的候选内容；
 * 接受时可携带编辑后的值与归属章节，删除即放弃该内容。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "未决候选项处理请求")
public class ResumeReviewResolveRequestDTO {

    public static final String ACTION_ACCEPT = "ACCEPT";
    public static final String ACTION_DELETE = "DELETE";

    @Schema(description = "未决候选项 ID", example = "u-1")
    @NotBlank(message = "缺少候选项 ID")
    private String itemId;

    @Schema(description = "处理动作", example = "ACCEPT")
    @NotBlank(message = "缺少处理动作")
    private String action;

    @Schema(description = "编辑后的姓名，可空")
    private String name;

    @Schema(description = "联系方式类型（仅联系方式候选）", example = "PHONE")
    private String contactType;

    @Schema(description = "联系方式展示名（仅联系方式候选）")
    private String contactLabel;

    @Schema(description = "编辑后的联系方式值（仅联系方式候选）")
    private String contactValue;

    @Schema(description = "编辑后的文本内容（仅游离文本候选）")
    private String text;

    @Schema(description = "归属章节 ID；缺省时按候选类型进入对应章节，游离文本创建补充章节")
    private String targetSectionId;

    @Schema(description = "编辑后的条目字段（仅条目候选）")
    private ResumeDocumentEntryDTO entry;
}
