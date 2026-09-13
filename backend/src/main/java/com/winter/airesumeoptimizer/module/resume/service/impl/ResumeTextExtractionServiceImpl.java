package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceBlockRole;
import com.winter.airesumeoptimizer.module.resume.service.ResumeLayoutAwareTextExtractionService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextExtractionResult;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextExtractionService;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFHeaderFooter;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFSDT;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Service
public class ResumeTextExtractionServiceImpl implements ResumeTextExtractionService, ResumeLayoutAwareTextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ResumeTextExtractionServiceImpl.class);
    private static final String WORD_NAMESPACE =
            "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final String OFFICE_RELATIONSHIP_NAMESPACE =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String MARKUP_COMPATIBILITY_NAMESPACE =
            "http://schemas.openxmlformats.org/markup-compatibility/2006";
    private final FileStorageService fileStorageService;
    private final ResumePdfTextCandidateSelector pdfCandidateSelector;
    private final ResumeLayoutLiteExtractor layoutLiteExtractor;

    @org.springframework.beans.factory.annotation.Autowired
    public ResumeTextExtractionServiceImpl(FileStorageService fileStorageService) {
        this(fileStorageService, new ResumeLayoutLiteExtractor());
    }

    /** Package-visible seam for proving that optional layout extraction is best effort. */
    ResumeTextExtractionServiceImpl(
            FileStorageService fileStorageService,
            ResumeLayoutLiteExtractor layoutLiteExtractor) {
        this.fileStorageService = fileStorageService;
        this.pdfCandidateSelector = new ResumePdfTextCandidateSelector();
        this.layoutLiteExtractor = layoutLiteExtractor == null
                ? new ResumeLayoutLiteExtractor() : layoutLiteExtractor;
    }

    @Override
    public String extractText(String objectKey, String fileType) {
        return extractWithMetadata(objectKey, fileType).text();
    }

    @Override
    public ResumeTextExtractionResult extractWithMetadata(String objectKey, String fileType) {
        String normalizedFileType = normalizeFileType(fileType);

        try (InputStream inputStream = fileStorageService.loadAsStream(objectKey)) {
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
        } catch (RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            log.warn("Resume text extraction skipped: exceptionType={}", exception.getClass().getSimpleName());
            throw new BusinessException(500, "简历文本提取失败");
        }
    }

    private ResumeTextExtractionResult extractPdfText(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int pageCount = document.getNumberOfPages();
            Boolean imageContentPresent;
            try {
                imageContentPresent = containsImageContent(document);
            } catch (IOException | RuntimeException exception) {
                // Image classification is advisory; a resource lookup failure must not hide
                // otherwise extractable PDF text. Preserve UNKNOWN instead of converting a
                // failed inspection into the stronger EMPTY_PDF conclusion.
                log.warn("PDF image-content classification skipped: exceptionType={}",
                        exception.getClass().getSimpleName());
                imageContentPresent = null;
            }
            PdfCandidate legacyCandidate = null;
            PdfCandidate positionCandidate = null;
            Exception textExtractionFailure = null;
            try {
                legacyCandidate = extractPdfCandidate(document, false, "pdf-legacy");
            } catch (IOException | RuntimeException exception) {
                textExtractionFailure = exception;
                log.warn("PDF legacy text extraction skipped: exceptionType={}",
                        exception.getClass().getSimpleName());
            }
            try {
                positionCandidate = extractPdfCandidate(document, true, "pdf-position-sorted");
            } catch (IOException | RuntimeException exception) {
                if (textExtractionFailure == null) {
                    textExtractionFailure = exception;
                }
                log.warn("PDF position text extraction skipped: exceptionType={}",
                        exception.getClass().getSimpleName());
            }
            String legacyText = legacyCandidate == null ? null : legacyCandidate.text();
            String positionSortedText = positionCandidate == null ? null : positionCandidate.text();

            ResumeLayoutLiteExtractor.LayoutLiteResult layout;
            boolean layoutExtractionCompleted = false;
            try {
                ResumeLayoutLiteExtractor.LayoutLiteResult extractedLayout = layoutLiteExtractor.extract(document);
                if (extractedLayout == null) {
                    // A null optional result means that layout inspection was unavailable, not
                    // that the PDF was proven to contain no glyphs.
                    layout = ResumeLayoutLiteExtractor.LayoutLiteResult.empty();
                } else {
                    layout = extractedLayout;
                    layoutExtractionCompleted = true;
                }
            } catch (IOException | RuntimeException exception) {
                // Layout-lite is only a recovery hint. A PDFBox layout failure must not make a
                // normally extractable PDF unavailable; the established candidate remains the
                // authoritative fallback.
                log.warn("PDF layout-lite extraction skipped: exceptionType={}",
                        exception.getClass().getSimpleName());
                layout = ResumeLayoutLiteExtractor.LayoutLiteResult.empty();
            }

            boolean stableTextAvailable = hasText(legacyText) || hasText(positionSortedText);
            String safeLayoutText = null;
            if (!layout.hasParallelColumns()
                    && hasText(layout.text())
                    && (!stableTextAvailable
                    || (!layout.blocks().isEmpty()
                    && pdfCandidateSelector.layoutOrderCompatible(
                    layout.text(), legacyText, positionSortedText)))) {
                safeLayoutText = layout.text();
            }

            ResumePdfTextCandidateSelector.Selection selection = pdfCandidateSelector.select(
                    legacyText, positionSortedText, safeLayoutText);
            // Loading a valid PDF is success even when it contains no extractable glyphs. In
            // particular, blank and image-only PDFs must reach the normal EMPTY_TEXT/SCANNED_PDF
            // quality classification instead of becoming a generic extraction error.
            if (!hasText(legacyText) && !hasText(positionSortedText) && !hasText(safeLayoutText)) {
                boolean textExtractionCompleted = legacyCandidate != null && positionCandidate != null;
                if (!textExtractionCompleted && !layoutExtractionCompleted) {
                    if (textExtractionFailure instanceof IOException ioException) {
                        throw ioException;
                    }
                    if (textExtractionFailure instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new IOException("PDF text extraction produced no usable candidate");
                }
                log.info("PDF contains no extractable text: pageCount={}", pageCount);
                return new ResumeTextExtractionResult(
                        "", selection.candidateType().name(), List.of(),
                        selection.legacyScore(), selection.positionScore(), selection.layoutLiteScore(),
                        List.of(), pageCount, imageContentPresent, true);
            }

            List<ResumeTextExtractionResult.Candidate> candidates = new ArrayList<>();
            if (legacyCandidate != null && hasText(legacyText)) {
                candidates.add(new ResumeTextExtractionResult.Candidate(
                        legacyText,
                        ResumePdfTextCandidateSelector.CandidateType.LEGACY.name(),
                        legacyCandidate.sourceBlocks(),
                        pdfCandidateSelector.score(legacyText)));
            }
            if (positionCandidate != null && hasText(positionSortedText)) {
                candidates.add(new ResumeTextExtractionResult.Candidate(
                        positionSortedText,
                        ResumePdfTextCandidateSelector.CandidateType.POSITION_SORTED.name(),
                        positionCandidate.sourceBlocks(),
                        pdfCandidateSelector.score(positionSortedText)));
            }
            if (hasText(safeLayoutText)) {
                candidates.add(new ResumeTextExtractionResult.Candidate(
                        safeLayoutText,
                        ResumePdfTextCandidateSelector.CandidateType.LAYOUT_LITE.name(),
                        layout.blocks(),
                        pdfCandidateSelector.score(safeLayoutText)));
            }
            List<ResumeBlockDTO> sourceBlocks = switch (selection.candidateType()) {
                case LEGACY -> legacyCandidate == null ? List.of() : legacyCandidate.sourceBlocks();
                case POSITION_SORTED -> positionCandidate == null ? List.of() : positionCandidate.sourceBlocks();
                case LAYOUT_LITE -> layout.blocks();
                case NONE -> List.of();
            };
            log.info(
                    "PDF extraction candidate selected: legacyScore={}, positionScore={}, layoutLiteScore={}, selected={}, "
                            + "legacyLineCount={}, positionLineCount={}, layoutLineCount={}, sourceBlockCount={}, pageCount={}",
                    selection.legacyScore(),
                    selection.positionScore(),
                    selection.layoutLiteScore(),
                    selection.candidateType(),
                    lineCount(legacyText),
                    lineCount(positionSortedText),
                    lineCount(layout.text()),
                    sourceBlocks.size(),
                    pageCount);
            return new ResumeTextExtractionResult(
                    selection.text(),
                    selection.candidateType().name(),
                    sourceBlocks,
                    selection.legacyScore(),
                    selection.positionScore(),
                    selection.layoutLiteScore(),
                    candidates,
                    pageCount,
                    imageContentPresent,
                    true);
        }
    }

    /**
     * Best-effort image classifier for PDF quality messaging. The result is deliberately
     * nullable at the call boundary: an inspection failure means UNKNOWN, not "no image".
     *
     * <p>Resource dictionaries are inspected recursively because images may live below a Form
     * XObject rather than directly on the page. The graphics pass additionally observes inline
     * images, which have no XObject resource name.</p>
     */
    boolean containsImageContent(PDDocument document) throws IOException {
        if (document == null) {
            return false;
        }
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (PDPage page : document.getPages()) {
            if (page == null) {
                continue;
            }
            if (containsImageInResources(page.getResources(), visited)) {
                return true;
            }
            PdfImageContentDetector detector = new PdfImageContentDetector(page);
            detector.processPage(page);
            if (detector.imageFound()) {
                return true;
            }
        }
        return false;
    }

    private boolean containsImageInResources(
            org.apache.pdfbox.pdmodel.PDResources resources,
            Set<Object> visited) throws IOException {
        if (resources == null || !visited.add(resources.getCOSObject())) {
            return false;
        }
        for (var name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject) {
                return true;
            }
            if (xObject instanceof PDFormXObject form
                    && containsImageInResources(form.getResources(), visited)) {
                return true;
            }
        }
        return false;
    }

    /** PDFGraphicsStreamEngine reports both Do images and BI inline images through drawImage. */
    private static final class PdfImageContentDetector extends PDFGraphicsStreamEngine {

        private boolean imageFound;

        private PdfImageContentDetector(PDPage page) {
            super(page);
        }

        @Override
        public void drawImage(PDImage image) {
            if (image != null && !image.isEmpty()) {
                imageFound = true;
            }
        }

        @Override
        protected void operatorException(
                Operator operator, List<COSBase> operands, IOException exception) throws IOException {
            // A swallowed Do/BI error would turn an unknown classification into a false
            // "no-image" result. Let the outer classifier preserve UNKNOWN instead.
            throw exception;
        }

        boolean imageFound() {
            return imageFound;
        }

        @Override
        public void appendRectangle(Point2D point1, Point2D point2, Point2D point3, Point2D point4) {
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void moveTo(float x, float y) {
        }

        @Override
        public void lineTo(float x, float y) {
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        }

        @Override
        public Point2D getCurrentPoint() {
            return null;
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
        }

        @Override
        public void strokePath() {
        }

        @Override
        public void fillPath(int windingRule) {
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
        }

        @Override
        public void shadingFill(org.apache.pdfbox.cos.COSName shadingName) {
        }
    }

    private PdfCandidate extractPdfCandidate(PDDocument document, boolean sortByPosition, String sourceType)
            throws IOException {
        List<PdfPageText> pages = new ArrayList<>();
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(sortByPosition);
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            pages.add(new PdfPageText(page, normalizeExtractedText(stripper.getText(document))));
        }
        String text = pages.stream()
                .map(PdfPageText::text)
                .filter(pageText -> hasText(pageText))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        return new PdfCandidate(text, pdfSourceBlocks(pages, sourceType));
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private int lineCount(String text) {
        return text == null || text.isBlank() ? 0 : (int) text.lines().count();
    }

    private ResumeTextExtractionResult plainResult(String text, String candidateType) {
        return new ResumeTextExtractionResult(
                text,
                candidateType,
                plainSourceBlocks(text, candidateType.toLowerCase(Locale.ROOT)),
                -1,
                -1,
                -1,
                List.of(),
                0,
                null,
                false);
    }

    private List<ResumeBlockDTO> plainSourceBlocks(String text, String sourceType) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return plainSourceBlocks(List.of(new ExtractedTextBlock(sourceType, text)), sourceType);
    }

    private List<ResumeBlockDTO> pdfSourceBlocks(List<PdfPageText> pages, String sourceType) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }
        List<ResumeBlockDTO> blocks = new ArrayList<>();
        int order = 0;
        for (PdfPageText page : pages) {
            if (page == null || page.text() == null) {
                continue;
            }
            int lineOrder = 0;
            for (String line : page.text().lines().toList()) {
                String value = line == null ? "" : line.strip();
                if (value.isBlank()) {
                    continue;
                }
                String id = page.page() > 0
                        ? "%s-p%03d-l%04d".formatted(sourceType, page.page(), lineOrder)
                        : sourceType + "-" + order;
                blocks.add(plainSourceBlock(id, order, value, page.page() > 0 ? page.page() : null, sourceType));
                order++;
                lineOrder++;
            }
        }
        return List.copyOf(blocks);
    }

    private List<ResumeBlockDTO> plainSourceBlocks(List<ExtractedTextBlock> textBlocks, String sourceType) {
        if (textBlocks == null || textBlocks.isEmpty()) {
            return List.of();
        }
        List<ResumeBlockDTO> blocks = new ArrayList<>();
        java.util.Set<String> usedOccurrenceIds = new java.util.LinkedHashSet<>();
        int order = 0;
        for (DocxLogicalBlock logicalBlock : logicalDocxBlocks(textBlocks, sourceType)) {
            List<String> lines = splitDocxLines(logicalBlock.text());
            int lineIndex = 0;
            for (String rawLine : lines) {
                String value = normalizeDocxLine(rawLine);
                if (!hasDocxLineContent(value)) {
                    continue;
                }
                String fallbackOccurrence = sourceType + "-occurrence-" + order;
                List<String> occurrenceIds = logicalBlock.occurrenceIds().isEmpty()
                        ? List.of(fallbackOccurrence)
                        : logicalBlock.occurrenceIds();
                if (lines.size() > 1) {
                    int currentLineIndex = lineIndex;
                    occurrenceIds = occurrenceIds.stream()
                            .map(id -> id + "-line-" + currentLineIndex)
                            .toList();
                }
                occurrenceIds = uniqueOccurrenceIds(occurrenceIds, usedOccurrenceIds, fallbackOccurrence);
                String blockId = sourceType + "-block-" + order;
                List<String> sourceBlockIds = logicalBlock.sourceBlockIds().isEmpty()
                        ? List.of(blockId)
                        : logicalBlock.sourceBlockIds();
                String blockSourceType = logicalBlock.sourceType() == null || logicalBlock.sourceType().isBlank()
                        ? sourceType : logicalBlock.sourceType();
                blocks.add(plainSourceBlock(
                        blockId, sourceBlockIds, occurrenceIds, order, value, null, blockSourceType));
                order++;
                lineIndex++;
            }
        }
        return List.copyOf(blocks);
    }

    private List<DocxLogicalBlock> logicalDocxBlocks(
            List<ExtractedTextBlock> textBlocks, String sourceType) {
        List<DocxLogicalBlock> result = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        List<String> occurrenceIds = new ArrayList<>();
        List<String> sourceBlockIds = new ArrayList<>();
        String currentSourceType = null;
        for (ExtractedTextBlock block : textBlocks) {
            if (block == null || block.text() == null || block.text().isEmpty()) {
                continue;
            }
            boolean startsNewLine = block.startsNewLine()
                    || currentSourceType != null && !currentSourceType.equals(block.sourceType());
            if (startsNewLine && text.length() > 0) {
                result.add(new DocxLogicalBlock(
                        currentSourceType == null ? sourceType : currentSourceType,
                        text.toString(), List.copyOf(occurrenceIds), List.copyOf(sourceBlockIds)));
                text.setLength(0);
                occurrenceIds.clear();
                sourceBlockIds.clear();
            }
            if (currentSourceType == null || startsNewLine) {
                currentSourceType = block.sourceType();
            }
            text.append(block.text());
            if (isUsableId(block.occurrenceId())) {
                occurrenceIds.add(block.occurrenceId().strip());
            }
            if (isUsableId(block.sourceBlockId()) && !sourceBlockIds.contains(block.sourceBlockId().strip())) {
                sourceBlockIds.add(block.sourceBlockId().strip());
            }
        }
        if (text.length() > 0) {
            result.add(new DocxLogicalBlock(
                    currentSourceType == null ? sourceType : currentSourceType,
                    text.toString(), List.copyOf(occurrenceIds), List.copyOf(sourceBlockIds)));
        }
        return List.copyOf(result);
    }

    private List<String> uniqueOccurrenceIds(
            List<String> requested,
            java.util.Set<String> usedOccurrenceIds,
            String fallback) {
        List<String> result = new ArrayList<>();
        for (String requestedId : requested == null ? List.<String>of() : requested) {
            if (!isUsableId(requestedId)) {
                continue;
            }
            String base = requestedId.strip();
            String candidate = base;
            int suffix = 2;
            while (usedOccurrenceIds.contains(candidate) || result.contains(candidate)) {
                candidate = base + "~" + suffix++;
            }
            result.add(candidate);
            usedOccurrenceIds.add(candidate);
        }
        if (result.isEmpty() && isUsableId(fallback)) {
            String base = fallback.strip();
            String candidate = base;
            int suffix = 2;
            while (usedOccurrenceIds.contains(candidate)) {
                candidate = base + "~" + suffix++;
            }
            result.add(candidate);
            usedOccurrenceIds.add(candidate);
        }
        return List.copyOf(result);
    }

    private boolean isUsableId(String value) {
        return value != null && !value.isBlank()
                && !"null".equalsIgnoreCase(value.strip())
                && !"undefined".equalsIgnoreCase(value.strip());
    }

    private List<String> splitDocxLines(String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        return java.util.Arrays.asList(normalized.split("\\n", -1));
    }

    private String normalizeDocxLine(String line) {
        if (line == null) {
            return "";
        }
        return trimDocxHorizontalSpaces(line.replaceAll("[ \\f]+", " "));
    }

    private String trimDocxHorizontalSpaces(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && (value.charAt(start) == ' ' || value.charAt(start) == '\f')) {
            start++;
        }
        while (end > start && (value.charAt(end - 1) == ' ' || value.charAt(end - 1) == '\f')) {
            end--;
        }
        return value.substring(start, end);
    }

    private boolean hasDocxLineContent(String value) {
        return value != null && (!value.isBlank() || value.indexOf('\t') >= 0);
    }

    private ResumeBlockDTO plainSourceBlock(
            String id, int order, String text, Integer page, String sourceType) {
        return plainSourceBlock(id, id, order, text, page, sourceType);
    }

    private ResumeBlockDTO plainSourceBlock(
            String id,
            String sourceBlockId,
            int order,
            String text,
            Integer page,
            String sourceType) {
        return plainSourceBlock(id, List.of(sourceBlockId), List.of(id + "-occurrence"), order, text, page, sourceType);
    }

    private ResumeBlockDTO plainSourceBlock(
            String id,
            List<String> sourceBlockIds,
            List<String> sourceOccurrenceIds,
            int order,
            String text,
            Integer page,
            String sourceType) {
        List<String> effectiveBlockIds = sourceBlockIds == null || sourceBlockIds.isEmpty()
                ? List.of(id + "-block") : sourceBlockIds;
        List<String> effectiveOccurrenceIds = sourceOccurrenceIds == null || sourceOccurrenceIds.isEmpty()
                ? List.of(id + "-occurrence") : sourceOccurrenceIds;
        return ResumeBlockDTO.builder()
                .id(id)
                .index(order)
                .originalIndex(order)
                .displayOrder(order)
                .text(text)
                .page(page)
                .role(ResumeSourceBlockRole.UNKNOWN)
                .sourceBlockIds(List.copyOf(effectiveBlockIds))
                .sourceOccurrenceIds(List.copyOf(effectiveOccurrenceIds))
                .sourceType(sourceType)
                .build();
    }

    private ResumeTextExtractionResult extractDocText(InputStream inputStream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(inputStream);
                WordExtractor extractor = new WordExtractor(document)) {
            return plainResult(normalizeExtractedText(extractor.getText()), "DOC");
        }
    }

    private ResumeTextExtractionResult extractDocxText(InputStream inputStream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(inputStream);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            List<ExtractedTextBlock> textBlocks = collectDocxTextBlocks(document);
            if (textBlocks.isEmpty()) {
                return plainResult(normalizeExtractedText(extractor.getText()), "DOCX");
            }
            String text = normalizeExtractedText(textBlocks);
            return new ResumeTextExtractionResult(
                    text, "DOCX", plainSourceBlocks(textBlocks, "docx"), -1, -1, -1,
                    List.of(), 0, null, false);
        }
    }

    /**
     * Walk the main document with secure DOM so a paragraph's own runs and embedded textboxes
     * stay in their XML order. Header/footer parts are deliberately appended separately: DOCX
     * section references provide structural relationship order, not reliable physical page paint
     * order.
     */
    List<ExtractedTextBlock> collectDocxTextBlocks(XWPFDocument document) {
        List<ExtractedTextBlock> blocks = new ArrayList<>();
        if (document == null) {
            return blocks;
        }

        Document documentXml = parseXml(document.getDocument());
        // A DOCX header is logically encountered before the body and a footer after it. This is
        // still not a page-level rendering order (pagination and linked sections are unavailable
        // here), but it avoids moving contact/header facts to the end of the extracted resume.
        collectHeaderFooterTextBlocksInSectionOrder(document, documentXml, blocks, "header");
        if (!collectDocxDomBodyTextBlocks(documentXml, blocks)) {
            for (IBodyElement bodyElement : document.getBodyElements()) {
                collectBodyElementTextBlocks(bodyElement, blocks);
            }
        }
        collectHeaderFooterTextBlocksInSectionOrder(document, documentXml, blocks, "footer");
        return blocks;
    }

    private boolean collectDocxDomBodyTextBlocks(
            Document documentXml, List<ExtractedTextBlock> blocks) {
        if (documentXml == null || documentXml.getDocumentElement() == null) {
            return false;
        }
        Element body = docxDirectWordChild(documentXml.getDocumentElement(), "body");
        if (body == null) {
            return false;
        }
        collectDocxDomContainer(body, "paragraph", "table", "sdt", "docx-body", blocks);
        return true;
    }

    private void collectDocxDomContainer(
            Node container,
            String paragraphSourceType,
            String tableSourceType,
            String sdtSourceType,
            String structuralPrefix,
            List<ExtractedTextBlock> blocks) {
        int paragraphIndex = 0;
        int tableIndex = 0;
        int sdtIndex = 0;
        int alternateIndex = 0;
        for (Node child : docxChildNodes(container)) {
            if (isAlternateContent(child)) {
                Node selected = selectedAlternateContent(child);
                if (selected != null) {
                    collectDocxDomContainer(
                            selected, paragraphSourceType, tableSourceType, sdtSourceType,
                            docxPath(structuralPrefix, "alternate", alternateIndex++), blocks);
                }
                continue;
            }
            if (isWordElement(child, "p")) {
                collectDocxDomParagraph(
                        child, paragraphSourceType,
                        docxPath(structuralPrefix, "paragraph", paragraphIndex++), blocks);
            } else if (isWordElement(child, "tbl")) {
                collectDocxDomTable(
                        child, tableSourceType,
                        docxPath(structuralPrefix, "table", tableIndex++), blocks);
            } else if (isWordElement(child, "sdt")) {
                collectDocxDomSdt(
                        child, sdtSourceType,
                        docxPath(structuralPrefix, "sdt", sdtIndex++), blocks);
            }
        }
    }

    /** Process table cells, SDT content, or malformed textbox content that has loose runs. */
    private void collectDocxDomNestedContainer(
            Node container,
            String paragraphSourceType,
            String tableSourceType,
            String sdtSourceType,
            String structuralPrefix,
            List<ExtractedTextBlock> blocks) {
        DocxDomParagraphState looseText = new DocxDomParagraphState(
                blocks, paragraphSourceType, structuralPrefix);
        int paragraphIndex = 0;
        int tableIndex = 0;
        int sdtIndex = 0;
        int alternateIndex = 0;
        for (Node child : docxChildNodes(container)) {
            if (isAlternateContent(child)) {
                Node selected = selectedAlternateContent(child);
                if (selected != null) {
                    looseText.markEmbedded();
                    collectDocxDomNestedContainer(
                            selected, paragraphSourceType, tableSourceType, sdtSourceType,
                            docxPath(structuralPrefix, "alternate", alternateIndex++), blocks);
                    looseText.markEmbedded();
                }
                continue;
            }
            if (isWordElement(child, "p")) {
                looseText.markEmbedded();
                collectDocxDomParagraph(
                        child, paragraphSourceType,
                        docxPath(structuralPrefix, "paragraph", paragraphIndex++), blocks);
                looseText.markEmbedded();
            } else if (isWordElement(child, "tbl")) {
                looseText.markEmbedded();
                collectDocxDomTable(
                        child, tableSourceType,
                        docxPath(structuralPrefix, "table", tableIndex++), blocks);
                looseText.markEmbedded();
            } else if (isWordElement(child, "sdt")) {
                looseText.markEmbedded();
                collectDocxDomSdt(
                        child, sdtSourceType,
                        docxPath(structuralPrefix, "sdt", sdtIndex++), blocks);
                looseText.markEmbedded();
            } else {
                collectDocxDomParagraphContent(child, looseText);
            }
        }
    }

    private void collectDocxDomTable(
            Node tableNode, String sourceType, String tablePath, List<ExtractedTextBlock> blocks) {
        int rowIndex = 0;
        for (Node row : docxChildNodes(tableNode)) {
            if (!isWordElement(row, "tr")) {
                continue;
            }
            String rowPath = docxPath(tablePath, "row", rowIndex++);
            int cellIndex = 0;
            for (Node cell : docxChildNodes(row)) {
                if (!isWordElement(cell, "tc")) {
                    continue;
                }
                collectDocxDomNestedContainer(
                        cell,
                        sourceType,
                        sourceType,
                        "sdt",
                        docxPath(rowPath, "cell", cellIndex++),
                        blocks);
            }
        }
    }

    private void collectDocxDomSdt(
            Node sdtNode, String sourceType, String structuralPrefix, List<ExtractedTextBlock> blocks) {
        Element content = docxDirectWordChild(sdtNode, "sdtContent");
        if (content == null) {
            return;
        }
        collectDocxDomNestedContainer(
                content,
                sourceType,
                "table",
                "sdt",
                structuralPrefix,
                blocks);
    }

    private void collectDocxDomParagraph(
            Node paragraphNode,
            String sourceType,
            String sourceBlockId,
            List<ExtractedTextBlock> blocks) {
        DocxDomParagraphState state = new DocxDomParagraphState(blocks, sourceType, sourceBlockId);
        for (Node child : docxChildNodes(paragraphNode)) {
            if (!isWordElement(child, "pPr")) {
                collectDocxDomParagraphContent(child, state);
            }
        }
    }

    private void collectDocxDomParagraphContent(Node node, DocxDomParagraphState state) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        if (isAlternateContent(node)) {
            Node selected = selectedAlternateContent(node);
            if (selected != null) {
                for (Node child : docxChildNodes(selected)) {
                    collectDocxDomParagraphContent(child, state);
                }
            }
            return;
        }
        if (isWordElement(node, "r")) {
            collectDocxDomRun(node, state, state.nextRunIndex());
            return;
        }
        if (isWordElement(node, "txbxContent")) {
            collectDocxDomTextBox(node, state);
            return;
        }
        if (isWordElement(node, "sdt")) {
            collectDocxDomSdtFromParagraph(node, state);
            return;
        }
        if (isWordElement(node, "t")) {
            state.emitSynthetic(node.getTextContent());
            return;
        }
        if (isWordElement(node, "tab") || isWordElement(node, "ptab")) {
            state.emitSynthetic("\t");
            return;
        }
        if (isWordElement(node, "br") || isWordElement(node, "cr")
                || isWordElement(node, "lastRenderedPageBreak")) {
            state.emitSynthetic("\n");
            return;
        }
        if (isWordElement(node, "pPr") || isWordElement(node, "rPr")) {
            return;
        }
        for (Node child : docxChildNodes(node)) {
            collectDocxDomParagraphContent(child, state);
        }
    }

    private void collectDocxDomRun(
            Node runNode, DocxDomParagraphState state, int runIndex) {
        DocxDomRunState run = new DocxDomRunState(runIndex);
        for (Node child : docxChildNodes(runNode)) {
            collectDocxDomRunContent(child, state, run);
        }
        flushDocxDomRun(state, run);
    }

    private void collectDocxDomRunContent(
            Node node, DocxDomParagraphState state, DocxDomRunState run) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        if (isWordElement(node, "rPr")) {
            return;
        }
        if (isAlternateContent(node)) {
            Node selected = selectedAlternateContent(node);
            if (selected != null) {
                for (Node child : docxChildNodes(selected)) {
                    collectDocxDomRunContent(child, state, run);
                }
            }
            return;
        }
        if (isWordElement(node, "t")) {
            String text = node.getTextContent();
            if (text != null) {
                run.text.append(text);
            }
            return;
        }
        if (isWordElement(node, "tab") || isWordElement(node, "ptab")) {
            run.text.append('\t');
            return;
        }
        if (isWordElement(node, "br") || isWordElement(node, "cr")
                || isWordElement(node, "lastRenderedPageBreak")) {
            run.text.append('\n');
            return;
        }
        if (isWordElement(node, "txbxContent")) {
            flushDocxDomRun(state, run);
            int before = state.blockCount();
            collectDocxDomTextBoxContent(node, state.nextTextBoxPath(), state.blocks());
            if (state.blockCount() > before) {
                state.markEmbedded();
            }
            run.partIndex++;
            return;
        }
        if (isWordElement(node, "sdt")) {
            flushDocxDomRun(state, run);
            int before = state.blockCount();
            collectDocxDomSdt(
                    node,
                    docxSourceTypeForNestedSdt(state.sourceType()),
                    state.nextSdtPath(),
                    state.blocks());
            if (state.blockCount() > before) {
                state.markEmbedded();
            }
            run.partIndex++;
            return;
        }
        for (Node child : docxChildNodes(node)) {
            collectDocxDomRunContent(child, state, run);
        }
    }

    private void flushDocxDomRun(DocxDomParagraphState state, DocxDomRunState run) {
        if (run.text.length() == 0) {
            return;
        }
        state.emitRun(run.text.toString(), run.runIndex, run.partIndex++);
        run.text.setLength(0);
    }

    private void collectDocxDomTextBox(Node textBoxContent, DocxDomParagraphState state) {
        int before = state.blockCount();
        collectDocxDomTextBoxContent(
                textBoxContent, state.nextTextBoxPath(), state.blocks());
        if (state.blockCount() > before) {
            state.markEmbedded();
        }
    }

    private void collectDocxDomTextBoxContent(
            Node textBoxContent, String structuralPrefix, List<ExtractedTextBlock> blocks) {
        collectDocxDomNestedContainer(
                textBoxContent, "textbox", "table", "sdt", structuralPrefix, blocks);
    }

    private void collectDocxDomSdtFromParagraph(
            Node sdtNode, DocxDomParagraphState state) {
        int before = state.blockCount();
        collectDocxDomSdt(
                sdtNode,
                docxSourceTypeForNestedSdt(state.sourceType()),
                state.nextSdtPath(),
                state.blocks());
        if (state.blockCount() > before) {
            state.markEmbedded();
        }
    }

    private String docxSourceTypeForNestedSdt(String parentSourceType) {
        return "sdt";
    }

    /**
     * Emit referenced header/footer occurrences in section XML relationship order. A shared part
     * is emitted once per section reference (not once per physical page), so linked sections keep
     * distinct source occurrences. This is structural ordering only; Word's physical page paint
     * order cannot be inferred reliably from these parts and is intentionally not claimed here.
     */
    private void collectHeaderFooterTextBlocksInSectionOrder(
            XWPFDocument document,
            Document documentXml,
            List<ExtractedTextBlock> blocks,
            String requestedSourceType) {
        Set<Object> referencedParts = Collections.newSetFromMap(new IdentityHashMap<>());
        NodeList sections = documentXml == null
                ? null : documentXml.getElementsByTagNameNS(WORD_NAMESPACE, "sectPr");
        if (sections != null) {
            for (int sectionIndex = 0; sectionIndex < sections.getLength(); sectionIndex++) {
                Node section = sections.item(sectionIndex);
                for (Node reference : docxChildNodes(section)) {
                    if (!isWordElement(reference, "headerReference")
                            && !isWordElement(reference, "footerReference")) {
                        continue;
                    }
                    String referenceSourceType = isWordElement(reference, "headerReference")
                            ? "header" : "footer";
                    if (!referenceSourceType.equals(requestedSourceType)) {
                        continue;
                    }
                    String relationshipId = docxRelationshipId(reference);
                    if (relationshipId == null) {
                        continue;
                    }
                    POIXMLDocumentPart part = document.getRelationById(relationshipId);
                    if (!(part instanceof XWPFHeaderFooter headerFooter)) {
                        continue;
                    }
                    referencedParts.add(headerFooter);
                    String sourceType = docxHeaderFooterSourceType(headerFooter);
                    if (sourceType != null) {
                        collectHeaderFooterTextBlocks(
                                headerFooter,
                                sourceType,
                                "docx-section-" + docxIndex(sectionIndex) + "-"
                                        + sourceType + "-" + docxSafePathPart(relationshipId),
                                blocks);
                    }
                }
            }
        }

        // Unreferenced parts remain available for compatibility. Package relation order is
        // deterministic, but it is not a physical page order either.
        int unreferencedIndex = 0;
        for (POIXMLDocumentPart part : document.getRelations()) {
            if (!(part instanceof XWPFHeaderFooter headerFooter)
                    || referencedParts.contains(headerFooter)) {
                continue;
            }
            String sourceType = docxHeaderFooterSourceType(headerFooter);
            if (sourceType == null || !sourceType.equals(requestedSourceType)) {
                continue;
            }
            referencedParts.add(headerFooter);
            String relationshipId = document.getRelationId(part);
            String relationToken = relationshipId == null
                    ? docxIndex(unreferencedIndex) : docxSafePathPart(relationshipId);
            collectHeaderFooterTextBlocks(
                    headerFooter,
                    sourceType,
                    "docx-unreferenced-" + docxIndex(unreferencedIndex++) + "-"
                            + sourceType + "-" + relationToken,
                    blocks);
        }

        // Conservative fallback for unusual POI packages that do not expose a header/footer
        // through the generic relations collection.
        if ("header".equals(requestedSourceType)) {
            for (XWPFHeader header : document.getHeaderList()) {
                if (referencedParts.add(header)) {
                    collectHeaderFooterTextBlocks(
                            header, "header", "docx-fallback-header-" + docxIndex(unreferencedIndex++), blocks);
                }
            }
        } else if ("footer".equals(requestedSourceType)) {
            for (XWPFFooter footer : document.getFooterList()) {
                if (referencedParts.add(footer)) {
                    collectHeaderFooterTextBlocks(
                            footer, "footer", "docx-fallback-footer-" + docxIndex(unreferencedIndex++), blocks);
                }
            }
        }
    }

    private String docxHeaderFooterSourceType(XWPFHeaderFooter headerFooter) {
        if (headerFooter instanceof XWPFHeader) {
            return "header";
        }
        if (headerFooter instanceof XWPFFooter) {
            return "footer";
        }
        return null;
    }

    private void collectHeaderFooterTextBlocks(
            XWPFHeaderFooter headerFooter,
            String sourceType,
            String structuralPrefix,
            List<ExtractedTextBlock> blocks) {
        if (headerFooter == null) {
            return;
        }
        Document headerFooterXml = parseXml(headerFooter._getHdrFtr());
        if (headerFooterXml != null && headerFooterXml.getDocumentElement() != null) {
            collectDocxDomContainer(
                    headerFooterXml.getDocumentElement(),
                    sourceType,
                    "table",
                    "sdt",
                    structuralPrefix,
                    blocks);
            return;
        }
        collectHeaderFooterTextBlocks(headerFooter, sourceType, blocks);
    }

    private String docxRelationshipId(Node reference) {
        if (!(reference instanceof Element element)) {
            return null;
        }
        String value = element.getAttributeNS(OFFICE_RELATIONSHIP_NAMESPACE, "id");
        if (value == null || value.isBlank()) {
            value = element.getAttribute("r:id");
        }
        return value == null || value.isBlank() ? null : value;
    }

    private Element docxDirectWordChild(Node node, String localName) {
        for (Node child : docxChildNodes(node)) {
            if (isWordElement(child, localName)) {
                return (Element) child;
            }
        }
        return null;
    }

    private List<Node> docxChildNodes(Node parent) {
        if (parent == null || parent.getChildNodes() == null) {
            return List.of();
        }
        List<Node> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            result.add(children.item(index));
        }
        return result;
    }

    private boolean isAlternateContent(Node node) {
        return node instanceof Element element
                && MARKUP_COMPATIBILITY_NAMESPACE.equals(element.getNamespaceURI())
                && "AlternateContent".equals(element.getLocalName());
    }

    /** Pick one markup-compatibility branch so Choice and Fallback are never both extracted. */
    private Node selectedAlternateContent(Node alternateContent) {
        Node fallback = null;
        for (Node child : docxChildNodes(alternateContent)) {
            if (!(child instanceof Element element)
                    || !MARKUP_COMPATIBILITY_NAMESPACE.equals(element.getNamespaceURI())) {
                continue;
            }
            if ("Choice".equals(element.getLocalName())) {
                return element;
            }
            if ("Fallback".equals(element.getLocalName())) {
                fallback = element;
            }
        }
        return fallback;
    }

    private boolean isWordElement(Node node, String localName) {
        return node instanceof Element element
                && WORD_NAMESPACE.equals(element.getNamespaceURI())
                && localName.equals(element.getLocalName());
    }

    private static String docxPath(String parent, String component, int value) {
        return (parent == null || parent.isBlank() ? "docx" : parent)
                + "-" + component + "-" + docxIndex(value);
    }

    private static String docxIndex(int value) {
        return String.format(Locale.ROOT, "%04d", Math.max(0, value));
    }

    private String docxSafePathPart(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private void collectBodyElementTextBlocks(IBodyElement bodyElement, List<ExtractedTextBlock> blocks) {
        if (bodyElement instanceof XWPFParagraph paragraph) {
            collectParagraphTextBlocks(paragraph, "paragraph", blocks);
        } else if (bodyElement instanceof XWPFTable table) {
            collectTableTextBlocks(table, blocks);
        } else if (bodyElement instanceof XWPFSDT structuredDocumentTag) {
            addTextBlock(blocks, "sdt", structuredDocumentTag.getContent().getText());
        }
    }

    private void collectParagraphTextBlocks(
            XWPFParagraph paragraph, String sourceType, List<ExtractedTextBlock> blocks) {
        if (paragraph == null) {
            return;
        }
        List<String> textBoxTexts = embeddedTextBoxTexts(paragraph.getCTP());
        if (textBoxTexts.isEmpty()) {
            addTextBlock(blocks, sourceType, paragraph.getText());
            return;
        }

        // XWPFParagraph#getText() may include text inside w:txbxContent. Read only the
        // paragraph's own runs first; otherwise the same textbox is emitted once as a paragraph
        // and once as a textbox. Textboxes are then appended in their XML order.
        addTextBlock(blocks, sourceType, textOutsideTextBoxes(paragraph.getCTP()));
        addTextBoxBlocks(blocks, textBoxTexts);
    }

    private void collectTableTextBlocks(XWPFTable table, List<ExtractedTextBlock> blocks) {
        collectTableTextBlocks(table, blocks, "table");
    }

    private void collectTableTextBlocks(
            XWPFTable table, List<ExtractedTextBlock> blocks, String sourceType) {
        if (table == null) {
            return;
        }
        for (XWPFTableRow row : table.getRows()) {
            if (row == null) {
                continue;
            }
            for (XWPFTableCell cell : row.getTableCells()) {
                if (cell == null) {
                    continue;
                }
                for (IBodyElement bodyElement : cell.getBodyElements()) {
                    if (bodyElement instanceof XWPFParagraph paragraph) {
                        collectParagraphTextBlocks(paragraph, sourceType, blocks);
                    } else if (bodyElement instanceof XWPFTable nestedTable) {
                        collectTableTextBlocks(nestedTable, blocks, sourceType);
                    } else if (bodyElement instanceof XWPFSDT structuredDocumentTag) {
                        addTextBlock(blocks, "sdt", structuredDocumentTag.getContent().getText());
                    }
                }
            }
        }
    }

    private void collectHeaderFooterTextBlocks(
            XWPFHeaderFooter headerFooter, String sourceType, List<ExtractedTextBlock> blocks) {
        if (headerFooter == null) {
            return;
        }
        for (IBodyElement bodyElement : headerFooter.getBodyElements()) {
            if (bodyElement instanceof XWPFParagraph paragraph) {
                collectParagraphTextBlocks(paragraph, sourceType, blocks);
            } else if (bodyElement instanceof XWPFTable table) {
                collectTableTextBlocks(table, blocks, sourceType);
            } else if (bodyElement instanceof XWPFSDT structuredDocumentTag) {
                addTextBlock(blocks, "sdt", structuredDocumentTag.getContent().getText());
            }
        }
    }

    /**
     * XMLBeans' XPath implementation loads Saxon lazily for selectPath(). Keep extraction
     * self-contained: DOCX XML is parsed with the JDK namespace-aware DOM parser, with external
     * entities disabled. This also gives us an explicit ancestor check so textbox text is not
     * emitted once as a paragraph and again as a shape.
     */
    private List<String> embeddedTextBoxTexts(XmlObject owner) {
        Document document = parseXml(owner);
        if (document == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        NodeList paragraphs = document.getElementsByTagNameNS(WORD_NAMESPACE, "p");
        for (int index = 0; index < paragraphs.getLength(); index++) {
            Node paragraph = paragraphs.item(index);
            if (!isDirectTextBoxParagraph(paragraph)) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            appendDocxInlineContent(paragraph, text);
            if (!text.toString().isBlank()) {
                result.add(text.toString().strip());
            }
        }
        if (result.isEmpty()) {
            // Keep a fallback for malformed textbox XML that has runs but no paragraph wrapper.
            NodeList textNodes = document.getElementsByTagNameNS(WORD_NAMESPACE, "t");
            for (int index = 0; index < textNodes.getLength(); index++) {
                Node textNode = textNodes.item(index);
                if (!insideTextBox(textNode)) {
                    continue;
                }
                String text = textNode.getTextContent();
                if (text != null && !text.isBlank()) {
                    result.add(text.strip());
                }
            }
        }
        return result;
    }

    private boolean isDirectTextBoxParagraph(Node node) {
        if (!insideTextBox(node)) {
            return false;
        }
        Node current = node == null ? null : node.getParentNode();
        while (current != null) {
            if (isWordElement(current, "p")) {
                return false;
            }
            if (isWordElement(current, "txbxContent")) {
                return true;
            }
            current = current.getParentNode();
        }
        return false;
    }

    private void appendDocxInlineContent(Node node, StringBuilder result) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        if (isAlternateContent(node)) {
            Node selected = selectedAlternateContent(node);
            if (selected != null) {
                for (Node child : docxChildNodes(selected)) {
                    appendDocxInlineContent(child, result);
                }
            }
            return;
        }
        if (isWordElement(node, "t")) {
            if (node.getTextContent() != null) {
                result.append(node.getTextContent());
            }
            return;
        }
        if (isWordElement(node, "tab") || isWordElement(node, "ptab")) {
            result.append('\t');
            return;
        }
        if (isWordElement(node, "br") || isWordElement(node, "cr")
                || isWordElement(node, "lastRenderedPageBreak")) {
            result.append('\n');
            return;
        }
        if (isWordElement(node, "instrText") || isWordElement(node, "delText")) {
            return;
        }
        for (Node child : docxChildNodes(node)) {
            appendDocxInlineContent(child, result);
        }
    }

    private String textOutsideTextBoxes(XmlObject owner) {
        Document document = parseXml(owner);
        if (document == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        appendDocxOutsideTextBoxContent(document.getDocumentElement(), result);
        return result.toString();
    }

    /** Fallback walker retaining inline controls and XML order while excluding shape text. */
    private void appendDocxOutsideTextBoxContent(Node node, StringBuilder result) {
        if (node == null || node.getNodeType() != Node.ELEMENT_NODE
                || insideTextBox(node) || isWordElement(node, "txbxContent")) {
            return;
        }
        if (isAlternateContent(node)) {
            Node selected = selectedAlternateContent(node);
            if (selected != null) {
                for (Node child : docxChildNodes(selected)) {
                    appendDocxOutsideTextBoxContent(child, result);
                }
            }
            return;
        }
        if (isWordElement(node, "t")) {
            String text = node.getTextContent();
            if (text != null) {
                result.append(text);
            }
            return;
        }
        if (isWordElement(node, "tab") || isWordElement(node, "ptab")) {
            result.append('\t');
            return;
        }
        if (isWordElement(node, "br") || isWordElement(node, "cr")
                || isWordElement(node, "lastRenderedPageBreak")) {
            result.append('\n');
            return;
        }
        if (isWordElement(node, "instrText") || isWordElement(node, "delText")) {
            return;
        }
        for (Node child : docxChildNodes(node)) {
            appendDocxOutsideTextBoxContent(child, result);
        }
    }

    private List<String> extractTextBoxTexts(XWPFDocument document) {
        if (document == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        String currentSourceBlockId = null;
        StringBuilder current = new StringBuilder();
        for (ExtractedTextBlock block : collectDocxTextBlocks(document)) {
            if (block == null || !isTextBoxSourceType(block.sourceType())
                    || block.text() == null || block.text().isEmpty()) {
                continue;
            }
            if (currentSourceBlockId != null
                    && !currentSourceBlockId.equals(block.sourceBlockId())) {
                result.add(current.toString());
                current.setLength(0);
            }
            if (currentSourceBlockId == null
                    || !currentSourceBlockId.equals(block.sourceBlockId())) {
                currentSourceBlockId = block.sourceBlockId();
            }
            if (block.startsNewLine() && current.length() > 0) {
                current.append('\n');
            }
            current.append(block.text());
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return List.copyOf(result);
    }

    private boolean isTextBoxSourceType(String sourceType) {
        return "textbox".equals(sourceType)
                || sourceType != null && sourceType.startsWith("textbox-");
    }

    private Document parseXml(XmlObject owner) {
        if (owner == null) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(
                    new InputSource(new StringReader(owner.xmlText())));
        } catch (Exception exception) {
            log.debug("DOCX XML fragment could not be inspected: exceptionType={}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private boolean insideTextBox(Node node) {
        Node current = node == null ? null : node.getParentNode();
        while (current != null) {
            if (current instanceof Element element
                    && WORD_NAMESPACE.equals(element.getNamespaceURI())
                    && "txbxContent".equals(element.getLocalName())) {
                return true;
            }
            current = current.getParentNode();
        }
        return false;
    }

    private void addTextBoxBlocks(List<ExtractedTextBlock> blocks, List<String> texts) {
        // Identical text in two shapes is still two source occurrences. Any extraction noise is
        // handled by quality/provenance accounting rather than text-based deletion here.
        for (String text : texts == null ? List.<String>of() : texts) {
            addTextBlock(blocks, "textbox", text);
        }
    }

    private void addTextBlock(
            List<ExtractedTextBlock> blocks,
            String sourceType,
            String text,
            String sourceBlockId) {
        if (text != null && !text.isBlank()) {
            String effectiveSourceBlockId = sourceBlockId == null || sourceBlockId.isBlank()
                    ? "docx-block-" + blocks.size() : sourceBlockId;
            String occurrenceId = effectiveSourceBlockId + "-occurrence-0000";
            blocks.add(new ExtractedTextBlock(
                    sourceType, text.strip(), occurrenceId, effectiveSourceBlockId, true));
        }
    }

    private void addTextBlock(List<ExtractedTextBlock> blocks, String sourceType, String text) {
        addTextBlock(blocks, sourceType, text, null);
    }

    List<String> extractDocxTextBoxText(XWPFDocument document) {
        return extractTextBoxTexts(document);
    }

    private String normalizeFileType(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            throw new BusinessException(400, "简历文件类型不能为空");
        }
        return fileType.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 规范提取文本的空白与行边界，但不按文本去重；相同内容可能来自不同的真实来源
     * occurrence，必须由后续质量与 provenance 校验决定是否为抽取噪声。
     */
    private String normalizeExtractedText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String rawLine : text.lines().toList()) {
            String line = rawLine == null ? "" : rawLine.strip();
            if (!line.isBlank()) {
                lines.add(line.replaceAll("\\s+", " "));
            }
        }
        return String.join("\n", lines).strip();
    }

    private String normalizeExtractedText(List<ExtractedTextBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (DocxLogicalBlock logicalBlock : logicalDocxBlocks(blocks, "docx")) {
            for (String rawLine : splitDocxLines(logicalBlock.text())) {
                String line = normalizeDocxLine(rawLine);
                if (hasDocxLineContent(line)) {
                    lines.add(line);
                }
            }
        }
        return String.join("\n", lines).strip();
    }

    private static final class DocxDomRunState {

        private final int runIndex;
        private final StringBuilder text = new StringBuilder();
        private int partIndex;

        private DocxDomRunState(int runIndex) {
            this.runIndex = runIndex;
        }
    }

    private static final class DocxDomParagraphState {

        private final List<ExtractedTextBlock> blocks;
        private final String sourceType;
        private final String sourceBlockId;
        private int runIndex;
        private int syntheticIndex;
        private int textBoxIndex;
        private int sdtIndex;
        private boolean lineBreakBefore = true;

        private DocxDomParagraphState(
                List<ExtractedTextBlock> blocks, String sourceType, String sourceBlockId) {
            this.blocks = blocks;
            this.sourceType = sourceType;
            this.sourceBlockId = sourceBlockId;
        }

        private int nextRunIndex() {
            return runIndex++;
        }

        private String nextTextBoxPath() {
            return docxPath(sourceBlockId, "textbox", textBoxIndex++);
        }

        private String nextSdtPath() {
            return docxPath(sourceBlockId, "sdt", sdtIndex++);
        }

        private void emitRun(String text, int runIndex, int partIndex) {
            emit(text,
                    sourceBlockId + "-run-" + docxIndex(runIndex)
                            + "-part-" + docxIndex(partIndex));
        }

        private void emitSynthetic(String text) {
            emit(text, sourceBlockId + "-text-" + docxIndex(syntheticIndex++));
        }

        private void emit(String text, String occurrenceId) {
            if (text == null || text.isEmpty()) {
                return;
            }
            blocks.add(new ExtractedTextBlock(
                    sourceType, text, occurrenceId, sourceBlockId, lineBreakBefore));
            lineBreakBefore = false;
        }

        private void markEmbedded() {
            lineBreakBefore = true;
        }

        private int blockCount() {
            return blocks.size();
        }

        private List<ExtractedTextBlock> blocks() {
            return blocks;
        }

        private String sourceType() {
            return sourceType;
        }

        private static String docxPath(String parent, String component, int value) {
            return (parent == null || parent.isBlank() ? "docx" : parent)
                    + "-" + component + "-" + docxIndex(value);
        }

        private static String docxIndex(int value) {
            return String.format(Locale.ROOT, "%04d", Math.max(0, value));
        }
    }

    private record DocxLogicalBlock(
            String sourceType,
            String text,
            List<String> occurrenceIds,
            List<String> sourceBlockIds) {
    }

    record ExtractedTextBlock(
            String sourceType,
            String text,
            String occurrenceId,
            String sourceBlockId,
            boolean startsNewLine) {

        ExtractedTextBlock(String sourceType, String text) {
            this(sourceType, text, null, null, true);
        }

        ExtractedTextBlock(String sourceType, String text, String occurrenceId) {
            this(sourceType, text, occurrenceId, null, true);
        }

        ExtractedTextBlock(
                String sourceType, String text, String occurrenceId, String sourceBlockId) {
            this(sourceType, text, occurrenceId, sourceBlockId, true);
        }
    }

    private record PdfPageText(int page, String text) {
    }

    private record PdfCandidate(String text, List<ResumeBlockDTO> sourceBlocks) {
    }
}
