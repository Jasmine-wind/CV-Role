package com.winter.airesumeoptimizer.infra.render;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

/** 对最终 PDF 做实际页数与可执行文字越界检查。 */
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
            GlyphBoundsStripper stripper = new GlyphBoundsStripper();
            stripper.getText(document);
            return new PdfLayoutInspection(pageCount, stripper.isOverflowDetected());
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ResumeRenderException renderException) {
                throw renderException;
            }
            throw new ResumeRenderException("简历 PDF 无法读取或检查", exception);
        }
    }

    private static final class GlyphBoundsStripper extends PDFTextStripper {

        private float pageWidth;
        private float pageHeight;
        private boolean overflowDetected;

        private GlyphBoundsStripper() throws IOException {
            super();
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            PDRectangle box = page.getCropBox() == null ? page.getMediaBox() : page.getCropBox();
            pageWidth = box.getWidth();
            pageHeight = box.getHeight();
            super.startPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            for (TextPosition position : positions) {
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
            }
            super.writeString(text, positions);
        }

        private boolean isOverflowDetected() {
            return overflowDetected;
        }
    }
}
