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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class ResumeTextExtractionServiceImplTest {

    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final ResumeTextExtractionServiceImpl service = new ResumeTextExtractionServiceImpl(fileStorageService);

    @Test
    void extractTextShouldReadDocxContent() throws IOException {
        byte[] docxBytes = buildDocx("Java 后端开发工程师");
        when(fileStorageService.open("resumes/1/resume.docx")).thenReturn(new ByteArrayInputStream(docxBytes));

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
        when(fileStorageService.open("resumes/1/missing.docx"))
                .thenThrow(new FileStorageException("file not found"));

        assertThatThrownBy(() -> service.extractText("resumes/1/missing.docx", "DOCX"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历文件读取失败");
    }

    @Test
    void extractTextShouldReturnBlankTextForQualityCheck() throws IOException {
        byte[] docxBytes = buildDocx(" ");
        when(fileStorageService.open("resumes/1/blank.docx")).thenReturn(new ByteArrayInputStream(docxBytes));

        String text = service.extractText("resumes/1/blank.docx", "docx");

        assertThat(text).isEmpty();
    }

    @Test
    void extractTextShouldReadDocxTextBoxContentAndDeduplicate() throws IOException {
        byte[] docxBytes = buildDocxWithTextBox("普通段落", "文本框内容", "文本框内容");
        when(fileStorageService.open("resumes/1/textbox.docx")).thenReturn(new ByteArrayInputStream(docxBytes));

        String text = service.extractText("resumes/1/textbox.docx", "docx");

        assertThat(text).contains("普通段落", "文本框内容");
        assertThat(text.indexOf("文本框内容")).isEqualTo(text.lastIndexOf("文本框内容"));
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

    private byte[] buildDocx(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(outputStream);
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
