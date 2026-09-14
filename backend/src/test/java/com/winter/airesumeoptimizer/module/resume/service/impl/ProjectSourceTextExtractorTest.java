package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceBlockRole;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectSourceTextExtractorTest {

    @Test
    void extractFromLinesShouldSplitConservativeNoDateProjectHeaders() {
        List<com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO> projects =
                ProjectSourceTextExtractor.extractFromLines(List.of(
                        "项目经历",
                        "订单结算平台",
                        "负责订单状态流转与对账开发",
                        "实时风控平台",
                        "负责风险识别与拦截开发"), "PROJECTS");

        assertThat(projects).extracting("name")
                .containsExactly("订单结算平台", "实时风控平台");
        assertThat(projects).extracting(project -> project.getResponsibilities())
                .allSatisfy(responsibilities -> assertThat(responsibilities).isNotEmpty());
    }

    @Test
    void expandProjectsShouldPartitionParentOccurrenceIdsWithTheirRows() {
        com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO parent =
                com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO.builder()
                        .startLine(1)
                        .endLine(4)
                        .text("订单结算平台\n负责订单对账开发\n实时风控平台\n负责风险识别开发")
                        .sourceOccurrenceIds(List.of("occ-1", "occ-2", "occ-3", "occ-4"))
                        .build();
        com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO candidate =
                com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO.builder()
                        .name("项目汇总")
                        .description("项目汇总")
                        .sourceRef(parent)
                        .build();

        List<com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO> projects =
                ProjectSourceTextExtractor.expandProjects(List.of(candidate));

        assertThat(projects).hasSize(2);
        assertThat(projects).extracting(project -> project.getSourceRef().getSourceOccurrenceIds())
                .containsExactly(List.of("occ-1", "occ-2"), List.of("occ-3", "occ-4"));
    }

    @Test
    void ordinalProjectHeadersShouldPreserveNumericAndArbitraryTitles() {
        var projects = ProjectSourceTextExtractor.extractFromLines(List.of(
                "项目一：2048",
                "负责实现棋盘状态与回放功能",
                "项目二：Order Mirror",
                "负责实现订单对账与审计流程"), "PROJECTS");

        assertThat(projects).extracting("name").containsExactly("2048", "Order Mirror");
    }

    @Test
    void ordinalOnlyProjectNameShouldNotBeAcceptedAsARealTitle() {
        var projects = ProjectSourceTextExtractor.extractFromLines(List.of(
                "项目名称：2.",
                "项目描述：用于验证订单一致性的内部工具",
                "主要职责：实现对账与异常追踪"), "PROJECTS");

        assertThat(projects).singleElement()
                .satisfies(project -> assertThat(project.getName()).isEqualTo("项目经历"));
    }

    @Test
    void visualEntryHeadersShouldSplitBrandOnlyProjectTitlesAndPreserveOccurrences() {
        List<ResumeRawSectionBlockDTO> blocks = List.of(
                sourceBlock("p1", "2048", ResumeSourceBlockRole.ENTRY_HEADER, false, "occ-1", 0),
                sourceBlock("p2", "负责实现棋盘状态与回放功能", ResumeSourceBlockRole.PARAGRAPH, false, "occ-2", 1),
                sourceBlock("p3", "Order Mirror", ResumeSourceBlockRole.PARAGRAPH, true, "occ-3", 2),
                sourceBlock("p4", "负责实现订单对账与审计流程", ResumeSourceBlockRole.PARAGRAPH, false, "occ-4", 3));
        ResumeRawSectionDTO section = ResumeRawSectionDTO.builder()
                .id("section-projects")
                .normalizedSection("PROJECTS")
                .blocks(blocks)
                .build();

        var projects = ProjectSourceTextExtractor.extractFromRawSections(List.of(section));

        assertThat(projects).extracting("name").containsExactly("2048", "Order Mirror");
        assertThat(projects).extracting(project -> project.getSourceRef().getSourceOccurrenceIds())
                .containsExactly(List.of("occ-1", "occ-2"), List.of("occ-3", "occ-4"));
    }

    @Test
    void boldProjectFieldLabelShouldNotCreateAFalseVisualBoundary() {
        ResumeRawSectionDTO section = ResumeRawSectionDTO.builder()
                .id("section-projects")
                .normalizedSection("PROJECTS")
                .blocks(List.of(
                        sourceBlock("p1", "Order Mirror", ResumeSourceBlockRole.ENTRY_HEADER, true, "occ-1", 0),
                        sourceBlock("p2", "负责实现订单对账与审计流程", ResumeSourceBlockRole.PARAGRAPH, false, "occ-2", 1),
                        sourceBlock("p3", "技术栈：Java、Kafka", ResumeSourceBlockRole.PARAGRAPH, true, "occ-3", 2),
                        sourceBlock("p4", "支持异常追踪与重放", ResumeSourceBlockRole.PARAGRAPH, false, "occ-4", 3)))
                .build();

        var projects = ProjectSourceTextExtractor.extractFromRawSections(List.of(section));

        assertThat(projects).singleElement()
                .satisfies(project -> assertThat(project.getName()).isEqualTo("Order Mirror"));
    }

    @Test
    void largerLessIndentedProjectTitleShouldCreateAVisualBoundary() {
        ResumeRawSectionDTO section = ResumeRawSectionDTO.builder()
                .id("section-projects")
                .normalizedSection("PROJECTS")
                .blocks(List.of(
                        typographicBlock("p1", "2048", 12.0, 0, "occ-1", 0),
                        typographicBlock("p2", "负责实现棋盘状态与回放功能", 10.0, 1, "occ-2", 1),
                        typographicBlock("p3", "Order Mirror", 12.0, 0, "occ-3", 2),
                        typographicBlock("p4", "负责实现订单对账与审计流程", 10.0, 1, "occ-4", 3)))
                .build();

        var projects = ProjectSourceTextExtractor.extractFromRawSections(List.of(section));

        assertThat(projects).extracting("name").containsExactly("2048", "Order Mirror");
    }

    private static ResumeRawSectionBlockDTO typographicBlock(
            String id, String text, double fontSize, int indent, String occurrenceId, int index) {
        return ResumeRawSectionBlockDTO.builder()
                .id(id).text(text).fontSize(fontSize).indent(indent)
                .index(index).originalIndex(index)
                .sourceOccurrenceIds(List.of(occurrenceId))
                .build();
    }

    private static ResumeRawSectionBlockDTO sourceBlock(
            String id, String text, ResumeSourceBlockRole role, boolean bold, String occurrenceId, int index) {
        return ResumeRawSectionBlockDTO.builder()
                .id(id)
                .text(text)
                .role(role)
                .boldHint(bold)
                .index(index)
                .originalIndex(index)
                .sourceOccurrenceIds(List.of(occurrenceId))
                .build();
    }

    @Test
    void extractFromLinesShouldNotSplitAContinuationThatOnlyLooksLikeTechnology() {
        List<com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO> projects =
                ProjectSourceTextExtractor.extractFromLines(List.of(
                        "订单结算平台",
                        "负责订单状态流转与对账开发",
                        "Java / Spring Boot"), "PROJECTS");

        assertThat(projects).singleElement()
                .satisfies(project -> assertThat(project.getName()).isEqualTo("订单结算平台"));
    }
}
