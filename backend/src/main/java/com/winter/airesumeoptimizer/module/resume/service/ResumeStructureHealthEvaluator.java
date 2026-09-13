package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceBlockRole;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructureHealthEvaluation;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredDataDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAchievementDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeExperienceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillEvidenceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillSetDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Deep deterministic health module for structure candidates.
 *
 * <p>It measures source preservation and structural consistency without assuming a preferred
 * section order. It never promotes a candidate; callers must use the hard-invariant result and
 * an explicit improvement threshold as the apply gate.</p>
 */
@Service
public class ResumeStructureHealthEvaluator {

    private static final Pattern TOKEN = Pattern.compile("[\\u4e00-\\u9fa5]+|[A-Za-z0-9+#.\\-]+");
    private static final Pattern SOURCE_CHECK_TOKEN = Pattern.compile("[\\u4e00-\\u9fa5]+|[A-Za-z0-9]+");
    private static final Pattern EMAIL_VALUE = Pattern.compile(
            "[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_SIGNAL = Pattern.compile("(?i).*(?:19|20)\\d{2}.*(?:至今|present|[-~—至到]).*");
    private static final Set<String> STRUCTURAL_HEADINGS = Set.of(
            "个人信息", "基本信息", "联系方式", "教育经历", "教育背景", "专业技能", "技能", "技术能力",
            "工作经历", "工作经验", "职业经历", "实习经历", "项目经历", "项目经验", "校园经历", "在校经历",
            "获奖经历", "荣誉奖项", "证书", "自我评价", "个人总结", "profile", "education", "skills",
            "experience", "projects", "summary", "others", "其他");

    public ResumeStructureHealthEvaluation evaluate(ResumeStructuredContentDTO content) {
        return evaluate(content, List.of(), List.of(), "BASELINE");
    }

    public ResumeStructureHealthEvaluation evaluate(
            ResumeStructuredContentDTO content,
            List<ResumeBlockDTO> sourceBlocks,
            List<String> pendingTexts) {
        return evaluate(content, sourceBlocks, pendingTexts, "UNKNOWN");
    }

    public ResumeStructureHealthEvaluation evaluate(
            ResumeStructuredContentDTO content,
            List<ResumeBlockDTO> sourceBlocks,
            List<String> pendingTexts,
            String candidateType) {
        ResumeStructuredContentDTO safeContent = content == null
                ? ResumeStructuredContentDTO.builder().build()
                : content;
        List<SourceFact> source = sourceFacts(safeContent, sourceBlocks);
        List<String> pending = normalizeValues(pendingTexts);
        List<String> displaySlots = displaySlots(safeContent);
        List<SourceFact> contentFacts = source.stream()
                .filter(fact -> fact.role() != ResumeSourceBlockRole.SECTION_HEADING && !isHeading(fact.text()))
                .toList();
        int meaningful = contentFacts.size();
        int represented = 0;
        int orphan = 0;
        int general = 0;
        int fragmented = 0;
        // Consume one semantic claim per source occurrence. A text-only boolean check would
        // incorrectly treat two identical source rows as covered by one canonical value.
        List<String> remainingDisplayClaims = new ArrayList<>(displaySlots);
        List<String> remainingPendingClaims = new ArrayList<>(pending);
        List<String> consumedClaims = new ArrayList<>();
        SourceFact previousFact = null;
        for (SourceFact fact : contentFacts) {
            if (fact.text().length() <= 2 || fact.text().matches("^[\\p{Punct}\\s]+$")) {
                fragmented++;
            }
            int displayIndex = matchingClaimIndex(remainingDisplayClaims, fact.text());
            if (displayIndex >= 0) {
                consumedClaims.add(remainingDisplayClaims.remove(displayIndex));
                represented++;
            } else if (representedByProjectedSegments(remainingDisplayClaims, fact.text())) {
                consumedClaims.addAll(removeProjectedSegmentClaims(remainingDisplayClaims, fact.text()));
                represented++;
            } else {
                int pendingIndex = matchingClaimIndex(remainingPendingClaims, fact.text());
                if (pendingIndex >= 0) {
                    consumedClaims.add(remainingPendingClaims.remove(pendingIndex));
                    represented++;
                } else if (isContinuationOfConsumedClaim(previousFact, fact, consumedClaims)) {
                    // A wrapped visual row can split one deterministic canonical value across
                    // adjacent occurrences. Keep occurrence-level accounting without requiring
                    // the joined projection to be consumed twice.
                    represented++;
                } else {
                    orphan++;
                }
            }
            if (fact.general()) {
                general++;
            }
            previousFact = fact;
        }
        int duplicate = duplicateSourceCount(safeContent.getStructuredData(), source);
        int boundary = entryBoundaryViolations(safeContent, source);
        int collapse = suspiciousEntryCollapse(safeContent, source);
        int consistency = sectionConsistency(safeContent, source);
        int coverage = meaningful == 0 ? 100 : percentage(represented, meaningful);
        int generalRatio = meaningful == 0 ? 0 : percentage(general, meaningful);
        int orphanPenalty = Math.min(40, orphan * 8);
        int duplicatePenalty = Math.min(35, duplicate * 8);
        int boundaryPenalty = Math.min(35, boundary * 20);
        int fragmentPenalty = Math.min(20, fragmented * 3);
        int collapsePenalty = Math.min(20, collapse * 10);
        int score = clamp(Math.round(
                coverage * 0.35f
                        + (100 - Math.min(100, duplicatePenalty)) * 0.15f
                        + (100 - Math.min(100, boundaryPenalty)) * 0.15f
                        + (100 - Math.min(100, orphanPenalty)) * 0.10f
                        + (100 - Math.min(100, generalRatio)) * 0.10f
                        + consistency * 0.10f
                        + (100 - Math.min(100, fragmentPenalty + collapsePenalty)) * 0.05f), 0, 100);

        List<String> violations = new ArrayList<>();
        if (orphan > 0) {
            violations.add("NO_LOSS");
        }
        int hallucinated = Math.toIntExact(canonicalClaims(safeContent).stream()
                .filter(claim -> isMeaningfulValue(claim.value()))
                .filter(claim -> !isAllowedGeneratedValue(claim, source))
                .count());
        if (hallucinated > 0) {
            violations.add("NO_HALLUCINATION");
        }
        if (duplicate > 0) {
            violations.add("NO_DUPLICATION");
        }
        if (boundary > 0) {
            violations.add("ENTRY_BOUNDARY");
        }
        return new ResumeStructureHealthEvaluation(
                score,
                coverage,
                meaningful,
                represented,
                orphan,
                duplicate,
                boundary,
                general,
                generalRatio,
                fragmented,
                collapse,
                consistency,
                entryCount(safeContent),
                violations.isEmpty(),
                violations,
                candidateType);
    }

    private List<SourceFact> sourceFacts(ResumeStructuredContentDTO content, List<ResumeBlockDTO> sourceBlocks) {
        List<SourceFact> result = new ArrayList<>();
        Set<String> usedOccurrenceIds = new LinkedHashSet<>();
        int occurrence = 0;
        if (sourceBlocks != null && !sourceBlocks.isEmpty()) {
            for (ResumeBlockDTO block : sourceBlocks) {
                if (block == null || !isMeaningfulValue(block.getText())) {
                    continue;
                }
                occurrence = appendSourceFact(
                        result,
                        block.getText(),
                        block.getRole() == null ? ResumeSourceBlockRole.UNKNOWN : block.getRole(),
                        isGeneral(block.getSourceSection()),
                        block.getSourceSection(),
                        block.getSourceBlockIds(),
                        block.getId(),
                        block.getSourceOccurrenceIds(),
                        occurrence,
                        usedOccurrenceIds);
            }
            return result;
        }
        for (ResumeRawSectionDTO section : safeList(content.getRawSections())) {
            if (section == null) {
                continue;
            }
            boolean general = "UNKNOWN".equals(section.getNormalizedSection())
                    || "OTHERS".equals(section.getNormalizedSection());
            for (ResumeRawSectionBlockDTO block : safeList(section.getBlocks())) {
                if (block != null && isMeaningfulValue(block.getText())) {
                    occurrence = appendSourceFact(
                            result,
                            block.getText(),
                            block.getRole() == null ? ResumeSourceBlockRole.UNKNOWN : block.getRole(),
                            general,
                            section.getNormalizedSection(),
                            block.getSourceBlockIds(),
                            block.getId(),
                            block.getSourceOccurrenceIds(),
                            occurrence,
                            usedOccurrenceIds);
                }
            }
        }
        if (result.isEmpty() && isMeaningfulValue(content.getRawText())) {
            for (String line : content.getRawText().split("\\R")) {
                if (isMeaningfulValue(line)) {
                    String blockId = "source-block-" + occurrence;
                    String occurrenceId = "source-occurrence-" + occurrence;
                    result.add(new SourceFact(line.strip(), ResumeSourceBlockRole.UNKNOWN, false, "GENERAL",
                            occurrenceId, List.of(blockId), List.of(occurrenceId), List.of(occurrenceId), occurrence));
                    occurrence++;
                }
            }
        }
        return result;
    }

    private int appendSourceFact(
            List<SourceFact> result,
            String text,
            ResumeSourceBlockRole role,
            boolean general,
            String section,
            List<String> sourceBlockIds,
            String blockId,
            List<String> requestedOccurrenceIds,
            int occurrence,
            Set<String> usedOccurrenceIds) {
        List<String> rawIds = rawOccurrenceIds(requestedOccurrenceIds);
        SourceFact projection = result.stream()
                .filter(fact -> rawIds.stream().anyMatch(fact.occurrenceIds()::contains))
                .filter(fact -> sameSourceProjection(sourceBlockIds, blockId, fact))
                .findFirst()
                .orElse(null);
        if (projection != null) {
            SourceFact merged = new SourceFact(
                    projection.text() + text.strip(),
                    projection.role() == null ? role : projection.role(),
                    projection.general() || general,
                    projection.section() == null ? section : projection.section(),
                    projection.id(),
                    unionSourceIds(projection.ids(), sourceBlockIds, blockId),
                    unionSourceIds(projection.occurrenceIds(), rawIds),
                    unionSourceIds(projection.rawOccurrenceIds(), rawIds),
                    projection.order());
            result.set(result.indexOf(projection), merged);
            return occurrence + 1;
        }
        List<String> ids = sourceIds(sourceBlockIds, blockId, "source-block-" + occurrence);
        List<String> occurrenceIds = sourceOccurrenceIds(
                requestedOccurrenceIds, "source-occurrence-" + occurrence, usedOccurrenceIds);
        result.add(new SourceFact(
                text.strip(),
                role,
                general,
                section,
                occurrenceIds.get(0),
                ids,
                occurrenceIds,
                rawIds,
                occurrence));
        return occurrence + 1;
    }

    private boolean sameSourceProjection(
            List<String> currentSourceBlockIds, String currentBlockId, SourceFact previous) {
        List<String> current = unionSourceIds(currentSourceBlockIds,
                isUsableOccurrenceId(currentBlockId) ? List.of(currentBlockId) : List.of());
        if (current.isEmpty() || previous == null || previous.ids().isEmpty()) {
            return false;
        }
        for (String currentId : current) {
            for (String previousId : previous.ids()) {
                if (currentId.equals(previousId)
                        || fragmentBase(currentId).equals(fragmentBase(previousId))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String fragmentBase(String value) {
        return value == null ? "" : value.replaceFirst("#fragment-\\d+$", "");
    }

    private List<String> unionSourceIds(List<String> first, List<String> second) {
        return unionSourceIds(first, second, null);
    }

    private List<String> unionSourceIds(List<String> first, List<String> second, String third) {
        List<String> result = new ArrayList<>();
        for (String value : first == null ? List.<String>of() : first) {
            if (isUsableOccurrenceId(value) && !result.contains(value.strip())) {
                result.add(value.strip());
            }
        }
        for (String value : second == null ? List.<String>of() : second) {
            if (isUsableOccurrenceId(value) && !result.contains(value.strip())) {
                result.add(value.strip());
            }
        }
        if (isUsableOccurrenceId(third) && !result.contains(third.strip())) {
            result.add(third.strip());
        }
        return List.copyOf(result);
    }

    private List<String> sourceIds(List<String> ids, String fallback, String occurrenceId) {
        List<String> result = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                if (isUsableOccurrenceId(id) && !result.contains(id.strip())) {
                    result.add(id.strip());
                }
            }
        }
        if (result.isEmpty() && isMeaningfulValue(fallback)) {
            result.add(fallback);
        }
        if (result.isEmpty()) {
            result.add(occurrenceId);
        }
        return List.copyOf(result);
    }

    private List<String> rawOccurrenceIds(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(this::isUsableOccurrenceId)
                .map(String::strip)
                .toList();
    }

    private List<String> sourceOccurrenceIds(
            List<String> ids, String fallback, Set<String> usedOccurrenceIds) {
        List<String> result = new ArrayList<>();
        if (ids != null) {
            for (String id : ids) {
                if (!isUsableOccurrenceId(id)) {
                    continue;
                }
                String base = id.strip();
                String candidate = base;
                int suffix = 2;
                while (usedOccurrenceIds.contains(candidate) || result.contains(candidate)) {
                    candidate = base + "~" + suffix++;
                }
                result.add(candidate);
                usedOccurrenceIds.add(candidate);
            }
        }
        if (result.isEmpty() && isUsableOccurrenceId(fallback)) {
            String base = fallback.strip();
            String candidate = base;
            int suffix = 2;
            while (usedOccurrenceIds.contains(candidate)) {
                candidate = base + "~" + suffix++;
            }
            result.add(candidate);
            usedOccurrenceIds.add(candidate);
        }
        return List.copyOf(result);
    }

    private List<Claim> canonicalClaims(ResumeStructuredContentDTO content) {
        List<Claim> claims = new ArrayList<>();
        ResumeStructuredDataDTO data = content.getStructuredData();
        addClaim(claims, content.getName(), Set.of());
        addClaim(claims, content.getPhone(), Set.of());
        addClaim(claims, content.getEmail(), Set.of());
        addClaim(claims, content.getJobIntention(), Set.of());
        addClaim(claims, content.getHighestEducation(), Set.of("EDUCATION"));
        // When semantic data exists, summary is its compatibility projection. The semantic claim
        // below carries the summary source reference; adding the unreferenced top-level copy would
        // incorrectly classify a wrapped summary as hallucinated.
        if (data == null) {
            addClaim(claims, content.getSummary(), Set.of("SUMMARY"));
        }
        if (content.getBasicInfo() != null) {
            content.getBasicInfo().forEach((key, value) -> {
                if (!"resumeType".equalsIgnoreCase(key)) addClaim(claims, value, Set.of());
            });
        }
        // The top-level collections are a compatibility projection populated from structuredData.
        // Counting both projections makes a joined legacy experience look like an unsupported
        // cross-line claim and can produce a false NO_HALLUCINATION violation. Use the legacy
        // collections only when no semantic projection exists.
        if (data == null) {
            addClaims(claims, content.getEducation(), Set.of("EDUCATION"));
            addClaims(claims, content.getSkills(), Set.of("SKILLS"));
            addClaims(claims, content.getProjects(), Set.of("PROJECTS"));
            addClaims(claims, content.getWorkExperiences(), Set.of("WORK_EXPERIENCES"));
            addClaims(claims, content.getInternships(), Set.of("INTERNSHIPS"));
            addClaims(claims, content.getCampusExperiences(), Set.of("CAMPUS_EXPERIENCES"));
            addClaims(claims, content.getAwards(), Set.of("AWARDS"));
            addClaims(claims, content.getCertificates(), Set.of("CERTIFICATES"));
            addClaims(claims, content.getOthers(), Set.of("OTHERS", "GENERAL"));
        }
        if (data != null) {
            addClaims(claims, data.getEducation(), Set.of("EDUCATION"));
            addClaim(claims, data.getSummary(), Set.of("SUMMARY"), data.getSummarySourceRef());
            addClaims(claims, data.getOthers(), Set.of("OTHERS", "GENERAL"));
            for (ResumeExperienceDTO item : safeList(data.getExperiences())) {
                if (item == null) continue;
                Set<String> sections = Set.of(item.getType() == null ? "WORK_EXPERIENCES" : item.getType());
                addClaim(claims, item.getOrganization(), sections, item.getSourceRef());
                addClaim(claims, item.getRole(), sections, item.getSourceRef());
                addClaim(claims, item.getStartDate(), sections, item.getSourceRef());
                addClaim(claims, item.getEndDate(), sections, item.getSourceRef());
                addClaim(claims, item.getDescription(), sections, item.getSourceRef());
                addClaims(claims, item.getBullets(), sections, item.getSourceRef());
                addClaims(claims, item.getEvidence(), sections, item.getSourceRef());
            }
            for (ResumeProjectDTO item : safeList(data.getProjects())) {
                if (item == null) continue;
                String projectSection = item.getSourceType() == null
                        || "INDEPENDENT".equalsIgnoreCase(item.getSourceType())
                        ? "PROJECTS" : item.getSourceType();
                Set<String> sections = Set.of(projectSection);
                addClaim(claims, item.getName(), sections, item.getSourceRef());
                addClaim(claims, item.getDescription(), sections, item.getSourceRef());
                addClaim(claims, item.getRole(), sections, item.getSourceRef());
                addClaim(claims, item.getTimeRange(), sections, item.getSourceRef());
                addClaim(claims, item.getEnvironment(), sections, item.getSourceRef());
                addClaim(claims, item.getStartDate(), sections, item.getSourceRef());
                addClaim(claims, item.getEndDate(), sections, item.getSourceRef());
                addClaims(claims, item.getTechStack(), sections, item.getSourceRef());
                addClaims(claims, item.getResponsibilities(), sections, item.getSourceRef());
                addClaims(claims, item.getEvidence(), sections, item.getSourceRef());
            }
            ResumeSkillSetDTO skills = data.getSkills();
            if (skills != null) {
                // The rule assembler can derive a skill keyword from a project/work line when
                // the source has no dedicated SKILLS section. Attribute that compatibility
                // projection to the actual source section instead of calling it hallucinated.
                for (String keyword : safeList(skills.getKeywords())) {
                    addClaim(claims, keyword, skillSourceSections(content, keyword));
                }
                addClaims(claims, skills.getDescriptions(), Set.of("SKILLS"));
                for (ResumeSkillEvidenceDTO evidence : safeList(skills.getEvidence())) {
                    if (evidence == null) continue;
                    addClaim(claims, evidence.getSkill(), Set.of("SKILL_EVIDENCE"), evidence.getSourceRef());
                    addClaim(claims, evidence.getSourceText(), Set.of("SKILL_EVIDENCE"), evidence.getSourceRef());
                    addClaim(claims, evidence.getDescription(), Set.of("SKILL_EVIDENCE"), evidence.getSourceRef());
                    addClaims(claims, evidence.getKeywords(), Set.of("SKILL_EVIDENCE"), evidence.getSourceRef());
                }
            }
            for (ResumeAchievementDTO item : safeList(data.getAchievements())) {
                if (item == null) continue;
                addClaim(claims, item.getTitle(), Set.of("AWARDS", "CAMPUS_EXPERIENCES"), item.getSourceRef());
                addClaim(claims, item.getLevel(), Set.of("AWARDS", "CAMPUS_EXPERIENCES"), item.getSourceRef());
                addClaim(claims, item.getCompetition(), Set.of("AWARDS", "CAMPUS_EXPERIENCES"), item.getSourceRef());
                addClaim(claims, item.getRanking(), Set.of("AWARDS", "CAMPUS_EXPERIENCES"), item.getSourceRef());
                addClaim(claims, item.getTimeRange(), Set.of("AWARDS", "CAMPUS_EXPERIENCES"), item.getSourceRef());
                addClaim(claims, item.getDate(), Set.of("AWARDS", "CAMPUS_EXPERIENCES"), item.getSourceRef());
                addClaims(claims, item.getEvidence(), Set.of("AWARDS", "CAMPUS_EXPERIENCES"), item.getSourceRef());
            }
            addClaims(claims, data.getCertificates(), Set.of("CERTIFICATES"));
        }
        return claims;
    }

    private Set<String> skillSourceSections(
            ResumeStructuredContentDTO content, String keyword) {
        Set<String> sections = new LinkedHashSet<>();
        for (ResumeRawSectionDTO section : safeList(content.getRawSections())) {
            if (section == null) {
                continue;
            }
            for (ResumeRawSectionBlockDTO block : safeList(section.getBlocks())) {
                if (block != null && containsFact(List.of(block.getText()), keyword)) {
                    sections.add(section.getNormalizedSection());
                }
            }
        }
        return sections.isEmpty() ? Set.of("SKILLS") : Set.copyOf(sections);
    }

    private void addClaims(List<Claim> target, List<String> values, Set<String> sections) {
        addClaims(target, values, sections, null);
    }

    private void addClaims(
            List<Claim> target, List<String> values, Set<String> sections,
            com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO sourceRef) {
        for (String value : safeList(values)) addClaim(target, value, sections, sourceRef);
    }

    private void addClaim(List<Claim> target, String value, Set<String> sections) {
        addClaim(target, value, sections, null);
    }

    private void addClaim(
            List<Claim> target, String value, Set<String> sections,
            com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO sourceRef) {
        if (isMeaningfulValue(value)) target.add(new Claim(value.strip(), sections, sourceRef));
    }

    private List<String> displaySlots(ResumeStructuredContentDTO content) {
        List<String> slots = new ArrayList<>();
        add(slots, content.getName(), content.getPhone(), content.getEmail(), content.getJobIntention(),
                content.getHighestEducation(), content.getSummary());
        if (content.getBasicInfo() != null) {
            addAll(slots, content.getBasicInfo().entrySet().stream()
                    .filter(entry -> !"resumeType".equals(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList());
        }
        addAll(slots, content.getEducation());
        addAll(slots, content.getSkills());
        addAll(slots, content.getProjects());
        addAll(slots, content.getWorkExperiences());
        addAll(slots, content.getInternships());
        addAll(slots, content.getCampusExperiences());
        addAll(slots, content.getAwards());
        addAll(slots, content.getCertificates());
        addAll(slots, content.getOthers());
        ResumeStructuredDataDTO data = content.getStructuredData();
        if (data != null) {
            addAll(slots, data.getEducation());
            add(slots, data.getSummary());
            addAll(slots, data.getOthers());
            for (ResumeExperienceDTO item : safeList(data.getExperiences())) {
                if (item == null) continue;
                add(slots, item.getOrganization(), item.getRole(), item.getStartDate(), item.getEndDate(), item.getDescription());
                addAll(slots, item.getBullets());
                addAll(slots, item.getEvidence());
                add(slots, item.getSourceRef() == null ? null : item.getSourceRef().getText());
            }
            for (ResumeProjectDTO item : safeList(data.getProjects())) {
                if (item == null) continue;
                add(slots, item.getName(), item.getDescription(), item.getRole(), item.getTimeRange(), item.getEnvironment(),
                        item.getStartDate(), item.getEndDate());
                addAll(slots, item.getTechStack());
                addAll(slots, item.getResponsibilities());
                addAll(slots, item.getEvidence());
                add(slots, item.getSourceRef() == null ? null : item.getSourceRef().getText());
            }
            ResumeSkillSetDTO skills = data.getSkills();
            if (skills != null) {
                addAll(slots, skills.getKeywords());
                addAll(slots, skills.getDescriptions());
                for (ResumeSkillEvidenceDTO evidence : safeList(skills.getEvidence())) {
                    if (evidence != null) {
                        add(slots, evidence.getDescription(), evidence.getSourceText(),
                                evidence.getSourceRef() == null ? null : evidence.getSourceRef().getText());
                    }
                }
            }
            for (ResumeAchievementDTO item : safeList(data.getAchievements())) {
                if (item == null) continue;
                add(slots, item.getTitle(), item.getLevel(), item.getCompetition(), item.getRanking(), item.getTimeRange(), item.getDate());
                addAll(slots, item.getEvidence());
                add(slots, item.getSourceRef() == null ? null : item.getSourceRef().getText());
            }
            addAll(slots, data.getCertificates());
        }
        return normalizeValues(slots);
    }

    /**
     * Count repeated ownership of source evidence, not the intentional legacy/structured
     * projections of one fact. A source line appearing in one experience's description and
     * bullets is therefore harmless; appearing in two sibling entry candidates is not.
     */
    private boolean representedByProjectedSegments(List<String> slots, String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return false;
        }
        List<String> segments = java.util.Arrays.stream(
                        sourceText.split("[|丨·•▪◦]+"))
                .map(String::strip)
                .filter(this::isMeaningfulValue)
                .toList();
        return segments.size() >= 2 && segments.stream().allMatch(segment ->
                containsFact(slots, segment)
                        || slots.stream().anyMatch(slot -> sourceBackedDerivedValue(slot, segment)));
    }

    private boolean isContinuationOfConsumedClaim(
            SourceFact previousFact,
            SourceFact currentFact,
            List<String> consumedClaims) {
        if (previousFact == null || currentFact == null
                || previousFact.order() + 1 != currentFact.order()
                || !java.util.Objects.equals(previousFact.section(), currentFact.section())) {
            return false;
        }
        String joinedSource = normalize(previousFact.text() + currentFact.text());
        if (joinedSource.isBlank()) {
            return false;
        }
        return consumedClaims.stream()
                .map(this::normalize)
                .anyMatch(claim -> claim.contains(joinedSource));
    }

    private List<String> removeProjectedSegmentClaims(List<String> claims, String sourceText) {
        List<String> removed = new ArrayList<>();
        List<String> segments = java.util.Arrays.stream(
                        sourceText == null ? new String[0] : sourceText.split("[|丨·•▪◦]+"))
                .map(String::strip)
                .filter(this::isMeaningfulValue)
                .toList();
        for (String segment : segments) {
            int index = -1;
            for (int claimIndex = 0; claimIndex < claims.size(); claimIndex++) {
                String claim = claims.get(claimIndex);
                if (containsFact(List.of(claim), segment)
                        || sourceBackedDerivedValue(claim, segment)) {
                    index = claimIndex;
                    break;
                }
            }
            if (index >= 0) {
                removed.add(claims.remove(index));
            }
        }
        return removed;
    }

    private int matchingClaimIndex(List<String> claims, String sourceText) {
        for (int index = 0; index < claims.size(); index++) {
            if (representedBy(List.of(claims.get(index)), sourceText)) {
                return index;
            }
        }
        return -1;
    }

    private boolean representedBy(List<String> slots, String sourceText) {
        if (containsFact(slots, sourceText)) {
            return true;
        }
        if (representedByProjectedSegments(slots, sourceText)) {
            return true;
        }
        if (sourceText == null) {
            return false;
        }
        int separator = Math.max(sourceText.indexOf('：'), sourceText.indexOf(':'));
        if (separator >= 0 && separator + 1 < sourceText.length()) {
            String value = sourceText.substring(separator + 1).strip();
            return value.length() >= 2 && containsFact(slots, value);
        }
        return false;
    }

    private boolean contiguousSourceSpan(List<SourceFact> source) {
        if (source == null || source.size() < 2) {
            return false;
        }
        for (int index = 1; index < source.size(); index++) {
            if (source.get(index).order() != source.get(index - 1).order() + 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * A structured field may use a contiguous subset of an entry reference. For example, a
     * project reference commonly covers its header plus three description rows, while the
     * description field itself contains only those three rows. The subset must still equal a
     * complete contiguous source span after deterministic normalization; arbitrary token
     * recombination across unrelated rows is not accepted.
     */
    private boolean matchesContiguousSourceSubspan(String value, List<SourceFact> referenced) {
        String expected = normalize(value);
        if (expected.isBlank() || referenced == null || referenced.size() < 2) {
            return false;
        }
        for (int start = 0; start < referenced.size(); start++) {
            StringBuilder span = new StringBuilder();
            for (int end = start; end < referenced.size(); end++) {
                if (span.length() > 0) {
                    span.append('\n');
                }
                span.append(referenced.get(end).text());
                if (expected.equals(normalize(span.toString()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private int duplicateSourceCount(ResumeStructuredDataDTO data, List<SourceFact> source) {
        int duplicates = duplicateSourceIds(source);
        if (data == null) {
            return duplicates;
        }
        return duplicates + duplicateEntryOwnership(data, source);
    }

    private int duplicateSourceIds(List<SourceFact> source) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (SourceFact fact : source) {
            List<String> rawIds = fact.rawOccurrenceIds().isEmpty()
                    ? fact.occurrenceIds() : fact.rawOccurrenceIds();
            for (String id : rawIds) {
                counts.merge(id, 1, Integer::sum);
            }
        }
        return counts.values().stream().filter(count -> count > 1)
                .mapToInt(count -> count - 1).sum();
    }

    /** Assign each entry's evidence to source occurrences before counting ownership. */
    private int duplicateEntryOwnership(ResumeStructuredDataDTO data, List<SourceFact> source) {
        Map<String, Set<String>> ownersBySourceId = new LinkedHashMap<>();
        int entryIndex = 0;
        for (ResumeExperienceDTO experience : safeList(data.getExperiences())) {
            if (experience != null) {
                assignEntryOwnership(ownersBySourceId, source, experience.getSourceRef(), experience.getEvidence(), "experience:" + entryIndex++);
            }
        }
        for (ResumeProjectDTO project : safeList(data.getProjects())) {
            if (project != null) {
                assignEntryOwnership(ownersBySourceId, source, project.getSourceRef(), project.getEvidence(), "project:" + entryIndex++);
            }
        }
        for (ResumeAchievementDTO achievement : safeList(data.getAchievements())) {
            if (achievement != null) {
                // An achievement derived from a campus/work entry is a semantic projection of
                // that parent, not a second sibling owner of the same source occurrence.
                String owner = achievement.getParentExperienceIndex() == null
                        ? "achievement:" + entryIndex++
                        : "experience:" + achievement.getParentExperienceIndex();
                assignEntryOwnership(ownersBySourceId, source, achievement.getSourceRef(), achievement.getEvidence(), owner);
            }
        }
        return ownersBySourceId.values().stream()
                .mapToInt(owners -> Math.max(0, owners.size() - 1)).sum();
    }

    private void assignEntryOwnership(
            Map<String, Set<String>> ownersBySourceId,
            List<SourceFact> source,
            com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO reference,
            List<String> evidence,
            String owner) {
        Set<String> assigned = new LinkedHashSet<>();
        if (reference != null && reference.getSourceOccurrenceIds() != null) {
            assigned.addAll(reference.getSourceOccurrenceIds().stream()
                    .filter(this::isUsableOccurrenceId)
                    .map(String::strip)
                    .toList());
        }
        for (String value : safeList(evidence)) {
            if (!isMeaningfulValue(value)) continue;
            SourceFact match = source.stream()
                    .filter(fact -> containsFact(List.of(fact.text()), value))
                    .min((left, right) -> Integer.compare(
                            sourceOwnerCount(ownersBySourceId, left), sourceOwnerCount(ownersBySourceId, right)))
                    .orElse(null);
            if (match != null) assigned.addAll(match.occurrenceIds());
        }
        for (String id : assigned) {
            ownersBySourceId.computeIfAbsent(id, ignored -> new LinkedHashSet<>()).add(owner);
        }
    }

    private int sourceOwnerCount(Map<String, Set<String>> ownersBySourceId, SourceFact fact) {
        return fact.occurrenceIds().stream()
                .mapToInt(id -> ownersBySourceId.getOrDefault(id, Set.of()).size())
                .sum();
    }

    private int entryBoundaryViolations(ResumeStructuredContentDTO content, List<SourceFact> source) {
        ResumeStructuredDataDTO data = content.getStructuredData();
        if (data == null) return 0;
        // Boundary errors are source-occurrence ownership collisions, not repeated normalized
        // phrases. Distinct rows containing the same phrase can legitimately occur in sections.
        Map<String, Set<String>> ownersBySourceId = new LinkedHashMap<>();
        int entryIndex = 0;
        for (ResumeExperienceDTO experience : safeList(data.getExperiences())) {
            if (experience != null) {
                assignEntryOwnership(ownersBySourceId, source, experience.getSourceRef(), experience.getEvidence(), "experience:" + entryIndex++);
            }
        }
        for (ResumeProjectDTO project : safeList(data.getProjects())) {
            if (project != null) {
                assignEntryOwnership(ownersBySourceId, source, project.getSourceRef(), project.getEvidence(), "project:" + entryIndex++);
            }
        }
        for (ResumeAchievementDTO achievement : safeList(data.getAchievements())) {
            if (achievement != null) {
                String owner = achievement.getParentExperienceIndex() == null
                        ? "achievement:" + entryIndex++
                        : "experience:" + achievement.getParentExperienceIndex();
                assignEntryOwnership(ownersBySourceId, source, achievement.getSourceRef(), achievement.getEvidence(), owner);
            }
        }
        return ownersBySourceId.values().stream()
                .mapToInt(owners -> Math.max(0, owners.size() - 1)).sum();
    }

    private int suspiciousEntryCollapse(ResumeStructuredContentDTO content, List<SourceFact> source) {
        ResumeStructuredDataDTO data = content.getStructuredData();
        if (data == null) return 0;
        int datedHeaders = (int) source.stream().filter(fact -> DATE_SIGNAL.matcher(fact.text()).matches()).count();
        int entries = entryCount(data);
        return datedHeaders >= 2 && entries <= 1 ? 1 : 0;
    }

    private int sectionConsistency(ResumeStructuredContentDTO content, List<SourceFact> source) {
        Set<String> sourceSections = new LinkedHashSet<>();
        for (ResumeRawSectionDTO section : safeList(content.getRawSections())) {
            if (section != null && section.getNormalizedSection() != null
                    && !"UNKNOWN".equals(section.getNormalizedSection())) {
                sourceSections.add(section.getNormalizedSection());
            }
        }
        if (sourceSections.isEmpty()) return 100;
        ResumeStructuredDataDTO data = content.getStructuredData();
        Set<String> represented = new LinkedHashSet<>();
        if (hasBasicInfo(content)) represented.add("BASIC_INFO");
        if (data != null) {
            if (!safeList(data.getEducation()).isEmpty()) represented.add("EDUCATION");
            if (data.getSkills() != null
                    && (!safeList(data.getSkills().getKeywords()).isEmpty()
                    || !safeList(data.getSkills().getDescriptions()).isEmpty())) {
                represented.add("SKILLS");
            }
            if (!safeList(data.getProjects()).isEmpty()) represented.add("PROJECTS");
            if (safeList(data.getExperiences()).stream().anyMatch(item -> item != null && "WORK".equals(item.getType()))) represented.add("WORK");
            if (safeList(data.getExperiences()).stream().anyMatch(item -> item != null && "INTERNSHIP".equals(item.getType()))) represented.add("INTERNSHIP");
            if (safeList(data.getExperiences()).stream().anyMatch(item -> item != null && "CAMPUS".equals(item.getType()))) represented.add("CAMPUS");
            if (!safeList(data.getAchievements()).isEmpty()) represented.add("ACHIEVEMENTS");
            if (!safeList(data.getCertificates()).isEmpty()) represented.add("CERTIFICATES");
            if (data.getSummary() != null) represented.add("SUMMARY");
            if (!safeList(data.getOthers()).isEmpty()) represented.add("OTHERS");
        }
        int matched = 0;
        for (String section : sourceSections) {
            if (sectionIsRepresented(section, represented)) {
                matched++;
            }
        }
        return percentage(matched, sourceSections.size());
    }

    private boolean sectionIsRepresented(String sourceSection, Set<String> represented) {
        return switch (sourceSection) {
            case "WORK_EXPERIENCES" -> represented.contains("WORK");
            case "INTERNSHIPS" -> represented.contains("INTERNSHIP");
            case "CAMPUS_EXPERIENCES" -> represented.contains("CAMPUS");
            case "AWARDS" -> represented.contains("ACHIEVEMENTS");
            case "CERTIFICATES" -> represented.contains("CERTIFICATES");
            default -> represented.contains(sourceSection);
        };
    }

    private boolean hasBasicInfo(ResumeStructuredContentDTO content) {
        if (content == null) {
            return false;
        }
        if (isMeaningfulValue(content.getName())
                || isMeaningfulValue(content.getPhone())
                || isMeaningfulValue(content.getEmail())) {
            return true;
        }
        return content.getBasicInfo() != null && content.getBasicInfo().entrySet().stream()
                .anyMatch(entry -> !"resumeType".equals(entry.getKey()) && isMeaningfulValue(entry.getValue()));
    }

    private int entryCount(ResumeStructuredContentDTO content) {
        return content.getStructuredData() == null ? 0 : entryCount(content.getStructuredData());
    }

    private int entryCount(ResumeStructuredDataDTO data) {
        return safeList(data.getExperiences()).size() + safeList(data.getProjects()).size();
    }

    private boolean isAllowedGeneratedValue(Claim claim, List<SourceFact> source) {
        String value = claim.value();
        if (isHeading(value) || value.matches("(?i)^(?:work|internship|campus|project|experience|summary|skills|education|other|language|framework|database|tool|backend|frontend)$")) {
            return true;
        }
        List<SourceFact> eligible = source.stream()
                .filter(fact -> sectionAllowed(fact.section(), claim.sections()))
                .toList();
        if (claim.sourceRef() != null) {
            List<String> ids = claim.sourceRef().getSourceOccurrenceIds() == null
                    ? List.of() : claim.sourceRef().getSourceOccurrenceIds();
            if (ids.isEmpty()
                    || ids.stream().anyMatch(id -> !isUsableOccurrenceId(id))
                    || new LinkedHashSet<>(ids).size() != ids.size()
                    || claim.sourceRef().getText() == null
                    || claim.sourceRef().getText().isBlank()) {
                return false;
            }
            List<SourceFact> referenced = eligible.stream()
                    .filter(fact -> fact.occurrenceIds().stream().anyMatch(ids::contains))
                    .sorted(java.util.Comparator.comparingInt(SourceFact::order))
                    .toList();
            if (referenced.isEmpty()) return false;
            String referencedText = referenced.stream().map(SourceFact::text)
                    .reduce((left, right) -> left + "\n" + right).orElse("");
            if (!containsFact(List.of(referencedText), claim.sourceRef().getText())) {
                return false;
            }
            // A field must be present in one source occurrence. The only multi-row exception is
            // an explicitly represented contiguous span (for example a wrapped SUMMARY); do
            // not let unrelated tokens from two rows manufacture a company, role, or date.
            if (referenced.stream().anyMatch(fact -> containsFact(List.of(fact.text()), value)
                    || sourceBackedDerivedValue(value, fact.text()))) {
                return true;
            }
            return referenced.size() > 1
                    && contiguousSourceSpan(referenced)
                    && matchesContiguousSourceSubspan(value, referenced);
        }
        return eligible.stream().anyMatch(fact -> containsFact(List.of(fact.text()), value)
                || sourceBackedDerivedValue(value, fact.text()))
                || value.matches("^项目经历\\s*\\d+$")
                || value.matches("^工作经历\\s*\\d+$");
    }

    private boolean sectionAllowed(String actual, Set<String> expected) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        if (expected.stream().anyMatch(value -> "SKILL_EVIDENCE".equalsIgnoreCase(value))) {
            return true;
        }
        if (actual == null || actual.isBlank()
                || "GENERAL".equalsIgnoreCase(actual) || "UNKNOWN".equalsIgnoreCase(actual)) {
            return true;
        }
        String normalizedActual = normalizeSection(actual);
        return expected.stream().map(this::normalizeSection).anyMatch(expectedSection ->
                expectedSection.equals(normalizedActual)
                        || sectionFamily(expectedSection).equals(sectionFamily(normalizedActual)));
    }

    private String normalizeSection(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        String normalized = value.strip().toUpperCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "WORK", "WORK_EXPERIENCE", "EXPERIENCE" -> "WORK_EXPERIENCES";
            case "INTERNSHIP", "INTERN" -> "INTERNSHIPS";
            case "PROJECT", "PROJECT_EXPERIENCE" -> "PROJECTS";
            case "CAMPUS", "ACTIVITIES" -> "CAMPUS_EXPERIENCES";
            case "AWARD", "HONOR", "HONORS", "ACHIEVEMENT", "ACHIEVEMENTS" -> "AWARDS";
            case "CERTIFICATE", "CERTIFICATION" -> "CERTIFICATES";
            case "SKILL", "TECHNICAL_SKILLS", "TECHNIQUE" -> "SKILLS";
            case "EDUCATION_BACKGROUND" -> "EDUCATION";
            case "PROFILE", "SELF_EVALUATION" -> "SUMMARY";
            default -> normalized;
        };
    }

    private String sectionFamily(String section) {
        return switch (normalizeSection(section)) {
            case "WORK_EXPERIENCES", "INTERNSHIPS", "CAMPUS_EXPERIENCES" -> "EXPERIENCE";
            default -> normalizeSection(section);
        };
    }

    /**
     * Allow deterministic projections such as a normalized date range or a description joined
     * from adjacent source lines, while still requiring every lexical token to be source-backed.
     * ASCII tokens must be present as complete tokens; Chinese tokens may be a source-token
     * substring because labels and dates commonly attach to Chinese text.
     */
    private boolean sourceBackedDerivedValue(String value, String sourceText) {
        if (EMAIL_VALUE.matcher(value == null ? "" : value.strip()).matches()
                && sourceText != null
                && sourceText.toLowerCase(Locale.ROOT).contains(value.strip().toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (isFormattedNumericValueBacked(value, sourceText)) {
            return true;
        }
        List<String> expected = sourceCheckTokens(value);
        List<String> available = sourceCheckTokens(sourceText);
        if (expected.isEmpty() || available.isEmpty()) {
            return false;
        }
        String compactExpected = String.join("", expected);
        if (available.stream().anyMatch(availableToken -> availableToken.equals(compactExpected))) {
            return true;
        }
        return containsTokenSubsequence(expected, available);
    }

    private boolean isFormattedNumericValueBacked(String value, String sourceText) {
        if (value == null || sourceText == null || !value.matches("\\d{7,15}")) {
            return false;
        }
        StringBuilder pattern = new StringBuilder("(?<!\\d)");
        for (int index = 0; index < value.length(); index++) {
            if (index > 0) {
                pattern.append("[^0-9A-Za-z]*");
            }
            pattern.append(java.util.regex.Pattern.quote(value.substring(index, index + 1)));
        }
        pattern.append("(?!\\d)");
        return sourceText.matches("(?s).*" + pattern + ".*");
    }

    private List<String> sourceCheckTokens(String value) {
        List<String> result = new ArrayList<>();
        var matcher = SOURCE_CHECK_TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) {
            String token = matcher.group().toLowerCase(Locale.ROOT);
            if (isChinese(token) && token.length() > 1 && token.matches("[年月日].+")) {
                result.add(token.substring(0, 1));
                result.add(token.substring(1));
            } else {
                result.add(token);
            }
        }
        return result;
    }

    private boolean isChinese(String value) {
        return value != null && value.matches("[\\u4e00-\\u9fa5]+");
    }

    private boolean containsFact(List<String> haystacks, String value) {
        String target = normalize(value);
        if (target.isBlank()) return true;
        List<String> targetTokens = tokens(value);
        for (String haystack : haystacks) {
            String normalized = normalize(haystack);
            if (normalized.equals(target)) return true;
            List<String> hayTokens = tokens(haystack);
            if (!targetTokens.isEmpty() && containsTokenSubsequence(targetTokens, hayTokens)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsTokenSubsequence(List<String> expected, List<String> available) {
        if (expected == null || expected.isEmpty() || available == null || available.size() < expected.size()) {
            return false;
        }
        for (int start = 0; start <= available.size() - expected.size(); start++) {
            boolean matches = true;
            for (int offset = 0; offset < expected.size(); offset++) {
                String wanted = expected.get(offset);
                String actual = available.get(start + offset);
                if (!wanted.equalsIgnoreCase(actual)
                        && !(isChinese(wanted) && actual.contains(wanted))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private List<String> tokens(String value) {
        List<String> result = new ArrayList<>();
        var matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) result.add(matcher.group().toLowerCase(Locale.ROOT));
        return result;
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("[^a-z0-9+.#\\u4e00-\\u9fa5]", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\p{Punct}，。；：、（）【】《》“”‘’·•●○◆◇■□▪◦▶►✓✔]+", "").toLowerCase(Locale.ROOT);
    }

    private boolean isGeneral(String section) {
        return section == null || section.isBlank() || "GENERAL".equals(section) || "OTHERS".equals(section);
    }

    private boolean isHeading(String value) {
        return value != null && (STRUCTURAL_HEADINGS.contains(normalize(value))
                || value.strip().matches("^项目\\s*(?:[一二三四五六七八九十]+|\\d+)\\s*[:：.、-]?\\s*$"));
    }

    private boolean isMeaningfulValue(String value) {
        // A one-character fact (for example a Chinese initial or a grade marker) is still source
        // material. Only blanks and punctuation-only rows are ignorable.
        return value != null && value.strip().length() >= 1 && !value.strip().matches("^[\\p{Punct}\\s]+$");
    }

    private boolean isUsableOccurrenceId(String value) {
        return value != null && !value.isBlank()
                && !"null".equalsIgnoreCase(value.strip())
                && !"undefined".equalsIgnoreCase(value.strip());
    }

    private int percentage(int numerator, int denominator) {
        return denominator <= 0 ? 100 : clamp(Math.round(numerator * 100.0f / denominator), 0, 100);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void add(List<String> target, String... values) {
        if (values != null) for (String value : values) if (isMeaningfulValue(value)) target.add(value.strip());
    }

    private void addAll(List<String> target, List<String> values) {
        if (values != null) values.forEach(value -> { if (isMeaningfulValue(value)) target.add(value.strip()); });
    }

    private List<String> normalizeValues(List<String> values) {
        return safeList(values).stream().filter(this::isMeaningfulValue).map(String::strip).toList();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record Claim(
            String value,
            Set<String> sections,
            com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO sourceRef) {
    }

    private record SourceFact(
            String text,
            ResumeSourceBlockRole role,
            boolean general,
            String section,
            String id,
            List<String> ids,
            List<String> occurrenceIds,
            List<String> rawOccurrenceIds,
            int order) {

        private SourceFact {
            ids = ids == null || ids.isEmpty()
                    ? (id == null ? List.of() : List.of(id)) : List.copyOf(ids);
            occurrenceIds = occurrenceIds == null ? List.of() : List.copyOf(occurrenceIds);
            rawOccurrenceIds = rawOccurrenceIds == null ? List.of() : List.copyOf(rawOccurrenceIds);
        }
    }
}
