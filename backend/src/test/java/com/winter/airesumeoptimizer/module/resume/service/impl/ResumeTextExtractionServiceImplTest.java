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
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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

    private byte[] buildDocx(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText(text);
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
