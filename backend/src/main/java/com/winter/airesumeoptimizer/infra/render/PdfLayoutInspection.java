package com.winter.airesumeoptimizer.infra.render;

/**
 * PDF 轻量排版检查结果。
 *
 * <p>overflowDetected 的冻结边界是：至少一个已渲染文字 glyph 的边界框超出所属页面
 * CropBox（无 CropBox 时使用 MediaBox）1pt 以上。它不声称检测视觉重叠或审美问题。
 *
 * <p>finalPageLineCount 是末页非空文本行数。finalPageContentRatio 是末页文字 glyph
 * 垂直包围盒高度占页面高度的比例；两者结合用于识别“末页有文字但只剩很少尾部”的
 * 稀疏分页。比例为负数表示调用方使用了旧兼容构造，未提供该事实。
 */
public record PdfLayoutInspection(
        int pageCount,
        boolean overflowDetected,
        int finalPageLineCount,
        float minimumFontSizeInPt,
        float finalPageContentRatio) {

    /** Slice A 之前的双参构造兼容入口。 */
    public PdfLayoutInspection(int pageCount, boolean overflowDetected) {
        this(pageCount, overflowDetected, 0, 0.0f, -1.0f);
    }

    /** 末页检查加入前的三参构造兼容入口。 */
    public PdfLayoutInspection(int pageCount, boolean overflowDetected, int finalPageLineCount) {
        this(pageCount, overflowDetected, finalPageLineCount, 0.0f, -1.0f);
    }

    /** 字号检查加入前的四参构造兼容入口。 */
    public PdfLayoutInspection(
            int pageCount,
            boolean overflowDetected,
            int finalPageLineCount,
            float minimumFontSizeInPt) {
        this(pageCount, overflowDetected, finalPageLineCount, minimumFontSizeInPt, -1.0f);
    }
}
