package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceBlockRole;
import com.winter.airesumeoptimizer.module.resume.dto.SourceSectionConfidence;
import com.winter.airesumeoptimizer.module.resume.service.ResumeBlockBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ResumeBlockBuilderImpl implements ResumeBlockBuilder {

    private static final int MAX_BLOCK_TEXT_LENGTH = 500;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\(\\+?86\\)|\\+?86|86)?[-\\s]*1[3-9]\\d[-\\s]?\\d{4}[-\\s]?\\d{4}(?!\\d)");
    private static final Pattern GITHUB_PATTERN = Pattern.compile("(?i)(?:https?://)?github\\.com/[A-Za-z0-9_.-]+");
    private static final Set<String> LOCKED_SOURCE_SECTIONS = Set.of(
            "BASIC_INFO",
            "EDUCATION",
            "SKILLS",
            "WORK_EXPERIENCES",
            "INTERNSHIPS",
            "PROJECTS",
            "CAMPUS_EXPERIENCES",
            "AWARDS",
            "CERTIFICATES",
            "SUMMARY");

    @Override
    public List<ResumeBlockDTO> build(ResumeTextCleanResultDTO cleanResult) {
        if (cleanResult == null || cleanResult.getSections() == null || cleanResult.getSections().isEmpty()) {
            return List.of();
        }

        List<ResumeBlockDTO> blocks = new ArrayList<>();
        Set<String> usedBlockIds = new java.util.LinkedHashSet<>();
        Set<String> usedSourceIds = new LinkedHashSet<>();
        List<SourceOccurrenceAssignment> occurrenceAssignments = new ArrayList<>();
        int index = 0;
        for (ResumeTextSectionDTO section : cleanResult.getSections()) {
            List<ResumeRawSectionBlockDTO> sourceLines = sourceLines(section);
            String sectionType = section.getSectionType();
            SourceSectionConfidence sourceConfidence = sourceSectionConfidence(section);
            for (ResumeRawSectionBlockDTO sourceLine : sourceLines) {
                String text = normalizeBlockText(sourceLine.getText());
                if (!shouldKeep(text) || sourceLine.getRole() == ResumeSourceBlockRole.SECTION_HEADING) {
                    continue;
                }
                String iconType = sourceLine.getIconType() == null
                        ? resolveIconType(section, text)
                        : sourceLine.getIconType();
                List<String> fragments = splitBlockText(text);
                String sourceId = sourceIdentifier(sourceLine, index, usedBlockIds);
                // A logical block ID is not an occurrence identity. When an older source line
                // lacks explicit occurrence IDs, allocate a deterministic row fallback instead
                // of copying sourceId and accidentally collapsing repeated source rows.
                // Resolve the occurrence set once per source row: length-based block fragments
                // are projections of the same occurrence, not new source occurrences.
                String occurrenceFallback = "source-occurrence-" + index;
                List<String> occurrenceIds = sourceOccurrenceIds(
                        sourceLine, sourceId, occurrenceFallback, usedSourceIds, occurrenceAssignments);
                for (int fragmentIndex = 0; fragmentIndex < fragments.size(); fragmentIndex++) {
                    String fragment = fragments.get(fragmentIndex);
                    int blockIndex = index++;
                    String blockId = fragments.size() == 1 ? sourceId : sourceId + "#fragment-" + fragmentIndex;
                    while (!usedBlockIds.add(blockId)) {
                        blockId = blockId + "~2";
                    }
                    blocks.add(ResumeBlockDTO.builder()
                            .id(blockId)
                            .index(blockIndex)
                            .originalIndex(sourceLine.getOriginalIndex() == null ? blockIndex : sourceLine.getOriginalIndex())
                            .displayOrder(sourceLine.getDisplayOrder() == null ? blockIndex : sourceLine.getDisplayOrder())
                            .text(fragment)
                            .page(sourceLine.getPage())
                            .x(sourceLine.getX())
                            .y(sourceLine.getY())
                            .width(sourceLine.getWidth())
                            .height(sourceLine.getHeight())
                            .fontSize(sourceLine.getFontSize())
                            .fontName(sourceLine.getFontName())
                            .boldHint(sourceLine.getBoldHint())
                            .indent(sourceLine.getIndent())
                            .bulletHint(sourceLine.getBulletHint())
                            .role(sourceLine.getRole() == null ? classifyRole(fragment, sourceLine) : sourceLine.getRole())
                            .sourceBlockIds(sourceBlockIds(sourceLine, blockId))
                            .sourceOccurrenceIds(occurrenceIds)
                            .sourceType(sourceLine.getSourceType() == null ? "cleanedText" : sourceLine.getSourceType())
                            .iconType(iconType)
                            .sourceSection(sectionType)
                            .ruleSection(normalizeRuleSection(sectionType))
                            .ruleConfidence(ruleConfidence(sourceConfidence, sectionType))
                            .sourceSectionConfidence(sourceConfidence.name())
                            .lockedLevel(sourceConfidence.name())
                            .finalSectionSource("RULE_SOURCE_SECTION")
                            .sectionLocked(sourceConfidence == SourceSectionConfidence.HIGH)
                            .build());
                }
                // Reserve the logical base block ID. Each fragment already reserved its own
                // occurrence IDs above, so a later source row receives a deterministic suffix.
                usedBlockIds.add(sourceId);
            }
        }
        fillNeighborContext(blocks);
        return blocks;
    }

    private String sourceIdentifier(
            ResumeRawSectionBlockDTO sourceLine,
            int fallbackIndex,
            Set<String> usedBlockIds) {
        String base = null;
        // Keep the block and occurrence namespaces independent. An occurrence ID can describe
        // one source row, but it is not a logical block ID and must not be copied here.
        if (sourceLine.getSourceBlockIds() != null) {
            base = sourceLine.getSourceBlockIds().stream()
                    .filter(this::isUsableSourceId)
                    .map(String::strip)
                    .findFirst()
                    .orElse(null);
        }
        if (base == null && sourceLine.getId() != null && !sourceLine.getId().isBlank()) {
            base = sourceLine.getId();
        }
        if (base == null || base.isBlank()) {
            base = "source-block-" + fallbackIndex;
        }
        String candidate = base;
        int suffix = 2;
        while (usedBlockIds.contains(candidate)) {
            candidate = base + "~" + suffix++;
        }
        return candidate;
    }

    private List<String> sourceBlockIds(ResumeRawSectionBlockDTO sourceLine, String fallbackId) {
        if (sourceLine != null && sourceLine.getSourceBlockIds() != null) {
            List<String> ids = sourceLine.getSourceBlockIds().stream()
                    .filter(this::isUsableSourceId)
                    .map(String::strip)
                    .toList();
            if (!ids.isEmpty()) {
                return ids;
            }
        }
        return fallbackId == null || fallbackId.isBlank() ? List.of() : List.of(fallbackId);
    }

    private List<String> sourceOccurrenceIds(
            ResumeRawSectionBlockDTO sourceLine,
            String sourceBlockId,
            String fallbackId,
            Set<String> usedSourceIds,
            List<SourceOccurrenceAssignment> assignments) {
        List<String> ids = new ArrayList<>();
        List<String> requested = sourceLine == null ? List.of() : sourceLine.getSourceOccurrenceIds();
        java.util.Map<String, Integer> requestedOrdinals = new java.util.LinkedHashMap<>();
        List<SourceOccurrenceAssignment> newAssignments = new ArrayList<>();
        for (String value : requested == null ? List.<String>of() : requested) {
            if (!isUsableSourceId(value)) {
                continue;
            }
            String base = value.strip();
            int ordinal = requestedOrdinals.merge(base, 1, Integer::sum) - 1;
            String assigned = findExistingAssignment(
                    base, sourceLine, sourceBlockId, assignments, ordinal);
            if (assigned == null) {
                assigned = uniqueSourceId(base, usedSourceIds);
                newAssignments.add(new SourceOccurrenceAssignment(
                        base, sourceIdentityIds(sourceLine, sourceBlockId), assigned));
            }
            if (!ids.contains(assigned)) {
                ids.add(assigned);
            }
        }
        if (ids.isEmpty() && fallbackId != null && !fallbackId.isBlank()) {
            String assigned = findExistingAssignment(
                    null, sourceLine, sourceBlockId, assignments, 0);
            if (assigned == null) {
                assigned = uniqueSourceId(fallbackId, usedSourceIds);
                newAssignments.add(new SourceOccurrenceAssignment(
                        null, sourceIdentityIds(sourceLine, sourceBlockId), assigned));
            }
            ids.add(assigned);
        }
        assignments.addAll(newAssignments);
        return List.copyOf(ids);
    }

    private String findExistingAssignment(
            String requestedBase,
            ResumeRawSectionBlockDTO sourceLine,
            String sourceBlockId,
            List<SourceOccurrenceAssignment> assignments,
            int ordinal) {
        List<String> currentSourceIds = sourceIdentityIds(sourceLine, sourceBlockId);
        return assignments.stream()
                .filter(assignment -> Objects.equals(requestedBase, assignment.requestedBase()))
                .filter(assignment -> sameSourceProjection(
                        sourceLine, currentSourceIds, assignment))
                .skip(ordinal)
                .map(SourceOccurrenceAssignment::assignedId)
                .findFirst()
                .orElse(null);
    }

    private boolean sameSourceProjection(
            ResumeRawSectionBlockDTO current,
            List<String> currentIds,
            SourceOccurrenceAssignment previous) {
        if (!hasUsableOccurrence(current)) {
            // Without an explicit occurrence marker, a repeated block ID is not enough to tell
            // a second physical row from a projection. Keep the fallback occurrence distinct.
            return false;
        }
        for (String currentId : currentIds == null ? List.<String>of() : currentIds) {
            for (String previousId : previous.sourceBlockIds() == null
                    ? List.<String>of() : previous.sourceBlockIds()) {
                if (currentId.equals(previousId)
                        || fragmentBase(currentId).equals(fragmentBase(previousId))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasUsableOccurrence(ResumeRawSectionBlockDTO sourceLine) {
        return sourceLine != null && sourceLine.getSourceOccurrenceIds() != null
                && sourceLine.getSourceOccurrenceIds().stream().anyMatch(this::isUsableSourceId);
    }

    private List<String> sourceIdentityIds(ResumeRawSectionBlockDTO sourceLine, String sourceBlockId) {
        List<String> ids = sourceBlockIds(sourceLine, sourceBlockId);
        return ids == null ? List.of() : ids;
    }

    private String fragmentBase(String value) {
        return value == null ? "" : value.replaceFirst("#fragment-\\d+$", "");
    }

    private String uniqueSourceId(String original, Set<String> usedSourceIds) {
        String base = original.strip();
        String candidate = base;
        int suffix = 2;
        while (usedSourceIds.contains(candidate)) {
            candidate = base + "~" + suffix++;
        }
        usedSourceIds.add(candidate);
        return candidate;
    }

    private record SourceOccurrenceAssignment(
            String requestedBase, List<String> sourceBlockIds, String assignedId) {
    }

    private boolean isUsableSourceId(String value) {
        return value != null && !value.isBlank()
                && !"null".equalsIgnoreCase(value.strip())
                && !"undefined".equalsIgnoreCase(value.strip());
    }

    private List<ResumeRawSectionBlockDTO> sourceLines(ResumeTextSectionDTO section) {
        if (section.getBlocks() != null && !section.getBlocks().isEmpty()) {
            return section.getBlocks().stream()
                    .filter(block -> block != null && block.getText() != null)
                    .map(this::toRawBlock)
                    .toList();
        }
        List<ResumeRawSectionBlockDTO> result = new ArrayList<>();
        List<String> lines = section.getLines() == null ? List.of() : section.getLines();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            result.add(ResumeRawSectionBlockDTO.builder()
                    .index(lineIndex)
                    .text(lines.get(lineIndex))
                    .role(classifyRole(lines.get(lineIndex), null))
                    .build());
        }
        return result;
    }

    private ResumeRawSectionBlockDTO toRawBlock(ResumeBlockDTO block) {
        return ResumeRawSectionBlockDTO.builder()
                .id(block.getId())
                .index(block.getIndex())
                .text(block.getText())
                .iconType(block.getIconType())
                .originalIndex(block.getOriginalIndex())
                .displayOrder(block.getDisplayOrder())
                .page(block.getPage())
                .x(block.getX())
                .y(block.getY())
                .width(block.getWidth())
                .height(block.getHeight())
                .fontSize(block.getFontSize())
                .fontName(block.getFontName())
                .boldHint(block.getBoldHint())
                .indent(block.getIndent())
                .bulletHint(block.getBulletHint())
                .role(block.getRole())
                .sourceBlockIds(block.getSourceBlockIds())
                .sourceOccurrenceIds(block.getSourceOccurrenceIds())
                .sourceType(block.getSourceType())
                .build();
    }

    private ResumeSourceBlockRole classifyRole(String text, ResumeRawSectionBlockDTO sourceLine) {
        if (sourceLine != null && Boolean.TRUE.equals(sourceLine.getBulletHint())) {
            return ResumeSourceBlockRole.BULLET;
        }
        if (text == null || text.isBlank()) {
            return ResumeSourceBlockRole.UNKNOWN;
        }
        String normalized = text.strip();
        if (normalized.matches("^(?:[-*•·●▪■◆◇○◦▶►✓✔])\\s+.*$")) {
            return ResumeSourceBlockRole.BULLET;
        }
        if (normalized.matches("^(?:[^:：]{1,20})[:：]\\s*.+$")) {
            return ResumeSourceBlockRole.LABEL_VALUE;
        }
        if (normalized.matches(".*(?:19|20)\\d{2}.*(?:至今|Present|[-~—至到]).*")) {
            return ResumeSourceBlockRole.ENTRY_HEADER;
        }
        return normalized.length() <= 90 && (sourceLine != null && Boolean.TRUE.equals(sourceLine.getBoldHint()))
                ? ResumeSourceBlockRole.ENTRY_HEADER
                : ResumeSourceBlockRole.PARAGRAPH;
    }

    private void fillNeighborContext(List<ResumeBlockDTO> blocks) {
        for (int index = 0; index < blocks.size(); index++) {
            ResumeBlockDTO current = blocks.get(index);
            current.setPrevText(index == 0 ? null : blocks.get(index - 1).getText());
            current.setNextText(index + 1 >= blocks.size() ? null : blocks.get(index + 1).getText());
        }
    }

    private boolean isLockedSourceSection(String sectionType) {
        return sectionType != null && LOCKED_SOURCE_SECTIONS.contains(sectionType);
    }

    private String normalizeRuleSection(String sectionType) {
        if (sectionType == null || sectionType.isBlank()) {
            return "OTHERS";
        }
        return "GENERAL".equals(sectionType) ? "OTHERS" : sectionType;
    }

    private double ruleConfidence(SourceSectionConfidence sourceConfidence, String sectionType) {
        return switch (sourceConfidence) {
            case HIGH -> 0.95;
            case MEDIUM -> 0.72;
            case LOW -> "OTHERS".equals(sectionType) ? 0.55 : 0.35;
        };
    }

    private SourceSectionConfidence sourceSectionConfidence(ResumeTextSectionDTO section) {
        SourceSectionConfidence explicitConfidence = SourceSectionConfidence.from(section.getSourceSectionConfidence());
        if (explicitConfidence != SourceSectionConfidence.LOW || section.getSourceSectionConfidence() != null) {
            return explicitConfidence;
        }
        if (isLockedSourceSection(section.getSectionType())) {
            return SourceSectionConfidence.HIGH;
        }
        return SourceSectionConfidence.LOW;
    }

    private String normalizeBlockText(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\t\\x0B\\f\\r 　]+", " ")
                .replaceFirst("^[\\s>*•·●▪■◆◇○◦▶►✓✔-]+", "")
                .strip();
    }

    private String resolveIconType(ResumeTextSectionDTO section, String text) {
        if (EMAIL_PATTERN.matcher(text).find()) {
            return "EMAIL_ICON";
        }
        if (PHONE_PATTERN.matcher(text).find()) {
            return "PHONE_ICON";
        }
        if (GITHUB_PATTERN.matcher(text).find()) {
            return "GITHUB_ICON";
        }
        return section == null ? null : section.getIconType();
    }

    private boolean shouldKeep(String text) {
        if (text.isBlank()) {
            return false;
        }
        if (text.length() >= 2) {
            return true;
        }
        // A one-character source value is still loss-sensitive evidence. Preserve letters and
        // digits while continuing to discard isolated punctuation/artifact marks.
        int codePoint = text.codePointAt(0);
        return Character.isLetterOrDigit(codePoint)
                || EMAIL_PATTERN.matcher(text).find()
                || PHONE_PATTERN.matcher(text).find()
                || text.contains("本科")
                || text.contains("硕士")
                || text.contains("博士");
    }

    List<String> splitBlockText(String text) {
        if (text == null || text.length() <= MAX_BLOCK_TEXT_LENGTH) {
            return text == null ? List.of() : List.of(text);
        }
        List<String> fragments = new ArrayList<>();
        int start = 0;
        while (text.length() - start > MAX_BLOCK_TEXT_LENGTH) {
            int end = findSplitPoint(text, start);
            fragments.add(text.substring(start, end));
            start = end;
        }
        fragments.add(text.substring(start));
        return fragments;
    }

    private int findSplitPoint(String text, int start) {
        int limit = Math.min(text.length(), start + MAX_BLOCK_TEXT_LENGTH);
        int windowStart = Math.min(limit, start + 300);
        String[] delimiterGroups = {"。！？!?；;", "，,", " "};
        for (String delimiters : delimiterGroups) {
            for (int index = limit - 1; index >= windowStart; index--) {
                if (delimiters.indexOf(text.charAt(index)) >= 0) {
                    return safeCodePointBoundary(text, index + 1);
                }
            }
        }
        return safeCodePointBoundary(text, limit);
    }

    private int safeCodePointBoundary(String text, int boundary) {
        if (boundary > 0 && boundary < text.length()
                && Character.isHighSurrogate(text.charAt(boundary - 1))
                && Character.isLowSurrogate(text.charAt(boundary))) {
            return boundary - 1;
        }
        return boundary;
    }
}
