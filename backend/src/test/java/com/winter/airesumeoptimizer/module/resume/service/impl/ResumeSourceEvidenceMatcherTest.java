package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeSourceEvidenceMatcherTest {

    @Test
    void duplicateOccurrenceIdsAreNamespacedWithoutUsingBlockIds() {
        List<ResumeSourceEvidenceMatcher.Occurrence> occurrences = ResumeSourceEvidenceMatcher.index(List.of(
                ResumeBlockDTO.builder()
                        .id("block-a")
                        .sourceBlockIds(List.of("block-a"))
                        .sourceOccurrenceIds(List.of("same-occurrence"))
                        .text("Java")
                        .build(),
                ResumeBlockDTO.builder()
                        .id("block-b")
                        .sourceBlockIds(List.of("block-b"))
                        .sourceOccurrenceIds(List.of("same-occurrence"))
                        .text("Java")
                        .build()));

        assertThat(occurrences).extracting(ResumeSourceEvidenceMatcher.Occurrence::sourceOccurrenceIds)
                .containsExactly(List.of("same-occurrence"), List.of("same-occurrence~2"));
        assertThat(occurrences.get(0).sourceBlockIds()).containsExactly("block-a");
        assertThat(occurrences.get(1).sourceBlockIds()).containsExactly("block-b");
    }

    @Test
    void longTextFragmentsWithOneSourceBlockKeepOneOccurrenceIdentity() {
        List<ResumeSourceEvidenceMatcher.Occurrence> occurrences = ResumeSourceEvidenceMatcher.index(List.of(
                ResumeBlockDTO.builder()
                        .id("source-row#fragment-0")
                        .sourceBlockIds(List.of("source-row#fragment-0"))
                        .sourceOccurrenceIds(List.of("row-occurrence"))
                        .text("负责订单")
                        .build(),
                ResumeBlockDTO.builder()
                        .id("source-row#fragment-1")
                        .sourceBlockIds(List.of("source-row#fragment-1"))
                        .sourceOccurrenceIds(List.of("row-occurrence"))
                        .text("服务开发")
                        .build()));

        assertThat(occurrences).extracting(ResumeSourceEvidenceMatcher.Occurrence::sourceOccurrenceIds)
                .containsExactly(List.of("row-occurrence"));
        assertThat(ResumeSourceEvidenceMatcher.byId(occurrences).get("row-occurrence"))
                .hasSize(1);
        assertThat(occurrences.getFirst().text()).isEqualTo("负责订单服务开发");
        assertThat(ResumeSourceEvidenceMatcher.isValidReference(
                ResumeSourceRefDTO.builder()
                        .startLine(2)
                        .endLine(2)
                        .text("服务开发")
                        .sourceOccurrenceIds(List.of("row-occurrence"))
                        .build(), occurrences, Set.of()))
                .isTrue();
    }

    @Test
    void duplicateOccurrenceIdsWithinOneBlockAreAlsoNamespaced() {
        List<ResumeSourceEvidenceMatcher.Occurrence> occurrences = ResumeSourceEvidenceMatcher.index(List.of(
                ResumeBlockDTO.builder()
                        .id("logical-block")
                        .sourceBlockIds(List.of("logical-block"))
                        .sourceOccurrenceIds(List.of("same-occurrence", "same-occurrence"))
                        .text("Java Java")
                        .build()));

        assertThat(occurrences).singleElement()
                .extracting(ResumeSourceEvidenceMatcher.Occurrence::sourceOccurrenceIds)
                .isEqualTo(List.of("same-occurrence", "same-occurrence~2"));
    }

    @Test
    void identicalTextOnDifferentPagesRetainsDistinctOccurrences() {
        List<ResumeSourceEvidenceMatcher.Occurrence> occurrences = ResumeSourceEvidenceMatcher.index(List.of(
                ResumeBlockDTO.builder()
                        .id("page-one-row")
                        .sourceBlockIds(List.of("page-one-row"))
                        .sourceOccurrenceIds(List.of("page-one-occurrence"))
                        .sourceSection("INTERNSHIPS")
                        .index(0)
                        .page(1)
                        .text("重复内容")
                        .build(),
                ResumeBlockDTO.builder()
                        .id("page-two-row")
                        .sourceBlockIds(List.of("page-two-row"))
                        .sourceOccurrenceIds(List.of("page-two-occurrence"))
                        .sourceSection("INTERNSHIPS")
                        .index(1)
                        .page(2)
                        .text("重复内容")
                        .build()));

        assertThat(occurrences).hasSize(2);
        assertThat(occurrences).extracting(ResumeSourceEvidenceMatcher.Occurrence::sourceOccurrenceIds)
                .containsExactly(List.of("page-one-occurrence"), List.of("page-two-occurrence"));
        assertThat(ResumeSourceEvidenceMatcher.isValidReference(
                ResumeSourceRefDTO.builder()
                        .startLine(1)
                        .endLine(2)
                        .text("重复内容\n重复内容")
                        .sourceOccurrenceIds(List.of("page-one-occurrence", "page-two-occurrence"))
                        .build(),
                occurrences,
                Set.of("INTERNSHIPS")))
                .isTrue();
    }

    @Test
    void formattedNumericValueCanBeProvedInsideConcatenatedContactOccurrence() {
        assertThat(ResumeSourceEvidenceMatcher.matchesOccurrence(
                "13812345678", "上海138-1234-5678liming.dev@example.com"))
                .isTrue();
        assertThat(ResumeSourceEvidenceMatcher.matchesOccurrence(
                "3812345678", "上海138-1234-5678liming.dev@example.com"))
                .isFalse();
        assertThat(ResumeSourceEvidenceMatcher.matchesOccurrence(
                "liming.dev@example.com", "上海138-1234-5678liming.dev@example.comGitHub: github.com/liming-dev"))
                .isTrue();
    }

    @Test
    void blockIdCannotBeUsedAsOccurrenceReference() {
        List<ResumeSourceEvidenceMatcher.Occurrence> occurrences = ResumeSourceEvidenceMatcher.index(List.of(
                ResumeBlockDTO.builder()
                        .id("logical-block")
                        .sourceBlockIds(List.of("logical-block"))
                        .text("Java")
                        .build()));

        ResumeSourceEvidenceMatcher.Occurrence occurrence = occurrences.getFirst();
        ResumeSourceRefDTO reference = ResumeSourceRefDTO.builder()
                .sourceOccurrenceIds(List.of("logical-block"))
                .text("Java")
                .build();

        assertThat(occurrence.sourceOccurrenceIds()).doesNotContain("logical-block");
        assertThat(ResumeSourceEvidenceMatcher.isValidReference(reference, occurrences, Set.of()))
                .isFalse();
    }
}
