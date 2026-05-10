package com.winter.airesumeoptimizer.module.job.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "岗位详情")
public class JobDetailVO {

    @Schema(description = "岗位 ID", example = "1")
    private Long id;
    @Schema(description = "岗位标题", example = "Java 后端开发工程师")
    private String title;
    @Schema(description = "公司名称", example = "Demo Inc.")
    private String companyName;
    @Schema(description = "岗位分类", example = "后端开发")
    private String jobCategory;
    @Schema(description = "工作地点", example = "成都")
    private String location;
    @Schema(description = "岗位描述")
    private String description;
    @Schema(description = "岗位要求")
    private String requirements;
    @Schema(description = "岗位技能关键词")
    private List<String> requiredSkills;
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
