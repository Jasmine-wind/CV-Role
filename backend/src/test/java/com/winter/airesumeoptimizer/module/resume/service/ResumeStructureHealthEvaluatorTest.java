package com.winter.airesumeoptimizer.module.resume.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeExperienceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredDataDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeStructureHealthEvaluatorTest {

    private final ResumeStructureHealthEvaluator evaluator = new ResumeStructureHealthEvaluator();

    @Test
    void headingsAreExcludedAndLabelValuesAreCountedAsRepresentedFacts() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("技能\n所在地：North Harbor\n目标岗位：Platform Engineer")
                .skills(List.of("Platform Engineer"))
                .basicInfo(java.util.Map.of("location", "North Harbor"))
                .jobIntention("Platform Engineer")
                .build();

        var health = evaluator.evaluate(content);

        assertThat(health.sourceCoverage()).isEqualTo(100);
        assertThat(health.hardInvariantPass()).isTrue();
    }

    @Test
    void longTextFragmentsSharingOneSourceOccurrenceAreCountedAsOneFact() {
        String original = "负责订单服务开发，保留完整来源";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText(original)
                .others(List.of(original))
                .build();
        List<ResumeBlockDTO> sourceBlocks = List.of(
                ResumeBlockDTO.builder()
                        .id("source-row#fragment-0")
                        .sourceBlockIds(List.of("source-row#fragment-0"))
                        .sourceOccurrenceIds(List.of("row-occurrence"))
                        .text("负责订单服务")
                        .build(),
                ResumeBlockDTO.builder()
                        .id("source-row#fragment-1")
                        .sourceBlockIds(List.of("source-row#fragment-1"))
                        .sourceOccurrenceIds(List.of("row-occurrence"))
                        .text("开发，保留完整来源")
                        .build());

        var health = evaluator.evaluate(content, sourceBlocks, List.of());

        assertThat(health.meaningfulSourceCount()).isEqualTo(1);
        assertThat(health.orphanContentCount()).isZero();
        assertThat(health.hardInvariantViolations()).doesNotContain("NO_LOSS", "NO_HALLUCINATION");
    }

    @Test
    void adjacentWrappedOccurrencesCanShareOneJoinedCanonicalClaim() {
        String joined = "重视系统稳定性、可观测性与可维护性";
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("重视系统稳定\n性、可观测性与可维护性")
                .others(List.of(joined))
                .build();
        List<ResumeBlockDTO> sourceBlocks = List.of(
                ResumeBlockDTO.builder()
                        .text("重视系统稳定")
                        .sourceOccurrenceIds(List.of("row-1"))
                        .build(),
                ResumeBlockDTO.builder()
                        .text("性、可观测性与可维护性")
                        .sourceOccurrenceIds(List.of("row-2"))
                        .build());

        var health = evaluator.evaluate(content, sourceBlocks, List.of());

        assertThat(health.meaningfulSourceCount()).isEqualTo(2);
        assertThat(health.representedSourceCount()).isEqualTo(2);
        assertThat(health.orphanContentCount()).isZero();
    }

    @Test
    void duplicateExplicitOccurrenceIdsRemainAHealthViolationAfterSafeRenaming() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("Java\nJava")
                .build();
        List<com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO> sourceBlocks = List.of(
                com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO.builder()
                        .text("Java")
                        .sourceOccurrenceIds(List.of("same"))
                        .build(),
                com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO.builder()
                        .text("Java")
                        .sourceOccurrenceIds(List.of("same"))
                        .build());

        var health = evaluator.evaluate(content, sourceBlocks, List.of());

        assertThat(health.duplicateSourceCount()).isEqualTo(1);
        assertThat(health.hardInvariantViolations()).contains("NO_DUPLICATION");
    }

    @Test
    void pendingSourceFactIsNotLostButRemainsUnresolvedUntilUserReview() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("Unresolved source-backed line")
                .build();

        var health = evaluator.evaluate(content, List.of(), List.of("Unresolved source-backed line"));

        assertThat(health.sourceCoverage()).isEqualTo(100);
        assertThat(health.orphanContentCount()).isZero();
    }

    @Test
    void singleCharacterSourceFactFailsNoLossWhenNotRepresented() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("A")
                .build();

        var health = evaluator.evaluate(content);

        assertThat(health.orphanContentCount()).isEqualTo(1);
        assertThat(health.hardInvariantViolations()).contains("NO_LOSS");
    }

    @Test
    void flattenedContactSourceRowIsRepresentedByItsProjectedValues() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("李明")
                .phone("13812345678")
                .email("liming.dev@example.com")
                .basicInfo(java.util.Map.of(
                        "location", "上海",
                        "github", "github.com/liming-dev"))
                .rawText("李明\n上海 · 138-1234-5678 · liming.dev@example.com · github.com/liming-dev")
                .build();
        List<ResumeBlockDTO> sourceBlocks = List.of(
                ResumeBlockDTO.builder().text("李明").build(),
                ResumeBlockDTO.builder()
                        .text("上海 · 138-1234-5678 · liming.dev@example.com · github.com/liming-dev")
                        .build());

        var health = evaluator.evaluate(content, sourceBlocks, List.of());

        assertThat(health.orphanContentCount()).isZero();
    }

    @Test
    void normalizedFormattedPhoneRemainsSourceBacked() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("上海138-1234-5678liming.dev@example.com")
                .phone("13812345678")
                .build();
        List<ResumeBlockDTO> sourceBlocks = List.of(ResumeBlockDTO.builder()
                .text("上海138-1234-5678liming.dev@example.com")
                .build());

        var health = evaluator.evaluate(content, sourceBlocks, List.of());

        assertThat(health.hardInvariantViolations()).doesNotContain("NO_HALLUCINATION");
    }

    @Test
    void inventedDisplayValueFailsNoHallucination() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("Confirmed source line")
                .name("Confirmed source line")
                .others(List.of("Invented value"))
                .build();

        var health = evaluator.evaluate(content);

        assertThat(health.hardInvariantPass()).isFalse();
        assertThat(health.hardInvariantViolations()).contains("NO_HALLUCINATION");
    }

    @Test
    void reorderedTokensAcrossOneSourceLineAreNotAcceptedAsSourceBacked() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("Java Spring Boot")
                .skills(List.of("Spring Java"))
                .build();

        var health = evaluator.evaluate(content);

        assertThat(health.hardInvariantPass()).isFalse();
        assertThat(health.hardInvariantViolations()).contains("NO_HALLUCINATION");
    }

    @Test
    void repeatedEvidenceAcrossSiblingEntriesFailsDuplicationAndBoundaryChecks() {
        ResumeExperienceDTO first = ResumeExperienceDTO.builder()
                .type("WORK")
                .evidence(List.of("Shared source evidence"))
                .build();
        ResumeExperienceDTO second = ResumeExperienceDTO.builder()
                .type("WORK")
                .evidence(List.of("Shared source evidence"))
                .build();
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .rawText("Shared source evidence")
                .structuredData(ResumeStructuredDataDTO.builder()
                        .experiences(List.of(first, second))
                        .build())
                .build();

        var health = evaluator.evaluate(content);

        assertThat(health.duplicateSourceCount()).isEqualTo(1);
        assertThat(health.entryBoundaryViolations()).isEqualTo(1);
        assertThat(health.hardInvariantViolations()).contains("NO_DUPLICATION", "ENTRY_BOUNDARY");
    }
}
