package com.winter.airesumeoptimizer.module.export.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 导出物对外视图。storage_key 等内部存储位置不进入响应，避免暴露存储路径。
 */
@Getter
@Builder
@Schema(description = "成功生成的 PDF 导出物")
public class ExportArtifactVO {

    @Schema(description = "导出物 ID")
    private final Long id;

    @Schema(description = "所属优化任务 ID")
    private final Long optimizationTaskId;

    @Schema(description = "渲染所用模板")
    private final String templateId;

    @Schema(description = "模板版本")
    private final String templateVersion;

    @Schema(description = "渲染器版本")
    private final String rendererVersion;

    @Schema(description = "渲染时的 TARGET 内容版本号")
    private final Long contentRevision;

    @Schema(description = "文件 MIME 类型")
    private final String mimeType;

    @Schema(description = "文件大小（字节）")
    private final Long fileSize;

    @Schema(description = "PDF 内容 SHA-256")
    private final String checksum;

    @Schema(description = "导出物状态：READY / DELETE_PENDING")
    private final String status;

    @Schema(description = "实际 PDF 页数")
    private final Integer pageCount;

    @Schema(description = "是否缺少联系方式")
    private final Boolean missingContact;

    @Schema(description = "是否超过建议的两页")
    private final Boolean pageLimitExceeded;

    @Schema(description = "是否检测到文字 glyph 超出页面 CropBox")
    private final Boolean overflowDetected;

    @Schema(description = "导出时刻的文档质量门结果；历史导出物为空")
    private final String documentGateStatus;

    @Schema(description = "导出时刻是否检出孤立末页；历史导出物为空")
    private final Boolean orphanFinalPage;

    @Schema(description = "导出时刻是否低于最低可读字号；历史导出物为空")
    private final Boolean readabilityTooSmall;

    @Schema(description = "建议下载文件名")
    private final String fileName;

    @Schema(description = "生成时间")
    private final LocalDateTime createdAt;
}
