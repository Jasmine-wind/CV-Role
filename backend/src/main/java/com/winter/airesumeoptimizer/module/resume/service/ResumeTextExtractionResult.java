package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Internal parse-time extraction result retaining candidate/provenance metadata. */
public record ResumeTextExtractionResult(
        String text,
        String candidateType,
        List<ResumeBlockDTO> sourceBlocks,
        int legacyScore,
        int positionScore,
        int layoutLiteScore,
        List<Candidate> candidates,
        int pageCount,
        /** true when a PDF image is present, false when checked and absent, null when unknown */
        Boolean imageContentPresent,
        /** true when the extractor knows the physical page count; false means unsupported/unknown */
        boolean pageCountKnown) {

    public ResumeTextExtractionResult {
        text = text == null ? "" : text;
        sourceBlocks = copyBlocks(sourceBlocks);
        candidateType = candidateType == null ? "LEGACY" : candidateType;
        pageCount = Math.max(0, pageCount);
        candidates = nonNullCopy(candidates);
    }

    public ResumeTextExtractionResult(
            String text,
            String candidateType,
            List<ResumeBlockDTO> sourceBlocks,
            int legacyScore,
            int positionScore,
            int layoutLiteScore) {
        this(text, candidateType, sourceBlocks, legacyScore, positionScore, layoutLiteScore,
                List.of(), 0, null, false);
    }

    /** Compatibility constructor retaining the pre-classification page metadata shape. */
    public ResumeTextExtractionResult(
            String text,
            String candidateType,
            List<ResumeBlockDTO> sourceBlocks,
            int legacyScore,
            int positionScore,
            int layoutLiteScore,
            List<Candidate> candidates,
            int pageCount) {
        this(text, candidateType, sourceBlocks, legacyScore, positionScore, layoutLiteScore,
                candidates, pageCount, null, pageCount > 0);
    }

    /**
     * Compatibility constructor for callers that already retain candidate details but do not
     * know the physical document page count.
     */
    public ResumeTextExtractionResult(
            String text,
            String candidateType,
            List<ResumeBlockDTO> sourceBlocks,
            int legacyScore,
            int positionScore,
            int layoutLiteScore,
            List<Candidate> candidates) {
        this(text, candidateType, sourceBlocks, legacyScore, positionScore, layoutLiteScore,
                candidates, 0, null, false);
    }

    /** One complete extraction candidate, including the source occurrences used to prove it. */
    /** Compatibility constructor retaining the image metadata shape before page-known metadata. */
    public ResumeTextExtractionResult(
            String text,
            String candidateType,
            List<ResumeBlockDTO> sourceBlocks,
            int legacyScore,
            int positionScore,
            int layoutLiteScore,
            List<Candidate> candidates,
            int pageCount,
            Boolean imageContentPresent) {
        this(text, candidateType, sourceBlocks, legacyScore, positionScore, layoutLiteScore,
                candidates, pageCount, imageContentPresent, pageCount > 0);
    }

    public record Candidate(
            String text,
            String candidateType,
            List<ResumeBlockDTO> sourceBlocks,
            int textScore) {

        public Candidate {
            text = text == null ? "" : text;
            candidateType = candidateType == null ? "LEGACY" : candidateType;
            sourceBlocks = copyBlocks(sourceBlocks);
        }

        /** Return a detached snapshot so mutable DTOs cannot mutate this candidate. */
        @Override
        public List<ResumeBlockDTO> sourceBlocks() {
            return copyBlocks(sourceBlocks);
        }
    }

    /** Return a detached snapshot of mutable source DTOs while retaining duplicate occurrences. */
    @Override
    public List<ResumeBlockDTO> sourceBlocks() {
        return copyBlocks(sourceBlocks);
    }

    private static <T> List<T> nonNullCopy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).toList();
    }

    private static List<ResumeBlockDTO> copyBlocks(List<ResumeBlockDTO> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(ResumeTextExtractionResult::copyBlock)
                .toList();
    }

    private static ResumeBlockDTO copyBlock(ResumeBlockDTO source) {
        return ResumeBlockDTO.builder()
                .id(source.getId())
                .index(source.getIndex())
                .originalIndex(source.getOriginalIndex())
                .displayOrder(source.getDisplayOrder())
                .text(source.getText())
                .page(source.getPage())
                .x(source.getX())
                .y(source.getY())
                .width(source.getWidth())
                .height(source.getHeight())
                .fontSize(source.getFontSize())
                .fontName(source.getFontName())
                .boldHint(source.getBoldHint())
                .indent(source.getIndent())
                .bulletHint(source.getBulletHint())
                .role(source.getRole())
                .sourceBlockIds(copyNestedList(source.getSourceBlockIds()))
                .sourceOccurrenceIds(copyNestedList(source.getSourceOccurrenceIds()))
                .prevText(source.getPrevText())
                .nextText(source.getNextText())
                .sourceType(source.getSourceType())
                .iconType(source.getIconType())
                .sourceSection(source.getSourceSection())
                .ruleSection(source.getRuleSection())
                .ruleConfidence(source.getRuleConfidence())
                .sourceSectionConfidence(source.getSourceSectionConfidence())
                .lockedLevel(source.getLockedLevel())
                .resumeTypeHint(source.getResumeTypeHint())
                .parseMode(source.getParseMode())
                .finalSectionSource(source.getFinalSectionSource())
                .sectionLocked(source.getSectionLocked())
                .build();
    }

    private static List<String> copyNestedList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return values == null ? null : List.of();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
