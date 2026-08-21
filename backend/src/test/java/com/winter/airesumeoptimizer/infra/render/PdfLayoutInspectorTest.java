package com.winter.airesumeoptimizer.infra.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
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
    }

    @Test
    void detectsRenderedGlyphOutsidePageCropBox() throws IOException {
        byte[] pdf = pdfWithTextAt(590, 100, 1);

        PdfLayoutInspection result = inspector.inspect(pdf);

        assertThat(result.pageCount()).isEqualTo(1);
        assertThat(result.overflowDetected()).isTrue();
    }

    @Test
    void rejectsBytesThatAreNotReadablePdf() {
        assertThatThrownBy(() -> inspector.inspect("not-pdf".getBytes()))
                .isInstanceOf(ResumeRenderException.class)
                .hasMessageContaining("PDF");
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
