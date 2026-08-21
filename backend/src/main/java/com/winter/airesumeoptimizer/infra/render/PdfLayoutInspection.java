package com.winter.airesumeoptimizer.infra.render;

/**
 * PDF 轻量排版检查结果。
 *
 * <p>overflowDetected 的冻结边界是：至少一个已渲染文字 glyph 的边界框超出所属页面
 * CropBox（无 CropBox 时使用 MediaBox）1pt 以上。它不声称检测视觉重叠或审美问题。
 */
public record PdfLayoutInspection(int pageCount, boolean overflowDetected) {
}
