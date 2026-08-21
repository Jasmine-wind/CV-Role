package com.winter.airesumeoptimizer.infra.render;

/** 最终 PDF 字节及从该 PDF 实际解析出的排版检查结果。 */
public record ResumePdfRenderResult(byte[] pdf, PdfLayoutInspection layout) {
}
