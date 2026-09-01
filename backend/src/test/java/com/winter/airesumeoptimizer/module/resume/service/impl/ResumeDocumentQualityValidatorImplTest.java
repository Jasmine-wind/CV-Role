package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeQualityIssueDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeUnresolvedItemDTO;
import com.winter.airesumeoptimizer.module.resume.enums.ResumeQualityStatus;
import com.winter.airesumeoptimizer.module.resume.service.ResumeDocumentQualityValidator;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 确定性验证的三档裁决测试：可确定 → READY；不确定（未决项）→ NEEDS_REVIEW；
 * 明显错误或无法形成文档 → 阻止 READY / FAILED。
 */
class ResumeDocumentQualityValidatorImplTest {

    private final ResumeDocumentQualityValidatorImpl validator = new ResumeDocumentQualityValidatorImpl();

    @Test
    void readyDocumentShouldPassWithoutBlockers() {
        ResumeDocumentQualityValidator.ValidationResult result =
                validator.validate(readyDocument(), List.of());

        assertThat(result.qualityStatus()).isEqualTo(ResumeQualityStatus.QUALITY_READY);
        assertThat(result.issues())
                .noneMatch(issue -> ResumeQualityIssueDTO.SEVERITY_BLOCKER.equals(issue.getSeverity()));
    }

    @Test
    void unresolvedItemsAloneShouldForceNeedsReview() {
        ResumeDocumentQualityValidator.ValidationResult result = validator.validate(
                readyDocument(),
                List.of(ResumeUnresolvedItemDTO.builder().id("u-1").kind("TEXT_FRAGMENT").build()));

        assertThat(result.qualityStatus()).isEqualTo(ResumeQualityStatus.QUALITY_NEEDS_REVIEW);
    }

    @Test
    void emptyDocumentShouldFail() {
        ResumeDocumentDTO empty = readyDocument();
        empty.setSections(List.of());

        ResumeDocumentQualityValidator.ValidationResult result = validator.validate(empty, List.of());

        assertThat(result.qualityStatus()).isEqualTo(ResumeQualityStatus.QUALITY_FAILED);
    }

    @Test
    void missingNameOrReachableContactShouldBlock() {
        ResumeDocumentDTO noName = readyDocument();
        noName.getBasics().setName(null);
        assertThat(statusOf(noName)).isEqualTo(ResumeQualityStatus.QUALITY_NEEDS_REVIEW);
        assertThat(codesOf(noName)).contains("MISSING_NAME");

        ResumeDocumentDTO noContact = readyDocument();
        noContact.getBasics().setContacts(List.of());
        assertThat(statusOf(noContact)).isEqualTo(ResumeQualityStatus.QUALITY_NEEDS_REVIEW);
        assertThat(codesOf(noContact)).contains("MISSING_REACHABLE_CONTACT");
    }

    @Test
    void invalidTypedContactFormatShouldBlock() {
        ResumeDocumentDTO badPhone = readyDocument();
        badPhone.getBasics().getContacts().get(0).setValue("abc");
        assertThat(codesOf(badPhone)).contains("INVALID_CONTACT_FORMAT");
    }

    @Test
    void duplicateTypedContactsShouldBlock() {
        ResumeDocumentDTO document = readyDocument();
        document.getBasics().getContacts().add(ResumeDocumentContactDTO.builder()
                .type("EMAIL")
                .label("邮箱")
                .value("lihua@example.com")
                .build());

        assertThat(codesOf(document)).contains("DUPLICATE_CONTACT");
    }

    @Test
    void blankOptionalContactShouldBlockExport() {
        ResumeDocumentDTO document = readyDocument();
        document.getBasics().getContacts().add(ResumeDocumentContactDTO.builder()
                .type("GITHUB")
                .label("GitHub")
                .value(" ")
                .build());

        assertThat(codesOf(document)).contains("INVALID_CONTACT_FORMAT");
    }

    @Test
    void emptyTitleWithManyBulletsShouldBlock() {
        ResumeDocumentDTO document = readyDocument();
        document.getSections().get(0).getEntries().add(ResumeDocumentEntryDTO.builder()
                .bullets(new ArrayList<>(List.of(
                        bullet("要点一"), bullet("要点二"), bullet("要点三"))))
                .build());

        assertThat(codesOf(document)).contains("ENTRY_MISSING_TITLE_WITH_MANY_BULLETS");
    }

    @Test
    void crossSectionDuplicateShouldBlock() {
        ResumeDocumentDTO document = readyDocument();
        String duplicated = "负责订单中台的统一模型设计与核心代码开发";
        document.getSections().get(0).getEntries().get(0).getBullets().add(bullet(duplicated));
        document.getSections().add(ResumeDocumentSectionDTO.builder()
                .kind("PROJECT")
                .title("项目经历")
                .entries(new ArrayList<>(List.of(ResumeDocumentEntryDTO.builder()
                        .organization("订单中台")
                        .bullets(new ArrayList<>(List.of(bullet(duplicated))))
                        .build())))
                .build());

        assertThat(codesOf(document)).contains("CROSS_SECTION_DUPLICATE");
    }

    @Test
    void fieldTypeAnomalyShouldBlock() {
        ResumeDocumentDTO document = readyDocument();
        document.getSections().get(0).getEntries().get(0).setOrganization("2022.07 - 2023.06");

        assertThat(codesOf(document)).contains("FIELD_TYPE_ANOMALY");
    }

    @Test
    void legalCrossSectionEntityReuseShouldNotBeTreatedAsDuplicateContent() {
        ResumeDocumentDTO document = readyDocument();
        document.getSections().add(ResumeDocumentSectionDTO.builder()
                .kind("PROJECT")
                .title("项目经历")
                .entries(new ArrayList<>(List.of(ResumeDocumentEntryDTO.builder()
                        .organization("某科技有限公司")
                        .bullets(new ArrayList<>(List.of(bullet("独立项目中复用该公司的业务经验"))))
                        .build())))
                .build());

        assertThat(codesOf(document)).doesNotContain("CROSS_SECTION_DUPLICATE");
    }

    @Test
    void suspiciousFragmentationShouldBlock() {
        ResumeDocumentDTO document = readyDocument();
        document.getSections().get(0).getEntries().get(0).setBullets(new ArrayList<>(List.of(
                bullet("负责订单"), bullet("服务的核心"), bullet("模块开发与"))));

        assertThat(codesOf(document)).contains("LINE_FRAGMENTATION");
    }

    @Test
    void continuationFragmentShouldBlockEvenWhenItIsNotThreeShortLines() {
        ResumeDocumentDTO document = readyDocument();
        document.getSections().get(0).getEntries().get(0).setBullets(new ArrayList<>(List.of(
                bullet("负责交易履约服务开发，日"), bullet("均处理约 120 万笔订单。"))));

        assertThat(codesOf(document)).contains("LINE_FRAGMENTATION");
    }

    @Test
    void blankBulletShouldBlockEvenWhenTheEntryHasOtherFields() {
        ResumeDocumentDTO document = readyDocument();
        document.getSections().get(0).getEntries().get(0).getBullets().add(bullet("  "));

        assertThat(codesOf(document)).contains("EMPTY_BULLET");
    }

    @Test
    void blankSkillItemShouldBlock() {
        ResumeDocumentDTO document = readyDocument();
        document.getSections().get(2).getEntries().get(0).getSkillItems().add(" ");

        assertThat(codesOf(document)).contains("EMPTY_SKILL_ITEM");
    }

    @Test
    void mismatchedSemanticFieldsShouldBlock() {
        ResumeDocumentDTO document = readyDocument();
        document.getSections().get(0).getEntries().get(0).setSchool("某大学");

        assertThat(codesOf(document)).contains("ENTRY_FIELD_MISMATCH");
    }

    @Test
    void systemSectionArtifactShouldBlock() {
        ResumeDocumentDTO document = readyDocument();
        document.getSections().add(ResumeDocumentSectionDTO.builder()
                .kind("OTHER")
                .title("其他原始内容")
                .entries(new ArrayList<>(List.of(ResumeDocumentEntryDTO.builder()
                        .bullets(new ArrayList<>(List.of(bullet("游离内容"))))
                        .build())))
                .build());

        assertThat(codesOf(document)).contains("SYSTEM_ARTIFACT_PRESENT");
    }

    private String statusOf(ResumeDocumentDTO document) {
        return validator.validate(document, List.of()).qualityStatus();
    }

    private List<String> codesOf(ResumeDocumentDTO document) {
        return validator.validate(document, List.of()).issues().stream()
                .filter(issue -> ResumeQualityIssueDTO.SEVERITY_BLOCKER.equals(issue.getSeverity()))
                .map(ResumeQualityIssueDTO::getCode)
                .toList();
    }

    private ResumeDocumentBulletDTO bullet(String text) {
        return ResumeDocumentBulletDTO.builder().text(text).build();
    }

    private ResumeDocumentDTO readyDocument() {
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder()
                        .name("李华")
                        .contacts(new ArrayList<>(List.of(
                                ResumeDocumentContactDTO.builder()
                                        .type("PHONE").label("电话").value("13800000000").build(),
                                ResumeDocumentContactDTO.builder()
                                        .type("EMAIL").label("邮箱").value("lihua@example.com").build())))
                        .build())
                .sections(new ArrayList<>(List.of(
                        ResumeDocumentSectionDTO.builder()
                                .kind("EXPERIENCE")
                                .title("工作经历")
                                .entries(new ArrayList<>(List.of(ResumeDocumentEntryDTO.builder()
                                        .organization("某科技有限公司")
                                        .role("Java 后端工程师")
                                        .startDate("2022.07")
                                        .endDate("至今")
                                        .bullets(new ArrayList<>(List.of(bullet("负责订单服务开发，提升系统稳定性"))))
                                        .build())))
                                .build(),
                        ResumeDocumentSectionDTO.builder()
                                .kind("EDUCATION")
                                .title("教育经历")
                                .entries(new ArrayList<>(List.of(ResumeDocumentEntryDTO.builder()
                                        .school("某大学")
                                        .degree("本科")
                                        .major("计算机科学与技术")
                                        .startDate("2018.09")
                                        .endDate("2022.06")
                                        .bullets(new ArrayList<>())
                                        .build())))
                                .build(),
                        ResumeDocumentSectionDTO.builder()
                                .kind("SKILL")
                                .title("技能")
                                .entries(new ArrayList<>(List.of(ResumeDocumentEntryDTO.builder()
                                        .group("编程语言")
                                        .skillItems(new ArrayList<>(List.of("Java", "Python")))
                                        .bullets(new ArrayList<>())
                                        .build())))
                                .build())))
                .build();
    }
}
