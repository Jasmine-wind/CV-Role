package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextExtractionService;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
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
            List<String> textParts = collectDocxTextBlocks(document).stream()
                    .map(ExtractedTextBlock::text)
                    .toList();
            if (textParts.isEmpty()) {
                textParts = List.of(extractor.getText());
            }
            return normalizeExtractedText(String.join("\n", textParts));
        }
    }

    List<ExtractedTextBlock> collectDocxTextBlocks(XWPFDocument document) {
        List<ExtractedTextBlock> blocks = new ArrayList<>();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            addTextBlock(blocks, "paragraph", paragraph.getText());
        }
        for (XWPFTable table : document.getTables()) {
            collectTableTextBlocks(table, blocks);
        }
        for (XWPFHeader header : document.getHeaderList()) {
            collectHeaderFooterTextBlocks(header, "header", blocks);
        }
        for (XWPFFooter footer : document.getFooterList()) {
            collectHeaderFooterTextBlocks(footer, "footer", blocks);
        }
        extractDocxTextBoxText(document).forEach(text -> addTextBlock(blocks, "textbox", text));
        return blocks;
    }

    private void collectTableTextBlocks(XWPFTable table, List<ExtractedTextBlock> blocks) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    addTextBlock(blocks, "table", paragraph.getText());
                }
            }
        }
    }

    private void collectHeaderFooterTextBlocks(XWPFHeaderFooter headerFooter, String sourceType, List<ExtractedTextBlock> blocks) {
        for (XWPFParagraph paragraph : headerFooter.getParagraphs()) {
            addTextBlock(blocks, sourceType, paragraph.getText());
        }
        for (XWPFTable table : headerFooter.getTables()) {
            collectTableTextBlocks(table, blocks);
        }
    }

    private void addTextBlock(List<ExtractedTextBlock> blocks, String sourceType, String text) {
        if (text != null && !text.isBlank()) {
            blocks.add(new ExtractedTextBlock(sourceType, text.strip()));
        }
    }

    List<String> extractDocxTextBoxText(XWPFDocument document) {
        XmlObject[] textObjects = document.getDocument().selectPath("""
                declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'
                .//w:txbxContent//w:t
                """);
        List<String> result = new ArrayList<>();
        for (XmlObject textObject : textObjects) {
            try (XmlCursor cursor = textObject.newCursor()) {
                String text = cursor.getTextValue();
                if (text != null && !text.isBlank()) {
                    result.add(text.strip());
                }
            }
        }
        return result;
    }

    private String normalizeFileType(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            throw new BusinessException(400, "简历文件类型不能为空");
        }
        return fileType.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeExtractedText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> lines = text.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .filter(line -> seen.add(line.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT)))
                .toList();
        return String.join("\n", lines).strip();
    }

    record ExtractedTextBlock(String sourceType, String text) {
    }
}
