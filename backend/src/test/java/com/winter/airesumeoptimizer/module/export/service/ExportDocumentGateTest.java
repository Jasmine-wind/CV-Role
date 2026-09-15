package com.winter.airesumeoptimizer.module.export.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Technical Export Gate：只阻断技术上无法完成的操作。
 * 内容质量（未确认候选、重复章节、系统兜底章节、缺失联系方式、质量校验）只作为
 * needsReview 提醒，不再阻止导出。
 */
class ExportDocumentGateTest {

    private static final Long USER_ID = 7L;
    private static final Long SOURCE_VERSION_ID = 40L;
    private static final Long RESUME_ID = 10L;

    private final ResumeVersionMapper resumeVersionMapper = mock(ResumeVersionMapper.class);
    private final ResumeParseResultMapper resumeParseResultMapper = mock(ResumeParseResultMapper.class);

    private ExportDocumentGate gate;
    private OptimizationTask task;

    @BeforeEach
    void setUp() {
        gate = new ExportDocumentGate(
                resumeVersionMapper,
                resumeParseResultMapper,
                new com.winter.airesumeoptimizer.module.resume.service.impl.ResumeDocumentQualityValidatorImpl());
        task = new OptimizationTask();
        task.setId(50L);
        task.setUserId(USER_ID);
        task.setSourceResumeVersionId(SOURCE_VERSION_ID);

        ResumeVersion source = new ResumeVersion();
        source.setId(SOURCE_VERSION_ID);
        source.setUserId(USER_ID);
        source.setResumeId(RESUME_ID);
        when(resumeVersionMapper.selectOne(any())).thenReturn(source);
    }

    private void givenQualityStatus(String qualityStatus) {
        givenQualityStatus(qualityStatus, null);
    }

    private void givenQualityStatus(String qualityStatus, String unresolvedItems) {
        ResumeParseResult parseResult = new ResumeParseResult();
        parseResult.setResumeId(RESUME_ID);
        parseResult.setQualityStatus(qualityStatus);
        parseResult.setUnresolvedItems(unresolvedItems);
        when(resumeParseResultMapper.selectOne(any())).thenReturn(parseResult);
    }

    @Test
    void readyQualityAndValidDocumentPass() {
        givenQualityStatus("READY");

        ExportDocumentGate.GateResult result = gate.check(USER_ID, task, validDocument());

        assertThat(result.blocked()).isFalse();
        assertThat(result.status()).isEqualTo(ExportDocumentGate.STATUS_PASS);
    }

    @Test
    void historicalNeedsReviewLabelDoesNotBlockWhenNothingRemainsUnconfirmed() {
        // 历史 parseResult 的 NEEDS_REVIEW 只是准备阶段标签；当前已无待确认项且 TARGET 文档合格时，
        // 不能用旧标签永久阻止用户导出已经修好的简历。
        givenQualityStatus("NEEDS_REVIEW", "[]");

        ExportDocumentGate.GateResult result = gate.check(USER_ID, task, validDocument());

        assertThat(result.blocked()).isFalse();
        assertThat(result.status()).isEqualTo(ExportDocumentGate.STATUS_PASS);
    }

    @Test
    void unresolvedItemsAreAdvisoryAndNoLongerBlockExport() {
        // 未确认候选只提醒：用户可以先继续分析、编辑、预览和导出。
        givenQualityStatus("NEEDS_REVIEW", "[{\"id\":\"u-1\",\"kind\":\"TEXT_FRAGMENT\"}]");

        ExportDocumentGate.GateResult result = gate.check(USER_ID, task, validDocument());

        assertThat(result.blocked()).isFalse();
        assertThat(result.status()).isEqualTo(ExportDocumentGate.STATUS_PASS);
        assertThat(result.needsReview()).isTrue();
    }

    @Test
    void failedQualityBlocks() {
        givenQualityStatus("FAILED");

        ExportDocumentGate.GateResult result = gate.check(USER_ID, task, validDocument());

        assertThat(result.blocked()).isTrue();
        assertThat(result.blockCode()).isEqualTo(ExportDocumentGate.CODE_RESUME_QUALITY_FAILED);
    }

    @Test
    void pendingParseBlocks() {
        givenQualityStatus("PENDING");

        ExportDocumentGate.GateResult result = gate.check(USER_ID, task, validDocument());

        assertThat(result.blocked()).isTrue();
        assertThat(result.blockCode()).isEqualTo(ExportDocumentGate.CODE_RESUME_PARSE_PENDING);
    }

    @Test
    void missingRenderableDocumentStillBlocksAsTechnicallyImpossible() {
        givenQualityStatus("READY");

        ExportDocumentGate.GateResult result = gate.check(USER_ID, task, null);

        assertThat(result.blocked()).isTrue();
        assertThat(result.blockCode()).isEqualTo(ExportDocumentGate.CODE_DOCUMENT_NOT_CONFIRMED);
    }

    @Test
    void legacyHistoryWithoutQualityRecordPasses() {
        when(resumeParseResultMapper.selectOne(any())).thenReturn(null);

        ExportDocumentGate.GateResult result = gate.check(USER_ID, task, validDocument());

        assertThat(result.blocked()).isFalse();
    }

    @Test
    void duplicateSectionTitlesAreAdvisory() {
        givenQualityStatus("READY");
        ResumeDocumentDTO document = validDocument();
        document.getSections().add(ResumeDocumentSectionDTO.builder()
                .id("s-dup")
                .kind("PROJECT")
                .title("工作经历")
                .entries(new ArrayList<>(List.of(ResumeDocumentEntryDTO.builder()
                        .id("s-dup-e-1")
                        .organization("另一家公司")
                        .bullets(new ArrayList<>())
                        .build())))
                .build());

        ExportDocumentGate.GateResult result = gate.check(USER_ID, task, document);

        assertThat(result.blocked()).isFalse();
        assertThat(result.needsReview()).isTrue();
    }

    @Test
    void systemArtifactSectionIsAdvisory() {
        givenQualityStatus("READY");
        ResumeDocumentDTO document = validDocument();
        document.getSections().add(ResumeDocumentSectionDTO.builder()
                .id("s-raw")
                .kind("OTHER")
                .title("其他原始内容")
                .entries(new ArrayList<>(List.of(ResumeDocumentEntryDTO.builder()
                        .id("s-raw-e-1")
                        .bullets(new ArrayList<>(List.of(ResumeDocumentBulletDTO.builder()
                                .id("s-raw-e-1-b-1")
                                .text("游离内容")
                                .build())))
                        .build())))
                .build());

        ExportDocumentGate.GateResult result = gate.check(USER_ID, task, document);

        assertThat(result.blocked()).isFalse();
        assertThat(result.needsReview()).isTrue();
    }

    @Test
    void missingReachableContactIsAdvisory() {
        givenQualityStatus("READY");
        ResumeDocumentDTO document = validDocument();
        document.getBasics().setContacts(List.of());

        ExportDocumentGate.GateResult result = gate.check(USER_ID, task, document);

        // 用户可以先导出草稿，之后自己补联系方式。
        assertThat(result.blocked()).isFalse();
        assertThat(result.needsReview()).isTrue();
    }

    @Test
    void currentTargetSemanticCorruptionIsAdvisoryEvenWhenParseQualityWasReady() {
        givenQualityStatus("READY");
        ResumeDocumentDTO document = validDocument();
        document.getSections().get(0).getEntries().get(0).setOrganization(null);

        ExportDocumentGate.GateResult result = gate.check(USER_ID, task, document);

        assertThat(result.blocked()).isFalse();
        assertThat(result.needsReview()).isTrue();
    }

    @Test
    void historicalSuccessfulTaskUsesFrozenSnapshotInsteadOfMutableResumeQuality() {
        givenQualityStatus("NEEDS_REVIEW");
        task.setStatus("SUCCESS");
        task.setResumeInputSnapshot("{\"schemaVersion\":\"RESUME_DOCUMENT_V1\"}");

        ExportDocumentGate.GateResult result = gate.check(USER_ID, task, validDocument());

        assertThat(result.blocked()).isFalse();
    }

    @Test
    void invalidFormatContactIsAdvisoryNotReachable() {
        givenQualityStatus("READY");
        ResumeDocumentDTO document = validDocument();
        document.getBasics().setContacts(List.of(ResumeDocumentContactDTO.builder()
                .id("c-bad")
                .type("EMAIL")
                .label("邮箱")
                .value("not-an-email")
                .build()));

        ExportDocumentGate.GateResult result = gate.check(USER_ID, task, document);

        assertThat(result.blocked()).isFalse();
        assertThat(result.needsReview()).isTrue();
    }

    private ResumeDocumentDTO validDocument() {
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder()
                        .name("李明")
                        .contacts(new ArrayList<>(List.of(
                                ResumeDocumentContactDTO.builder()
                                        .id("c-1")
                                        .type("PHONE")
                                        .label("电话")
                                        .value("13812345678")
                                        .build(),
                                ResumeDocumentContactDTO.builder()
                                        .id("c-2")
                                        .type("EMAIL")
                                        .label("邮箱")
                                        .value("liming.dev@example.com")
                                        .build())))
                        .build())
                .sections(new ArrayList<>(List.of(ResumeDocumentSectionDTO.builder()
                        .id("s-1")
                        .kind("EXPERIENCE")
                        .title("工作经历")
                        .entries(new ArrayList<>(List.of(ResumeDocumentEntryDTO.builder()
                                .id("s-1-e-1")
                                .organization("某科技有限公司")
                                .role("Java 后端工程师")
                                .bullets(new ArrayList<>(List.of(ResumeDocumentBulletDTO.builder()
                                        .id("s-1-e-1-b-1")
                                        .text("负责订单服务开发")
                                        .build())))
                                .build())))
                        .build())))
                .build();
    }
}
