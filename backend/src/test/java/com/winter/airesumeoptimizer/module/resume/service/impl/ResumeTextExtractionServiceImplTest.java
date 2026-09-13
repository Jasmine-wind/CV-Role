package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDInlineImage;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class ResumeTextExtractionServiceImplTest {

    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final ResumeTextExtractionServiceImpl service = new ResumeTextExtractionServiceImpl(fileStorageService);

    @Test
    void extractTextShouldReadDocxContent() throws IOException {
        byte[] docxBytes = buildDocx("Java 后端开发工程师");
        when(fileStorageService.loadAsStream("resumes/1/resume.docx")).thenReturn(new ByteArrayInputStream(docxBytes));

        String text = service.extractText("resumes/1/resume.docx", "docx");

        assertThat(text).contains("Java 后端开发工程师");
    }

    @Test
    void extractTextShouldRejectBlankFileType() {
        assertThatThrownBy(() -> service.extractText("resumes/1/resume.docx", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历文件类型不能为空");
    }

    @Test
    void extractTextShouldWrapStorageFailure() {
        when(fileStorageService.loadAsStream("resumes/1/missing.docx"))
                .thenThrow(new FileStorageException("file not found"));

        assertThatThrownBy(() -> service.extractText("resumes/1/missing.docx", "DOCX"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历文件读取失败");
    }

    @Test
    void extractTextShouldReturnBlankTextForQualityCheck() throws IOException {
        byte[] docxBytes = buildDocx(" ");
        when(fileStorageService.loadAsStream("resumes/1/blank.docx")).thenReturn(new ByteArrayInputStream(docxBytes));

        String text = service.extractText("resumes/1/blank.docx", "docx");

        assertThat(text).isEmpty();
    }

    @Test
    void extractTextShouldConcatenateRunsAndPreserveDocxInlineControls() throws IOException {
        byte[] docxBytes = buildDocxWithRunsAndInlineControls();
        when(fileStorageService.loadAsStream("resumes/1/controls.docx"))
                .thenReturn(new ByteArrayInputStream(docxBytes));

        String text = service.extractText("resumes/1/controls.docx", "docx");

        assertThat(text).isEqualTo("AB\tC\nD");
    }

    @Test
    void collectDocxTextBlocksShouldKeepTextboxAtItsXmlPosition() throws IOException {
        byte[] docxBytes = buildDocxWithInterleavedTextBox();
        when(fileStorageService.loadAsStream("resumes/1/interleaved-textbox.docx"))
                .thenReturn(new ByteArrayInputStream(docxBytes));

        var result = service.extractWithMetadata("resumes/1/interleaved-textbox.docx", "docx");
        var blocks = service.collectDocxTextBlocks(new XWPFDocument(new ByteArrayInputStream(docxBytes)));

        assertThat(result.text()).isEqualTo("before\ninside\nafter");
        assertThat(blocks).extracting("sourceType")
                .containsExactly("paragraph", "textbox", "paragraph");
        assertThat(blocks).extracting("text")
                .containsExactly("before", "inside", "after");
        assertThat(blocks).extracting("occurrenceId").doesNotHaveDuplicates();
        assertThat(blocks).extracting("sourceBlockId", String.class)
                .allSatisfy(sourceBlockId -> assertThat(sourceBlockId).isNotBlank());
        assertThat(result.sourceBlocks()).isNotEmpty().allSatisfy(block -> {
            assertThat(block.getId()).isNotBlank();
            assertThat(block.getSourceOccurrenceIds()).doesNotContain(block.getId());
            assertThat(block.getSourceBlockIds()).doesNotContain(block.getId());
        });
    }

    @Test
    void extractTextShouldReadDocxTextBoxContentAndPreserveDuplicateOccurrences() throws IOException {
        byte[] docxBytes = buildDocxWithTextBox("普通段落", "文本框内容", "文本框内容");
        when(fileStorageService.loadAsStream("resumes/1/textbox.docx")).thenReturn(new ByteArrayInputStream(docxBytes));

        String text = service.extractText("resumes/1/textbox.docx", "docx");

        assertThat(text).contains("普通段落", "文本框内容");
        assertThat(text.split("文本框内容", -1)).hasSize(3);
    }

    @Test
    void extractTextShouldPreserveInterleavedDocxBodyOrder() throws IOException {
        byte[] docxBytes = buildInterleavedDocx();
        when(fileStorageService.loadAsStream("resumes/1/interleaved.docx"))
                .thenReturn(new ByteArrayInputStream(docxBytes));

        String text = service.extractText("resumes/1/interleaved.docx", "docx");

        assertThat(text).containsSubsequence("段落 A", "表格 B", "段落 C", "表格 D");
    }

    @Test
    void collectDocxTextBlocksShouldPlaceHeaderBodyAndFooterInDeterministicLogicalOrder() throws IOException {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT)
                    .createParagraph().createRun().setText("页眉");
            document.createParagraph().createRun().setText("正文");
            document.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT)
                    .createParagraph().createRun().setText("页脚");
            document.write(outputStream);
            bytes = outputStream.toByteArray();
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            var blocks = service.collectDocxTextBlocks(document);

            assertThat(blocks).extracting("text")
                    .containsSubsequence("页眉", "正文", "页脚");
            assertThat(blocks).extracting("sourceType")
                    .containsSubsequence("header", "paragraph", "footer");
        }
    }

    @Test
    void collectDocxTextBlocksShouldKeepMultipleParagraphsInsideTableCell() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            var table = document.createTable(1, 1);
            var cell = table.getRow(0).getCell(0);
            cell.setText("单元格第一段");
            cell.addParagraph().createRun().setText("单元格第二段");

            var blocks = service.collectDocxTextBlocks(document);

            assertThat(blocks).extracting("text")
                    .containsSubsequence("单元格第一段", "单元格第二段");
        }
    }

    @Test
    void extractWithMetadataShouldPreservePdfPageCountAndDistinguishImageContent() throws IOException {
        byte[] blankPdf = buildBlankPdf(2);
        when(fileStorageService.loadAsStream("resumes/1/blank.pdf"))
                .thenReturn(new ByteArrayInputStream(blankPdf));

        var blankResult = service.extractWithMetadata("resumes/1/blank.pdf", "pdf");

        assertThat(blankResult.text()).isEmpty();
        assertThat(blankResult.candidateType()).isEqualTo("NONE");
        assertThat(blankResult.candidates()).isEmpty();
        assertThat(blankResult.pageCount()).isEqualTo(2);
        assertThat(blankResult.pageCountKnown()).isTrue();
        assertThat(blankResult.imageContentPresent()).isFalse();

        byte[] imagePdf = buildImageOnlyPdf();
        when(fileStorageService.loadAsStream("resumes/1/scanned.pdf"))
                .thenReturn(new ByteArrayInputStream(imagePdf));

        var imageResult = service.extractWithMetadata("resumes/1/scanned.pdf", "pdf");

        assertThat(imageResult.text()).isEmpty();
        assertThat(imageResult.candidateType()).isEqualTo("NONE");
        assertThat(imageResult.candidates()).isEmpty();
        assertThat(imageResult.pageCount()).isEqualTo(1);
        assertThat(imageResult.pageCountKnown()).isTrue();
        assertThat(imageResult.imageContentPresent()).isTrue();
    }

    @Test
    void extractWithMetadataShouldDetectImagesNestedInFormResources() throws IOException {
        byte[] formPdf = buildFormImageOnlyPdf();
        when(fileStorageService.loadAsStream("resumes/1/form-image.pdf"))
                .thenReturn(new ByteArrayInputStream(formPdf));

        var result = service.extractWithMetadata("resumes/1/form-image.pdf", "pdf");

        assertThat(result.text()).isEmpty();
        assertThat(result.imageContentPresent()).isTrue();
    }

    @Test
    void extractWithMetadataShouldDetectInlineImages() throws IOException {
        byte[] inlinePdf = buildInlineImageOnlyPdf();
        when(fileStorageService.loadAsStream("resumes/1/inline-image.pdf"))
                .thenReturn(new ByteArrayInputStream(inlinePdf));

        var result = service.extractWithMetadata("resumes/1/inline-image.pdf", "pdf");

        assertThat(result.text()).isEmpty();
        assertThat(result.imageContentPresent()).isTrue();
    }

    @Test
    void imageClassificationFailureShouldRemainUnknown() throws IOException {
        byte[] blankPdf = buildBlankPdf(1);
        when(fileStorageService.loadAsStream("resumes/1/classification-failure.pdf"))
                .thenReturn(new ByteArrayInputStream(blankPdf));
        ResumeTextExtractionServiceImpl failingService = new ResumeTextExtractionServiceImpl(fileStorageService) {
            @Override
            boolean containsImageContent(PDDocument document) throws IOException {
                throw new IOException("classification unavailable");
            }
        };

        var result = failingService.extractWithMetadata("resumes/1/classification-failure.pdf", "pdf");

        assertThat(result.text()).isEmpty();
        assertThat(result.imageContentPresent()).isNull();
    }

    @Test
    void extractTextShouldKeepSyntheticPdfTextStableWhenBothOrdersAreHealthy() throws IOException {
        byte[] pdfBytes = buildPositionSortedPdf();
        when(fileStorageService.loadAsStream("resumes/1/position-sorted.pdf"))
                .thenReturn(new ByteArrayInputStream(pdfBytes));

        String text = service.extractText("resumes/1/position-sorted.pdf", "pdf");

        assertThat(text).contains("Summary", "Projects", "Education", "Skills");
    }

    @Test
    void pdfSourceBlocksKeepBlockAndOccurrenceNamespacesSeparate() throws IOException {
        byte[] pdfBytes = buildPositionSortedPdf();
        when(fileStorageService.loadAsStream("resumes/1/pdf-namespaces.pdf"))
                .thenReturn(new ByteArrayInputStream(pdfBytes));

        var result = service.extractWithMetadata("resumes/1/pdf-namespaces.pdf", "pdf");

        assertThat(result.sourceBlocks()).isNotEmpty().allSatisfy(block -> {
            assertThat(block.getSourceBlockIds()).isNotEmpty();
            assertThat(block.getSourceOccurrenceIds()).isNotEmpty();
            assertThat(block.getSourceOccurrenceIds())
                    .doesNotContainAnyElementsOf(block.getSourceBlockIds());
        });
    }

    @Test
    void layoutLiteFailureFallsBackToPdfBoxTextExtraction() throws IOException {
        byte[] pdfBytes = buildPositionSortedPdf();
        when(fileStorageService.loadAsStream("resumes/1/layout-failure.pdf"))
                .thenReturn(new ByteArrayInputStream(pdfBytes));
        ResumeLayoutLiteExtractor layoutExtractor = new ResumeLayoutLiteExtractor() {
            @Override
            public ResumeLayoutLiteExtractor.LayoutLiteResult extract(PDDocument document) {
                throw new IllegalStateException("layout hint unavailable");
            }
        };
        ResumeTextExtractionServiceImpl fallbackService =
                new ResumeTextExtractionServiceImpl(fileStorageService, layoutExtractor);

        var result = fallbackService.extractWithMetadata("resumes/1/layout-failure.pdf", "pdf");

        assertThat(result.text()).contains("Summary", "Projects", "Education", "Skills");
        assertThat(result.candidateType())
                .isNotEqualTo(ResumePdfTextCandidateSelector.CandidateType.LAYOUT_LITE.name());
        assertThat(result.sourceBlocks()).isNotEmpty();
        assertThat(result.candidates()).extracting("candidateType")
                .doesNotContain(ResumePdfTextCandidateSelector.CandidateType.LAYOUT_LITE.name());
    }

    @Test
    void nullLayoutLiteResultFallsBackWithoutChangingPdfText() throws IOException {
        byte[] pdfBytes = buildPositionSortedPdf();
        when(fileStorageService.loadAsStream("resumes/1/null-layout.pdf"))
                .thenReturn(new ByteArrayInputStream(pdfBytes));
        ResumeLayoutLiteExtractor layoutExtractor = new ResumeLayoutLiteExtractor() {
            @Override
            public ResumeLayoutLiteExtractor.LayoutLiteResult extract(PDDocument document) {
                return null;
            }
        };
        ResumeTextExtractionServiceImpl fallbackService =
                new ResumeTextExtractionServiceImpl(fileStorageService, layoutExtractor);

        var result = fallbackService.extractWithMetadata("resumes/1/null-layout.pdf", "pdf");

        assertThat(result.text()).contains("Summary", "Projects", "Education", "Skills");
        assertThat(result.sourceBlocks()).isNotEmpty();
    }

    @Test
    void collectDocxTextBlocksShouldRecordTableCellStructuralProvenance() throws IOException {
        byte[] bytes;
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            var table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("重复单元格");
            table.getRow(0).getCell(1).setText("重复单元格");
            table.getRow(1).getCell(0).setText("重复单元格");
            table.getRow(1).getCell(1).setText("重复单元格");
            document.write(outputStream);
            bytes = outputStream.toByteArray();
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            var blocks = service.collectDocxTextBlocks(document);

            assertThat(blocks).extracting("text")
                    .containsExactly("重复单元格", "重复单元格", "重复单元格", "重复单元格");
            assertThat(blocks).extracting("sourceBlockId").doesNotHaveDuplicates();
            assertThat(blocks).extracting("occurrenceId").doesNotHaveDuplicates();
            assertThat(blocks).allSatisfy(block ->
                    assertThat(block.sourceBlockId()).contains("table", "row", "cell"));
        }
    }

    @Test
    void collectDocxTextBlocksShouldRecordSourceTypes() throws IOException {
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("普通段落");
            XWPFTable table = document.createTable(1, 1);
            table.getRow(0).getCell(0).setText("表格内容");

            var blocks = service.collectDocxTextBlocks(document);

            assertThat(blocks).extracting("sourceType")
                    .contains("paragraph", "table");
            assertThat(blocks).extracting("text")
                    .contains("普通段落", "表格内容");
        }
    }

    private byte[] buildInterleavedDocx() throws IOException {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("段落 A");
            XWPFTable firstTable = document.createTable(1, 1);
            firstTable.getRow(0).getCell(0).setText("表格 B");
            document.createParagraph().createRun().setText("段落 C");
            XWPFTable secondTable = document.createTable(1, 1);
            secondTable.getRow(0).getCell(0).setText("表格 D");
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] buildBlankPdf(int pageCount) throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (int i = 0; i < pageCount; i++) {
                document.addPage(new PDPage());
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] buildImageOnlyPdf() throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            BufferedImage bufferedImage = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
            var graphics = bufferedImage.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 20, 20);
            graphics.setColor(Color.BLACK);
            graphics.fillRect(2, 2, 16, 16);
            graphics.dispose();
            PDImageXObject image = LosslessFactory.createFromImage(document, bufferedImage);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 72, 72, 144, 144);
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] buildFormImageOnlyPdf() throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            page.setResources(new PDResources());
            document.addPage(page);
            BufferedImage bufferedImage = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            var graphics = bufferedImage.createGraphics();
            graphics.setColor(Color.BLACK);
            graphics.fillRect(0, 0, 8, 8);
            graphics.dispose();
            PDImageXObject image = LosslessFactory.createFromImage(document, bufferedImage);

            PDFormXObject form = new PDFormXObject(document);
            form.setResources(new PDResources());
            form.getResources().add(image);
            page.getResources().add(form);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawForm(form);
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] buildInlineImageOnlyPdf() throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            page.setResources(new PDResources());
            document.addPage(page);
            COSDictionary parameters = new COSDictionary();
            parameters.setInt(COSName.WIDTH, 2);
            parameters.setInt(COSName.HEIGHT, 2);
            parameters.setInt(COSName.BITS_PER_COMPONENT, 8);
            parameters.setItem(COSName.COLORSPACE, COSName.DEVICERGB);
            PDInlineImage image = new PDInlineImage(
                    parameters, new byte[] {0, 0, 0, (byte) 255, (byte) 255, (byte) 255},
                    page.getResources());
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image, 72, 72, 20, 20);
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] buildPositionSortedPdf() throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.beginText();
                content.newLineAtOffset(72, 600);
                content.showText("Projects");
                content.endText();
                content.beginText();
                content.newLineAtOffset(72, 720);
                content.showText("Summary");
                content.endText();
                content.beginText();
                content.newLineAtOffset(72, 560);
                content.showText("Education");
                content.endText();
                content.beginText();
                content.newLineAtOffset(72, 520);
                content.showText("Skills");
                content.endText();
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] buildDocx(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] buildDocxWithInterleavedTextBox() throws IOException {
        String documentXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p>
                      <w:r><w:t>before</w:t></w:r>
                      <w:r><w:drawing><w:txbxContent><w:p><w:r><w:t>inside</w:t></w:r></w:p></w:txbxContent></w:drawing></w:r>
                      <w:r><w:t>after</w:t></w:r>
                    </w:p>
                  </w:body>
                </w:document>
                """;
        return buildMinimalDocx(documentXml);
    }

    private byte[] buildDocxWithRunsAndInlineControls() throws IOException {
        String documentXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p>
                      <w:r><w:t>A</w:t></w:r>
                      <w:r><w:t>B</w:t></w:r>
                      <w:r><w:tab/></w:r>
                      <w:r><w:t>C</w:t></w:r>
                      <w:r><w:br/></w:r>
                      <w:r><w:t>D</w:t></w:r>
                    </w:p>
                  </w:body>
                </w:document>
                """;
        return buildMinimalDocx(documentXml);
    }

    private byte[] buildMinimalDocx(String documentXml) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            addZipEntry(zipOutputStream, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            addZipEntry(zipOutputStream, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            addZipEntry(zipOutputStream, "word/document.xml", documentXml);
            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    private byte[] buildDocxWithTextBox(String paragraphText, String textBoxText, String duplicateText) throws IOException {
        String documentXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                    <w:p><w:r><w:drawing><w:txbxContent>
                      <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                      <w:p><w:r><w:t>%s</w:t></w:r></w:p>
                    </w:txbxContent></w:drawing></w:r></w:p>
                  </w:body>
                </w:document>
                """.formatted(paragraphText, textBoxText, duplicateText);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            addZipEntry(zipOutputStream, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>
                    """);
            addZipEntry(zipOutputStream, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>
                    """);
            addZipEntry(zipOutputStream, "word/document.xml", documentXml);
            zipOutputStream.finish();
            return outputStream.toByteArray();
        }
    }

    private void addZipEntry(ZipOutputStream zipOutputStream, String name, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }
}
