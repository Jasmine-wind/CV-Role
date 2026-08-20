package com.winter.airesumeoptimizer.module.workspace.vo;

import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "工作区内容保存结果")
public class WorkspaceContentSaveResultVO {

    @Schema(description = "是否保存成功", example = "true")
    private boolean saved;

    @Schema(description = "是否因版本号不一致产生冲突；冲突时本地草稿应保留并停止盲目重试", example = "false")
    private boolean conflict;

    @Schema(description = "保存成功时为新的内容版本号；冲突时为服务端当前版本号", example = "4")
    private Long revision;

    @Schema(description = "保存成功时为服务端实际持久化的规范文档；冲突时为空")
    private ResumeDocumentDTO document;
}
