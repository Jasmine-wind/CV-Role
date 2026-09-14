package com.winter.airesumeoptimizer.module.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.enums.WorkspaceSourceMappingStatus;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkspaceSourceReferenceAssemblerImplTest {

    private final WorkspaceSourceReferenceAssemblerImpl assembler = new WorkspaceSourceReferenceAssemblerImpl();

    @Test
    void keepsSourceOrderAndBuildsExactBidirectionalLinksWithoutTextGuessing() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "构建 OmniGateway"),
                List.of(bullet("b-1", "构建 OmniGateway", List.of("occ-body"))));
        ResumeDocumentDTO target = document(List.of("forged"), Map.of("forged", "伪造"),
                List.of(bullet("b-1", "重构 OmniGateway 网关", List.of("occ-body"))));

        WorkspaceSourceReferenceVO result = assembler.assemble(1L, 2L, 3L, 4L,
                "resume.pdf", true, source, target);

        assertThat(result.sourceBlocks()).extracting(WorkspaceSourceReferenceVO.SourceBlock::id)
                .containsExactly("occ-heading", "occ-body");
        assertThat(result.sourceBlocks().get(1).targetNodeIds())
                .containsExactly("section:s-1/entry:e-1/bullet:b-1");
        assertThat(result.mappings()).filteredOn(mapping -> "b-1".equals(mapping.bulletId()))
                .singleElement().satisfies(mapping -> {
                    assertThat(mapping.status()).isEqualTo(WorkspaceSourceMappingStatus.EXACT);
                    assertThat(mapping.textChanged()).isTrue();
                    assertThat(mapping.reliable()).isTrue();
                });
        assertThat(result.exportBlocked()).isFalse();
    }

    @Test
    void reportsMergedLineageAndComparesTargetTextWithTheWholeFrozenSpan() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-a", "occ-b"),
                Map.of("occ-heading", "个人总结", "occ-a", "重视系统稳定", "occ-b", "性与可维护性"),
                List.of(bullet("b-1", "重视系统稳定性与可维护性", List.of("occ-a", "occ-b"))));
        ResumeDocumentDTO unchanged = document(List.of(), Map.of(),
                List.of(bullet("b-1", "重视系统稳定性与可维护性", List.of("occ-a", "occ-b"))));
        ResumeDocumentDTO changed = document(List.of(), Map.of(),
                List.of(bullet("b-1", "重新编写的个人总结", List.of("occ-a", "occ-b"))));

        WorkspaceSourceReferenceVO unchangedResult = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, unchanged);
        WorkspaceSourceReferenceVO changedResult = assembler.assemble(
                1L, 2L, 3L, 2L, "resume.pdf", true, source, changed);

        assertThat(unchangedResult.mappings()).filteredOn(mapping -> "b-1".equals(mapping.bulletId()))
                .singleElement().satisfies(mapping -> {
                    assertThat(mapping.status()).isEqualTo(WorkspaceSourceMappingStatus.MERGED);
                    assertThat(mapping.textChanged()).isFalse();
                });
        assertThat(changedResult.mappings()).filteredOn(mapping -> "b-1".equals(mapping.bulletId()))
                .singleElement().satisfies(mapping -> {
                    assertThat(mapping.status()).isEqualTo(WorkspaceSourceMappingStatus.MERGED);
                    assertThat(mapping.textChanged()).isTrue();
                });
        assertThat(unchangedResult.sourceBlocks())
                .filteredOn(block -> block.occurrenceIds().contains("occ-a")
                        || block.occurrenceIds().contains("occ-b"))
                .allSatisfy(block -> {
                    assertThat(block.status()).isEqualTo(WorkspaceSourceMappingStatus.MERGED);
                    assertThat(block.reliable()).isTrue();
                });
    }

    @Test
    void exposesFrozenOccurrenceGeometryWithoutInferringItFromTargetContent() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "构建 OmniGateway"),
                List.of(bullet("b-1", "构建 OmniGateway", List.of("occ-body"))));
        source.setSourceOccurrenceRefs(Map.of("occ-body", ResumeSourceRefDTO.builder()
                .text("构建 OmniGateway")
                .sourceOccurrenceIds(List.of("occ-body"))
                .page(3).x(20.0).y(40.0).width(180.0).height(12.0)
                .fontSize(10.5).boldHint(true).indent(1)
                .build()));
        ResumeDocumentDTO target = document(List.of(), Map.of(),
                List.of(bullet("b-1", "重构网关", List.of("occ-body"))));

        WorkspaceSourceReferenceVO result = assembler.assemble(1L, 2L, 3L, 1L,
                "resume.pdf", true, source, target);

        assertThat(result.sourceBlocks()).filteredOn(block -> "occ-body".equals(block.id()))
                .singleElement()
                .satisfies(block -> {
                    assertThat(block.sourceGeometry()).isNotNull();
                    assertThat(block.sourceGeometry().page()).isEqualTo(3);
                    assertThat(block.sourceGeometry().x()).isEqualTo(20.0);
                    assertThat(block.sourceGeometry().boldHint()).isTrue();
                });
    }

    @Test
    void tamperedOccurrenceGeometrySidecarFailsClosed() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "构建 OmniGateway"),
                List.of(bullet("b-1", "构建 OmniGateway", List.of("occ-body"))));
        source.setSourceOccurrenceRefs(Map.of("occ-body", ResumeSourceRefDTO.builder()
                .text("另一段原文")
                .sourceOccurrenceIds(List.of("unknown-occurrence"))
                .page(3).x(20.0).y(40.0).width(180.0).height(12.0)
                .build()));

        WorkspaceSourceReferenceVO result = assembler.assemble(1L, 2L, 3L, 1L,
                "resume.pdf", true, source, source);

        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("SOURCE_MANIFEST_INVALID");
        assertThat(result.exportBlocked()).isTrue();
        assertThat(result.sourceBlocks()).filteredOn(block -> "occ-body".equals(block.id()))
                .singleElement().satisfies(block -> assertThat(block.sourceGeometry()).isNull());
    }

    @Test
    void danglingReferenceIsAmbiguousAndFailsClosed() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原文"),
                List.of(bullet("b-1", "原文", List.of("occ-body"))));
        ResumeDocumentDTO target = document(List.of(), Map.of(),
                List.of(bullet("b-1", "当前内容", List.of("unknown-occurrence"))));

        WorkspaceSourceReferenceVO result = assembler.assemble(1L, 2L, 3L, 1L,
                "resume.pdf", true, source, target);

        assertThat(result.mappings()).filteredOn(mapping -> "b-1".equals(mapping.bulletId()))
                .extracting(WorkspaceSourceReferenceVO.TargetMapping::status)
                .containsExactly(WorkspaceSourceMappingStatus.AMBIGUOUS);
        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("AMBIGUOUS_MAPPING");
        assertThat(result.exportBlocked()).isTrue();
    }

    @Test
    void knownOccurrenceCannotBeReassignedToAnotherTargetNode() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        ResumeDocumentDTO target = document(List.of(), Map.of(),
                List.of(bullet("b-1", "伪造关联", List.of("occ-heading"))));

        WorkspaceSourceReferenceVO result = assembler.assemble(1L, 2L, 3L, 1L,
                "resume.pdf", true, source, target);

        assertThat(result.mappings()).filteredOn(mapping -> "b-1".equals(mapping.bulletId()))
                .extracting(WorkspaceSourceReferenceVO.TargetMapping::status)
                .containsExactly(WorkspaceSourceMappingStatus.AMBIGUOUS);
        assertThat(result.exportBlocked()).isTrue();
    }

    @Test
    void knownBulletCannotBeReparentedToAnotherEntry() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-1", "occ-2"),
                Map.of("occ-heading", "项目经历", "occ-1", "职责一", "occ-2", "职责二"),
                List.of());
        source.getSections().get(0).setEntries(List.of(
                entry("e-1", "项目一", List.of(bullet("b-1", "职责一", List.of("occ-1")))),
                entry("e-2", "项目二", List.of(bullet("b-2", "职责二", List.of("occ-2"))))));
        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        target.getSections().get(0).setEntries(List.of(
                entry("e-1", "项目一", List.of()),
                entry("e-2", "项目二", List.of(
                        bullet("b-2", "职责二", List.of("occ-2")),
                        bullet("b-1", "职责一", List.of("occ-1"))))));

        WorkspaceSourceReferenceVO result = assembler.assemble(1L, 2L, 3L, 1L,
                "resume.pdf", true, source, target);

        assertThat(result.mappings()).filteredOn(mapping -> "b-1".equals(mapping.bulletId()))
                .extracting(WorkspaceSourceReferenceVO.TargetMapping::status)
                .containsExactly(WorkspaceSourceMappingStatus.AMBIGUOUS);
        assertThat(result.exportBlocked()).isTrue();
    }

    @Test
    void knownEntryCannotBeReparentedToAnotherSection() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-1"),
                Map.of("occ-heading", "项目经历", "occ-1", "项目一"), List.of());
        ResumeDocumentEntryDTO moved = entry("e-1", "项目一", List.of());
        moved.setSourceOccurrenceIds(List.of("occ-1"));
        source.getSections().get(0).setEntries(List.of(moved));
        source.setSections(List.of(
                source.getSections().get(0),
                ResumeDocumentSectionDTO.builder().id("s-2").kind("OTHER").title("其他")
                        .entries(List.of()).build()));
        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        target.setSections(List.of(
                ResumeDocumentSectionDTO.builder().id("s-1").kind("PROJECT").title("项目经历")
                        .sourceOccurrenceIds(List.of("occ-heading")).entries(List.of()).build(),
                ResumeDocumentSectionDTO.builder().id("s-2").kind("OTHER").title("其他")
                        .entries(List.of(moved)).build()));

        WorkspaceSourceReferenceVO result = assembler.assemble(1L, 2L, 3L, 1L,
                "resume.pdf", true, source, target);

        assertThat(result.mappings()).filteredOn(mapping -> "e-1".equals(mapping.entryId()))
                .extracting(WorkspaceSourceReferenceVO.TargetMapping::status)
                .containsExactly(WorkspaceSourceMappingStatus.AMBIGUOUS);
        assertThat(result.exportBlocked()).isTrue();
    }

    @Test
    void confirmedIntentionalOmissionSuppressesOnlyItsUnmappedSourceBlock() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "可主动省略的职责"),
                List.of(bullet("b-1", "可主动省略的职责", List.of("occ-body"))));
        source.getSections().get(0).getEntries().get(0).setSourceOccurrenceIds(List.of("occ-heading"));
        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        target.getSections().get(0).getEntries().get(0).setSourceOccurrenceIds(List.of("occ-heading"));
        target.setConfirmedSourceOmissionIds(List.of("occ-body"));

        WorkspaceSourceReferenceVO result = assembler.assemble(1L, 2L, 3L, 4L,
                "resume.pdf", true, source, target);

        assertThat(result.sourceBlocks()).filteredOn(block -> "occ-body".equals(block.id()))
                .singleElement().satisfies(block -> {
                    assertThat(block.status()).isEqualTo(WorkspaceSourceMappingStatus.UNMAPPED);
                    assertThat(block.sourceNodeType()).isEqualTo("BULLET");
                    assertThat(block.sourceSectionKind()).isEqualTo("PROJECT");
                    assertThat(block.sourceSectionId()).isEqualTo("s-1");
                    assertThat(block.sourceEntryId()).isEqualTo("e-1");
                    assertThat(block.sourceBulletId()).isEqualTo("b-1");
                    assertThat(block.omissionEligible()).isTrue();
                    assertThat(block.omissionConfirmed()).isTrue();
                });
        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .doesNotContain("SOURCE_CONTENT_UNMAPPED");
        assertThat(result.confirmedOmissionCount()).isEqualTo(1);
        assertThat(result.exportBlocked()).isFalse();
    }

    @Test
    void deletingADeepChildDoesNotRemainMappedThroughAncestorAggregateProvenance() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "可主动省略的职责"),
                List.of(bullet("b-1", "可主动省略的职责", List.of("occ-body"))));
        source.getSections().get(0).getEntries().get(0)
                .setSourceOccurrenceIds(List.of("occ-heading", "occ-body"));
        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        target.getSections().get(0).getEntries().get(0)
                .setSourceOccurrenceIds(List.of("occ-heading", "occ-body"));

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);

        assertThat(result.sourceBlocks()).filteredOn(block -> "occ-body".equals(block.id()))
                .singleElement().satisfies(block -> {
                    assertThat(block.status()).isEqualTo(WorkspaceSourceMappingStatus.UNMAPPED);
                    assertThat(block.targetNodeIds()).isEmpty();
                    assertThat(block.omissionEligible()).isTrue();
                });
        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("SOURCE_CONTENT_UNMAPPED");
        assertThat(result.exportBlocked()).isTrue();
    }

    @Test
    void forgedConfirmationCannotBypassAmbiguousMapping() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        source.getSections().get(0).getEntries().get(0).setSourceOccurrenceIds(List.of("occ-heading"));
        ResumeDocumentDTO target = document(List.of(), Map.of(),
                List.of(bullet("new-bullet", "错误归属", List.of("occ-body"))));
        target.getSections().get(0).getEntries().get(0).setSourceOccurrenceIds(List.of("occ-heading"));
        target.setConfirmedSourceOmissionIds(List.of("occ-body"));

        WorkspaceSourceReferenceVO result = assembler.assemble(1L, 2L, 3L, 1L,
                "resume.pdf", true, source, target);

        assertThat(result.sourceBlocks()).filteredOn(block -> "occ-body".equals(block.id()))
                .singleElement().satisfies(block -> {
                    assertThat(block.omissionEligible()).isFalse();
                    assertThat(block.omissionConfirmed()).isFalse();
                });
        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("AMBIGUOUS_MAPPING", "SOURCE_CONTENT_UNMAPPED");
        assertThat(result.exportBlocked()).isTrue();
    }

    @Test
    void projectBoundaryIsRestoredUnlessEveryMeaningfulOccurrenceOfMissingEntryIsConfirmed() {
        ResumeDocumentDTO source = document(List.of(
                        "occ-heading", "occ-p1", "occ-p2", "occ-p2-field", "occ-p2-bullet"),
                Map.of("occ-heading", "项目经历", "occ-p1", "项目一", "occ-p2", "项目二",
                        "occ-p2-field", "项目负责人", "occ-p2-bullet", "项目二职责"), List.of());
        ResumeDocumentEntryDTO first = entry("e-1", "项目一", List.of());
        first.setSourceOccurrenceIds(List.of("occ-p1"));
        ResumeDocumentEntryDTO second = entry("e-2", "项目二",
                List.of(bullet("b-2", "项目二职责", List.of("occ-p2-bullet"))));
        second.setSourceOccurrenceIds(List.of("occ-p2"));
        second.setFieldSourceRefs(Map.of("role", ResumeSourceRefDTO.builder()
                .text("项目负责人").sourceOccurrenceIds(List.of("occ-p2-field")).build()));
        source.getSections().get(0).setEntries(List.of(first, second));

        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        target.getSections().get(0).setEntries(List.of(first));
        WorkspaceSourceReferenceVO unconfirmed = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);
        assertThat(unconfirmed.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("PROJECT_BOUNDARY_LOST", "SOURCE_CONTENT_UNMAPPED");

        target.setConfirmedSourceOmissionIds(List.of("occ-p2"));
        WorkspaceSourceReferenceVO partiallyConfirmed = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);
        assertThat(partiallyConfirmed.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("PROJECT_BOUNDARY_LOST", "SOURCE_CONTENT_UNMAPPED");

        target.setConfirmedSourceOmissionIds(List.of("occ-p2", "occ-p2-bullet"));
        WorkspaceSourceReferenceVO fieldUnconfirmed = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);
        assertThat(fieldUnconfirmed.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("PROJECT_BOUNDARY_LOST", "SOURCE_CONTENT_UNMAPPED");

        target.setConfirmedSourceOmissionIds(List.of("occ-p2", "occ-p2-field", "occ-p2-bullet"));
        WorkspaceSourceReferenceVO confirmed = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);
        assertThat(confirmed.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .doesNotContain("PROJECT_BOUNDARY_LOST", "SOURCE_CONTENT_UNMAPPED");
        assertThat(confirmed.exportBlocked()).isFalse();
    }

    @Test
    void silentProjectMergeCannotBeDisguisedAsConfirmedOmission() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-p1", "occ-p2"),
                Map.of("occ-heading", "项目经历", "occ-p1", "项目一", "occ-p2", "项目二"), List.of());
        ResumeDocumentEntryDTO first = entry("e-1", "项目一", List.of());
        first.setSourceOccurrenceIds(List.of("occ-p1"));
        ResumeDocumentEntryDTO second = entry("e-2", "项目二", List.of());
        second.setSourceOccurrenceIds(List.of("occ-p2"));
        source.getSections().get(0).setEntries(List.of(first, second));

        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        ResumeDocumentEntryDTO merged = entry("e-1", "项目一 / 项目二", List.of());
        merged.setSourceOccurrenceIds(List.of("occ-p1", "occ-p2"));
        target.getSections().get(0).setEntries(List.of(merged));
        target.setConfirmedSourceOmissionIds(List.of("occ-p2"));

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);

        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("AMBIGUOUS_MAPPING", "SOURCE_CONTENT_UNMAPPED", "PROJECT_BOUNDARY_LOST");
        assertThat(result.sourceBlocks()).filteredOn(block -> "occ-p2".equals(block.id()))
                .singleElement().satisfies(block -> {
                    assertThat(block.omissionEligible()).isFalse();
                    assertThat(block.omissionConfirmed()).isFalse();
                });
        assertThat(result.exportBlocked()).isTrue();
    }

    @Test
    void duplicateUseOfOneOccurrenceIsSplitAndBlocksDuplicateMapping() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "同一职责"),
                List.of(
                        bullet("b-1", "同一职责", List.of("occ-body")),
                        bullet("b-2", "同一职责", List.of("occ-body"))));
        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of(
                bullet("b-1", "同一职责", List.of("occ-body")),
                bullet("b-2", "同一职责", List.of("occ-body"))));
        target.setConfirmedSourceOmissionIds(List.of("occ-body"));

        WorkspaceSourceReferenceVO result = assembler.assemble(1L, 2L, 3L, 1L,
                "resume.pdf", true, source, target);

        assertThat(result.mappings()).filteredOn(mapping -> mapping.bulletId() != null)
                .extracting(WorkspaceSourceReferenceVO.TargetMapping::status)
                .containsOnly(WorkspaceSourceMappingStatus.SPLIT);
        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("DUPLICATE_MAPPING");
        assertThat(result.sourceBlocks()).filteredOn(block -> "occ-body".equals(block.id()))
                .singleElement().satisfies(block -> {
                    assertThat(block.omissionEligible()).isFalse();
                    assertThat(block.omissionConfirmed()).isFalse();
                });
        assertThat(result.exportBlocked()).isTrue();
    }

    @Test
    void aliasGroupMustBeCompleteAndManifestMustHaveCanonicalRoots() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body", "occ-body-alias"),
                Map.of("occ-heading", "项目经历", "occ-body", "同一职责", "occ-body-alias", "同一职责"),
                List.of(bullet("b-1", "同一职责", List.of("occ-body", "occ-body-alias"))));
        source.getSourceOccurrencePrimaryIds().put("occ-body-alias", "occ-body");
        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        target.getSections().get(0).getEntries().get(0).setSourceOccurrenceIds(List.of("occ-heading"));
        target.setConfirmedSourceOmissionIds(List.of("occ-body-alias"));

        WorkspaceSourceReferenceVO partial = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);
        assertThat(partial.sourceBlocks()).filteredOn(block -> "occ-body".equals(block.id()))
                .singleElement().satisfies(block -> assertThat(block.omissionConfirmed()).isFalse());
        assertThat(partial.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("SOURCE_CONTENT_UNMAPPED");

        source.getSourceOccurrencePrimaryIds().remove("occ-body-alias");
        WorkspaceSourceReferenceVO invalid = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);
        assertThat(invalid.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("SOURCE_MANIFEST_INVALID");
        assertThat(invalid.exportBlocked()).isTrue();
    }

    @Test
    void sectionKindIsPartOfAuthenticatedLineage() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        ResumeDocumentDTO target = document(List.of(), Map.of(),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        target.getSections().get(0).setKind("EXPERIENCE");

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);

        assertThat(result.mappings()).filteredOn(mapping -> "b-1".equals(mapping.bulletId()))
                .extracting(WorkspaceSourceReferenceVO.TargetMapping::status)
                .containsExactly(WorkspaceSourceMappingStatus.AMBIGUOUS);
        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("AMBIGUOUS_MAPPING");
    }

    private static ResumeDocumentEntryDTO entry(
            String id, String organization, List<ResumeDocumentBulletDTO> bullets) {
        return ResumeDocumentEntryDTO.builder()
                .id(id).organization(organization).bullets(bullets).build();
    }

    private static ResumeDocumentBulletDTO bullet(String id, String text, List<String> occurrences) {
        return ResumeDocumentBulletDTO.builder().id(id).text(text).sourceOccurrenceIds(occurrences).build();
    }

    private static ResumeDocumentDTO document(
            List<String> occurrenceOrder,
            Map<String, String> texts,
            List<ResumeDocumentBulletDTO> bullets) {
        LinkedHashMap<String, String> primary = new LinkedHashMap<>();
        occurrenceOrder.forEach(id -> primary.put(id, id));
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .sourceOccurrenceIds(occurrenceOrder)
                .sourceOccurrenceTexts(new LinkedHashMap<>(texts))
                .sourceOccurrencePrimaryIds(primary)
                .sections(List.of(ResumeDocumentSectionDTO.builder()
                        .id("s-1").kind("PROJECT").title("项目经历")
                        .sourceOccurrenceIds(List.of("occ-heading"))
                        .entries(List.of(ResumeDocumentEntryDTO.builder()
                                .id("e-1").organization("OmniGateway")
                                .sourceOccurrenceIds(List.of("occ-heading", "occ-body"))
                                .bullets(bullets)
                                .build()))
                        .build()))
                .build();
    }
}
