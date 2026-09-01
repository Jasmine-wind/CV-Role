package com.winter.airesumeoptimizer.module.export.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.render.ResumePdfRenderer;
import com.winter.airesumeoptimizer.infra.render.ResumePdfRenderResult;
import com.winter.airesumeoptimizer.infra.render.ResumeRenderException;
import com.winter.airesumeoptimizer.infra.render.ResumeTemplateId;
import com.winter.airesumeoptimizer.infra.storage.FileStorageException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.infra.storage.StoreFileCommand;
import com.winter.airesumeoptimizer.infra.storage.StoredFile;
import com.winter.airesumeoptimizer.module.export.entity.ExportArtifact;
import com.winter.airesumeoptimizer.module.export.dto.WorkspaceExportRequestDTO;
import com.winter.airesumeoptimizer.module.export.mapper.ExportArtifactMapper;
import com.winter.airesumeoptimizer.module.export.service.ArtifactDownload;
import com.winter.airesumeoptimizer.module.export.service.ExportArtifactCleanupService;
import com.winter.airesumeoptimizer.module.export.service.ExportDocumentGate;
import com.winter.airesumeoptimizer.module.export.service.ExportPreflight;
import com.winter.airesumeoptimizer.module.export.service.ExportPreflightChecker;
import com.winter.airesumeoptimizer.module.export.service.PreviewReceiptClaims;
import com.winter.airesumeoptimizer.module.export.service.RenderedPdf;
import com.winter.airesumeoptimizer.module.export.service.WorkspaceExportService;
import com.winter.airesumeoptimizer.module.export.vo.ExportArtifactVO;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.workspace.service.WorkspaceContentService;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentVO;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Preview / Export 编排：数据源固定为 optimizationTaskId → TARGET structured_content，
 * 渲染同步完成，失败只返回错误，不触碰 Workspace 已保存内容。
 */
@Slf4j
@Service
public class WorkspaceExportServiceImpl implements WorkspaceExportService {

    private static final String MIME_PDF = "application/pdf";
    private static final String STORAGE_BIZ_TYPE = "exports";
    private static final String ARTIFACT_READY = "READY";

    private final WorkspaceContentService workspaceContentService;
    private final ResumePdfRenderer resumePdfRenderer;
    private final FileStorageService fileStorageService;
    private final ExportArtifactMapper exportArtifactMapper;
    private final OptimizationTaskMapper optimizationTaskMapper;
    private final ExportPreflightChecker preflightChecker;
    private final ExportDocumentGate exportDocumentGate;
    private final PreviewReceiptService previewReceiptService;
    private final ExportArtifactCleanupService exportArtifactCleanupService;
    private final TransactionTemplate transactionTemplate;

    public WorkspaceExportServiceImpl(
            WorkspaceContentService workspaceContentService,
            ResumePdfRenderer resumePdfRenderer,
            FileStorageService fileStorageService,
            ExportArtifactMapper exportArtifactMapper,
            OptimizationTaskMapper optimizationTaskMapper,
            ExportPreflightChecker preflightChecker,
            ExportDocumentGate exportDocumentGate,
            PreviewReceiptService previewReceiptService,
            ExportArtifactCleanupService exportArtifactCleanupService,
            TransactionTemplate transactionTemplate) {
        this.workspaceContentService = workspaceContentService;
        this.resumePdfRenderer = resumePdfRenderer;
        this.fileStorageService = fileStorageService;
        this.exportArtifactMapper = exportArtifactMapper;
        this.optimizationTaskMapper = optimizationTaskMapper;
        this.preflightChecker = preflightChecker;
        this.exportDocumentGate = exportDocumentGate;
        this.previewReceiptService = previewReceiptService;
        this.exportArtifactCleanupService = exportArtifactCleanupService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public RenderedPdf preview(Long userId, Long optimizationTaskId, String templateId, Long expectedRevision) {
        ResumeTemplateId template = resolveTemplate(templateId);
        WorkspaceContentVO content = currentSavedContent(userId, optimizationTaskId, expectedRevision);
        OptimizationTask task = getOwnedTask(userId, content.getOptimizationTaskId());
        long targetVersionId = resolveTargetVersionId(userId, content.getOptimizationTaskId());
        // Document Quality Gate：预览是审查工具，仅在无法形成文档时阻断；
        // 待确认/内容阻断项允许预览并以响应头透传，正式导出仍被拒绝。
        ExportDocumentGate.GateResult gate = exportDocumentGate.check(userId, task, content.getDocument());
        if (gate.blocked() && !gate.needsReview() && isUnrenderable(gate.blockCode())) {
            throw new BusinessException(409, gate.blockCode() + "：简历内容当前不可预览");
        }
        ResumePdfRenderResult rendered = render(content, template);
        // Any non-rendering Document Gate blocker is still previewable for diagnosis, but
        // must be surfaced as a non-exportable preflight result to the client.
        ExportPreflight preflight = preflightChecker.check(
                content.getDocument(), rendered.layout(), gate.blocked() || gate.needsReview());
        String checksum = sha256Hex(rendered.pdf());
        String receipt = previewReceiptService.issue(new PreviewReceiptClaims(
                userId,
                content.getOptimizationTaskId(),
                targetVersionId,
                content.getRevision(),
                template.getTemplateId(),
                template.getTemplateVersion(),
                ResumePdfRenderer.RENDERER_VERSION,
                checksum));
        return new RenderedPdf(
                rendered.pdf(), content.getRevision(), targetVersionId, template, preflight, receipt);
    }

    @Override
    public ExportArtifactVO export(Long userId, Long optimizationTaskId, WorkspaceExportRequestDTO request) {
        if (request == null || request.getExpectedRevision() == null) {
            throw new BusinessException(400, "缺少内容版本号");
        }
        ResumeTemplateId template = resolveTemplate(request.getTemplateId());
        WorkspaceContentVO content = currentSavedContent(userId, optimizationTaskId, request.getExpectedRevision());
        // 在产生编译/存储副作用前先过 Document Quality Gate：不可信内容禁止正式导出。
        OptimizationTask task = getOwnedTask(userId, content.getOptimizationTaskId());
        ExportDocumentGate.GateResult gate = exportDocumentGate.check(userId, task, content.getDocument());
        if (gate.blocked()) {
            throw new BusinessException(409, gate.blockCode() + "：简历内容未通过质量检查，不能导出");
        }
        // 在产生编译/存储副作用前冻结 TARGET 关系并验证服务端签名 Preview receipt。
        Long targetVersionId = resolveTargetVersionId(userId, content.getOptimizationTaskId());
        PreviewReceiptClaims receipt = verifyReceipt(request.getPreviewReceipt());
        validateReceiptBinding(receipt, userId, content, targetVersionId, template);

        ResumePdfRenderResult rendered = render(content, template);
        byte[] pdf = rendered.pdf();
        String checksum = sha256Hex(pdf);
        if (!checksum.equals(receipt.pdfChecksum())) {
            throw new BusinessException(409, "预览结果已失效，请重新预览后导出");
        }
        ExportPreflight preflight = preflightChecker.check(
                content.getDocument(), rendered.layout(), gate.blocked() || gate.needsReview());
        // PDF Quality Gate：越界、孤立末页和不可读字号属于不可交付排版，正式导出阻断；预览阶段仅作告警。
        if (preflight.overflowDetected()) {
            throw new BusinessException(409, "CONTENT_OUT_OF_PAGE_BOUNDS：排版存在文字越界，请调整后重新预览");
        }
        if (preflight.orphanFinalPage()) {
            throw new BusinessException(409, "ORPHAN_FINAL_PAGE：末页内容过少，请调整后重新预览");
        }
        if (preflight.readabilityTooSmall()) {
            throw new BusinessException(409, "READABILITY_TOO_SMALL：字号过小，请调整模板或内容后重新预览");
        }
        String fileName = artifactFileName(content, template);

        StoredFile stored;
        try {
            stored = fileStorageService.store(new StoreFileCommand(
                    userId, fileName, MIME_PDF, pdf.length, new ByteArrayInputStream(pdf), STORAGE_BIZ_TYPE));
        } catch (FileStorageException exception) {
            log.warn("导出 PDF 存储失败: taskId={}", optimizationTaskId, exception);
            throw new BusinessException(500, "简历导出失败，请稍后重试");
        }

        ExportArtifact artifact = new ExportArtifact();
        artifact.setUserId(userId);
        artifact.setOptimizationTaskId(content.getOptimizationTaskId());
        artifact.setTargetResumeVersionId(targetVersionId);
        artifact.setContentRevision(content.getRevision());
        artifact.setTemplateId(template.getTemplateId());
        artifact.setTemplateVersion(template.getTemplateVersion());
        artifact.setRendererVersion(ResumePdfRenderer.RENDERER_VERSION);
        artifact.setStorageKey(stored.storageKey());
        artifact.setMimeType(MIME_PDF);
        artifact.setFileSize((long) pdf.length);
        artifact.setChecksumSha256(checksum);
        artifact.setStatus(ARTIFACT_READY);
        artifact.setPageCount(preflight.pageCount());
        artifact.setMissingContact(preflight.missingContact());
        artifact.setPageLimitExceeded(preflight.pageLimitExceeded());
        artifact.setOverflowDetected(preflight.overflowDetected());
        artifact.setReadabilityTooSmall(preflight.readabilityTooSmall());
        artifact.setDocumentGateStatus(gate.status());
        artifact.setOrphanFinalPage(preflight.orphanFinalPage());
        artifact.setCreatedAt(LocalDateTime.now());

        try {
            // 独立事务保证“插入已提交”才继续；提交失败时补偿删除存储对象，不留孤儿文件。
            transactionTemplate.executeWithoutResult(status -> {
                int rows = exportArtifactMapper.insert(artifact);
                if (rows != 1) {
                    throw new IllegalStateException("导出记录写入行数不正确");
                }
            });
        } catch (RuntimeException exception) {
            compensateStorage(stored.storageKey(), optimizationTaskId);
            log.error("导出记录写入失败，已补偿删除存储对象: taskId={}", optimizationTaskId, exception);
            throw new BusinessException(500, "简历导出失败，请稍后重试");
        }
        return toVO(artifact, fileName);
    }

    @Override
    public List<ExportArtifactVO> listArtifacts(Long userId, Long optimizationTaskId) {
        validateUserId(userId);
        validateTaskId(optimizationTaskId);
        return exportArtifactMapper.selectList(new LambdaQueryWrapper<ExportArtifact>()
                        .eq(ExportArtifact::getUserId, userId)
                        .eq(ExportArtifact::getOptimizationTaskId, optimizationTaskId)
                        .orderByDesc(ExportArtifact::getId))
                .stream()
                .map(artifact -> toVO(artifact, artifactFileName(artifact)))
                .toList();
    }

    @Override
    public ArtifactDownload loadArtifact(Long userId, Long artifactId) {
        ExportArtifact artifact = ownedArtifact(userId, artifactId);
        if (!ARTIFACT_READY.equals(artifact.getStatus())) {
            throw new BusinessException(409, "导出文件正在删除，请重试删除操作");
        }
        byte[] pdf;
        try {
            pdf = fileStorageService.loadAsBytes(artifact.getStorageKey());
        } catch (FileStorageException exception) {
            log.warn("导出文件读取失败: artifactId={}", artifactId, exception);
            throw new BusinessException(500, "导出文件读取失败，请稍后重试");
        }
        // 读取时复核校验和：存储对象被意外篡改或替换时 fail closed，不下发错误内容。
        if (!sha256Hex(pdf).equals(artifact.getChecksumSha256())) {
            log.error("导出文件校验和不一致: artifactId={}", artifactId);
            throw new BusinessException(500, "导出文件校验失败，请重新导出");
        }
        return new ArtifactDownload(pdf, artifactFileName(artifact), artifact.getMimeType());
    }

    @Override
    public void deleteArtifact(Long userId, Long artifactId) {
        exportArtifactCleanupService.deleteArtifact(userId, artifactId);
    }

    /**
     * 读取服务端已保存的 TARGET 内容并校验 revision 一致。
     * Workspace seam 内部完成任务归属、版本链与内容状态校验；此处只拒绝过期渲染。
     */
    private WorkspaceContentVO currentSavedContent(Long userId, Long optimizationTaskId, Long expectedRevision) {
        if (expectedRevision == null || expectedRevision < 0) {
            throw new BusinessException(400, "缺少内容版本号");
        }
        WorkspaceContentVO content = workspaceContentService.getPersistedContentForRender(userId, optimizationTaskId);
        if (!expectedRevision.equals(content.getRevision())) {
            // 旧 Preview 在 revision 变化后必须失效；不允许静默渲染旧版本。
            throw new BusinessException(409, "简历内容已更新，预览已失效，请刷新后重试");
        }
        return content;
    }

    private boolean isUnrenderable(String blockCode) {
        return ExportDocumentGate.CODE_RESUME_PARSE_PENDING.equals(blockCode)
                || ExportDocumentGate.CODE_RESUME_QUALITY_FAILED.equals(blockCode);
    }

    private OptimizationTask getOwnedTask(Long userId, Long optimizationTaskId) {
        OptimizationTask task = optimizationTaskMapper.selectOne(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getId, optimizationTaskId)
                .eq(OptimizationTask::getUserId, userId));
        if (task == null) {
            throw new BusinessException(404, "优化任务不存在");
        }
        return task;
    }

    private ResumePdfRenderResult render(WorkspaceContentVO content, ResumeTemplateId template) {
        try {
            return resumePdfRenderer.render(content.getDocument(), template);
        } catch (ResumeRenderException exception) {
            throw new BusinessException(500, exception.getMessage());
        }
    }

    private PreviewReceiptClaims verifyReceipt(String receipt) {
        try {
            return previewReceiptService.verify(receipt);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(409, "预览凭证无效或已过期，请重新预览");
        }
    }

    private void validateReceiptBinding(
            PreviewReceiptClaims receipt,
            Long userId,
            WorkspaceContentVO content,
            Long targetVersionId,
            ResumeTemplateId template) {
        if (receipt.userId() != userId
                || receipt.optimizationTaskId() != content.getOptimizationTaskId()
                || receipt.targetResumeVersionId() != targetVersionId
                || receipt.contentRevision() != content.getRevision()
                || !receipt.templateId().equals(template.getTemplateId())
                || !receipt.templateVersion().equals(template.getTemplateVersion())
                || !receipt.rendererVersion().equals(ResumePdfRenderer.RENDERER_VERSION)) {
            throw new BusinessException(409, "预览与当前任务、内容或模板不一致，请重新预览");
        }
    }

    private Long resolveTargetVersionId(Long userId, Long optimizationTaskId) {
        // currentSavedContent 已校验任务归属；这里只为记录导出物与 TARGET 版本的正式关系。
        OptimizationTask task = optimizationTaskMapper.selectOne(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getId, optimizationTaskId)
                .eq(OptimizationTask::getUserId, userId));
        if (task == null || task.getTargetResumeVersionId() == null) {
            throw new BusinessException(500, "优化任务的简历版本关系不一致");
        }
        return task.getTargetResumeVersionId();
    }

    private ResumeTemplateId resolveTemplate(String templateId) {
        ResumeTemplateId template = ResumeTemplateId.fromValue(templateId);
        if (template == null) {
            throw new BusinessException(400, "不支持的简历模板");
        }
        return template;
    }

    private ExportArtifact ownedArtifact(Long userId, Long artifactId) {
        validateUserId(userId);
        if (artifactId == null || artifactId <= 0) {
            throw new BusinessException(400, "导出文件 ID 必须大于 0");
        }
        ExportArtifact artifact = exportArtifactMapper.selectOne(new LambdaQueryWrapper<ExportArtifact>()
                .eq(ExportArtifact::getId, artifactId)
                .eq(ExportArtifact::getUserId, userId));
        if (artifact == null) {
            throw new BusinessException(404, "导出文件不存在");
        }
        return artifact;
    }

    private void compensateStorage(String storageKey, Long optimizationTaskId) {
        try {
            fileStorageService.delete(storageKey);
        } catch (FileStorageException exception) {
            log.error("导出补偿删除存储对象失败，需要人工清理: taskId={}", optimizationTaskId, exception);
        }
    }

    private String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private String artifactFileName(WorkspaceContentVO content, ResumeTemplateId template) {
        String base = "resume";
        if (content.getDocument() != null
                && content.getDocument().getBasics() != null
                && content.getDocument().getBasics().getName() != null
                && !content.getDocument().getBasics().getName().isBlank()) {
            base = content.getDocument().getBasics().getName().strip();
        }
        return sanitize(base) + "-" + template.getTemplateId() + ".pdf";
    }

    private String artifactFileName(ExportArtifact artifact) {
        return sanitize("resume-" + artifact.getTemplateId() + "-" + artifact.getId()) + ".pdf";
    }

    private String sanitize(String value) {
        String clean = value.replaceAll("[^\\p{L}\\p{N}._-]", "_").replaceAll("_+", "_");
        return clean.isBlank() ? "resume" : clean;
    }

    private ExportArtifactVO toVO(ExportArtifact artifact, String fileName) {
        return ExportArtifactVO.builder()
                .id(artifact.getId())
                .optimizationTaskId(artifact.getOptimizationTaskId())
                .templateId(artifact.getTemplateId())
                .templateVersion(artifact.getTemplateVersion())
                .rendererVersion(artifact.getRendererVersion())
                .contentRevision(artifact.getContentRevision())
                .mimeType(artifact.getMimeType())
                .fileSize(artifact.getFileSize())
                .checksum(artifact.getChecksumSha256())
                .status(artifact.getStatus())
                .pageCount(artifact.getPageCount())
                .missingContact(artifact.getMissingContact())
                .pageLimitExceeded(artifact.getPageLimitExceeded())
                .overflowDetected(artifact.getOverflowDetected())
                .documentGateStatus(artifact.getDocumentGateStatus())
                .orphanFinalPage(artifact.getOrphanFinalPage())
                .readabilityTooSmall(artifact.getReadabilityTooSmall())
                .fileName(fileName)
                .createdAt(artifact.getCreatedAt())
                .build();
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
    }

    private void validateTaskId(Long optimizationTaskId) {
        if (optimizationTaskId == null || optimizationTaskId <= 0) {
            throw new BusinessException(400, "优化任务 ID 必须大于 0");
        }
    }
}
