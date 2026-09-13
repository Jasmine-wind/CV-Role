package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

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
