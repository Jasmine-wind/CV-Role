package com.winter.airesumeoptimizer.module.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 确定性验证发现的单个问题；code 为稳定机器码，前端负责文案。 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "简历文档质量检查项")
public class ResumeQualityIssueDTO {

    public static final String SEVERITY_BLOCKER = "BLOCKER";
    public static final String SEVERITY_WARNING = "WARNING";

    @Schema(description = "问题机器码", example = "MISSING_REACHABLE_CONTACT")
    private String code;

    @Schema(description = "严重程度", example = "BLOCKER")
    private String severity;

    @Schema(description = "服务端说明，仅供排查，不作为用户文案")
    private String message;
}
