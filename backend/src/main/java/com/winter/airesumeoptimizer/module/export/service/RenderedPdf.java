package com.winter.airesumeoptimizer.module.export.service;

import com.winter.airesumeoptimizer.infra.render.ResumeTemplateId;

/**
 * 一次成功 Preview 的 PDF、真实 preflight 结果与服务端签名 receipt。
 * Export 必须提交该 receipt 并重新验证完整绑定及 PDF checksum。
 */
public record RenderedPdf(
        byte[] pdf,
        long revision,
        long targetResumeVersionId,
        ResumeTemplateId template,
        ExportPreflight preflight,
        String previewReceipt) {
}
