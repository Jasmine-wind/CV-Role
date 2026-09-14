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
    void duplicateUseOfOneOccurrenceIsSplitAndBlocksDuplicateMapping() {
        ResumeDocumentDTO source = document(List.of("occ-heading", "occ-body"),
                Map.of("occ-heading", "项目经历", "occ-body", "同一职责"),
                List.of(
                        bullet("b-1", "同一职责", List.of("occ-body")),
                        bullet("b-2", "同一职责", List.of("occ-body"))));
        ResumeDocumentDTO target = document(List.of(), Map.of(), List.of(
                bullet("b-1", "同一职责", List.of("occ-body")),
                bullet("b-2", "同一职责", List.of("occ-body"))));

        WorkspaceSourceReferenceVO result = assembler.assemble(1L, 2L, 3L, 1L,
                "resume.pdf", true, source, target);

        assertThat(result.mappings()).filteredOn(mapping -> mapping.bulletId() != null)
                .extracting(WorkspaceSourceReferenceVO.TargetMapping::status)
                .containsOnly(WorkspaceSourceMappingStatus.SPLIT);
        assertThat(result.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("DUPLICATE_MAPPING");
        assertThat(result.exportBlocked()).isTrue();
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
