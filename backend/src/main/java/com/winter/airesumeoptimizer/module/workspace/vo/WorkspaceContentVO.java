package com.winter.airesumeoptimizer.module.workspace.vo;

import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "工作区当前内容，只来自当前优化任务的岗位版本")
public class WorkspaceContentVO {

    @Schema(description = "优化任务 ID", example = "1")
    private Long optimizationTaskId;

    @Schema(description = "服务端当前内容版本号，保存时需原样带回", example = "0")
    private Long revision;

    @Schema(description = "结构化简历文档")
    private ResumeDocumentDTO document;
}
