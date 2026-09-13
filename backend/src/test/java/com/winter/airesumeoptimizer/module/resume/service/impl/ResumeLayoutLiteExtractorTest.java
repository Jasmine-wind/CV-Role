package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.fixture.ResumeFixtureFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;

class ResumeLayoutLiteExtractorTest {

    private final ResumeLayoutLiteExtractor extractor = new ResumeLayoutLiteExtractor();
    private final ResumeTextCleanServiceImpl cleaner = new ResumeTextCleanServiceImpl();

    @Test
    void pdfLayoutExtractionIsDeterministicAndRetainsCoordinates() throws IOException {
        byte[] pdf = ResumeFixtureFactory.renderPdf(List.of(
                "Synthetic Candidate Layout",
                "工作经历",
                "Synthetic Studio  2022-01 至今",
                "负责构建可观测的 Java 服务"));

        var first = extractor.extract(pdf);
        var second = extractor.extract(pdf);

        assertThat(first.text()).isEqualTo(second.text());
        assertThat(first.blocks()).hasSizeGreaterThan(2);
        assertThat(first.blocks()).extracting(ResumeBlockDTO::getId)
                .containsExactlyElementsOf(second.blocks().stream().map(ResumeBlockDTO::getId).toList());
        assertThat(first.blocks()).allSatisfy(block -> {
            assertThat(block.getPage()).isPositive();
            assertThat(block.getX()).isNotNull();
            assertThat(block.getY()).isNotNull();
            assertThat(block.getSourceBlockIds()).containsExactly(block.getId());
            assertThat(block.getSourceOccurrenceIds())
                    .containsExactly(block.getId() + "-occurrence")
                    .doesNotContainAnyElementsOf(block.getSourceBlockIds());
            assertThat(block.getSourceType()).isEqualTo("pdf-layout-lite");
        });
    }

    @Test
    void sameBaselineColumnsBecomeSeparateVisualLines() throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.beginText();
                content.newLineAtOffset(72, 700);
                content.showText("LEFT");
                content.endText();
                content.beginText();
                content.newLineAtOffset(110, 700);
                content.showText("RIGHT");
                content.endText();
            }
            document.save(output);

            var result = extractor.extract(output.toByteArray());

            assertThat(result.hasParallelColumns()).isTrue();
            assertThat(result.blocks()).extracting(ResumeBlockDTO::getText)
                    .containsExactly("LEFT", "RIGHT");
            assertThat(result.blocks()).extracting(ResumeBlockDTO::getY)
                    .allSatisfy(y -> assertThat(y).isNotNull());
            assertThat(result.blocks().get(0).getY())
                    .isEqualTo(result.blocks().get(1).getY());
        }
    }

    @Test
    void rotatedTextKeepsReadableOrderAndPositiveGeometry() throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            page.setRotation(90);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.beginText();
                content.setTextMatrix(Matrix.getRotateInstance(Math.PI, 300, 300));
                content.showText("ROTATED");
                content.endText();
            }
            document.save(output);

            var result = extractor.extract(output.toByteArray());
            var block = result.blocks().stream().findFirst().orElseThrow();

            assertThat(result.text()).isEqualTo("ROTATED");
            assertThat(block.getPage()).isEqualTo(1);
            assertThat(block.getFontSize()).isPositive();
            assertThat(block.getWidth()).isPositive();
            assertThat(block.getHeight()).isPositive();
            assertThat(block.getX()).isGreaterThanOrEqualTo(0d);
            assertThat(block.getY()).isGreaterThanOrEqualTo(0d);
        }
    }

    @Test
    void physicalPageNumbersSurviveBlankPages() throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage first = new PDPage(PDRectangle.LETTER);
            document.addPage(first);
            try (PDPageContentStream content = new PDPageContentStream(document, first)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.beginText();
                content.newLineAtOffset(72, 700);
                content.showText("FIRST PAGE");
                content.endText();
            }
            document.addPage(new PDPage(PDRectangle.LETTER));
            PDPage third = new PDPage(PDRectangle.LETTER);
            document.addPage(third);
            try (PDPageContentStream content = new PDPageContentStream(document, third)) {
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.beginText();
                content.newLineAtOffset(72, 700);
                content.showText("THIRD PAGE");
                content.endText();
            }
            document.save(output);

            var result = extractor.extract(output.toByteArray());

            assertThat(result.pageCount()).isEqualTo(3);
            assertThat(result.blocks()).extracting(ResumeBlockDTO::getPage)
                    .containsExactly(1, 3);
            assertThat(result.blocks()).extracting(ResumeBlockDTO::getId)
                    .containsExactly("layout-p001-l0000", "layout-p003-l0001");
        }
    }

    @Test
    void blankDocumentRetainsItsPhysicalPageCount() throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.LETTER));
            document.addPage(new PDPage(PDRectangle.LETTER));

            var result = extractor.extract(document);

            assertThat(result.pageCount()).isEqualTo(2);
            assertThat(result.text()).isEmpty();
            assertThat(result.blocks()).isEmpty();
        }
    }

    @Test
    void wrappedVisualLinesMergeWithoutDroppingSourceIds() {
        List<ResumeBlockDTO> source = List.of(
                ResumeBlockDTO.builder()
                        .id("layout-p001-l0000")
                        .index(0).originalIndex(0).displayOrder(0)
                        .text("This long source sentence continues without a terminal punctuation")
                        .page(1).x(72d).y(100d).width(260d).height(12d).fontSize(10d)
                        .sourceBlockIds(List.of("layout-p001-l0000"))
                        .sourceType("pdf-layout-lite")
                        .build(),
                ResumeBlockDTO.builder()
                        .id("layout-p001-l0001")
                        .index(1).originalIndex(1).displayOrder(1)
                        .text("on the following visual line and remains source backed")
                        .page(1).x(72d).y(112d).width(230d).height(12d).fontSize(10d)
                        .sourceBlockIds(List.of("layout-p001-l0001"))
                        .sourceType("pdf-layout-lite")
                        .build());

        var result = cleaner.cleanAndSplitSections("ignored", source);
        var block = result.getSections().stream()
                .flatMap(section -> section.getBlocks().stream())
                .findFirst()
                .orElseThrow();

        assertThat(block.getText()).contains("following visual line");
        assertThat(block.getSourceBlockIds())
                .containsExactly("layout-p001-l0000", "layout-p001-l0001");
    }

    @Test
    void layoutLiteOnlyWinsWhenItHasARealHealthAdvantage() {
        String fragmented = "x".repeat(700);
        String healthy = "Synthetic Candidate\nExperience\nBuilt reliable Java services";

        var selection = new ResumePdfTextCandidateSelector().select(fragmented, fragmented, healthy);

        assertThat(selection.candidateType()).isEqualTo(ResumePdfTextCandidateSelector.CandidateType.LAYOUT_LITE);
        assertThat(selection.layoutLiteScore())
                .isGreaterThanOrEqualTo(selection.legacyScore() + ResumePdfTextCandidateSelector.LAYOUT_LITE_MIN_ADVANTAGE);
    }
}
