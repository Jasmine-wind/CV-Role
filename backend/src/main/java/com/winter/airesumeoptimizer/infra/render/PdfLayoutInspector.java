package com.winter.airesumeoptimizer.infra.render;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.state.PDGraphicsState;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

/** 对最终 PDF 做实际页数、可执行文字越界、字号与末页占用检查。 */
@Component
public class PdfLayoutInspector {

    private static final float TOLERANCE_POINTS = 1.0f;

    public PdfLayoutInspection inspect(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new ResumeRenderException("简历 PDF 为空");
        }
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount <= 0) {
                throw new ResumeRenderException("简历 PDF 没有有效页面");
            }
            GlyphBoundsStripper stripper = new GlyphBoundsStripper(pageCount);
            stripper.getText(document);
            int finalPageLineCount = countFinalPageLines(document, pageCount);
            return new PdfLayoutInspection(
                    pageCount,
                    stripper.isOverflowDetected(),
                    finalPageLineCount,
                    stripper.minimumFontSizeInPt(),
                    stripper.finalPageContentRatio());
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ResumeRenderException renderException) {
                throw renderException;
            }
            throw new ResumeRenderException("简历 PDF 无法读取或检查", exception);
        }
    }

    /** 末页非空文本行数；用于识别明确的孤立一两行尾页。 */
    private int countFinalPageLines(PDDocument document, int pageCount) throws IOException {
        VisibleTextLineStripper stripper = new VisibleTextLineStripper();
        stripper.setStartPage(pageCount);
        stripper.setEndPage(pageCount);
        String text = stripper.getText(document);
        int lines = 0;
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                lines++;
            }
        }
        return lines;
    }

    /** PDFTextStripper normally includes text painted with rendering mode 3 and whitespace-only runs. */
    private static boolean isVisibleTextPosition(PDGraphicsState graphicsState, TextPosition position) {
        if (position == null) {
            return false;
        }
        String unicode = position.getUnicode();
        if (unicode != null && !unicode.isEmpty()
                && unicode.codePoints().allMatch(codePoint -> Character.isWhitespace(codePoint)
                || Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT)) {
            return false;
        }
        if (graphicsState == null || graphicsState.getTextState() == null) {
            return true;
        }
        RenderingMode mode = graphicsState.getTextState().getRenderingMode();
        if (mode == null) {
            return true;
        }
        boolean fillVisible = mode.isFill() && graphicsState.getNonStrokeAlphaConstant() > 0.0d;
        boolean strokeVisible = mode.isStroke() && graphicsState.getAlphaConstant() > 0.0d;
        return fillVisible || strokeVisible;
    }

    private static boolean hasMeasurableGlyph(TextPosition position) {
        return position.getWidthDirAdj() > 0.0f && position.getHeightDir() > 0.0f;
    }

    private static class VisibleTextLineStripper extends PDFTextStripper {

        private VisibleTextLineStripper() throws IOException {
            super();
        }

        @Override
        protected void processTextPosition(TextPosition textPosition) {
            if (isVisibleTextPosition(getGraphicsState(), textPosition)) {
                super.processTextPosition(textPosition);
            }
        }
    }

    private static final class GlyphBoundsStripper extends VisibleTextLineStripper {

        private final int finalPageNumber;
        private int currentPageNumber;
        private float pageWidth;
        private float pageHeight;
        private float finalContentTop = Float.POSITIVE_INFINITY;
        private float finalContentBottom = Float.NEGATIVE_INFINITY;
        private boolean overflowDetected;
        private float minimumFontSizeInPt = Float.POSITIVE_INFINITY;

        private GlyphBoundsStripper(int finalPageNumber) throws IOException {
            super();
            this.finalPageNumber = finalPageNumber;
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            currentPageNumber++;
            PDRectangle box = page.getCropBox() == null ? page.getMediaBox() : page.getCropBox();
            pageWidth = box.getWidth();
            pageHeight = box.getHeight();
            super.startPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            for (TextPosition position : positions) {
                if (!hasMeasurableGlyph(position)) {
                    continue;
                }
                if (position.getFontSizeInPt() > 0) {
                    minimumFontSizeInPt = Math.min(minimumFontSizeInPt, position.getFontSizeInPt());
                }
                float left = position.getXDirAdj();
                float right = left + position.getWidthDirAdj();
                float baselineFromTop = position.getYDirAdj();
                float top = baselineFromTop - position.getHeightDir();
                float bottom = baselineFromTop;
                if (left < -TOLERANCE_POINTS
                        || right > pageWidth + TOLERANCE_POINTS
                        || top < -TOLERANCE_POINTS
                        || bottom > pageHeight + TOLERANCE_POINTS) {
                    overflowDetected = true;
                }
                if (currentPageNumber == finalPageNumber) {
                    finalContentTop = Math.min(finalContentTop, top);
                    finalContentBottom = Math.max(finalContentBottom, bottom);
                }
            }
            super.writeString(text, positions);
        }

        private boolean isOverflowDetected() {
            return overflowDetected;
        }

        private float minimumFontSizeInPt() {
            return Float.isFinite(minimumFontSizeInPt) ? minimumFontSizeInPt : 0.0f;
        }

        /**
         * 返回末页 glyph 垂直包围盒占整页高度的比例。使用整页而非模板 margin，
         * 让历史 PDF 和测试 PDF 不需要携带额外的排版元数据；ExportPreflight 再采用
         * 保守的 20% 产品阈值。无可见文字时返回 0。
         */
        private float finalPageContentRatio() {
            if (!Float.isFinite(finalContentTop)
                    || !Float.isFinite(finalContentBottom)
                    || pageHeight <= 0) {
                return 0.0f;
            }
            float visibleTop = Math.max(0.0f, finalContentTop);
            float visibleBottom = Math.min(pageHeight, finalContentBottom);
            if (visibleBottom <= visibleTop) {
                return 0.0f;
            }
            return Math.min(1.0f, Math.max(0.0f, (visibleBottom - visibleTop) / pageHeight));
        }
    }
}
