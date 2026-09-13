package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeAchievementDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeExperienceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredDataDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumePointerPostProcessor;
import com.winter.airesumeoptimizer.module.resume.service.ResumePointerValidator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ResumePointerPostProcessorImpl implements ResumePointerPostProcessor {

    private final ResumePointerValidator pointerValidator;

    public ResumePointerPostProcessorImpl(ResumePointerValidator pointerValidator) {
        this.pointerValidator = pointerValidator;
    }

    @Override
    public void attachSourceRefs(ResumeStructuredContentDTO structuredContent, List<ResumeIndexedLineDTO> indexedLines) {
        if (structuredContent == null || structuredContent.getStructuredData() == null || indexedLines == null || indexedLines.isEmpty()) {
            return;
        }
        ResumeStructuredDataDTO data = structuredContent.getStructuredData();
        data.setEducationSourceRefs(buildRefs(
                data.getEducation(), data.getEducationSourceRefs(), indexedLines));
        if (data.getExperiences() != null) {
            for (ResumeExperienceDTO experience : data.getExperiences()) {
                experience.setSourceRef(mergeRef(
                        experience.getSourceRef(),
                        resolveRef(experience.getSourceSectionId(), evidence(experience), indexedLines)));
            }
        }
        if (data.getProjects() != null) {
            for (ResumeProjectDTO project : data.getProjects()) {
                project.setSourceRef(mergeRef(
                        project.getSourceRef(),
                        resolveRef(project.getSourceSectionId(), evidence(project), indexedLines)));
            }
        }
        if (data.getAchievements() != null) {
            for (ResumeAchievementDTO achievement : data.getAchievements()) {
                achievement.setSourceRef(mergeRef(
                        achievement.getSourceRef(),
                        resolveRef(achievement.getSourceSectionId(), evidence(achievement), indexedLines)));
            }
        }
        data.setSummarySourceRef(mergeRef(
                data.getSummarySourceRef(),
                resolveRef(null, singleEvidence(data.getSummary()), indexedLines)));
    }

    private List<ResumeSourceRefDTO> buildRefs(
            List<String> values,
            List<ResumeSourceRefDTO> existingRefs,
            List<ResumeIndexedLineDTO> indexedLines) {
        List<String> safeValues = values == null ? List.of() : values;
        List<ResumeSourceRefDTO> safeExistingRefs = existingRefs == null ? List.of() : existingRefs;
        List<ResumeSourceRefDTO> refs = new ArrayList<>();
        Set<String> consumedOccurrenceIds = new java.util.LinkedHashSet<>();
        int size = Math.max(safeValues.size(), safeExistingRefs.size());
        for (int index = 0; index < size; index++) {
            ResumeSourceRefDTO existing = index < safeExistingRefs.size() ? safeExistingRefs.get(index) : null;
            ResumeSourceRefDTO resolved = index < safeValues.size()
                    && safeValues.get(index) != null && !safeValues.get(index).isBlank()
                    ? resolveDistinctValueRef(safeValues.get(index), indexedLines, consumedOccurrenceIds)
                    : null;
            ResumeSourceRefDTO merged = mergeRef(existing, resolved);
            if (merged != null) {
                refs.add(merged);
            }
        }
        return refs;
    }

    private ResumeSourceRefDTO mergeRef(ResumeSourceRefDTO existing, ResumeSourceRefDTO resolved) {
        // References are derived from the current candidate on every parse. Keeping an old ref
        // after a value no longer resolves is worse than returning no ref: it silently points to
        // a different fact. Never union a stale range with a newly resolved range either.
        return resolved;
    }

    private List<String> singleEvidence(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value);
    }

    private ResumeSourceRefDTO resolveDistinctValueRef(
            String value,
            List<ResumeIndexedLineDTO> indexedLines,
            Set<String> consumedOccurrenceIds) {
        List<ResumeIndexedLineDTO> candidates = indexedLines.stream()
                .filter(Objects::nonNull)
                .filter(line -> !Boolean.TRUE.equals(line.getIsNoise()))
                .sorted(Comparator.comparing(ResumeIndexedLineDTO::getLineId))
                .toList();
        for (ResumeIndexedLineDTO candidate : candidates) {
            if (!matchesEvidence(candidate, List.of(value))) {
                continue;
            }
            ResumeSourceRefDTO resolved = pointerValidator.sourceRef(
                    candidate.getLineId(), candidate.getLineId(), indexedLines);
            if (resolved == null) {
                continue;
            }
            List<String> ids = resolved.getSourceOccurrenceIds() == null
                    ? List.of() : resolved.getSourceOccurrenceIds().stream()
                    .filter(this::hasUsableOccurrenceId)
                    .map(String::strip)
                    .toList();
            if (!ids.isEmpty() && !java.util.Collections.disjoint(consumedOccurrenceIds, ids)) {
                continue;
            }
            consumedOccurrenceIds.addAll(ids);
            return resolved;
        }
        return null;
    }

    private ResumeSourceRefDTO resolveRef(String rawSectionId, List<String> evidence, List<ResumeIndexedLineDTO> indexedLines) {
        // Keep noise in the positional candidate list so a page artifact does not make an
        // otherwise physical-contiguous source span look disjoint. Noise is still excluded from
        // evidence matching and from the resulting reference text by the validator.
        List<ResumeIndexedLineDTO> candidates = indexedLines.stream()
                .filter(Objects::nonNull)
                .filter(line -> rawSectionId == null || rawSectionId.equals(line.getRawSectionId()))
                .sorted(Comparator.comparing(ResumeIndexedLineDTO::getLineId))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        List<ResumeIndexedLineDTO> matched = candidates.stream()
                .filter(line -> !Boolean.TRUE.equals(line.getIsNoise()))
                .filter(line -> matchesEvidence(line, evidence))
                .toList();
        // 证据无法匹配时不附引用：回退整个章节会把无关行的引用错位到条目上，
        // 宁可缺少 sourceRef 也不能指向错误行。
        if (matched.isEmpty()) {
            return null;
        }
        ResumeSourceRefDTO crossPageEntryRef = resolveCrossPageEntryRef(matched, candidates, indexedLines);
        if (crossPageEntryRef != null) {
            return crossPageEntryRef;
        }
        // A single ref cannot describe disjoint pages/sections. For ordinary same-page entries,
        // retain the established page-local selection and let the validator enforce the physical
        // range. A cross-page range is admitted only by resolveCrossPageEntryRef above.
        Map<String, List<ResumeIndexedLineDTO>> groups = new LinkedHashMap<>();
        for (ResumeIndexedLineDTO line : matched) {
            String key = String.valueOf(line.getRawSectionId()) + "|" + String.valueOf(line.getPage());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(line);
        }
        List<ResumeIndexedLineDTO> selected = groups.values().stream()
                .max(Comparator.comparingInt(List::size))
                .orElse(List.of());
        if (selected.isEmpty()) {
            return null;
        }
        int start = selected.stream().map(ResumeIndexedLineDTO::getLineId).min(Integer::compareTo).orElse(0);
        int end = selected.stream().map(ResumeIndexedLineDTO::getLineId).max(Integer::compareTo).orElse(0);
        return pointerValidator.sourceRef(start, end, indexedLines);
    }

    private ResumeSourceRefDTO resolveCrossPageEntryRef(
            List<ResumeIndexedLineDTO> matched,
            List<ResumeIndexedLineDTO> candidates,
            List<ResumeIndexedLineDTO> indexedLines) {
        Set<Integer> pages = matched.stream()
                .map(ResumeIndexedLineDTO::getPage)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (pages.size() < 2) {
            return null;
        }
        int firstMatched = matched.stream()
                .map(ResumeIndexedLineDTO::getLineId)
                .filter(Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(0);
        int lastMatched = matched.stream()
                .map(ResumeIndexedLineDTO::getLineId)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0);
        List<ResumeIndexedLineDTO> headers = candidates.stream()
                .filter(line -> line.getLineId() != null
                        && line.getLineId() >= firstMatched && line.getLineId() <= lastMatched)
                .filter(line -> line.getRole() == com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceBlockRole.ENTRY_HEADER)
                .toList();
        // The header must itself be matched and be the only entry boundary in the proposed
        // range. This prevents repeated body text from joining two separate entries merely
        // because they happen to occupy different pages.
        if (headers.size() != 1 || headers.get(0).getLineId() != firstMatched
                || !matched.contains(headers.get(0))) {
            return null;
        }
        return pointerValidator.sourceRef(firstMatched, lastMatched, indexedLines);
    }

    private boolean matchesEvidence(ResumeIndexedLineDTO line, List<String> evidence) {
        String lineText = line == null ? "" : line.getText() == null ? line.getNormalizedText() : line.getText();
        if (lineText == null || lineText.isBlank()) {
            return false;
        }
        for (String item : evidence == null ? List.<String>of() : evidence) {
            if (matchesWholeEvidence(lineText, item)) {
                return true;
            }
        }
        return false;
    }

    private List<String> evidence(ResumeExperienceDTO experience) {
        List<String> values = new ArrayList<>();
        add(values, experience.getOrganization());
        add(values, experience.getRole());
        add(values, experience.getDescription());
        add(values, experience.getBullets());
        add(values, experience.getEvidence());
        return values;
    }

    private List<String> evidence(ResumeProjectDTO project) {
        List<String> values = new ArrayList<>();
        add(values, project.getName());
        add(values, project.getDescription());
        add(values, project.getTimeRange());
        add(values, project.getTechStack());
        add(values, project.getResponsibilities());
        add(values, project.getEvidence());
        return values;
    }

    private boolean hasUsableOccurrenceId(String value) {
        return value != null && !value.isBlank()
                && !"null".equalsIgnoreCase(value.strip())
                && !"undefined".equalsIgnoreCase(value.strip());
    }

    private List<String> evidence(ResumeAchievementDTO achievement) {
        List<String> values = new ArrayList<>();
        add(values, achievement.getTitle());
        add(values, achievement.getLevel());
        add(values, achievement.getCompetition());
        add(values, achievement.getRanking());
        add(values, achievement.getTimeRange());
        add(values, achievement.getEvidence());
        return values;
    }

    private void add(List<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private void add(List<String> values, List<String> items) {
        for (String item : items == null ? List.<String>of() : items) {
            add(values, item);
        }
    }

    private boolean matchesWholeEvidence(String line, String evidence) {
        if (line == null || evidence == null) {
            return false;
        }
        String normalizedLine = line.strip().toLowerCase();
        String normalizedEvidence = evidence.strip().toLowerCase();
        if (normalizedLine.equals(normalizedEvidence)) {
            return true;
        }
        if (normalizedEvidence.length() < 2) {
            return false;
        }
        // ASCII evidence must occupy complete lexical tokens. Chinese evidence may be a
        // meaningful substring of a Chinese line, but never an empty/punctuation fragment.
        String compactLine = normalizedLine.replaceAll("[\\s\\p{Punct}，。；：、（）【】]", "");
        String compactEvidence = normalizedEvidence.replaceAll("[\\s\\p{Punct}，。；：、（）【】]", "");
        if (normalizedEvidence.matches(".*[a-z0-9].*")) {
            String escaped = java.util.regex.Pattern.quote(normalizedEvidence);
            if (normalizedLine.matches(".*(?<![a-z0-9])" + escaped + "(?![a-z0-9]).*")) {
                return true;
            }
            // Composite education/project evidence may contain a complete Chinese line plus
            // ASCII facts. Permit that line-level relation, but not an ASCII prefix such as
            // Java in JavaScript.
            return compactLine.length() >= 4 && compactEvidence.contains(compactLine)
                    && (!compactLine.matches("[a-z0-9]+") || compactLine.matches(".*\\d.*"));
        }
        return normalizedEvidence.length() >= 2 && compactLine.length() >= 4
                && compactLine.contains(compactEvidence);
    }
}
