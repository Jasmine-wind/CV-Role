package com.winter.airesumeoptimizer.module.export.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.infra.render.PdfLayoutInspection;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExportPreflightCheckerTest {

    private final ExportPreflightChecker checker = new ExportPreflightChecker();

    @Test
    void reportsMissingContactActualPagesAndDetectedOverflow() {
        ResumeDocumentDTO document = documentWithContacts();

        ExportPreflight result = checker.check(document, new PdfLayoutInspection(3, true, 12), false);

        assertThat(result.pageCount()).isEqualTo(3);
        assertThat(result.missingContact()).isTrue();
        assertThat(result.pageLimitExceeded()).isTrue();
        assertThat(result.overflowDetected()).isTrue();
        assertThat(result.needsReview()).isFalse();
        assertThat(result.warnings()).containsExactly(
                "MISSING_CONTACT", "PAGE_LIMIT_EXCEEDED", "CONTENT_OUT_OF_PAGE_BOUNDS");
    }

    @Test
    void cityMetadataOrUntypedValueDoesNotCountAsReachableContact() {
        ResumeDocumentDTO document = documentWithContacts(
                contact("LOCATION", "所在地", "北京"),
                contact("OTHER", "城市", "上海"));

        ExportPreflight result = checker.check(document, new PdfLayoutInspection(1, false, 20), false);

        assertThat(result.missingContact()).isTrue();
    }

    @Test
    void acceptsTypedContactAndTwoPages() {
        ResumeDocumentDTO document = documentWithContacts(
                contact("EMAIL", "邮箱", "zhang@example.com"));

        ExportPreflight result = checker.check(document, new PdfLayoutInspection(2, false, 24), false);

        assertThat(result.missingContact()).isFalse();
        assertThat(result.pageLimitExceeded()).isFalse();
        assertThat(result.overflowDetected()).isFalse();
        assertThat(result.orphanFinalPage()).isFalse();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void twoPagesWithSparseFinalPageIsOrphan() {
        ResumeDocumentDTO document = documentWithContacts(
                contact("PHONE", "电话", "13800000000"));

        ExportPreflight result = checker.check(document, new PdfLayoutInspection(2, false, 1), false);

        assertThat(result.orphanFinalPage()).isTrue();
        assertThat(result.warnings()).contains("ORPHAN_FINAL_PAGE");
    }

    @Test
    void legacyInspectionWithoutPaginationFactsDoesNotInventOrphan() {
        ResumeDocumentDTO document = documentWithContacts(
                contact("PHONE", "电话", "13800000000"));

        ExportPreflight result = checker.check(document, new PdfLayoutInspection(2, false), false);

        assertThat(result.orphanFinalPage()).isFalse();
    }

    @Test
    void singlePageIsNeverOrphanRegardlessOfLineCount() {
        ResumeDocumentDTO document = documentWithContacts(
                contact("PHONE", "电话", "13800000000"));

        ExportPreflight result = checker.check(document, new PdfLayoutInspection(1, false, 0), true);

        assertThat(result.orphanFinalPage()).isFalse();
        assertThat(result.needsReview()).isTrue();
    }

    @Test
    void reportsUnreadableFontAsPdfWarning() {
        ResumeDocumentDTO document = documentWithContacts(
                contact("PHONE", "电话", "13800000000"));

        ExportPreflight result = checker.check(document, new PdfLayoutInspection(1, false, 20, 8.0f), false);

        assertThat(result.readabilityTooSmall()).isTrue();
        assertThat(result.warnings()).contains("READABILITY_TOO_SMALL");
    }

    @Test
    void reportsSparseFinalPageEvenWhenItHasMoreThanThreeLines() {
        ResumeDocumentDTO document = documentWithContacts(
                contact("PHONE", "电话", "13800000000"));

        ExportPreflight result = checker.check(
                document,
                new PdfLayoutInspection(2, false, 8, 9.0f, 0.19f),
                false);

        assertThat(result.orphanFinalPage()).isTrue();
        assertThat(result.warnings()).contains("ORPHAN_FINAL_PAGE");
    }

    @Test
    void acceptsContentAtTheSparseFinalPageBoundary() {
        ResumeDocumentDTO document = documentWithContacts(
                contact("PHONE", "电话", "13800000000"));

        ExportPreflight result = checker.check(
                document,
                new PdfLayoutInspection(2, false, 8, 9.0f,
                        ExportPreflightChecker.MIN_FINAL_PAGE_CONTENT_RATIO),
                false);

        assertThat(result.orphanFinalPage()).isFalse();
    }

    private ResumeDocumentContactDTO contact(String type, String label, String value) {
        return ResumeDocumentContactDTO.builder()
                .id("c-" + System.nanoTime())
                .type(type)
                .label(label)
                .value(value)
                .build();
    }

    private ResumeDocumentDTO documentWithContacts(ResumeDocumentContactDTO... contacts) {
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder().name("张三").contacts(List.of(contacts)).build())
                .sections(List.of())
                .build();
    }
}
