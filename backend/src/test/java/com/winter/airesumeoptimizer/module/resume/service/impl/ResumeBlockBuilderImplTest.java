package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeBlockBuilderImplTest {

    private final ResumeBlockBuilderImpl service = new ResumeBlockBuilderImpl();

    @Test
    void splitBlockTextShouldKeepBoundaryAt500() {
        assertThat(service.splitBlockText("a".repeat(500))).containsExactly("a".repeat(500));
        assertThat(service.splitBlockText("a".repeat(501))).containsExactly("a".repeat(500), "a");
    }

    @Test
    void splitBlockTextShouldPreserveLongMixedTextWithoutLoss() {
        String original = ("中文项目描述，使用 Java Spring Boot 完成服务建设；".repeat(35)) + "结尾";

        var fragments = service.splitBlockText(original);

        assertThat(fragments).isNotEmpty().allSatisfy(fragment -> assertThat(fragment.length()).isLessThanOrEqualTo(500));
        assertThat(String.join("", fragments)).isEqualTo(original);
        assertThat(fragments.size()).isLessThanOrEqualTo((int) Math.ceil(original.length() / 500.0) + 1);
    }

    @Test
    void splitBlockTextShouldHardSplitUnpunctuatedTextWithoutLoss() {
        String original = "x".repeat(1201);

        var fragments = service.splitBlockText(original);

        assertThat(fragments).hasSize(3);
        assertThat(String.join("", fragments)).isEqualTo(original);
    }

    @Test
    void splitFragmentsShouldRetainMetadataAndNeighborContext() {
        String longText = "项目描述。".repeat(280);
        ResumeTextCleanResultDTO cleanResult = ResumeTextCleanResultDTO.builder()
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("PROJECTS")
                        .sourceSectionConfidence("HIGH")
                        .lines(List.of(longText, "下一条内容"))
                        .build()))
                .build();

        var blocks = service.build(cleanResult);

        assertThat(blocks).hasSize(4);
        assertThat(blocks).allSatisfy(block -> {
            assertThat(block.getText()).hasSizeLessThanOrEqualTo(500);
            assertThat(block.getSourceType()).isEqualTo("cleanedText");
            assertThat(block.getSourceSection()).isEqualTo("PROJECTS");
            assertThat(block.getRuleSection()).isEqualTo("PROJECTS");
            assertThat(block.getSourceSectionConfidence()).isEqualTo("HIGH");
            assertThat(block.getLockedLevel()).isEqualTo("HIGH");
            assertThat(block.getSectionLocked()).isTrue();
        });
        assertThat(blocks.get(0).getPrevText()).isNull();
        assertThat(blocks.get(blocks.size() - 1).getText()).isEqualTo("下一条内容");
        assertThat(blocks.get(blocks.size() - 2).getNextText()).isEqualTo("下一条内容");
        assertThat(blocks).extracting("index").containsExactlyElementsOf(
                java.util.stream.IntStream.range(0, blocks.size()).boxed().toList());
        assertThat(blocks.subList(0, 3)).extracting(ResumeBlockDTO::getSourceOccurrenceIds)
                .containsOnly(List.of("source-occurrence-0"));
        assertThat(blocks.get(3).getSourceOccurrenceIds())
                .containsExactly("source-occurrence-3");
    }

    @Test
    void buildShouldKeepBlockAndOccurrenceNamespacesSeparateForMissingOccurrenceIds() {
        ResumeTextCleanResultDTO cleanResult = ResumeTextCleanResultDTO.builder()
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("EDUCATION")
                        .blocks(List.of(
                                ResumeBlockDTO.builder()
                                        .id("same-block")
                                        .sourceBlockIds(List.of("same-block"))
                                        .text("某大学")
                                        .build(),
                                ResumeBlockDTO.builder()
                                        .id("same-block")
                                        .sourceBlockIds(List.of("same-block"))
                                        .text("某大学")
                                        .build()))
                        .build()))
                .build();

        var blocks = service.build(cleanResult);

        assertThat(blocks).hasSize(2);
        assertThat(blocks).extracting(ResumeBlockDTO::getId)
                .containsExactly("same-block", "same-block~2");
        assertThat(blocks).extracting(ResumeBlockDTO::getSourceOccurrenceIds)
                .containsExactly(List.of("source-occurrence-0"), List.of("source-occurrence-1"));
        assertThat(blocks).allSatisfy(block ->
                assertThat(block.getSourceOccurrenceIds()).doesNotContain(block.getId()));
    }

    @Test
    void buildShouldSuffixDuplicateExplicitOccurrenceIdsInsteadOfFallingBackToBlockOrdinals() {
        ResumeTextCleanResultDTO cleanResult = ResumeTextCleanResultDTO.builder()
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("EDUCATION")
                        .blocks(List.of(
                                ResumeBlockDTO.builder()
                                        .id("block-a")
                                        .sourceOccurrenceIds(List.of("same-occurrence"))
                                        .text("某大学")
                                        .build(),
                                ResumeBlockDTO.builder()
                                        .id("block-b")
                                        .sourceOccurrenceIds(List.of("same-occurrence"))
                                        .text("某大学")
                                        .build()))
                        .build()))
                .build();

        var blocks = service.build(cleanResult);

        assertThat(blocks).extracting(ResumeBlockDTO::getSourceOccurrenceIds)
                .containsExactly(List.of("same-occurrence"), List.of("same-occurrence~2"));
    }

    @Test
    void buildShouldReuseExplicitOccurrenceAcrossRepeatedSourceProjections() {
        ResumeTextCleanResultDTO cleanResult = ResumeTextCleanResultDTO.builder()
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("PROJECTS")
                        .blocks(List.of(
                                ResumeBlockDTO.builder()
                                        .id("source-row#fragment-0")
                                        .sourceBlockIds(List.of("source-row#fragment-0"))
                                        .sourceOccurrenceIds(List.of("row-occurrence"))
                                        .text("项目第一段")
                                        .build(),
                                ResumeBlockDTO.builder()
                                        .id("source-row#fragment-1")
                                        .sourceBlockIds(List.of("source-row#fragment-1"))
                                        .sourceOccurrenceIds(List.of("row-occurrence"))
                                        .text("项目第二段")
                                        .build()))
                        .build()))
                .build();

        assertThat(service.build(cleanResult))
                .extracting(ResumeBlockDTO::getSourceOccurrenceIds)
                .containsExactly(List.of("row-occurrence"), List.of("row-occurrence"));
    }

    @Test
    void buildShouldKeepDuplicateOccurrenceAliasesInsideOneSourceRow() {
        ResumeTextCleanResultDTO cleanResult = ResumeTextCleanResultDTO.builder()
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("PROJECTS")
                        .blocks(List.of(ResumeBlockDTO.builder()
                                .id("source-row")
                                .sourceBlockIds(List.of("source-row"))
                                .sourceOccurrenceIds(List.of("row-occurrence", "row-occurrence"))
                                .text("项目内容")
                                .build()))
                        .build()))
                .build();

        assertThat(service.build(cleanResult).getFirst().getSourceOccurrenceIds())
                .containsExactly("row-occurrence", "row-occurrence~2");
    }

    @Test
    void buildShouldPreserveSingleCharacterFactsButDropPunctuationArtifacts() {
        ResumeTextCleanResultDTO cleanResult = ResumeTextCleanResultDTO.builder()
                .sections(List.of(ResumeTextSectionDTO.builder()
                        .sectionType("GENERAL")
                        .lines(List.of("A", "甲", "·"))
                        .build()))
                .build();

        var blocks = service.build(cleanResult);

        assertThat(blocks).extracting(ResumeBlockDTO::getText).containsExactly("A", "甲");
    }

    @Test
    void buildShouldCreateIndexedBlocksFromSections() {
        ResumeTextCleanResultDTO cleanResult = ResumeTextCleanResultDTO.builder()
                .sections(List.of(
                        ResumeTextSectionDTO.builder()
                                .sectionType("BASIC_INFO")
                                .heading("个人信息")
                                .sourceSectionConfidence("HIGH")
                                .lines(List.of("张三", " "))
                                .build(),
                        ResumeTextSectionDTO.builder()
                                .sectionType("SKILLS")
                                .heading("专业技能")
                                .sourceSectionConfidence("MEDIUM")
                                .lines(List.of("• Java Spring Boot"))
                                .build()))
                .build();

        var blocks = service.build(cleanResult);

        assertThat(blocks).hasSize(2);
        assertThat(blocks).extracting("index").containsExactly(0, 1);
        assertThat(blocks).extracting("originalIndex").containsExactly(0, 1);
        assertThat(blocks).extracting("displayOrder").containsExactly(0, 1);
        assertThat(blocks).extracting("sourceType").containsOnly("cleanedText");
        assertThat(blocks).extracting("sourceSection").containsExactly("BASIC_INFO", "SKILLS");
        assertThat(blocks).extracting("ruleSection").containsExactly("BASIC_INFO", "SKILLS");
        assertThat(blocks).extracting("ruleConfidence").containsExactly(0.95, 0.72);
        assertThat(blocks).extracting("sourceSectionConfidence").containsExactly("HIGH", "MEDIUM");
        assertThat(blocks).extracting("lockedLevel").containsExactly("HIGH", "MEDIUM");
        assertThat(blocks).extracting("finalSectionSource").containsOnly("RULE_SOURCE_SECTION");
        assertThat(blocks).extracting("sectionLocked").containsExactly(true, false);
        assertThat(blocks).extracting("text").containsExactly("张三", "Java Spring Boot");
        assertThat(blocks.get(0).getPrevText()).isNull();
        assertThat(blocks.get(0).getNextText()).isEqualTo("Java Spring Boot");
        assertThat(blocks.get(1).getPrevText()).isEqualTo("张三");
        assertThat(blocks.get(1).getNextText()).isNull();
    }
}
