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
import java.util.List;
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
        data.setEducationSourceRefs(buildRefs(data.getEducation(), indexedLines));
        if (data.getExperiences() != null) {
            for (ResumeExperienceDTO experience : data.getExperiences()) {
                experience.setSourceRef(resolveRef(experience.getSourceSectionId(), evidence(experience), indexedLines));
            }
        }
        if (data.getProjects() != null) {
            for (ResumeProjectDTO project : data.getProjects()) {
                project.setSourceRef(resolveRef(project.getSourceSectionId(), evidence(project), indexedLines));
            }
        }
        if (data.getAchievements() != null) {
            for (ResumeAchievementDTO achievement : data.getAchievements()) {
                achievement.setSourceRef(resolveRef(achievement.getSourceSectionId(), evidence(achievement), indexedLines));
            }
        }
        data.setSummarySourceRef(resolveRef(null, singleEvidence(data.getSummary()), indexedLines));
    }

    private List<ResumeSourceRefDTO> buildRefs(List<String> values, List<ResumeIndexedLineDTO> indexedLines) {
        List<ResumeSourceRefDTO> refs = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            ResumeSourceRefDTO ref = resolveRef(null, List.of(value), indexedLines);
            if (ref != null) {
                refs.add(ref);
            }
        }
        return refs;
    }

    private List<String> singleEvidence(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value);
    }

    private ResumeSourceRefDTO resolveRef(String rawSectionId, List<String> evidence, List<ResumeIndexedLineDTO> indexedLines) {
        List<ResumeIndexedLineDTO> candidates = indexedLines.stream()
                .filter(line -> rawSectionId == null || rawSectionId.equals(line.getRawSectionId()))
                .filter(line -> !Boolean.TRUE.equals(line.getIsNoise()))
                .sorted(Comparator.comparing(ResumeIndexedLineDTO::getLineId))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        List<ResumeIndexedLineDTO> matched = candidates.stream()
                .filter(line -> matchesEvidence(line, evidence))
                .toList();
        if (matched.isEmpty() && rawSectionId != null) {
            matched = candidates;
        }
        if (matched.isEmpty()) {
            return null;
        }
        int start = matched.stream().map(ResumeIndexedLineDTO::getLineId).min(Integer::compareTo).orElse(0);
        int end = matched.stream().map(ResumeIndexedLineDTO::getLineId).max(Integer::compareTo).orElse(0);
        return pointerValidator.sourceRef(start, end, indexedLines);
    }

    private boolean matchesEvidence(ResumeIndexedLineDTO line, List<String> evidence) {
        String lineText = compact(line.getNormalizedText());
        if (lineText.isBlank()) {
            return false;
        }
        for (String item : evidence == null ? List.<String>of() : evidence) {
            String evidenceText = compact(item);
            if (evidenceText.length() >= 4 && (lineText.contains(evidenceText) || evidenceText.contains(lineText))) {
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

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("[\\s,，、；;:：.。/\\\\|()（）\\[\\]【】]", "").toLowerCase();
    }
}
