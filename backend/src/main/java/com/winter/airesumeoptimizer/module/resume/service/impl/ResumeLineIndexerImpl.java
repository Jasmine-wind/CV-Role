package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeLineIndexer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResumeLineIndexerImpl implements ResumeLineIndexer {

    @Override
    public List<ResumeIndexedLineDTO> index(List<ResumeRawSectionDTO> rawSections) {
        List<ResumeIndexedLineDTO> indexedLines = new ArrayList<>();
        int lineId = 1;
        java.util.Set<String> usedOccurrenceIds = new java.util.LinkedHashSet<>();
        java.util.Map<String, java.util.List<OccurrenceAssignment>> occurrenceAssignments = new java.util.LinkedHashMap<>();
        List<ResumeRawSectionDTO> sections = (rawSections == null ? List.<ResumeRawSectionDTO>of() : rawSections).stream()
                .filter(section -> section != null)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        sections.sort(Comparator.comparing(section -> section.getDisplayOrder() == null ? Integer.MAX_VALUE : section.getDisplayOrder()));
        for (ResumeRawSectionDTO section : sections) {
            List<ResumeRawSectionBlockDTO> blocks = (section.getBlocks() == null ? List.<ResumeRawSectionBlockDTO>of() : section.getBlocks()).stream()
                    .filter(block -> block != null)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            blocks.sort(Comparator
                    .comparing((ResumeRawSectionBlockDTO block) -> block.getDisplayOrder() == null ? Integer.MAX_VALUE : block.getDisplayOrder())
                    .thenComparing(block -> block.getOriginalIndex() == null ? Integer.MAX_VALUE : block.getOriginalIndex())
                    .thenComparing(block -> block.getIndex() == null ? Integer.MAX_VALUE : block.getIndex()));
            for (ResumeRawSectionBlockDTO block : blocks) {
                String text = block.getText() == null ? "" : block.getText();
                String normalized = normalize(text);
                int currentLineId = lineId++;
                List<String> sourceBlockIds = sourceBlockIds(block);
                List<String> sourceOccurrenceIds = sourceOccurrenceIds(
                        block, currentLineId, usedOccurrenceIds, occurrenceAssignments);
                indexedLines.add(ResumeIndexedLineDTO.builder()
                        .lineId(currentLineId)
                        .page(block.getPage())
                        .text(text)
                        .normalizedText(normalized)
                        .sourceType(resolveSourceType(block))
                        .rawSectionId(section.getId())
                        .sectionHint(section.getNormalizedSection())
                        .sectionConfidence(section.getConfidence())
                        .isNoise(isNoise(normalized))
                        .sourceBlockId(sourceBlockIds.isEmpty() && hasUsableId(block.getId())
                                ? block.getId().strip() : sourceBlockIds.isEmpty() ? null : sourceBlockIds.get(0))
                        .sourceBlockIds(sourceBlockIds)
                        .sourceOccurrenceIds(sourceOccurrenceIds)
                        .originalIndex(block.getOriginalIndex())
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
                        .build());
            }
        }
        return indexedLines;
    }

    private List<String> sourceOccurrenceIds(
            ResumeRawSectionBlockDTO block,
            int lineId,
            java.util.Set<String> usedOccurrenceIds,
            java.util.Map<String, java.util.List<OccurrenceAssignment>> occurrenceAssignments) {
        List<String> requested = block == null ? List.of() : block.getSourceOccurrenceIds();
        java.util.List<String> result = new java.util.ArrayList<>();
        java.util.Map<String, Integer> requestedOrdinals = new java.util.LinkedHashMap<>();
        java.util.List<OccurrenceAssignment> newAssignments = new java.util.ArrayList<>();
        for (String value : requested == null ? List.<String>of() : requested) {
            if (!hasUsableOccurrenceId(value)) {
                continue;
            }
            String base = value.strip();
            int ordinal = requestedOrdinals.merge(base, 1, Integer::sum) - 1;
            OccurrenceAssignment existing = findAssignment(
                    block, occurrenceAssignments.getOrDefault(base, List.of()), ordinal);
            String assigned;
            if (existing != null) {
                assigned = existing.assignedId();
            } else {
                assigned = uniqueOccurrenceId(base, usedOccurrenceIds, result);
                newAssignments.add(new OccurrenceAssignment(
                        base, sourceBlockIds(block), assigned));
            }
            if (!result.contains(assigned)) {
                result.add(assigned);
            }
        }
        if (!newAssignments.isEmpty()) {
            for (OccurrenceAssignment assignment : newAssignments) {
                occurrenceAssignments.computeIfAbsent(
                                assignment.requestedBase(), ignored -> new java.util.ArrayList<>())
                        .add(assignment);
            }
        }
        if (!result.isEmpty()) {
            return List.copyOf(result);
        }
        // sourceBlockId/id describes the visual block and must not be promoted to an
        // occurrence. A line ordinal is a deterministic fallback for this newly indexed view.
        if (lineId <= 0) {
            return List.of();
        }
        String fallback = "indexed-line-" + lineId;
        usedOccurrenceIds.add(fallback);
        return List.of(fallback);
    }

    private OccurrenceAssignment findAssignment(
            ResumeRawSectionBlockDTO block,
            List<OccurrenceAssignment> assignments,
            int ordinal) {
        List<String> currentIds = sourceBlockIds(block);
        return assignments.stream()
                .filter(assignment -> isSameSourceProjection(currentIds, assignment.sourceBlockIds()))
                .skip(ordinal)
                .findFirst()
                .orElse(null);
    }

    private boolean isSameSourceProjection(
            List<String> currentIds, List<String> previousIds) {
        for (String currentId : currentIds == null ? List.<String>of() : currentIds) {
            for (String previousId : previousIds == null ? List.<String>of() : previousIds) {
                if (currentId.equals(previousId)
                        || fragmentBase(currentId).equals(fragmentBase(previousId))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String uniqueOccurrenceId(
            String base, java.util.Set<String> usedOccurrenceIds, List<String> currentResult) {
        String candidate = base;
        int suffix = 2;
        while (usedOccurrenceIds.contains(candidate) || currentResult.contains(candidate)) {
            candidate = base + "~" + suffix++;
        }
        usedOccurrenceIds.add(candidate);
        return candidate;
    }

    private record OccurrenceAssignment(
            String requestedBase, List<String> sourceBlockIds, String assignedId) {
    }

    private String fragmentBase(String value) {
        return value == null ? "" : value.replaceFirst("#fragment-\\d+$", "");
    }

    private boolean hasUsableOccurrenceId(String value) {
        return value != null && !value.isBlank()
                && !"null".equalsIgnoreCase(value.strip())
                && !"undefined".equalsIgnoreCase(value.strip());
    }

    private String resolveSourceType(ResumeRawSectionBlockDTO block) {
        if (block != null && block.getSourceType() != null && !block.getSourceType().isBlank()) {
            return block.getSourceType();
        }
        if (block != null && block.getIconType() != null && !block.getIconType().isBlank()) {
            return "icon-line";
        }
        return "line";
    }

    private List<String> sourceBlockIds(ResumeRawSectionBlockDTO block) {
        if (block == null) {
            return List.of();
        }
        if (block.getSourceBlockIds() != null && !block.getSourceBlockIds().isEmpty()) {
            return block.getSourceBlockIds().stream()
                    .filter(this::hasUsableId)
                    .map(String::strip)
                    .distinct()
                    .toList();
        }
        return hasUsableId(block.getId()) ? List.of(block.getId().strip()) : List.of();
    }

    private boolean hasUsableId(String value) {
        return value != null && !value.isBlank()
                && !"null".equalsIgnoreCase(value.strip())
                && !"undefined".equalsIgnoreCase(value.strip());
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    private boolean isNoise(String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (value.matches("^[\\d一二三四五六七八九十]+[.、．)]?$")) {
            return true;
        }
        return value.matches("^[\\s\\-_=+*#·•。.,，、;；:：|/\\\\\\[\\]()（）]+$");
    }
}
