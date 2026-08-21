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
        ResumeDocumentDTO document = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder().name("张三").contacts(List.of()).build())
                .sections(List.of())
                .build();

        ExportPreflight result = checker.check(document, new PdfLayoutInspection(3, true));

        assertThat(result.pageCount()).isEqualTo(3);
        assertThat(result.missingContact()).isTrue();
        assertThat(result.pageLimitExceeded()).isTrue();
        assertThat(result.overflowDetected()).isTrue();
        assertThat(result.warnings()).containsExactly(
                "MISSING_CONTACT", "PAGE_LIMIT_EXCEEDED", "CONTENT_OUT_OF_PAGE_BOUNDS");
    }

    @Test
    void cityOrEducationMetadataAloneDoesNotCountAsContactMethod() {
        ResumeDocumentDTO document = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder()
                        .name("张三")
                        .contacts(List.of(ResumeDocumentContactDTO.builder()
                                .id("c-1").label("城市").value("北京").build()))
                        .build())
                .sections(List.of())
                .build();

        ExportPreflight result = checker.check(document, new PdfLayoutInspection(1, false));

        assertThat(result.missingContact()).isTrue();
    }

    @Test
    void acceptsCommunicationContactAndTwoPages() {
        ResumeDocumentDTO document = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder()
                        .name("张三")
                        .contacts(List.of(ResumeDocumentContactDTO.builder()
                                .id("c-1").label("邮箱").value("zhang@example.com").build()))
                        .build())
                .sections(List.of())
                .build();

        ExportPreflight result = checker.check(document, new PdfLayoutInspection(2, false));

        assertThat(result.missingContact()).isFalse();
        assertThat(result.pageLimitExceeded()).isFalse();
        assertThat(result.overflowDetected()).isFalse();
        assertThat(result.warnings()).isEmpty();
    }
}
