package com.winter.airesumeoptimizer.module.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ResumeTextExtractionResultTest {

    @Test
    void nullCollectionsAndNullEntriesAreSafeWithoutInventingPageMetadata() {
        ResumeBlockDTO block = ResumeBlockDTO.builder()
                .id("source-1")
                .text("source text")
                .build();
        ResumeTextExtractionResult.Candidate candidate = new ResumeTextExtractionResult.Candidate(
                null, null, Arrays.asList(null, block), 3);

        ResumeTextExtractionResult result = new ResumeTextExtractionResult(
                null,
                null,
                Arrays.asList(null, block),
                1,
                2,
                3,
                Arrays.asList(null, candidate));

        assertThat(result.text()).isEmpty();
        assertThat(result.candidateType()).isEqualTo("LEGACY");
        assertThat(result.sourceBlocks()).singleElement()
                .satisfies(value -> {
                    assertThat(value).isNotSameAs(block);
                    assertThat(value.getId()).isEqualTo("source-1");
                    assertThat(value.getPage()).isNull();
                });
        assertThat(result.pageCount()).isZero();
        assertThat(result.pageCountKnown()).isFalse();
        assertThat(result.imageContentPresent()).isNull();
        assertThat(result.candidates()).hasSize(1);
        ResumeTextExtractionResult.Candidate storedCandidate = result.candidates().get(0);
        assertThat(storedCandidate.text()).isEmpty();
        assertThat(storedCandidate.candidateType()).isEqualTo("LEGACY");
        assertThat(storedCandidate.sourceBlocks()).singleElement()
                .satisfies(value -> assertThat(value).isNotSameAs(block));
    }

    @Test
    void sourceBlockSnapshotsDoNotAliasNestedProvenanceLists() {
        var sourceBlockIds = new java.util.ArrayList<>(java.util.List.of("block-1"));
        var occurrenceIds = new java.util.ArrayList<>(java.util.List.of("occurrence-1"));
        ResumeBlockDTO block = ResumeBlockDTO.builder()
                .id("source-1")
                .text("source text")
                .sourceBlockIds(sourceBlockIds)
                .sourceOccurrenceIds(occurrenceIds)
                .build();
        ResumeTextExtractionResult result = new ResumeTextExtractionResult(
                "source text", "LEGACY", java.util.List.of(block), 1, 1, 1);

        sourceBlockIds.add("block-2");
        occurrenceIds.add("occurrence-2");
        ResumeBlockDTO snapshot = result.sourceBlocks().get(0);

        assertThat(snapshot.getSourceBlockIds()).containsExactly("block-1");
        assertThat(snapshot.getSourceOccurrenceIds()).containsExactly("occurrence-1");
        assertThatThrownBy(() -> snapshot.getSourceOccurrenceIds().add("occurrence-3"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.sourceBlocks().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
