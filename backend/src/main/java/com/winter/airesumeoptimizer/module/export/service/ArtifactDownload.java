package com.winter.airesumeoptimizer.module.export.service;

/** 导出物下载载荷：PDF 字节与经过清洗的下载文件名。 */
public record ArtifactDownload(byte[] pdf, String fileName, String mimeType) {
}
