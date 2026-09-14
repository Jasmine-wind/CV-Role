package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceBlockRole;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Occurrence-level source evidence primitives shared by AI advisory filtering and delivery
 * validation. Text in different rows is never combined to prove one claim.
 */
final class ResumeSourceEvidenceMatcher {

    private static final Pattern ASCII_VALUE = Pattern.compile(".*[a-z0-9].*", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_VALUE = Pattern.compile(
            "[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STRUCTURAL_HEADING = Pattern.compile(
            "(?i)^(?:个人信息|基本信息|联系方式|教育经历|教育背景|专业技能|技能|技术能力|工作经历|工作经验|职业经历|实习经历|项目经历|项目经验|校园经历|在校经历|获奖经历|荣誉奖项|证书|自我评价|个人总结|个人概述|profile|personal info|education|skills|experience|projects|summary|others|awards|certificates?)$");
    private static final Pattern SEPARATOR = Pattern.compile("[\\s\\p{Punct}\\p{Pd}，。；：、（）【】《》“”‘’·•]+");
    /** Separators that do not split a formatted lexical value such as 138-1234-5678. */
    private static final Pattern VALUE_TOKEN_SEPARATOR = Pattern.compile(
            "[\\s，。；：、（）【】《》“”‘’·•|]+");

    private ResumeSourceEvidenceMatcher() {
    }

    static List<Occurrence> index(List<ResumeBlockDTO> blocks) {
        List<Occurrence> occurrences = new ArrayList<>();
        Set<String> usedOccurrenceIds = new LinkedHashSet<>();
        Map<String, Occurrence> firstOccurrenceById = new LinkedHashMap<>();
        int synthetic = 0;
        for (ResumeBlockDTO block : blocks == null ? List.<ResumeBlockDTO>of() : blocks) {
            if (block == null || !hasText(block.getText())) {
                continue;
            }
            boolean syntheticId = !hasConcreteSourceOccurrenceId(block);
            String fallbackBlockId = "source-block-" + synthetic;
            String fallbackOccurrenceId = "source-occurrence-" + synthetic++;
            List<String> blockIds = sourceIds(block.getSourceBlockIds(), block.getId(), fallbackBlockId);
            // A visual block can contain several source rows. Never promote block IDs to
            // occurrence IDs; when the payload has no occurrence marker, allocate a distinct
            // deterministic synthetic occurrence for this row instead.
            List<String> occurrenceIds = occurrenceIdsForBlock(
                    block,
                    rawOccurrenceIds(block.getSourceOccurrenceIds()),
                    fallbackOccurrenceId,
                    usedOccurrenceIds,
                    firstOccurrenceById);
            List<String> ids = unionIds(occurrenceIds, blockIds,
                    hasText(block.getId()) ? List.of(block.getId()) : List.of());
            if (ids.isEmpty()) {
                ids = List.of(fallbackBlockId);
                occurrenceIds = uniqueOccurrenceIds(
                        List.of(), List.of(), fallbackOccurrenceId, usedOccurrenceIds);
            }
            Occurrence projectedOnto = occurrenceIds.stream()
                    .map(firstOccurrenceById::get)
                    .filter(previous -> previous != null && isSameSourceProjection(block, previous))
                    .findFirst()
                    .orElse(null);
            if (projectedOnto != null) {
                Occurrence merged = mergeProjection(projectedOnto, block, ids, blockIds, occurrenceIds);
                int previousIndex = occurrences.indexOf(projectedOnto);
                if (previousIndex >= 0) {
                    occurrences.set(previousIndex, merged);
                }
                firstOccurrenceById.replaceAll((id, occurrence) -> occurrence == projectedOnto ? merged : occurrence);
                for (String occurrenceId : occurrenceIds) {
                    firstOccurrenceById.put(occurrenceId, merged);
                }
                continue;
            }
            Occurrence occurrence = new Occurrence(
                    occurrenceIds.get(0),
                    ids,
                    blockIds,
                    occurrenceIds,
                    block.getSourceSection(),
                    block.getText().strip(),
                    block.getOriginalIndex() == null ? block.getIndex() : block.getOriginalIndex(),
                    block.getOriginalIndex() == null ? block.getIndex() : block.getOriginalIndex(),
                    block.getIndex(),
                    block.getIndex(),
                    block.getPage(),
                    block.getX(),
                    block.getY(),
                    block.getWidth(),
                    block.getHeight(),
                    block.getFontSize(),
                    block.getFontName(),
                    block.getBoldHint(),
                    block.getIndent(),
                    block.getBulletHint(),
                    block.getRole(),
                    block.getSourceType(),
                    syntheticId);
            occurrences.add(occurrence);
            for (String occurrenceId : occurrenceIds) {
                firstOccurrenceById.putIfAbsent(occurrenceId, occurrence);
            }
        }
        return List.copyOf(occurrences);
    }

    static Map<String, List<Occurrence>> byId(List<Occurrence> occurrences) {
        Map<String, List<Occurrence>> result = new LinkedHashMap<>();
        for (Occurrence occurrence : occurrences == null ? List.<Occurrence>of() : occurrences) {
            // `ids` intentionally includes block IDs for non-authoritative lookup and UI
            // traceability. A source reference, however, is valid only against the occurrence
            // namespace; otherwise a legacy sourceBlockId can masquerade as provenance.
            for (String id : occurrence.sourceOccurrenceIds()) {
                if (hasOccurrenceId(id)) {
                    result.computeIfAbsent(id, ignored -> new ArrayList<>()).add(occurrence);
                }
            }
        }
        return result;
    }

    static boolean isClaimSupported(String value, List<Occurrence> occurrences, Set<String> allowedSections) {
        if (!hasText(value)) {
            return false;
        }
        return (occurrences == null ? List.<Occurrence>of() : occurrences).stream()
                .filter(occurrence -> sectionAllowed(occurrence.section(), allowedSections))
                .anyMatch(occurrence -> matchesOccurrence(value, occurrence.text()));
    }

    static List<Occurrence> matchingOccurrences(
            String value, List<Occurrence> occurrences, Set<String> allowedSections) {
        return matchingOccurrences(value, occurrences, allowedSections, false);
    }

    static List<Occurrence> matchingOccurrences(
            String value,
            List<Occurrence> occurrences,
            Set<String> allowedSections,
            boolean allowUnscopedSections) {
        if (!hasText(value)) {
            return List.of();
        }
        return (occurrences == null ? List.<Occurrence>of() : occurrences).stream()
                .filter(occurrence -> sectionAllowed(
                        occurrence.section(), allowedSections, allowUnscopedSections))
                .filter(occurrence -> matchesOccurrence(value, occurrence.text()))
                .toList();
    }

    static boolean isValidReference(
            ResumeSourceRefDTO reference,
            List<Occurrence> occurrences,
            Set<String> allowedSections) {
        return isValidReference(reference, occurrences, allowedSections, false);
    }

    static boolean isValidReference(
            ResumeSourceRefDTO reference,
            List<Occurrence> occurrences,
            Set<String> allowedSections,
            boolean allowUnscopedSections) {
        if (reference == null || reference.getSourceOccurrenceIds() == null
                || reference.getSourceOccurrenceIds().isEmpty() || !hasText(reference.getText())) {
            return false;
        }
        List<String> rawIds = reference.getSourceOccurrenceIds();
        List<String> ids = rawIds.stream()
                .filter(ResumeSourceEvidenceMatcher::hasOccurrenceId)
                .map(String::strip)
                .toList();
        if (ids.size() != rawIds.size()
                || new LinkedHashSet<>(ids).size() != ids.size()) {
            return false;
        }
        Map<String, List<Occurrence>> byId = byId(occurrences);
        List<Occurrence> selected = new ArrayList<>();
        Set<String> selectedPrimaryIds = new LinkedHashSet<>();
        for (String id : ids) {
            List<Occurrence> matches = byId.get(id);
            if (matches == null || matches.size() != 1) {
                return false;
            }
            Occurrence occurrence = matches.get(0);
            if (!sectionAllowed(occurrence.section(), allowedSections, allowUnscopedSections)) {
                return false;
            }
            // A logical block may retain several namespaced source occurrences (for example,
            // wrapped fragments or a repeated raw ID). They all describe the same retained block
            // text, so validate the IDs individually but check span continuity once per block.
            if (selectedPrimaryIds.add(occurrence.primaryId())) {
                selected.add(occurrence);
            }
        }
        selected.sort(physicalOrderComparator());
        // A reference has one line/span boundary. Multiple selected occurrences are valid only
        // when their source order proves a contiguous wrapped span; otherwise the reference is
        // an implicit recombination of unrelated rows.
        if (selected.size() > 1 && !isContiguous(selected)) {
            return false;
        }
        if (!referenceCoordinatesAgree(reference, selected)) {
            return false;
        }
        String selectedText = selected.stream().map(Occurrence::text).reduce((left, right) -> left + "\n" + right).orElse("");
        return matchesCombined(reference.getText(), selectedText);
    }

    static boolean referenceSupportsValue(
            String value,
            ResumeSourceRefDTO reference,
            List<Occurrence> occurrences,
            Set<String> allowedSections) {
        return isValidReference(reference, occurrences, allowedSections)
                && matchesCombined(value, reference.getText());
    }

    static boolean sectionAllowed(String actual, Set<String> allowedSections) {
        return sectionAllowed(actual, allowedSections, false);
    }

    /**
     * Match a source occurrence only inside the requested section. Unscoped rows are accepted
     * only for a corpus that has no section boundary at all (for example the raw-text-only
     * compatibility path); once any explicit section is present, an unscoped row is not allowed
     * to satisfy an arbitrary semantic section.
     */
    static boolean sectionAllowed(
            String actual, Set<String> allowedSections, boolean allowUnscopedSections) {
        if (allowedSections == null || allowedSections.isEmpty()) {
            return true;
        }
        Set<String> expectedSections = allowedSections.stream()
                .filter(ResumeSourceEvidenceMatcher::hasText)
                .map(ResumeSourceEvidenceMatcher::normalizeSection)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (expectedSections.isEmpty()) {
            return true;
        }
        String normalizedActual = normalizeSection(actual);
        if ("GENERAL".equals(normalizedActual) || "UNKNOWN".equals(normalizedActual)) {
            return allowUnscopedSections || expectedSections.contains(normalizedActual);
        }
        return expectedSections.contains(normalizedActual);
    }

    static String normalizeSection(String value) {
        if (!hasText(value)) {
            return "UNKNOWN";
        }
        String normalized = value.strip().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "WORK", "WORK_EXPERIENCE", "EXPERIENCE", "EMPLOYMENT" -> "WORK_EXPERIENCES";
            case "INTERNSHIP", "INTERN" -> "INTERNSHIPS";
            case "PROJECT", "PROJECT_EXPERIENCE" -> "PROJECTS";
            case "CAMPUS", "ACTIVITIES" -> "CAMPUS_EXPERIENCES";
            case "AWARD", "HONOR", "HONORS" -> "AWARDS";
            case "CERTIFICATE", "CERTIFICATION" -> "CERTIFICATES";
            case "SKILL", "SKILL_EVIDENCE", "TECHNICAL_SKILLS", "TECHNIQUE" -> "SKILLS";
            case "EDUCATION_BACKGROUND" -> "EDUCATION";
            case "PROFILE", "SELF_EVALUATION" -> "SUMMARY";
            default -> normalized;
        };
    }

    static boolean matchesOccurrence(String value, String sourceText) {
        if (!hasText(value) || !hasText(sourceText) || isStructuralHeading(sourceText)) {
            return false;
        }
        String expected = value.strip().toLowerCase(Locale.ROOT);
        String actual = sourceText.strip().toLowerCase(Locale.ROOT);
        if (expected.equals(actual)) {
            return true;
        }
        String compactExpected = compact(expected);
        String compactActual = compact(actual);
        if (compactExpected.isBlank() || compactActual.isBlank()) {
            return false;
        }
        if (EMAIL_VALUE.matcher(expected).matches() && actual.contains(expected)) {
            // A visual contact row may be flattened without separators around the email. The
            // complete address is a stronger boundary than the neighbouring flattened values.
            return true;
        }
        if (ASCII_VALUE.matcher(expected).matches()) {
            String normalizedQuoted = Pattern.quote(expected);
            if (actual.matches(".*(?<![a-z0-9])" + normalizedQuoted + "(?![a-z0-9]).*")) {
                return true;
            }
        }
        // PDF extraction can concatenate a formatted phone with adjacent contact values in one
        // visual occurrence. Once punctuation is removed, digit boundaries still prove the full
        // numeric value without allowing a shorter numeric substring to match.
        if (compactExpected.matches("\\d{7,15}")
                && compactActual.matches(".*(?<!\\d)" + Pattern.quote(compactExpected) + "(?!\\d).*")) {
            return true;
        }
        if (!ASCII_VALUE.matcher(expected).matches()) {
            return compactExpected.length() >= 2 && compactActual.contains(compactExpected);
        }
        // A source row may contain a formatted value next to another ASCII value (for example
        // “138-1234-5678 · github.com/x”). Check separator-delimited tokens before compacting
        // the whole row, otherwise the neighbouring “github” would incorrectly invalidate the
        // phone's right boundary.
        for (String token : VALUE_TOKEN_SEPARATOR.split(actual)) {
            if (!token.isBlank() && compact(token).equals(compactExpected)) {
                return true;
            }
        }
        // ASCII claims must be lexical tokens. Chinese characters are valid boundaries, so
        // Java in “Java开发” is accepted while Java in JavaScript is not.
        String quoted = Pattern.quote(compactExpected);
        if (compactActual.matches(".*(?<![a-z0-9])" + quoted + "(?![a-z0-9]).*")) {
            return true;
        }
        // Mixed Chinese/ASCII claims may be a compact projection of one row, never a union of
        // rows. The minimum Chinese/ASCII token lengths avoid accepting punctuation fragments.
        return compactExpected.length() >= 4 && compactExpected.matches(".*[\\u4e00-\\u9fa5].*")
                && compactActual.contains(compactExpected);
    }

    static boolean matchesCombined(String value, String sourceText) {
        if (!hasText(value) || !hasText(sourceText)) {
            return false;
        }
        String expected = value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String actual = sourceText.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (expected.equals(actual) || actual.contains(expected)) {
            return true;
        }
        String compactExpected = compact(expected);
        String compactActual = compact(actual);
        return compactExpected.length() >= 2 && compactActual.contains(compactExpected);
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean hasOccurrenceId(String value) {
        return hasText(value)
                && !"null".equalsIgnoreCase(value.strip())
                && !"undefined".equalsIgnoreCase(value.strip());
    }

    private static boolean hasConcreteSourceOccurrenceId(ResumeBlockDTO block) {
        return block.getSourceOccurrenceIds() != null && block.getSourceOccurrenceIds().stream()
                .anyMatch(ResumeSourceEvidenceMatcher::hasOccurrenceId);
    }

    private static List<String> occurrenceIdsForBlock(
            ResumeBlockDTO block,
            List<String> requested,
            String fallback,
            Set<String> used,
            Map<String, Occurrence> firstOccurrenceById) {
        List<String> result = new ArrayList<>();
        for (String requestedId : requested == null ? List.<String>of() : requested) {
            if (!hasOccurrenceId(requestedId)) {
                continue;
            }
            String base = requestedId.strip();
            Occurrence previous = firstOccurrenceById.get(base);
            if (previous != null && isSameSourceProjection(block, previous)) {
                result.add(base);
                continue;
            }
            String candidate = base;
            int suffix = 2;
            while (used.contains(candidate) || result.contains(candidate)) {
                candidate = base + "~" + suffix++;
            }
            used.add(candidate);
            result.add(candidate);
        }
        if (result.isEmpty() && hasOccurrenceId(fallback)) {
            String base = fallback.strip();
            String candidate = base;
            int suffix = 2;
            while (used.contains(candidate)) {
                candidate = base + "~" + suffix++;
            }
            used.add(candidate);
            result.add(candidate);
        }
        return List.copyOf(result);
    }

    private static Occurrence mergeProjection(
            Occurrence previous,
            ResumeBlockDTO block,
            List<String> ids,
            List<String> blockIds,
            List<String> occurrenceIds) {
        Integer currentOrder = block.getOriginalIndex() == null ? block.getIndex() : block.getOriginalIndex();
        Integer currentLineOrder = block.getIndex();
        Integer order = previous.order();
        if (order == null) {
            order = currentOrder;
        } else if (currentOrder != null) {
            order = Math.min(order, currentOrder);
        }
        Integer endOrder = previous.endOrder();
        if (endOrder == null) {
            endOrder = currentOrder;
        } else if (currentOrder != null) {
            endOrder = Math.max(endOrder, currentOrder);
        }
        Integer lineOrder = previous.lineOrder();
        if (lineOrder == null) {
            lineOrder = currentLineOrder;
        } else if (currentLineOrder != null) {
            lineOrder = Math.min(lineOrder, currentLineOrder);
        }
        Integer endLineOrder = previous.endLineOrder();
        if (endLineOrder == null) {
            endLineOrder = currentLineOrder;
        } else if (currentLineOrder != null) {
            endLineOrder = Math.max(endLineOrder, currentLineOrder);
        }
        Integer page = previous.page() == null ? block.getPage() : previous.page();
        String text = previous.text() == null ? block.getText().strip()
                : previous.text() + block.getText().strip();
        return new Occurrence(
                previous.primaryId(),
                unionIds(previous.ids(), ids),
                unionIds(previous.sourceBlockIds(), blockIds),
                unionIds(previous.sourceOccurrenceIds(), occurrenceIds),
                previous.section() == null ? block.getSourceSection() : previous.section(),
                text,
                order,
                endOrder,
                lineOrder,
                endLineOrder,
                page,
                firstNonNull(previous.x(), block.getX()),
                firstNonNull(previous.y(), block.getY()),
                firstNonNull(previous.width(), block.getWidth()),
                firstNonNull(previous.height(), block.getHeight()),
                firstNonNull(previous.fontSize(), block.getFontSize()),
                firstNonNull(previous.fontName(), block.getFontName()),
                firstNonNull(previous.boldHint(), block.getBoldHint()),
                firstNonNull(previous.indent(), block.getIndent()),
                firstNonNull(previous.bulletHint(), block.getBulletHint()),
                previous.role() == null ? block.getRole() : previous.role(),
                firstNonNull(previous.sourceType(), block.getSourceType()),
                previous.syntheticId() && !hasConcreteSourceOccurrenceId(block));
    }

    private static boolean isSameSourceProjection(
            ResumeBlockDTO block, Occurrence previous) {
        if (block == null || previous == null) {
            return false;
        }
        List<String> currentBlockIds = sourceIds(block.getSourceBlockIds(), block.getId(), null);
        if (!currentBlockIds.isEmpty() && !previous.sourceBlockIds().isEmpty()) {
            for (String current : currentBlockIds) {
                for (String prior : previous.sourceBlockIds()) {
                    if (current.equals(prior)
                            || fragmentBase(current).equals(fragmentBase(prior))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String fragmentBase(String value) {
        return value == null ? "" : value.replaceFirst("#fragment-\\d+$", "");
    }

    private static List<String> uniqueOccurrenceIds(
            List<String> requested,
            List<String> fallbackIds,
            String fallback,
            Set<String> used) {
        List<String> candidates = requested == null || requested.isEmpty() ? fallbackIds : requested;
        List<String> result = new ArrayList<>();
        for (String requestedId : candidates == null ? List.<String>of() : candidates) {
            if (!hasOccurrenceId(requestedId)) {
                continue;
            }
            String base = requestedId.strip();
            String candidate = base;
            int suffix = 2;
            while (used.contains(candidate) || result.contains(candidate)) {
                candidate = base + "~" + suffix++;
            }
            used.add(candidate);
            result.add(candidate);
        }
        if (result.isEmpty() && hasOccurrenceId(fallback)) {
            String base = fallback.strip();
            String candidate = base;
            int suffix = 2;
            while (used.contains(candidate)) {
                candidate = base + "~" + suffix++;
            }
            used.add(candidate);
            result.add(candidate);
        }
        return List.copyOf(result);
    }

    private static List<String> rawOccurrenceIds(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(ResumeSourceEvidenceMatcher::hasOccurrenceId)
                .map(String::strip)
                .toList();
    }

    private static List<String> sourceIds(List<String> values, String id, String fallback) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (values != null) {
            values.stream()
                    .filter(ResumeSourceEvidenceMatcher::hasUsableId)
                    .map(String::strip)
                    .forEach(ids::add);
        }
        if (hasUsableId(id)) {
            ids.add(id.strip());
        }
        if (ids.isEmpty() && hasUsableId(fallback)) {
            ids.add(fallback.strip());
        }
        return List.copyOf(ids);
    }

    private static boolean hasUsableId(String value) {
        return hasOccurrenceId(value);
    }

    @SafeVarargs
    private static List<String> unionIds(List<String>... values) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (values != null) {
            for (List<String> group : values) {
                if (group != null) {
                    group.stream().filter(ResumeSourceEvidenceMatcher::hasUsableId)
                            .map(String::strip).forEach(ids::add);
                }
            }
        }
        return List.copyOf(ids);
    }

    private static boolean referenceCoordinatesAgree(ResumeSourceRefDTO reference, List<Occurrence> selected) {
        if (reference.getStartLine() != null && reference.getStartLine() < 1
                || reference.getEndLine() != null && reference.getEndLine() < 1
                || reference.getStartLine() != null && reference.getEndLine() != null
                && reference.getStartLine() > reference.getEndLine()) {
            return false;
        }
        if (reference.getPage() != null) {
            for (Occurrence occurrence : selected) {
                if (occurrence.page() != null && !reference.getPage().equals(occurrence.page())) {
                    return false;
                }
            }
        }
        if (reference.getStartLine() != null && reference.getEndLine() != null) {
            for (Occurrence occurrence : selected) {
                Integer occurrenceStart = occurrence.lineOrder() == null
                        ? occurrence.order() : occurrence.lineOrder();
                Integer occurrenceEnd = occurrence.endLineOrder() == null
                        ? (occurrence.endOrder() == null ? occurrenceStart : occurrence.endOrder())
                        : occurrence.endLineOrder();
                if (occurrenceStart != null && occurrenceEnd != null
                        && (occurrenceEnd + 1 < reference.getStartLine()
                        || occurrenceStart + 1 > reference.getEndLine())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Prefer the physical indexed-line order when it is available. {@code order} is the
     * retained source order and may legitimately reset at a page boundary (for example, a PDF
     * page-local original index); it is not a safe coordinate for a cross-page span.
     */
    static Comparator<Occurrence> physicalOrderComparator() {
        return Comparator.comparing(
                        ResumeSourceEvidenceMatcher::physicalStart,
                        Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Occurrence::order, Comparator.nullsLast(Integer::compareTo));
    }

    private static Integer physicalStart(Occurrence occurrence) {
        return occurrence == null
                ? null
                : occurrence.lineOrder() == null ? occurrence.order() : occurrence.lineOrder();
    }

    private static Integer physicalEnd(Occurrence occurrence) {
        return occurrence == null
                ? null
                : occurrence.endLineOrder() == null
                ? (occurrence.endOrder() == null ? physicalStart(occurrence) : occurrence.endOrder())
                : occurrence.endLineOrder();
    }

    private static boolean isContiguous(List<Occurrence> selected) {
        if (selected == null || selected.size() <= 1) {
            return true;
        }
        String section = normalizeSection(selected.get(0).section());
        for (int index = 1; index < selected.size(); index++) {
            Integer previous = physicalEnd(selected.get(index - 1));
            Integer current = physicalStart(selected.get(index));
            if (previous == null || current == null || current != previous + 1
                    || !section.equals(normalizeSection(selected.get(index).section()))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStructuralHeading(String value) {
        String normalized = value == null ? "" : value.strip()
                .replaceAll("[：:|丨]+$", "").strip();
        return STRUCTURAL_HEADING.matcher(normalized).matches();
    }

    private static String compact(String value) {
        return SEPARATOR.matcher(value == null ? "" : value).replaceAll("");
    }

    private static <T> T firstNonNull(T first, T second) {
        return first == null ? second : first;
    }

    record Occurrence(
            String primaryId,
            List<String> ids,
            List<String> sourceBlockIds,
            List<String> sourceOccurrenceIds,
            String section,
            String text,
            Integer order,
            Integer endOrder,
            Integer lineOrder,
            Integer endLineOrder,
            Integer page,
            Double x,
            Double y,
            Double width,
            Double height,
            Double fontSize,
            String fontName,
            Boolean boldHint,
            Integer indent,
            Boolean bulletHint,
            ResumeSourceBlockRole role,
            String sourceType,
            boolean syntheticId) {
    }
}
