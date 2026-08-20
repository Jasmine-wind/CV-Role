package com.winter.airesumeoptimizer.module.workspace.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "工作区内容保存请求，基于客户端已知的内容版本号做乐观并发控制")
public class WorkspaceContentSaveRequestDTO {

    @NotNull(message = "缺少内容版本号")
    @PositiveOrZero(message = "内容版本号不能为负数")
    @Schema(description = "客户端当前草稿基于的服务端内容版本号", example = "3")
    private Long expectedRevision;

    @NotNull(message = "简历内容不能为空")
    @Valid
    @Schema(description = "完整结构化简历文档，服务端整体替换当前岗位版本内容")
    private ResumeDocumentDTO document;
}
