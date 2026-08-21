package com.winter.airesumeoptimizer.module.export.service;

/** 服务端签名 Preview receipt 的冻结绑定字段。 */
public record PreviewReceiptClaims(
        long userId,
        long optimizationTaskId,
        long targetResumeVersionId,
        long contentRevision,
        String templateId,
        String templateVersion,
        String rendererVersion,
        String pdfChecksum) {
}
