package com.winter.airesumeoptimizer.module.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
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
        assertThat(result.exportBlocked()).isFalse();
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
        assertThat(result.exportBlocked()).isFalse();
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
        assertThat(result.exportBlocked()).isFalse();
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
        assertThat(result.exportBlocked()).isFalse();
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
        assertThat(result.exportBlocked()).isFalse();
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
        assertThat(result.exportBlocked()).isFalse();
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
        assertThat(result.exportBlocked()).isFalse();
    }

    @Test
    void projectBoundaryBlockerFollowsConfirmedOmissionCoverage() {
        ResumeDocumentDTO source = document(List.of(
                        "occ-heading", "occ-p1", "occ-p2", "occ-p2-field", "occ-p2-bullet"),
                Map.of("occ-heading", "项目经历", "occ-p1", "项目一", "occ-p2", "项目二",
                        "occ-p2-field", "项目负责人", "occ-p2-bullet", "项目二职责"), List.of());
        ResumeDocumentEntryDTO first = entry("e-1", "项目一", List.of());
        first.setSourceOccurrenceIds(List.of("occ-p1"));
        ResumeDocumentEntryDTO second = entry("e-2", "项目二",
                List.of(bullet("b-2", "项目二职责", List.of("occ-p2-bullet"))));
        second.setSourceOccurrenceIds(List.of("occ-p2"));
        second.setRole("项目负责人");
        second.setFieldSourceRefs(Map.of("role", ResumeSourceRefDTO.builder()
                .text("项目负责人").sourceOccurrenceIds(List.of("occ-p2-field")).build()));
        source.getSections().get(0).setEntries(List.of(first, second));

        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        target.getSections().get(0).setEntries(List.of(first));
        WorkspaceSourceReferenceVO unconfirmed = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);
        assertThat(unconfirmed.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("PROJECT_BOUNDARY_LOST", "SOURCE_CONTENT_UNMAPPED");
        assertThat(unconfirmed.exportBlocked()).isFalse();

        target.setConfirmedSourceOmissionIds(List.of("occ-p2"));
        WorkspaceSourceReferenceVO partiallyConfirmed = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);
        assertThat(partiallyConfirmed.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("PROJECT_BOUNDARY_LOST", "SOURCE_CONTENT_UNMAPPED");
        assertThat(partiallyConfirmed.exportBlocked()).isFalse();

        target.setConfirmedSourceOmissionIds(List.of("occ-p2", "occ-p2-bullet"));
        WorkspaceSourceReferenceVO fieldUnconfirmed = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);
        assertThat(fieldUnconfirmed.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("PROJECT_BOUNDARY_LOST", "SOURCE_CONTENT_UNMAPPED");
        assertThat(fieldUnconfirmed.exportBlocked()).isFalse();

        // Whole-Project intentional omission: every frozen occurrence of the deleted
        // entry is server-confirmed, so the boundary advisory is released. Export was never
        // blocked by fidelity; the flag stays false in every branch.
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
        assertThat(result.exportBlocked()).isFalse();
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
        assertThat(result.exportBlocked()).isFalse();
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
        assertThat(invalid.exportBlocked()).isFalse();
    }

    @Test
    void rejectsTrimmedManifestIdsAndNonExactAliasText() {
        ResumeDocumentDTO trimmedId = document(List.of(" occ-heading", "occ-body"),
                Map.of(" occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        trimmedId.getSections().get(0).setSourceOccurrenceIds(List.of(" occ-heading"));
        trimmedId.getSections().get(0).getEntries().get(0)
                .setSourceOccurrenceIds(List.of(" occ-heading", "occ-body"));

        assertManifestInvalid(trimmedId);

        ResumeDocumentDTO normalizedAlias = document(List.of("occ-heading", "occ-body", "occ-body-alias"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始 职责", "occ-body-alias", " 原始  职责 "),
                List.of(bullet("b-1", "原始 职责", List.of("occ-body", "occ-body-alias"))));
        normalizedAlias.getSourceOccurrencePrimaryIds().put("occ-body-alias", "occ-body");

        assertManifestInvalid(normalizedAlias);
    }

    @Test
    void manifestOrderTextsAndPrimaryKeysMustMatchAndTextsMustBeNonBlank() {
        ResumeDocumentDTO missingText = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        missingText.getSourceOccurrenceTexts().remove("occ-body");
        assertManifestInvalid(missingText);

        ResumeDocumentDTO extraPrimary = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        extraPrimary.getSourceOccurrencePrimaryIds().put("unknown", "unknown");
        assertManifestInvalid(extraPrimary);

        ResumeDocumentDTO blankText = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", " "),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        assertManifestInvalid(blankText);
    }

    @Test
    void aliasResolutionUsesOneCanonicalPrimaryAndPreservesPhysicalOrder() {
        ResumeDocumentDTO source = document(List.of(
                        "occ-heading", "occ-body-alias", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责",
                        "occ-body-alias", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body-alias", "occ-body"))));
        source.getSourceOccurrencePrimaryIds().put("occ-body-alias", "occ-body");

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, source);

        assertThat(result.sourceBlocks()).filteredOn(block -> "occ-body".equals(block.id()))
                .singleElement().satisfies(block -> {
                    assertThat(block.occurrenceIds()).containsExactly("occ-body-alias", "occ-body");
                    assertThat(block.status()).isEqualTo(WorkspaceSourceMappingStatus.EXACT);
                });
        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .doesNotContain("SOURCE_MANIFEST_INVALID");
    }

    @Test
    void rejectsPrimaryChainsCyclesAndRootsOutsideTheManifest() {
        ResumeDocumentDTO chain = document(List.of("occ-heading", "occ-a", "occ-b"),
                Map.of("occ-heading", "项目经历", "occ-a", "原始职责", "occ-b", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-a", "occ-b"))));
        chain.getSourceOccurrencePrimaryIds().put("occ-a", "occ-b");
        chain.getSourceOccurrencePrimaryIds().put("occ-b", "occ-heading");
        assertManifestInvalid(chain);

        ResumeDocumentDTO cycle = document(List.of("occ-heading", "occ-a", "occ-b"),
                Map.of("occ-heading", "项目经历", "occ-a", "原始职责", "occ-b", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-a", "occ-b"))));
        cycle.getSourceOccurrencePrimaryIds().put("occ-a", "occ-b");
        cycle.getSourceOccurrencePrimaryIds().put("occ-b", "occ-a");
        assertManifestInvalid(cycle);

        ResumeDocumentDTO outside = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        outside.getSourceOccurrencePrimaryIds().put("occ-body", "unknown");
        assertManifestInvalid(outside);
    }

    @Test
    void duplicateOrBlankOccurrenceIdsAreInvalidInsteadOfBeingNormalizedAway() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));

        for (List<String> ids : List.of(
                List.of("occ-body", "occ-body"),
                List.of("occ-body", " "),
                List.of(" occ-body"))) {
            ResumeDocumentDTO target = document(List.of(), Map.of(),
                    List.of(bullet("b-1", "原始职责", ids)));

            WorkspaceSourceReferenceVO result = assembler.assemble(
                    1L, 2L, 3L, 1L, "resume.pdf", true, source, target);

            assertThat(result.mappings()).filteredOn(mapping -> "b-1".equals(mapping.bulletId()))
                    .extracting(WorkspaceSourceReferenceVO.TargetMapping::status)
                    .containsExactly(WorkspaceSourceMappingStatus.AMBIGUOUS);
            assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                    .contains("AMBIGUOUS_MAPPING");
        }

        source.getSections().get(0).getEntries().get(0).setSourceOccurrenceIds(List.of("occ-body"));
        ResumeDocumentDTO duplicateEntryTarget = document(List.of(), Map.of(), List.of());
        duplicateEntryTarget.getSections().get(0).getEntries().get(0)
                .setSourceOccurrenceIds(List.of("occ-body", "occ-body"));
        WorkspaceSourceReferenceVO duplicateEntry = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, duplicateEntryTarget);
        assertThat(duplicateEntry.mappings()).filteredOn(mapping -> "e-1".equals(mapping.entryId()))
                .extracting(WorkspaceSourceReferenceVO.TargetMapping::status)
                .containsExactly(WorkspaceSourceMappingStatus.AMBIGUOUS);
    }

    @Test
    void sidecarRequiresExactTextAndIdsFromOnlyItsLogicalAliasGroup() {
        ResumeDocumentDTO mismatchedText = sourceWithBodySidecar(ResumeSourceRefDTO.builder()
                .text(" 原始职责 ")
                .sourceOccurrenceIds(List.of("occ-body"))
                .page(1)
                .build());
        assertManifestInvalid(mismatchedText);

        ResumeDocumentDTO unknownId = sourceWithBodySidecar(ResumeSourceRefDTO.builder()
                .text("原始职责")
                .sourceOccurrenceIds(List.of("occ-body", "unknown"))
                .page(1)
                .build());
        assertManifestInvalid(unknownId);

        ResumeDocumentDTO crossedGroup = sourceWithBodySidecar(ResumeSourceRefDTO.builder()
                .text("原始职责")
                .sourceOccurrenceIds(List.of("occ-body", "occ-heading"))
                .page(1)
                .build());
        assertManifestInvalid(crossedGroup);
    }

    @Test
    void frozenChildReferencesMustMatchRootTextAndOneLogicalGroup() {
        ResumeDocumentDTO mismatchedText = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"), List.of());
        ResumeDocumentEntryDTO entry = entry("e-1", "OmniGateway", List.of());
        entry.setFieldSourceRefs(Map.of("role", ResumeSourceRefDTO.builder()
                .text("伪造职责").sourceOccurrenceIds(List.of("occ-body")).build()));
        mismatchedText.getSections().get(0).setEntries(List.of(entry));
        assertManifestInvalid(mismatchedText);

        ResumeDocumentDTO crossedGroup = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"), List.of());
        ResumeDocumentEntryDTO crossedEntry = entry("e-1", "OmniGateway", List.of());
        crossedEntry.setFieldSourceRefs(Map.of("role", ResumeSourceRefDTO.builder()
                .text("原始职责").sourceOccurrenceIds(List.of("occ-body", "occ-heading")).build()));
        crossedGroup.getSections().get(0).setEntries(List.of(crossedEntry));
        assertManifestInvalid(crossedGroup);

        ResumeDocumentDTO contradictoryPair = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"), List.of());
        ResumeDocumentEntryDTO contradictoryEntry = entry("e-1", "OmniGateway", List.of());
        contradictoryEntry.setSourceOccurrenceIds(List.of("occ-heading"));
        contradictoryEntry.setSourceRef(ref("原始职责", "occ-body"));
        contradictoryPair.getSections().get(0).setEntries(List.of(contradictoryEntry));
        assertManifestInvalid(contradictoryPair);

        ResumeDocumentDTO unsupportedSlot = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"), List.of());
        ResumeDocumentEntryDTO unsupportedEntry = entry("e-1", "OmniGateway", List.of());
        unsupportedEntry.setFieldSourceRefs(Map.of("forgedField", ref("原始职责", "occ-body")));
        unsupportedSlot.getSections().get(0).setEntries(List.of(unsupportedEntry));
        assertManifestInvalid(unsupportedSlot);

        ResumeDocumentDTO unalignedList = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"), List.of());
        ResumeDocumentEntryDTO unalignedEntry = entry("e-1", "OmniGateway", List.of());
        unalignedEntry.setTechStack(List.of());
        unalignedEntry.setTechStackSourceRefs(List.of(ref("原始职责", "occ-body")));
        unalignedList.getSections().get(0).setEntries(List.of(unalignedEntry));
        assertManifestInvalid(unalignedList);
    }

    @Test
    void multiOccurrenceFrozenReferencesAreValidatedAgainstTheOrderedRootSpan() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-a", "occ-b"),
                Map.of("occ-heading", "项目经历", "occ-a", "第一行", "occ-b", "第二行"), List.of());
        ResumeDocumentEntryDTO entry = entry("e-1", "OmniGateway", List.of());
        entry.setSourceRef(ResumeSourceRefDTO.builder()
                .text("第一行\n第二行")
                .sourceOccurrenceIds(List.of("occ-a", "occ-b"))
                .build());
        entry.setSourceOccurrenceIds(List.of("occ-a", "occ-b"));
        source.getSections().get(0).setEntries(List.of(entry));

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, source);

        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .doesNotContain("SOURCE_MANIFEST_INVALID");

        entry.setSourceRef(ResumeSourceRefDTO.builder()
                .text("第二行\n第一行")
                .sourceOccurrenceIds(List.of("occ-b", "occ-a"))
                .build());
        assertManifestInvalid(source);

        ResumeDocumentDTO nonContiguous = document(
                List.of("occ-heading", "occ-a", "occ-gap", "occ-b"),
                Map.of("occ-heading", "项目经历", "occ-a", "第一行",
                        "occ-gap", "中间行", "occ-b", "第二行"), List.of());
        ResumeDocumentEntryDTO spanningEntry = entry("e-1", "OmniGateway", List.of());
        spanningEntry.setSourceRef(ResumeSourceRefDTO.builder()
                .text("第一行\n第二行")
                .sourceOccurrenceIds(List.of("occ-a", "occ-b"))
                .build());
        spanningEntry.setSourceOccurrenceIds(List.of("occ-a", "occ-b"));
        nonContiguous.getSections().get(0).setEntries(List.of(spanningEntry));
        assertManifestInvalid(nonContiguous);
    }

    @Test
    void duplicateTargetIdentityIsAmbiguousEvenWhenTheTextsDiffer() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of(
                bullet("b-1", "第一份内容", List.of("occ-body")),
                bullet("b-1", "不同的第二份内容", List.of("occ-body"))));

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);

        assertThat(result.mappings()).filteredOn(mapping -> "b-1".equals(mapping.bulletId()))
                .extracting(WorkspaceSourceReferenceVO.TargetMapping::status)
                .containsOnly(WorkspaceSourceMappingStatus.AMBIGUOUS);
        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("AMBIGUOUS_MAPPING");
        assertThat(result.exportBlocked()).isFalse();
    }

    @Test
    void duplicateStableNodeIdentityIsInvalidRegardlessOfSectionKindOrNodeType() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        source.setSections(List.of(
                source.getSections().get(0),
                ResumeDocumentSectionDTO.builder()
                        .id("s-1").kind("EXPERIENCE").title("工作经历").entries(List.of()).build()));

        assertManifestInvalid(source);

        ResumeDocumentDTO crossType = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("shared-id", "原始职责", List.of("occ-body"))));
        crossType.getSections().get(0).setId("shared-id");
        assertManifestInvalid(crossType);

        ResumeDocumentDTO contactCollision = document(List.of(
                        "occ-heading", "occ-body", "occ-contact"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责",
                        "occ-contact", "contact@example.test"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        contactCollision.getSections().get(0).setId("shared-contact-id");
        contactCollision.setBasics(ResumeDocumentBasicsDTO.builder().contacts(List.of(
                ResumeDocumentContactDTO.builder().id("shared-contact-id").type("EMAIL")
                        .value("contact@example.test")
                        .sourceOccurrenceIds(List.of("occ-contact")).build())).build());
        assertManifestInvalid(contactCollision);
    }

    @Test
    void multipleNonContactDeepestOwnersInvalidateTheWholeManifest() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(
                        bullet("b-1", "原始职责上半段", List.of("occ-body")),
                        bullet("b-2", "原始职责下半段", List.of("occ-body"))));

        assertManifestInvalid(source);
    }

    @Test
    void ownerlessManifestOccurrenceInvalidatesTheWholeManifest() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body", "occ-orphan"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责", "occ-orphan", "孤立原文"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));

        assertManifestInvalid(source);
    }

    @Test
    void sourceSectionKindMustBeCanonicalAndTargetKindMustMatchExactly() {
        ResumeDocumentDTO nonCanonicalSource = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        nonCanonicalSource.getSections().get(0).setKind("project");
        assertManifestInvalid(nonCanonicalSource);

        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        ResumeDocumentDTO target = document(List.of(), Map.of(),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        target.getSections().get(0).setKind(" project ");

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);

        assertThat(result.mappings()).filteredOn(mapping -> "b-1".equals(mapping.bulletId()))
                .extracting(WorkspaceSourceReferenceVO.TargetMapping::status)
                .containsExactly(WorkspaceSourceMappingStatus.AMBIGUOUS);
    }

    @Test
    void emptyShellTargetProjectEntryDoesNotHideLostProjectBoundary() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-p1", "occ-p2"),
                Map.of("occ-heading", "项目经历", "occ-p1", "项目一", "occ-p2", "项目二"), List.of());
        ResumeDocumentEntryDTO first = entry("e-1", "项目一", List.of());
        first.setSourceOccurrenceIds(List.of("occ-p1"));
        ResumeDocumentEntryDTO second = entry("e-2", "项目二", List.of());
        second.setSourceOccurrenceIds(List.of("occ-p2"));
        source.getSections().get(0).setEntries(List.of(first, second));

        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        target.getSections().get(0).setEntries(List.of(
                first,
                ResumeDocumentEntryDTO.builder().id("e-2")
                        .sourceOccurrenceIds(List.of("occ-p2")).bullets(List.of()).build()));

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);

        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("PROJECT_BOUNDARY_LOST");
    }

    @Test
    void nonEmptyProjectShellWithoutAuthenticatedLineageDoesNotHideLostBoundary() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-p1", "occ-p2"),
                Map.of("occ-heading", "项目经历", "occ-p1", "项目一", "occ-p2", "项目二"), List.of());
        ResumeDocumentEntryDTO first = entry("e-1", "项目一", List.of());
        first.setSourceOccurrenceIds(List.of("occ-p1"));
        ResumeDocumentEntryDTO second = entry("e-2", "项目二", List.of());
        second.setSourceOccurrenceIds(List.of("occ-p2"));
        source.getSections().get(0).setEntries(List.of(first, second));

        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        target.getSections().get(0).setEntries(List.of(
                first,
                ResumeDocumentEntryDTO.builder().id("e-2").organization("伪造的空壳项目")
                        .bullets(List.of()).build()));

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);

        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("PROJECT_BOUNDARY_LOST");
    }

    @Test
    void fullyConfirmedProjectOmissionWithEveryChildReferenceReleasesBoundaryBlocker() {
        ResumeDocumentDTO source = document(List.of(
                        "occ-heading", "occ-p1", "occ-p2", "occ-field", "occ-tech",
                        "occ-skill", "occ-description", "occ-bullet"),
                Map.of("occ-heading", "项目经历", "occ-p1", "项目一", "occ-p2", "项目二",
                        "occ-field", "负责人", "occ-tech", "Java", "occ-skill", "治理",
                        "occ-description", "平台治理", "occ-bullet", "降低延迟"), List.of());
        ResumeDocumentEntryDTO first = entry("e-1", "项目一", List.of());
        first.setSourceOccurrenceIds(List.of("occ-p1"));
        ResumeDocumentEntryDTO second = entry("e-2", "项目二", List.of(
                ResumeDocumentBulletDTO.builder().id("b-2").text("降低延迟")
                        .sourceRef(ref("降低延迟", "occ-bullet")).build()));
        second.setSourceRef(ref("项目二", "occ-p2"));
        second.setRole("负责人");
        second.setFieldSourceRefs(Map.of("role", ref("负责人", "occ-field")));
        second.setTechStack(List.of("Java"));
        second.setTechStackSourceRefs(List.of(ref("Java", "occ-tech")));
        second.setSkillItems(List.of("治理"));
        second.setSkillItemSourceRefs(List.of(ref("治理", "occ-skill")));
        second.setSkillDescriptions(List.of("平台治理"));
        second.setSkillDescriptionSourceRefs(List.of(ref("平台治理", "occ-description")));
        source.getSections().get(0).setEntries(List.of(first, second));

        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        target.getSections().get(0).setEntries(List.of(first));
        target.setConfirmedSourceOmissionIds(List.of(
                "occ-p2", "occ-field", "occ-tech", "occ-skill", "occ-description", "occ-bullet"));

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);

        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .doesNotContain("PROJECT_BOUNDARY_LOST", "SOURCE_CONTENT_UNMAPPED", "SOURCE_MANIFEST_INVALID");
        assertThat(result.exportBlocked()).isFalse();
    }

    @Test
    void forgedConfirmationCannotReleaseProjectBoundaryBlocker() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-p1", "occ-p2"),
                Map.of("occ-heading", "项目经历", "occ-p1", "项目一", "occ-p2", "项目二"), List.of());
        ResumeDocumentEntryDTO first = entry("e-1", "项目一", List.of());
        first.setSourceOccurrenceIds(List.of("occ-p1"));
        ResumeDocumentEntryDTO second = entry("e-2", "项目二", List.of());
        second.setSourceOccurrenceIds(List.of("occ-p2"));
        source.getSections().get(0).setEntries(List.of(first, second));

        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        target.getSections().get(0).setEntries(List.of(first));
        // A forged occurrence ID that is not part of the frozen manifest invalidates the
        // whole ConfirmationState; the boundary blocker must stay in force.
        target.setConfirmedSourceOmissionIds(List.of("occ-p2", "forged-occurrence"));

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);

        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("CONFIRMED_OMISSION_INVALID", "PROJECT_BOUNDARY_LOST");
        assertThat(result.exportBlocked()).isFalse();
    }

    @Test
    void onlyGenuinelyMissingProjectsContributeToBoundaryBlocker() {
        ResumeDocumentDTO source = document(List.of(
                        "occ-heading", "occ-a", "occ-b", "occ-c", "occ-d"),
                Map.of("occ-heading", "项目经历", "occ-a", "项目 A", "occ-b", "项目 B",
                        "occ-c", "项目 C", "occ-d", "项目 D"), List.of());
        ResumeDocumentEntryDTO entryA = entry("e-a", "项目 A", List.of());
        entryA.setSourceOccurrenceIds(List.of("occ-a"));
        ResumeDocumentEntryDTO entryB = entry("e-b", "项目 B", List.of());
        entryB.setSourceOccurrenceIds(List.of("occ-b"));
        ResumeDocumentEntryDTO entryC = entry("e-c", "项目 C", List.of());
        entryC.setSourceOccurrenceIds(List.of("occ-c"));
        ResumeDocumentEntryDTO entryD = entry("e-d", "项目 D", List.of());
        entryD.setSourceOccurrenceIds(List.of("occ-d"));
        source.getSections().get(0).setEntries(List.of(entryA, entryB, entryC, entryD));

        // TARGET keeps A and C; B is fully confirmed omitted; D is accidentally missing.
        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        target.getSections().get(0).setEntries(List.of(entryA, entryC));
        target.setConfirmedSourceOmissionIds(List.of("occ-b"));

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);

        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("PROJECT_BOUNDARY_LOST");
        WorkspaceSourceReferenceVO.FidelityIssue boundaryIssue = result.fidelityIssues().stream()
                .filter(issue -> "PROJECT_BOUNDARY_LOST".equals(issue.code()))
                .findFirst().orElseThrow();
        // Only D's occurrences are reported; B is a legitimate confirmed omission.
        assertThat(boundaryIssue.sourceOccurrenceIds()).containsExactly("occ-d");
        assertThat(result.exportBlocked()).isFalse();
    }

    @Test
    void alignedSemanticListsMayRetainCanonicalNullReferencePlaceholders() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "技能", "occ-body", "Java, Python"), List.of());
        source.getSections().get(0).setKind("SKILL");
        ResumeDocumentEntryDTO entry = source.getSections().get(0).getEntries().get(0);
        entry.setSkillItems(List.of("Java", "Python"));
        entry.setSkillItemSourceRefs(java.util.Arrays.asList(
                ref("Java, Python", "occ-body"), null));

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, source);

        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .doesNotContain("SOURCE_MANIFEST_INVALID");
    }

    @Test
    void legitimateSharedBasicsAndContactLineDoesNotInvalidateFrozenOwnership() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body", "occ-header"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责",
                        "occ-header", "Candidate · Backend · candidate@example.test"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        ResumeSourceRefDTO headerRef = ref(
                "Candidate · Backend · candidate@example.test", "occ-header");
        source.setBasics(ResumeDocumentBasicsDTO.builder()
                .name("Candidate")
                .jobIntention("Backend")
                .fieldSourceRefs(Map.of("name", headerRef, "jobIntention", headerRef))
                .contacts(List.of(ResumeDocumentContactDTO.builder()
                        .id("c-email").type("EMAIL").value("candidate@example.test")
                        .sourceRef(headerRef).sourceOccurrenceIds(List.of("occ-header")).build()))
                .build());

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, source);

        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .doesNotContain("SOURCE_MANIFEST_INVALID");
        assertThat(result.sourceBlocks()).filteredOn(block -> "occ-header".equals(block.id()))
                .singleElement().satisfies(block ->
                        assertThat(block.status()).isEqualTo(WorkspaceSourceMappingStatus.SPLIT));
    }

    @Test
    void legitimateContactSplitDoesNotInvalidateFrozenOwnership() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body", "occ-contact"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责",
                        "occ-contact", "邮箱与电话"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        source.setBasics(ResumeDocumentBasicsDTO.builder().contacts(List.of(
                ResumeDocumentContactDTO.builder().id("c-email").type("EMAIL")
                        .value("user@example.test").sourceOccurrenceIds(List.of("occ-contact")).build(),
                ResumeDocumentContactDTO.builder().id("c-phone").type("PHONE")
                        .value("13000000000").sourceOccurrenceIds(List.of("occ-contact")).build())).build());

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, source);

        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .doesNotContain("SOURCE_MANIFEST_INVALID");
        assertThat(result.sourceBlocks()).filteredOn(block -> "occ-contact".equals(block.id()))
                .singleElement().satisfies(block ->
                        assertThat(block.status()).isEqualTo(WorkspaceSourceMappingStatus.SPLIT));
    }

    @Test
    void targetNodeTypeIsPartOfAuthenticatedLineage() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body", "occ-contact"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责",
                        "occ-contact", "candidate@example.test"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        source.setBasics(ResumeDocumentBasicsDTO.builder().contacts(List.of(
                ResumeDocumentContactDTO.builder().id("c-email").type("EMAIL")
                        .value("candidate@example.test")
                        .sourceOccurrenceIds(List.of("occ-contact")).build())).build());
        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of());
        target.getSections().get(0).setId("c-email");
        target.getSections().get(0).setSourceOccurrenceIds(List.of("occ-contact"));
        target.getSections().get(0).setEntries(List.of());

        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, target);

        assertThat(result.mappings()).filteredOn(mapping -> "c-email".equals(mapping.sectionId()))
                .extracting(WorkspaceSourceReferenceVO.TargetMapping::status)
                .containsExactly(WorkspaceSourceMappingStatus.AMBIGUOUS);
        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("AMBIGUOUS_MAPPING");
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

    private void assertManifestInvalid(ResumeDocumentDTO source) {
        WorkspaceSourceReferenceVO result = assembler.assemble(
                1L, 2L, 3L, 1L, "resume.pdf", true, source, source);
        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("SOURCE_MANIFEST_INVALID");
        assertThat(result.exportBlocked()).isFalse();
    }

    private static ResumeDocumentDTO sourceWithBodySidecar(ResumeSourceRefDTO sidecar) {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "原始职责"),
                List.of(bullet("b-1", "原始职责", List.of("occ-body"))));
        source.setSourceOccurrenceRefs(Map.of("occ-body", sidecar));
        return source;
    }

    private static ResumeSourceRefDTO ref(String text, String occurrenceId) {
        return ResumeSourceRefDTO.builder().text(text).sourceOccurrenceIds(List.of(occurrenceId)).build();
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
