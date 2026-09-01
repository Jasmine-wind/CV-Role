package com.winter.airesumeoptimizer.infra.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.winter.airesumeoptimizer.module.export.service.ExportPreflightChecker;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfLayoutInspectorTest {

    private final PdfLayoutInspector inspector = new PdfLayoutInspector();

    @Test
    void reportsActualPageCountAndNoOverflowForInBoundsText() throws IOException {
        byte[] pdf = pdfWithTextAt(40, 100, 2);

        PdfLayoutInspection result = inspector.inspect(pdf);

        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.overflowDetected()).isFalse();
        assertThat(result.minimumFontSizeInPt()).isEqualTo(12.0f);
    }

    @Test
    void detectsRenderedGlyphOutsidePageCropBox() throws IOException {
        byte[] pdf = pdfWithTextAt(590, 100, 1);

        PdfLayoutInspection result = inspector.inspect(pdf);

        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.overflowDetected()).isTrue();
    }

    @Test
    void countsSparseFinalPageForOrphanGate() throws IOException {
        byte[] pdf = pdfWithSparseFinalPage();

        PdfLayoutInspection result = inspector.inspect(pdf);

        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.finalPageLineCount()).isEqualTo(1);
        assertThat(result.finalPageContentRatio()).isGreaterThan(0.0f)
                .isLessThan(ExportPreflightChecker.MIN_FINAL_PAGE_CONTENT_RATIO);
    }

    @Test
    void ignoresWhitespaceAndInvisibleTextForFinalPageBoundsAndLines() throws IOException {
        PdfLayoutInspection result = inspector.inspect(pdfWithInvisibleFinalPage());

        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.finalPageLineCount()).isZero();
        assertThat(result.finalPageContentRatio()).isZero();
    }

    @Test
    void usesTranslatedCropBoxCoordinatesForBounds() throws IOException {
        PdfLayoutInspection result = inspector.inspect(pdfWithCropBoxTextAt(120, "inside"));

        assertThat(result.overflowDetected()).isFalse();
    }

    @Test
    void detectsGlyphOverflowRelativeToCropBoxNotMediaBox() throws IOException {
        PdfLayoutInspection result = inspector.inspect(pdfWithCropBoxTextAt(490, "crop-overflow-check"));

        assertThat(result.overflowDetected()).isTrue();
    }

    @Test
    void rejectsBytesThatAreNotReadablePdf() {
        assertThatThrownBy(() -> inspector.inspect("not-pdf".getBytes()))
                .isInstanceOf(ResumeRenderException.class)
                .hasMessageContaining("PDF");
    }

    private byte[] pdfWithInvisibleFinalPage() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage(PDRectangle.A4);
            PDPage last = new PDPage(PDRectangle.A4);
            document.addPage(first);
            document.addPage(last);
            try (PDPageContentStream content = new PDPageContentStream(document, first)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(40, 780);
                content.showText("visible first page");
                content.endText();
            }
            try (PDPageContentStream content = new PDPageContentStream(document, last)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.setRenderingMode(RenderingMode.NEITHER);
                content.newLineAtOffset(40, 780);
                content.showText("invisible text");
                content.endText();
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.setRenderingMode(RenderingMode.FILL);
                content.newLineAtOffset(40, 120);
                content.showText("   ");
                content.endText();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] pdfWithCropBoxTextAt(float x, String text) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(600, 800));
            page.setCropBox(new PDRectangle(100, 100, 400, 500));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(x, 300);
                content.showText(text);
                content.endText();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] pdfWithSparseFinalPage() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage(PDRectangle.A4);
            PDPage last = new PDPage(PDRectangle.A4);
            document.addPage(first);
            document.addPage(last);
            try (PDPageContentStream content = new PDPageContentStream(document, first)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(40, 780);
                content.showText("page one content");
                content.endText();
            }
            try (PDPageContentStream content = new PDPageContentStream(document, last)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(40, 780);
                content.showText("orphan");
                content.endText();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] pdfWithTextAt(float x, float y, int pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(x, y);
                    content.showText("overflow-check");
                    content.endText();
                }
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }
}
