package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextExtractionService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

@Service
public class ResumeTextExtractionServiceImpl implements ResumeTextExtractionService {

    private final FileStorageService fileStorageService;

    public ResumeTextExtractionServiceImpl(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public String extractText(String objectKey, String fileType) {
        String normalizedFileType = normalizeFileType(fileType);

        try (InputStream inputStream = fileStorageService.open(objectKey)) {
            return switch (normalizedFileType) {
                case "PDF" -> extractPdfText(inputStream);
                case "DOC" -> extractDocText(inputStream);
                case "DOCX" -> extractDocxText(inputStream);
                default -> throw new BusinessException(400, "不支持的简历文件类型");
            };
        } catch (FileStorageException exception) {
            throw new BusinessException(500, "简历文件读取失败");
        } catch (IOException exception) {
            throw new BusinessException(500, "简历文本提取失败");
        }
    }

    private String extractPdfText(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            return normalizeExtractedText(new PDFTextStripper().getText(document));
        }
    }

    private String extractDocText(InputStream inputStream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(inputStream);
                WordExtractor extractor = new WordExtractor(document)) {
            return normalizeExtractedText(extractor.getText());
        }
    }

    private String extractDocxText(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return normalizeExtractedText(extractor.getText());
        }
    }

    private String normalizeFileType(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            throw new BusinessException(400, "简历文件类型不能为空");
        }
        return fileType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeExtractedText(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(400, "未提取到简历文本");
        }
        return text.strip();
    }
}
