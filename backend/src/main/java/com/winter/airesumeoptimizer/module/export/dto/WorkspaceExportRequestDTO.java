package com.winter.airesumeoptimizer.module.export.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "岗位版本 PDF 导出请求")
public class WorkspaceExportRequestDTO {

    @Schema(description = "内置模板：classic / modern / minimal", example = "classic")
    @Size(max = 30, message = "模板标识过长")
    private String templateId;

    @Schema(description = "发起导出时前端持有的内容版本号；与服务端不一致时拒绝导出")
    @NotNull(message = "缺少内容版本号")
    @PositiveOrZero(message = "内容版本号不能为负数")
    private Long expectedRevision;

    @Schema(description = "最近一次成功 Preview 返回的服务端签名 receipt")
    @NotBlank(message = "请先预览当前简历")
    @Size(max = 2000, message = "预览凭证过长")
    private String previewReceipt;
}
