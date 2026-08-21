package com.winter.airesumeoptimizer.module.export.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.export.dto.WorkspaceExportRequestDTO;
import com.winter.airesumeoptimizer.module.export.service.ArtifactDownload;
import com.winter.airesumeoptimizer.module.export.service.RenderedPdf;
import com.winter.airesumeoptimizer.module.export.service.WorkspaceExportService;
import com.winter.airesumeoptimizer.module.export.vo.ExportArtifactVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Workspace Preview / Export 入口。全部端点要求 JWT 认证并按 current_user + resource_id
 * 校验归属；二进制响应只输出 PDF 字节，不返回 Typst 源码、存储路径或内部堆栈。
 */
@RestController
@RequestMapping("/api/workspace")
@Validated
@Tag(name = "Workspace Export", description = "岗位版本简历 PDF 预览、导出与导出物管理")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceExportController {

    private final WorkspaceExportService workspaceExportService;

    public WorkspaceExportController(WorkspaceExportService workspaceExportService) {
        this.workspaceExportService = workspaceExportService;
    }

    @GetMapping("/{optimizationTaskId}/preview.pdf")
    @Operation(summary = "PDF 预览", description = "同步渲染服务端已保存的岗位版本内容；expectedRevision 不一致时拒绝，防止渲染过期版本")
    public ResponseEntity<byte[]> preview(
            @PathVariable @Positive(message = "优化任务 ID 必须大于 0") Long optimizationTaskId,
            @RequestParam(required = false, defaultValue = "classic") String templateId,
            @RequestParam Long expectedRevision,
            Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        RenderedPdf rendered = workspaceExportService.preview(
                user.getUserId(), optimizationTaskId, templateId, expectedRevision);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"resume-preview.pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Revision", Long.toString(rendered.revision()))
                .header("X-Target-Resume-Version", Long.toString(rendered.targetResumeVersionId()))
                .header("X-Template-Version", rendered.template().getTemplateVersion())
                .header("X-Renderer-Version", com.winter.airesumeoptimizer.infra.render.ResumePdfRenderer.RENDERER_VERSION)
                .header("X-Resume-Page-Count", Integer.toString(rendered.preflight().pageCount()))
                .header("X-Resume-Missing-Contact", Boolean.toString(rendered.preflight().missingContact()))
                .header("X-Resume-Page-Limit-Exceeded", Boolean.toString(rendered.preflight().pageLimitExceeded()))
                .header("X-Resume-Overflow-Detected", Boolean.toString(rendered.preflight().overflowDetected()))
                .header("X-Preview-Receipt", rendered.previewReceipt())
                .body(rendered.pdf());
    }

    @PostMapping("/{optimizationTaskId}/export")
    @Operation(summary = "导出 PDF", description = "验证最近 Preview receipt 后，编译、存储与数据库记录全部成功才返回导出物")
    public Result<ExportArtifactVO> export(
            @PathVariable @Positive(message = "优化任务 ID 必须大于 0") Long optimizationTaskId,
            @Valid @RequestBody WorkspaceExportRequestDTO request,
            Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(workspaceExportService.export(user.getUserId(), optimizationTaskId, request));
    }

    @GetMapping("/{optimizationTaskId}/artifacts")
    @Operation(summary = "列出导出物", description = "只返回当前用户在该优化任务下成功生成的导出物")
    public Result<List<ExportArtifactVO>> listArtifacts(
            @PathVariable @Positive(message = "优化任务 ID 必须大于 0") Long optimizationTaskId,
            Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(workspaceExportService.listArtifacts(user.getUserId(), optimizationTaskId));
    }

    @GetMapping("/artifacts/{artifactId}/download")
    @Operation(summary = "下载导出物", description = "校验用户归属并复核内容校验和后返回 PDF")
    public ResponseEntity<byte[]> downloadArtifact(
            @PathVariable @Positive(message = "导出文件 ID 必须大于 0") Long artifactId,
            Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        ArtifactDownload download = workspaceExportService.loadArtifact(user.getUserId(), artifactId);
        String encodedName = URLEncoder.encode(download.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + download.fileName() + "\"; filename*=UTF-8''" + encodedName)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(download.pdf());
    }

    @DeleteMapping("/artifacts/{artifactId}")
    @Operation(summary = "删除导出物", description = "移除数据库记录并尽力删除存储对象")
    public Result<Void> deleteArtifact(
            @PathVariable @Positive(message = "导出文件 ID 必须大于 0") Long artifactId,
            Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        workspaceExportService.deleteArtifact(user.getUserId(), artifactId);
        return Result.success(null);
    }
}
