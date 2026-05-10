package com.winter.airesumeoptimizer.module.resume.dto;

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
@Schema(description = "简历结构化内容")
public class ResumeStructuredContentDTO {

    @Schema(description = "姓名", example = "张三")
    private String name;

    @Schema(description = "手机号", example = "13800000000")
    private String phone;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "教育经历")
    private List<String> education;

    @Schema(description = "技能列表")
    private List<String> skills;

    @Schema(description = "项目经历")
    private List<String> projects;

    @Schema(description = "实习经历")
    private List<String> internships;

    @Schema(description = "原始简历文本")
    private String rawText;
}
