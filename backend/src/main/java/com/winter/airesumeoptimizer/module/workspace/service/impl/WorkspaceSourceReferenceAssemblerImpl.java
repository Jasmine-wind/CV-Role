package com.winter.airesumeoptimizer.module.workspace.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentSectionKind;
import com.winter.airesumeoptimizer.module.workspace.enums.WorkspaceSourceMappingStatus;
import com.winter.airesumeoptimizer.module.workspace.enums.WorkspaceSourceRestoreScope;
import com.winter.airesumeoptimizer.module.workspace.service.WorkspaceSourceReferenceAssembler;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO.FidelityIssue;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO.SourceBlock;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO.SourceGeometry;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO.TargetMapping;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Occurrence-ID-only implementation; ambiguous and dangling links fail closed. */
@Component
public class WorkspaceSourceReferenceAssemblerImpl implements WorkspaceSourceReferenceAssembler {

    private static final String BLOCKER = "BLOCKER";
    private static final String WARNING = "WARNING";
    private static final Set<String> CANONICAL_SECTION_KINDS = Arrays.stream(ResumeDocumentSectionKind.values())
            .map(Enum::name)
            .collect(Collectors.toUnmodifiableSet());
    private static final Map<String, Function<ResumeDocumentBasicsDTO, String>> BASICS_FIELDS = Map.of(
            "name", ResumeDocumentBasicsDTO::getName,
            "jobIntention", ResumeDocumentBasicsDTO::getJobIntention,
            "highestEducation", ResumeDocumentBasicsDTO::getHighestEducation);
    private static final Map<String, Function<ResumeDocumentEntryDTO, String>> ENTRY_FIELDS = Map.ofEntries(
            Map.entry("organization", ResumeDocumentEntryDTO::getOrganization),
            Map.entry("role", ResumeDocumentEntryDTO::getRole),
            Map.entry("school", ResumeDocumentEntryDTO::getSchool),
            Map.entry("degree", ResumeDocumentEntryDTO::getDegree),
            Map.entry("major", ResumeDocumentEntryDTO::getMajor),
            Map.entry("startDate", ResumeDocumentEntryDTO::getStartDate),
            Map.entry("endDate", ResumeDocumentEntryDTO::getEndDate),
            Map.entry("location", ResumeDocumentEntryDTO::getLocation),
            Map.entry("environment", ResumeDocumentEntryDTO::getEnvironment),
            Map.entry("mentor", ResumeDocumentEntryDTO::getMentor),
            Map.entry("group", ResumeDocumentEntryDTO::getGroup),
            Map.entry("awardTitle", ResumeDocumentEntryDTO::getAwardTitle),
            Map.entry("awardLevel", ResumeDocumentEntryDTO::getAwardLevel),
            Map.entry("awardCompetition", ResumeDocumentEntryDTO::getAwardCompetition),
            Map.entry("awardRanking", ResumeDocumentEntryDTO::getAwardRanking),
            Map.entry("awardDate", ResumeDocumentEntryDTO::getAwardDate));

    @Override
    public WorkspaceSourceReferenceVO assemble(
            Long taskId,
            Long sourceVersionId,
            Long targetVersionId,
            long targetRevision,
            String sourceFilename,
            boolean sourcePdfAvailable,
            ResumeDocumentDTO source,
            ResumeDocumentDTO target) {
        List<FidelityIssue> issues = new ArrayList<>();
        Manifest manifest = manifest(source, issues);
        ConfirmationState confirmations = confirmations(target, manifest, issues);
        List<Node> sourceNodes = flatten(source);
        List<Node> nodes = flatten(target);
        Map<String, Node> authenticatedNodes = authenticationIndex(sourceNodes);
        Set<String> duplicateTargetIdentities = duplicateIdentities(nodes);
        FrozenOwnership sourceOwnership = frozenOwners(manifest, source, sourceNodes);
        if (!sourceOwnership.valid() && issues.stream().noneMatch(issue -> "SOURCE_MANIFEST_INVALID".equals(issue.code()))) {
            issues.add(issue("SOURCE_MANIFEST_INVALID", BLOCKER,
                    "冻结原文归属关系不完整，无法安全建立定位关系。", List.of(), List.of()));
        }
        Map<String, FrozenOwner> sourceOwners = sourceOwnership.owners();

        Map<String, List<Node>> sourceTargets = deepestTargets(
                manifest, nodes, authenticatedNodes, sourceOwners);
        List<TargetMapping> mappings = new ArrayList<>();
        for (Node node : nodes) {
            Resolution resolution = resolve(node.occurrenceIds(), manifest);
            boolean lineageMismatch = duplicateTargetIdentities.contains(stableIdentity(node))
                    || !authenticatedLineage(node, resolution, manifest, authenticatedNodes);
            WorkspaceSourceMappingStatus status;
            if (resolution.invalid() || lineageMismatch) {
                status = WorkspaceSourceMappingStatus.AMBIGUOUS;
                issues.add(issue("AMBIGUOUS_MAPPING", BLOCKER,
                        "目标内容包含未知或不一致的原文引用，必须人工核对。",
                        resolution.rawIds(), List.of(node.id())));
            } else if (resolution.primaryIds().isEmpty()) {
                status = WorkspaceSourceMappingStatus.UNMAPPED;
                if (hasText(node.text())) {
                    issues.add(issue("TARGET_CONTENT_UNMAPPED", WARNING,
                            "当前内容没有可靠的原文定位。", List.of(), List.of(node.id())));
                }
            } else if (resolution.primaryIds().size() > 1) {
                status = WorkspaceSourceMappingStatus.MERGED;
            } else {
                List<Node> inverse = sourceTargets.getOrDefault(resolution.primaryIds().get(0), List.of());
                status = inverse.size() > 1
                        ? WorkspaceSourceMappingStatus.SPLIT : WorkspaceSourceMappingStatus.EXACT;
            }
            boolean reliable = status != WorkspaceSourceMappingStatus.UNMAPPED
                    && status != WorkspaceSourceMappingStatus.AMBIGUOUS;
            String frozenSourceText = resolution.primaryIds().stream()
                    .map(manifest.texts()::get)
                    .filter(Objects::nonNull)
                    .map(WorkspaceSourceReferenceAssemblerImpl::normalized)
                    .collect(Collectors.joining());
            boolean textChanged = reliable && !resolution.primaryIds().isEmpty()
                    && !normalized(node.text()).equals(frozenSourceText);
            mappings.add(new TargetMapping(
                    node.id(), node.type(), node.sectionId(), node.entryId(), node.bulletId(), node.text(),
                    resolution.primaryIds(), status, reliable, textChanged, false, false));
        }

        Map<String, Node> targetIdentityIndex = authenticationIndex(nodes);
        TargetIndex targetIndex = targetIndex(target);
        List<PrimaryState> primaryStates = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : manifest.aliasesByPrimary().entrySet()) {
            String primary = entry.getKey();
            List<Node> inverse = sourceTargets.getOrDefault(primary, List.of());
            WorkspaceSourceMappingStatus status;
            if (inverse.isEmpty()) {
                status = WorkspaceSourceMappingStatus.UNMAPPED;
            } else if (inverse.size() > 1) {
                status = WorkspaceSourceMappingStatus.SPLIT;
            } else {
                Resolution targetResolution = resolve(inverse.get(0).occurrenceIds(), manifest);
                status = targetResolution.primaryIds().size() > 1
                        ? WorkspaceSourceMappingStatus.MERGED : WorkspaceSourceMappingStatus.EXACT;
            }
            FrozenOwner owner = sourceOwners.get(primary);
            boolean ambiguousClaim = hasAmbiguousTargetFor(
                    primary, nodes, manifest, authenticatedNodes, duplicateTargetIdentities);
            boolean omissionEligible = status == WorkspaceSourceMappingStatus.UNMAPPED
                    && manifest.valid() && sourceOwnership.valid()
                    && owner != null && owner.eligible()
                    && !ambiguousClaim;
            boolean omissionConfirmed = omissionEligible && confirmations.valid()
                    && confirmations.ids().containsAll(entry.getValue());
            if (inverse.isEmpty()) {
                if (!omissionConfirmed) {
                    issues.add(issue("SOURCE_CONTENT_UNMAPPED", BLOCKER,
                            "冻结原文仍有内容未进入当前结构，导出已阻止。", entry.getValue(), List.of()));
                }
            } else if (inverse.size() > 1) {
                boolean duplicate = inverse.stream().map(Node::text).map(WorkspaceSourceReferenceAssemblerImpl::normalized)
                        .filter(WorkspaceSourceReferenceAssemblerImpl::hasText)
                        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                        .values().stream().anyMatch(count -> count > 1);
                issues.add(issue(duplicate ? "DUPLICATE_MAPPING" : "SOURCE_CONTENT_SPLIT",
                        duplicate ? BLOCKER : WARNING,
                        duplicate ? "同一原文被重复写入多个目标位置，导出已阻止。" : "一段原文被拆分到多个目标位置，请核对边界。",
                        entry.getValue(), inverse.stream().map(Node::id).toList()));
            }
            primaryStates.add(new PrimaryState(primary, entry.getValue(), manifest.texts().get(primary),
                    sourceGeometryFor(primary, entry.getValue(), manifest.refs()), inverse, status, owner,
                    omissionEligible, omissionConfirmed, ambiguousClaim));
        }

        RestoreContext restoreContext = new RestoreContext(
                manifest, source, targetIndex, targetIdentityIndex, authenticatedNodes);
        List<SourceBlock> blocks = new ArrayList<>();
        int order = 0;
        for (PrimaryState state : primaryStates) {
            RestoreVerdict verdict = restoreVerdict(state, primaryStates, sourceOwnership, restoreContext);
            FrozenOwner owner = state.owner();
            blocks.add(new SourceBlock(state.primary(), order++, state.text(), state.occurrenceIds(),
                    state.geometry(), state.inverse().stream().map(Node::id).toList(), state.status(),
                    state.inverse().size() == 1,
                    owner == null ? null : owner.nodeType(), owner == null ? null : owner.sectionKind(),
                    owner == null ? null : owner.sectionId(), owner == null ? null : owner.entryId(),
                    owner == null ? null : owner.bulletId(), state.omissionConfirmed(), state.omissionEligible(),
                    verdict.scope(), verdict.eligible(), verdict.reason()));
        }

        addStructuralIssues(
                source, target, sourceOwnership, manifest, blocks, nodes, authenticatedNodes, issues);
        EnumMap<WorkspaceSourceMappingStatus, Integer> counts = new EnumMap<>(WorkspaceSourceMappingStatus.class);
        for (WorkspaceSourceMappingStatus status : WorkspaceSourceMappingStatus.values()) counts.put(status, 0);
        for (TargetMapping mapping : mappings) counts.compute(mapping.status(), (key, value) -> value == null ? 1 : value + 1);
        int confirmedOmissionCount = (int) blocks.stream().filter(SourceBlock::omissionConfirmed).count();
        boolean blocked = issues.stream().anyMatch(FidelityIssue::blocker);
        return new WorkspaceSourceReferenceVO(taskId, sourceVersionId, targetVersionId, targetRevision,
                sourceFilename, sourcePdfAvailable, blocks, mappings, issues, counts,
                confirmedOmissionCount, blocked);
    }

    private static Manifest manifest(ResumeDocumentDTO source, List<FidelityIssue> issues) {
        List<String> rawOrder = source == null || source.getSourceOccurrenceIds() == null
                ? List.of() : source.getSourceOccurrenceIds();
        Map<String, String> rawTexts = source == null || source.getSourceOccurrenceTexts() == null
                ? Map.of() : source.getSourceOccurrenceTexts();
        Map<String, String> rawPrimary = source == null || source.getSourceOccurrencePrimaryIds() == null
                ? Map.of() : source.getSourceOccurrencePrimaryIds();
        Map<String, ResumeSourceRefDTO> rawRefs = source == null || source.getSourceOccurrenceRefs() == null
                ? Map.of() : source.getSourceOccurrenceRefs();
        if (rawOrder.isEmpty() || rawTexts.isEmpty()) {
            issues.add(issue("SOURCE_MANIFEST_UNAVAILABLE", BLOCKER,
                    "该历史版本没有可验证的原文 occurrence 清单，无法证明结构完整性。", List.of(), List.of()));
            return new Manifest(Map.of(), Map.of(), Map.of(), Set.of(), Map.of(), false);
        }

        List<String> order = immutableListAllowingNull(rawOrder);
        LinkedHashSet<String> known = new LinkedHashSet<>(order);
        boolean invalid = known.size() != order.size()
                || order.stream().anyMatch(id -> !canonicalId(id))
                || !rawTexts.keySet().equals(known)
                || !rawPrimary.keySet().equals(known)
                || rawRefs.keySet().stream().anyMatch(id -> !canonicalId(id) || !known.contains(id));

        LinkedHashMap<String, List<String>> aliases = new LinkedHashMap<>();
        LinkedHashMap<String, String> aliasToPrimary = new LinkedHashMap<>();
        LinkedHashMap<String, String> texts = new LinkedHashMap<>();
        for (String occurrenceId : order) {
            if (!canonicalId(occurrenceId)) {
                continue;
            }
            String text = rawTexts.get(occurrenceId);
            String primary = rawPrimary.get(occurrenceId);
            if (!hasText(text) || !canonicalId(primary) || !known.contains(primary)) {
                invalid = true;
                continue;
            }
            String primaryRoot = rawPrimary.get(primary);
            String primaryText = rawTexts.get(primary);
            if (!primary.equals(primaryRoot) || !hasText(primaryText) || !primaryText.equals(text)) {
                invalid = true;
                continue;
            }
            if (aliasToPrimary.putIfAbsent(occurrenceId, primary) != null) {
                invalid = true;
                continue;
            }
            aliases.computeIfAbsent(primary, ignored -> new ArrayList<>()).add(occurrenceId);
            texts.putIfAbsent(primary, primaryText);
        }

        for (Map.Entry<String, List<String>> group : aliases.entrySet()) {
            if (!group.getValue().contains(group.getKey())
                    || group.getValue().stream().distinct().count() != group.getValue().size()
                    || group.getValue().stream().anyMatch(id -> !group.getKey().equals(aliasToPrimary.get(id)))) {
                invalid = true;
            }
        }
        if (aliasToPrimary.size() != known.size()
                || aliases.values().stream().mapToInt(List::size).sum() != known.size()) {
            invalid = true;
        }

        LinkedHashMap<String, ResumeSourceRefDTO> refs = new LinkedHashMap<>();
        for (Map.Entry<String, ResumeSourceRefDTO> entry : rawRefs.entrySet()) {
            String occurrenceId = entry.getKey();
            ResumeSourceRefDTO ref = entry.getValue();
            if (!validSidecarRef(occurrenceId, ref, rawTexts, aliasToPrimary, known)) {
                invalid = true;
            } else {
                refs.put(occurrenceId, ref);
            }
        }
        if (invalid) {
            issues.add(issue("SOURCE_MANIFEST_INVALID", BLOCKER,
                    "冻结原文清单不完整，无法安全建立定位关系。", List.of(), List.of()));
        }
        return new Manifest(immutableAliases(aliases), immutableMap(aliasToPrimary), immutableMap(texts),
                immutableSet(known), immutableMap(refs), !invalid);
    }

    private static boolean validSidecarRef(
            String occurrenceId,
            ResumeSourceRefDTO ref,
            Map<String, String> rawTexts,
            Map<String, String> aliasToPrimary,
            Set<String> known) {
        if (ref == null || !Objects.equals(ref.getText(), rawTexts.get(occurrenceId))) {
            return false;
        }
        List<String> ids = ref.getSourceOccurrenceIds();
        if (ids == null || ids.isEmpty() || !ids.contains(occurrenceId)) {
            return false;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>(ids);
        String expectedPrimary = aliasToPrimary.get(occurrenceId);
        return expectedPrimary != null
                && unique.size() == ids.size()
                && ids.stream().allMatch(id -> canonicalId(id)
                        && known.contains(id)
                        && Objects.equals(expectedPrimary, aliasToPrimary.get(id)));
    }

    private static ConfirmationState confirmations(
            ResumeDocumentDTO target, Manifest manifest, List<FidelityIssue> issues) {
        List<String> raw = target == null || target.getConfirmedSourceOmissionIds() == null
                ? List.of() : target.getConfirmedSourceOmissionIds();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        boolean valid = manifest.valid();
        for (String id : raw) {
            if (!canonicalId(id) || !manifest.knownIds().contains(id) || !ids.add(id)) {
                valid = false;
            }
        }
        if (!valid && !raw.isEmpty()) {
            issues.add(issue("CONFIRMED_OMISSION_INVALID", BLOCKER,
                    "已确认省略项不属于当前冻结原文，必须重新核对。", raw.stream()
                            .filter(Objects::nonNull).toList(), List.of()));
            return new ConfirmationState(Set.of(), false);
        }
        return new ConfirmationState(Set.copyOf(ids), valid);
    }

    private static FrozenOwnership frozenOwners(
            Manifest manifest, ResumeDocumentDTO source, List<Node> sourceNodes) {
        Map<String, FrozenOwner> result = new LinkedHashMap<>();
        boolean valid = manifest.valid() && validFrozenDocument(source, manifest)
                && sourceNodes.stream().map(Node::occurrenceIds)
                .map(ids -> resolve(ids, manifest)).noneMatch(Resolution::invalid);
        Set<String> identities = new LinkedHashSet<>();
        if (sourceNodes.stream().map(WorkspaceSourceReferenceAssemblerImpl::stableIdentity)
                .anyMatch(identity -> !identities.add(identity))) {
            valid = false;
        }
        for (String primary : manifest.aliasesByPrimary().keySet()) {
            List<Node> candidates = sourceNodes.stream().filter(node -> {
                Resolution resolution = resolve(node.occurrenceIds(), manifest);
                return !resolution.invalid() && resolution.primaryIds().contains(primary);
            }).toList();
            int depth = candidates.stream().mapToInt(Node::depth).max().orElse(-1);
            List<Node> deepest = candidates.stream().filter(node -> node.depth() == depth).toList();
            if (deepest.isEmpty()) {
                valid = false;
                continue;
            }
            if (deepest.size() > 1) {
                // One physical header line can legitimately materialize as several typed contacts
                // and BASICS scalar fields. It remains a SPLIT relationship and is not omission-
                // eligible, but does not make the frozen manifest corrupt. Other owners are invalid.
                boolean headerFieldSplit = deepest.stream().allMatch(node ->
                        "CONTACT".equals(node.type()) || "BASICS".equals(node.type()));
                if (!headerFieldSplit) {
                    valid = false;
                }
                continue;
            }
            if (!hasText(manifest.texts().get(primary))) {
                valid = false;
                continue;
            }
            Node node = deepest.get(0);
            boolean eligible = Set.of("SECTION", "ENTRY", "BULLET", "BASICS", "CONTACT").contains(node.type());
            result.put(primary, new FrozenOwner(
                    node.type(), node.sectionKind(), node.sectionId(), node.entryId(), node.bulletId(),
                    node.depth(), eligible));
        }
        return new FrozenOwnership(Collections.unmodifiableMap(new LinkedHashMap<>(result)), valid);
    }

    private static boolean validFrozenDocument(ResumeDocumentDTO source, Manifest manifest) {
        if (source == null) {
            return false;
        }
        boolean valid = validChildRef(source.getSourceRef(), manifest);
        Set<String> stableIdentities = new LinkedHashSet<>();
        ResumeDocumentBasicsDTO basics = source.getBasics();
        if (basics != null) {
            valid &= validOccurrencePair(
                    basics.getSourceOccurrenceIds(), basics.getSourceRef(), manifest);
            valid &= validSemanticRefMap(
                    basics.getFieldSourceRefs(), basics, BASICS_FIELDS, manifest);
            for (ResumeDocumentContactDTO contact : safe(basics.getContacts())) {
                if (contact == null || !canonicalId(contact.getId())
                        || !stableIdentities.add(contact.getId())) {
                    valid = false;
                    continue;
                }
                valid &= validOccurrencePair(
                        contact.getSourceOccurrenceIds(), contact.getSourceRef(), manifest);
            }
        }
        for (ResumeDocumentSectionDTO section : safe(source.getSections())) {
            if (section == null || !canonicalId(section.getId())
                    || !CANONICAL_SECTION_KINDS.contains(section.getKind())
                    || !stableIdentities.add(section == null ? null : section.getId())) {
                valid = false;
                continue;
            }
            valid &= validOccurrencePair(
                    section.getSourceOccurrenceIds(), section.getSourceRef(), manifest);
            for (ResumeDocumentEntryDTO entry : safe(section.getEntries())) {
                if (entry == null || !canonicalId(entry.getId())
                        || !stableIdentities.add(entry.getId())) {
                    valid = false;
                    continue;
                }
                valid &= validOccurrencePair(
                        entry.getSourceOccurrenceIds(), entry.getSourceRef(), manifest);
                valid &= validSemanticRefMap(
                        entry.getFieldSourceRefs(), entry, ENTRY_FIELDS, manifest);
                valid &= validAlignedRefs(entry.getTechStack(), entry.getTechStackSourceRefs(), manifest);
                valid &= validAlignedRefs(entry.getSkillItems(), entry.getSkillItemSourceRefs(), manifest);
                valid &= validAlignedRefs(
                        entry.getSkillDescriptions(), entry.getSkillDescriptionSourceRefs(), manifest);
                for (ResumeDocumentBulletDTO bullet : safe(entry.getBullets())) {
                    if (bullet == null || !canonicalId(bullet.getId())
                            || !stableIdentities.add(bullet.getId())) {
                        valid = false;
                        continue;
                    }
                    valid &= validOccurrencePair(
                            bullet.getSourceOccurrenceIds(), bullet.getSourceRef(), manifest);
                }
            }
        }
        return valid;
    }

    private static boolean validOccurrenceIds(List<String> ids, Manifest manifest) {
        return ids == null || !resolve(ids, manifest).invalid();
    }

    private static boolean validOccurrencePair(
            List<String> ids, ResumeSourceRefDTO ref, Manifest manifest) {
        if (!validOccurrenceIds(ids, manifest) || !validChildRef(ref, manifest)) {
            return false;
        }
        if (ids == null || ids.isEmpty() || ref == null) {
            return true;
        }
        Resolution aggregate = resolve(ids, manifest);
        Resolution reference = resolve(ref.getSourceOccurrenceIds(), manifest);
        return new LinkedHashSet<>(aggregate.primaryIds()).containsAll(reference.primaryIds());
    }

    private static <T> boolean validSemanticRefMap(
            Map<String, ResumeSourceRefDTO> refs,
            T owner,
            Map<String, Function<T, String>> fields,
            Manifest manifest) {
        return refs == null || refs.entrySet().stream().allMatch(entry -> {
            Function<T, String> value = fields.get(entry.getKey());
            return value != null
                    && hasText(value.apply(owner))
                    && entry.getValue() != null
                    && validChildRef(entry.getValue(), manifest);
        });
    }

    private static boolean validAlignedRefs(
            List<String> values, List<ResumeSourceRefDTO> refs, Manifest manifest) {
        if (refs == null) return true;
        if (values == null || values.size() != refs.size()) return false;
        for (int index = 0; index < refs.size(); index++) {
            ResumeSourceRefDTO ref = refs.get(index);
            if (!hasText(values.get(index)) || (ref != null && !validChildRef(ref, manifest))) {
                return false;
            }
        }
        return true;
    }

    private static boolean validChildRef(ResumeSourceRefDTO ref, Manifest manifest) {
        if (ref == null) {
            return true;
        }
        List<String> ids = ref.getSourceOccurrenceIds();
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        Resolution resolution = resolve(ids, manifest);
        String expectedText = resolution.primaryIds().stream()
                .map(manifest.texts()::get)
                .collect(Collectors.joining("\n"));
        List<String> rootPrimaryOrder = List.copyOf(manifest.aliasesByPrimary().keySet());
        int previousIndex = -1;
        boolean rootOrdered = true;
        for (String primary : resolution.primaryIds()) {
            int currentIndex = rootPrimaryOrder.indexOf(primary);
            if (currentIndex < 0 || (previousIndex >= 0 && currentIndex != previousIndex + 1)) {
                rootOrdered = false;
                break;
            }
            previousIndex = currentIndex;
        }
        return !resolution.invalid()
                && !resolution.primaryIds().isEmpty()
                && rootOrdered
                && Objects.equals(ref.getText(), expectedText);
    }

    private static boolean hasAmbiguousTargetFor(
            String primary,
            List<Node> nodes,
            Manifest manifest,
            Map<String, Node> authenticatedNodes,
            Set<String> duplicateTargetIdentities) {
        for (Node node : nodes) {
            Resolution resolution = resolve(node.occurrenceIds(), manifest);
            if (resolution.primaryIds().contains(primary)
                    && (resolution.invalid()
                    || duplicateTargetIdentities.contains(stableIdentity(node))
                    || !authenticatedLineage(node, resolution, manifest, authenticatedNodes))) {
                return true;
            }
        }
        return false;
    }

    private static SourceGeometry sourceGeometryFor(
            String primary, List<String> aliases, Map<String, ResumeSourceRefDTO> refs) {
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        ResumeSourceRefDTO ref = refs.get(primary);
        if (ref == null) {
            ref = safe(aliases).stream().map(refs::get).filter(Objects::nonNull).findFirst().orElse(null);
        }
        return ref == null ? null : new SourceGeometry(
                ref.getPage(), ref.getX(), ref.getY(), ref.getWidth(), ref.getHeight(),
                ref.getFontSize(), ref.getFontName(), ref.getBoldHint(), ref.getIndent(), ref.getBulletHint());
    }

    private static Map<String, List<Node>> deepestTargets(
            Manifest manifest,
            List<Node> nodes,
            Map<String, Node> authenticatedNodes,
            Map<String, FrozenOwner> sourceOwners) {
        Map<String, List<Node>> result = new HashMap<>();
        for (String primary : manifest.aliasesByPrimary().keySet()) {
            FrozenOwner owner = sourceOwners.get(primary);
            List<Node> candidates = nodes.stream()
                    .filter(node -> {
                        Resolution resolution = resolve(node.occurrenceIds(), manifest);
                        return (owner == null || node.depth() == owner.depth())
                                && !resolution.invalid()
                                && authenticatedLineage(node, resolution, manifest, authenticatedNodes)
                                && resolution.primaryIds().contains(primary);
                    })
                    .toList();
            if (owner == null) {
                int depth = candidates.stream().mapToInt(Node::depth).max().orElse(-1);
                candidates = candidates.stream().filter(node -> node.depth() == depth).toList();
            }
            result.put(primary, candidates);
        }
        return result;
    }

    private static Map<String, Node> authenticationIndex(List<Node> sourceNodes) {
        LinkedHashMap<String, Node> result = new LinkedHashMap<>();
        for (Node node : sourceNodes) {
            result.putIfAbsent(stableIdentity(node), node);
        }
        return Collections.unmodifiableMap(result);
    }

    private static Set<String> duplicateIdentities(List<Node> nodes) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        LinkedHashSet<String> duplicates = new LinkedHashSet<>();
        for (Node node : nodes) {
            String identity = stableIdentity(node);
            if (!seen.add(identity)) {
                duplicates.add(identity);
            }
        }
        return Collections.unmodifiableSet(duplicates);
    }

    /**
     * Server-computed restore verdict for one SOURCE block. The unit kind and its safety are both
     * derived from the frozen ownership graph plus the authenticated current TARGET structure;
     * client input never participates in this decision and a hidden client verdict is worthless.
     */
    private static RestoreVerdict restoreVerdict(
            PrimaryState state,
            List<PrimaryState> allStates,
            FrozenOwnership ownership,
            RestoreContext context) {
        FrozenOwner owner = state.owner();
        if (!ownership.valid() || owner == null || !owner.eligible()) {
            return RestoreVerdict.none();
        }
        if (state.status() != WorkspaceSourceMappingStatus.UNMAPPED) {
            return RestoreVerdict.none();
        }
        boolean project = "PROJECT".equals(upper(owner.sectionKind()));
        return switch (owner.nodeType()) {
            case "CONTACT" -> contactVerdict(state, allStates, context);
            case "BULLET" -> project && !context.targetIndex().sectionByEntryId().containsKey(owner.entryId())
                    ? projectVerdict(state, allStates, context)
                    : bulletVerdict(state, allStates, context);
            case "ENTRY" -> project
                    ? projectVerdict(state, allStates, context)
                    : entryVerdict(state, allStates, context);
            default -> RestoreVerdict.none();
        };
    }

    private static RestoreVerdict bulletVerdict(
            PrimaryState state, List<PrimaryState> allStates, RestoreContext context) {
        FrozenOwner owner = state.owner();
        if (!hasText(owner.sectionId()) || !hasText(owner.entryId()) || !hasText(owner.bulletId())) {
            return RestoreVerdict.none();
        }
        boolean boundaryIntact = boundaryClean(allStates, candidate -> {
            FrozenOwner candidateOwner = candidate.owner();
            return candidateOwner != null && "BULLET".equals(candidateOwner.nodeType())
                    && Objects.equals(owner.sectionId(), candidateOwner.sectionId())
                    && Objects.equals(owner.entryId(), candidateOwner.entryId())
                    && Objects.equals(owner.bulletId(), candidateOwner.bulletId());
        });
        if (!boundaryIntact) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.BULLET, "BOUNDARY_HAS_OTHER_MAPPINGS");
        }
        ResumeDocumentSectionDTO section = context.targetIndex().sectionsById().get(owner.sectionId());
        ResumeDocumentSectionDTO entrySection = context.targetIndex().sectionByEntryId().get(owner.entryId());
        if (section == null || entrySection == null || !Objects.equals(section.getId(), entrySection.getId())) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.BULLET, "PARENT_SECTION_MISSING");
        }
        if (!Objects.equals(upper(section.getKind()), upper(owner.sectionKind()))
                || !parentLineageClean(owner.sectionId(), owner.entryId(), context)) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.BULLET, "PARENT_LINEAGE_MISMATCH");
        }
        if (context.targetIndex().bulletIds().contains(owner.bulletId())) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.BULLET, "TARGET_VALUE_CONFLICT");
        }
        return RestoreVerdict.eligible(WorkspaceSourceRestoreScope.BULLET);
    }

    private static RestoreVerdict entryVerdict(
            PrimaryState state, List<PrimaryState> allStates, RestoreContext context) {
        FrozenOwner owner = state.owner();
        if (!hasText(owner.sectionId()) || !hasText(owner.entryId())) {
            return RestoreVerdict.none();
        }
        boolean boundaryIntact = boundaryClean(allStates, candidate -> belongsToBoundary(candidate, owner.sectionId(), owner.entryId()));
        if (!boundaryIntact) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.ENTRY, "BOUNDARY_HAS_OTHER_MAPPINGS");
        }
        if (context.targetIndex().sectionByEntryId().containsKey(owner.entryId())) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.ENTRY, "ENTRY_ALREADY_PRESENT");
        }
        ResumeDocumentSectionDTO section = context.targetIndex().sectionsById().get(owner.sectionId());
        if (section == null) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.ENTRY, "PARENT_SECTION_MISSING");
        }
        if (!Objects.equals(upper(section.getKind()), upper(owner.sectionKind()))
                || !parentLineageClean(owner.sectionId(), null, context)) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.ENTRY, "PARENT_LINEAGE_MISMATCH");
        }
        if (boundaryBulletIdCollides(allStates, owner.sectionId(), owner.entryId(), context)) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.ENTRY, "TARGET_VALUE_CONFLICT");
        }
        return RestoreVerdict.eligible(WorkspaceSourceRestoreScope.ENTRY);
    }

    private static RestoreVerdict projectVerdict(
            PrimaryState state, List<PrimaryState> allStates, RestoreContext context) {
        FrozenOwner owner = state.owner();
        if (!hasText(owner.sectionId()) || !hasText(owner.entryId())) {
            return RestoreVerdict.none();
        }
        boolean boundaryIntact = boundaryClean(allStates, candidate -> belongsToBoundary(candidate, owner.sectionId(), owner.entryId()));
        if (!boundaryIntact) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.PROJECT_ENTRY, "BOUNDARY_HAS_OTHER_MAPPINGS");
        }
        if (context.targetIndex().sectionByEntryId().containsKey(owner.entryId())) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.PROJECT_ENTRY, "ENTRY_ALREADY_PRESENT");
        }
        ResumeDocumentSectionDTO section = context.targetIndex().sectionsById().get(owner.sectionId());
        if (section == null) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.PROJECT_ENTRY, "PARENT_SECTION_MISSING");
        }
        if (!"PROJECT".equals(upper(section.getKind()))
                || !parentLineageClean(owner.sectionId(), null, context)) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.PROJECT_ENTRY, "PARENT_LINEAGE_MISMATCH");
        }
        if (boundaryBulletIdCollides(allStates, owner.sectionId(), owner.entryId(), context)) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.PROJECT_ENTRY, "TARGET_VALUE_CONFLICT");
        }
        return RestoreVerdict.eligible(WorkspaceSourceRestoreScope.PROJECT_ENTRY);
    }

    private static RestoreVerdict contactVerdict(
            PrimaryState state, List<PrimaryState> allStates, RestoreContext context) {
        boolean boundaryIntact = boundaryClean(allStates,
                candidate -> Objects.equals(state.primary(), candidate.primary()));
        if (!boundaryIntact) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.CONTACT, "BOUNDARY_HAS_OTHER_MAPPINGS");
        }
        ResumeDocumentContactDTO frozenContact = frozenContact(state.primary(), context);
        if (frozenContact == null) {
            return RestoreVerdict.none();
        }
        if (hasText(frozenContact.getId()) && context.targetIndex().contactIds().contains(frozenContact.getId())) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.CONTACT, "TARGET_VALUE_CONFLICT");
        }
        String value = normalized(frozenContact.getValue());
        if (hasText(value) && context.targetIndex().contactValues().getOrDefault(value, 0) > 0) {
            return RestoreVerdict.blocked(WorkspaceSourceRestoreScope.CONTACT, "TARGET_VALUE_CONFLICT");
        }
        return RestoreVerdict.eligible(WorkspaceSourceRestoreScope.CONTACT);
    }

    /**
     * A restore unit is only safe when every frozen occurrence owned by the whole boundary is
     * currently unmapped and unambiguous. Legitimate ancestor containers (section/entry nodes)
     * naturally carry their children's occurrence ids, so they do not count as owner-level or
     * ambiguous claims; any broken-lineage claim on the boundary is rejected instead.
     */
    private static boolean boundaryClean(List<PrimaryState> allStates, Predicate<PrimaryState> match) {
        boolean sawAny = false;
        for (PrimaryState candidate : allStates) {
            if (!match.test(candidate)) continue;
            sawAny = true;
            if (candidate.status() != WorkspaceSourceMappingStatus.UNMAPPED
                    || candidate.ambiguousClaim()) {
                return false;
            }
        }
        return sawAny;
    }

    private static boolean belongsToBoundary(PrimaryState candidate, String sectionId, String entryId) {
        FrozenOwner owner = candidate.owner();
        return owner != null
                && ("ENTRY".equals(owner.nodeType()) || "BULLET".equals(owner.nodeType()))
                && Objects.equals(sectionId, owner.sectionId())
                && Objects.equals(entryId, owner.entryId());
    }

    private static boolean boundaryBulletIdCollides(
            List<PrimaryState> allStates, String sectionId, String entryId, RestoreContext context) {
        for (PrimaryState candidate : allStates) {
            FrozenOwner owner = candidate.owner();
            if (owner == null || !"BULLET".equals(owner.nodeType()) || owner.bulletId() == null) continue;
            if (!Objects.equals(sectionId, owner.sectionId()) || !Objects.equals(entryId, owner.entryId())) continue;
            if (context.targetIndex().bulletIds().contains(owner.bulletId())) {
                return true;
            }
        }
        return false;
    }

    /** A parent node may only host a restored child while its own lineage stays authentic. */
    private static boolean parentLineageClean(String sectionId, String entryId, RestoreContext context) {
        for (String identity : new String[] {sectionId, entryId}) {
            if (!hasText(identity)) continue;
            Node node = context.targetIdentityIndex().get(identity);
            if (node == null || node.occurrenceIds().isEmpty()) continue;
            Resolution resolution = resolve(node.occurrenceIds(), context.manifest());
            if (resolution.invalid()
                    || !authenticatedLineage(node, resolution, context.manifest(), context.frozenIdentityIndex())) {
                return false;
            }
        }
        return true;
    }

    /** Resolve the unique frozen contact that owns this primary; several matches are ambiguous. */
    private static ResumeDocumentContactDTO frozenContact(String primary, RestoreContext context) {
        ResumeDocumentContactDTO match = null;
        for (ResumeDocumentContactDTO contact : safe(context.source() == null || context.source().getBasics() == null
                ? null : context.source().getBasics().getContacts())) {
            if (contact == null) continue;
            List<String> ids = contact.getSourceOccurrenceIds() != null && !contact.getSourceOccurrenceIds().isEmpty()
                    ? contact.getSourceOccurrenceIds()
                    : (contact.getSourceRef() == null ? null : contact.getSourceRef().getSourceOccurrenceIds());
            Resolution resolution = resolve(ids, context.manifest());
            if (resolution.invalid() || !resolution.primaryIds().contains(primary)) continue;
            if (match != null) return null;
            match = contact;
        }
        return match;
    }

    private static TargetIndex targetIndex(ResumeDocumentDTO target) {
        LinkedHashSet<String> contactIds = new LinkedHashSet<>();
        LinkedHashMap<String, Integer> contactValues = new LinkedHashMap<>();
        LinkedHashMap<String, ResumeDocumentSectionDTO> sectionsById = new LinkedHashMap<>();
        LinkedHashMap<String, ResumeDocumentSectionDTO> sectionByEntryId = new LinkedHashMap<>();
        LinkedHashSet<String> bulletIds = new LinkedHashSet<>();
        if (target != null) {
            for (ResumeDocumentContactDTO contact : safe(target.getBasics() == null
                    ? null : target.getBasics().getContacts())) {
                if (contact == null) continue;
                if (canonicalId(contact.getId())) contactIds.add(contact.getId());
                String value = normalized(contact.getValue());
                if (hasText(value)) contactValues.merge(value, 1, Integer::sum);
            }
            for (ResumeDocumentSectionDTO section : safe(target.getSections())) {
                if (section == null) continue;
                if (canonicalId(section.getId())) sectionsById.putIfAbsent(section.getId(), section);
                for (ResumeDocumentEntryDTO entry : safe(section.getEntries())) {
                    if (entry == null) continue;
                    if (canonicalId(entry.getId())) sectionByEntryId.putIfAbsent(entry.getId(), section);
                    for (ResumeDocumentBulletDTO bullet : safe(entry.getBullets())) {
                        if (bullet != null && canonicalId(bullet.getId())) bulletIds.add(bullet.getId());
                    }
                }
            }
        }
        return new TargetIndex(contactIds, contactValues, sectionsById, sectionByEntryId, bulletIds);
    }

    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private static boolean authenticatedLineage(
            Node node, Resolution resolution, Manifest manifest, Map<String, Node> authenticatedNodes) {
        if (resolution.primaryIds().isEmpty()) return true;
        Node frozenNode = authenticatedNodes.get(stableIdentity(node));
        if (frozenNode == null || !boundary(frozenNode).equals(boundary(node))) return false;
        Resolution frozen = resolve(frozenNode.occurrenceIds(), manifest);
        return !frozen.invalid() && new LinkedHashSet<>(frozen.primaryIds())
                .equals(new LinkedHashSet<>(resolution.primaryIds()));
    }

    private static String stableIdentity(Node node) {
        if (node == null) return "UNKNOWN";
        return switch (node.type()) {
            case "SECTION" -> node.sectionId();
            case "ENTRY" -> node.entryId();
            case "BULLET" -> node.bulletId();
            case "CONTACT" -> node.id().startsWith("contact:")
                    ? node.id().substring("contact:".length()) : node.id();
            default -> node.id();
        };
    }

    private static AuthenticationBoundary boundary(Node node) {
        return new AuthenticationBoundary(
                node.type(), node.sectionKind(), node.sectionId(), node.entryId(), node.bulletId());
    }

    private static Resolution resolve(List<String> values, Manifest manifest) {
        List<String> raw = values == null ? List.of() : immutableListAllowingNull(values);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        LinkedHashSet<String> primary = new LinkedHashSet<>();
        boolean invalid = !manifest.valid();
        for (String id : raw) {
            if (!canonicalId(id) || !seen.add(id)) {
                invalid = true;
                continue;
            }
            String resolved = manifest.aliasToPrimary().get(id);
            if (resolved == null) invalid = true;
            else primary.add(resolved);
        }
        return new Resolution(raw, List.copyOf(primary), invalid);
    }

    private static List<Node> flatten(ResumeDocumentDTO document) {
        List<Node> nodes = new ArrayList<>();
        if (document == null) return nodes;
        ResumeDocumentBasicsDTO basics = document.getBasics();
        if (basics != null) {
            addField(nodes, "basics", "BASICS", null, null, null, null,
                    basics.getSourceRef() == null ? null : basics.getSourceRef().getText(),
                    firstIds(basics.getSourceOccurrenceIds(), basics.getSourceRef()), 1);
            addField(nodes, "basics:name", "BASICS", null, null, null, null, basics.getName(), refIds(basics.getFieldSourceRefs(), "name"), 3);
            addField(nodes, "basics:jobIntention", "BASICS", null, null, null, null, basics.getJobIntention(), refIds(basics.getFieldSourceRefs(), "jobIntention"), 3);
            addField(nodes, "basics:highestEducation", "BASICS", null, null, null, null, basics.getHighestEducation(), refIds(basics.getFieldSourceRefs(), "highestEducation"), 3);
            for (ResumeDocumentContactDTO contact : safe(basics.getContacts())) {
                if (contact != null) addField(nodes, "contact:" + safeId(contact.getId()), "CONTACT", null, null, null, null,
                        contact.getValue(), firstIds(contact.getSourceOccurrenceIds(), contact.getSourceRef()), 3);
            }
        }
        for (ResumeDocumentSectionDTO section : safe(document.getSections())) {
            if (section == null) continue;
            String sectionId = safeId(section.getId());
            String sectionKind = section.getKind();
            addField(nodes, "section:" + sectionId, "SECTION", sectionKind, sectionId, null, null, section.getTitle(),
                    firstIds(section.getSourceOccurrenceIds(), section.getSourceRef()), 1);
            for (ResumeDocumentEntryDTO entry : safe(section.getEntries())) {
                if (entry == null) continue;
                String entryId = safeId(entry.getId());
                addField(nodes, "section:" + sectionId + "/entry:" + entryId, "ENTRY", sectionKind, sectionId, entryId, null,
                        entryText(entry), new ArrayList<>(entryOwnOccurrenceIds(entry)), 2);
                for (ResumeDocumentBulletDTO bullet : safe(entry.getBullets())) {
                    if (bullet == null) continue;
                    String bulletId = safeId(bullet.getId());
                    addField(nodes, "section:" + sectionId + "/entry:" + entryId + "/bullet:" + bulletId,
                            "BULLET", sectionKind, sectionId, entryId, bulletId, bullet.getText(),
                            firstIds(bullet.getSourceOccurrenceIds(), bullet.getSourceRef()), 3);
                }
            }
        }
        return nodes;
    }

    private static void addField(List<Node> nodes, String id, String type, String sectionKind,
            String sectionId, String entryId, String bulletId, String text, List<String> ids, int depth) {
        if (hasText(text) || !ids.isEmpty()) {
            nodes.add(new Node(id, type, sectionKind, sectionId, entryId, bulletId, text, ids, depth));
        }
    }

    private static List<String> refIds(Map<String, ResumeSourceRefDTO> refs, String key) {
        return refs == null ? List.of() : firstIds(null, refs.get(key));
    }

    private static List<String> firstIds(List<String> ids, ResumeSourceRefDTO ref) {
        if (ids != null && !ids.isEmpty()) return ids;
        return ref == null || ref.getSourceOccurrenceIds() == null ? List.of() : ref.getSourceOccurrenceIds();
    }

    private static String entryText(ResumeDocumentEntryDTO entry) {
        return java.util.stream.Stream.of(entry.getOrganization(), entry.getRole(), entry.getSchool(), entry.getDegree(), entry.getMajor(),
                        entry.getStartDate(), entry.getEndDate(), entry.getLocation(), entry.getEnvironment(), entry.getMentor(),
                        entry.getGroup(), entry.getAwardTitle(), entry.getAwardLevel(), entry.getAwardCompetition(),
                        entry.getAwardRanking(), entry.getAwardDate())
                .filter(Objects::nonNull).filter(WorkspaceSourceReferenceAssemblerImpl::hasText)
                .collect(Collectors.joining(" · "));
    }

    private static LinkedHashSet<String> entryOwnOccurrenceIds(ResumeDocumentEntryDTO entry) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        addOccurrenceIds(ids, entry.getSourceOccurrenceIds());
        addRefOccurrenceIds(ids, entry.getSourceRef());
        if (entry.getFieldSourceRefs() != null) {
            entry.getFieldSourceRefs().values().forEach(ref -> addRefOccurrenceIds(ids, ref));
        }
        safe(entry.getTechStackSourceRefs()).forEach(ref -> addRefOccurrenceIds(ids, ref));
        safe(entry.getSkillItemSourceRefs()).forEach(ref -> addRefOccurrenceIds(ids, ref));
        safe(entry.getSkillDescriptionSourceRefs()).forEach(ref -> addRefOccurrenceIds(ids, ref));
        return ids;
    }

    private static LinkedHashSet<String> projectEntryOccurrenceIds(
            String sectionId, String entryId, FrozenOwnership sourceOwnership, Manifest manifest) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (!sourceOwnership.valid()) {
            return ids;
        }
        for (Map.Entry<String, FrozenOwner> ownerEntry : sourceOwnership.owners().entrySet()) {
            FrozenOwner owner = ownerEntry.getValue();
            if ("PROJECT".equals(owner.sectionKind())
                    && Objects.equals(sectionId, owner.sectionId())
                    && Objects.equals(entryId, owner.entryId())) {
                ids.addAll(manifest.aliasesByPrimary().getOrDefault(ownerEntry.getKey(), List.of()));
            }
        }
        return ids;
    }

    private static boolean meaningfulTargetEntry(ResumeDocumentEntryDTO entry) {
        if (hasText(entryText(entry))
                || safe(entry.getTechStack()).stream().anyMatch(WorkspaceSourceReferenceAssemblerImpl::hasText)
                || safe(entry.getSkillItems()).stream().anyMatch(WorkspaceSourceReferenceAssemblerImpl::hasText)
                || safe(entry.getSkillDescriptions()).stream().anyMatch(WorkspaceSourceReferenceAssemblerImpl::hasText)) {
            return true;
        }
        return safe(entry.getBullets()).stream().filter(Objects::nonNull)
                .anyMatch(bullet -> hasText(bullet.getText()));
    }

    private static String boundaryKey(String sectionId, String entryId) {
        return String.valueOf(sectionId) + "/" + String.valueOf(entryId);
    }

    private static Set<String> authenticatedProjectEntries(
            ResumeDocumentDTO target,
            List<Node> targetNodes,
            Manifest manifest,
            Map<String, Node> authenticatedNodes,
            Map<String, FrozenOwner> sourceOwners) {
        Set<String> meaningfulEntries = safe(target == null ? null : target.getSections()).stream()
                .filter(Objects::nonNull)
                .filter(section -> "PROJECT".equals(section.getKind()))
                .flatMap(section -> safe(section.getEntries()).stream()
                        .filter(Objects::nonNull)
                        .filter(WorkspaceSourceReferenceAssemblerImpl::meaningfulTargetEntry)
                        .map(entry -> boundaryKey(section.getId(), entry.getId())))
                .collect(Collectors.toSet());
        LinkedHashSet<String> authenticated = new LinkedHashSet<>();
        for (Node node : targetNodes) {
            if (!"PROJECT".equals(node.sectionKind()) || node.entryId() == null
                    || !meaningfulEntries.contains(boundaryKey(node.sectionId(), node.entryId()))) {
                continue;
            }
            Resolution resolution = resolve(node.occurrenceIds(), manifest);
            if (resolution.invalid() || resolution.primaryIds().isEmpty()
                    || !authenticatedLineage(node, resolution, manifest, authenticatedNodes)) {
                continue;
            }
            boolean ownedByEntry = resolution.primaryIds().stream().anyMatch(primary -> {
                FrozenOwner owner = sourceOwners.get(primary);
                return owner != null
                        && "PROJECT".equals(owner.sectionKind())
                        && Objects.equals(node.sectionId(), owner.sectionId())
                        && Objects.equals(node.entryId(), owner.entryId());
            });
            if (ownedByEntry) {
                authenticated.add(boundaryKey(node.sectionId(), node.entryId()));
            }
        }
        return Collections.unmodifiableSet(authenticated);
    }

    private static void addRefOccurrenceIds(Set<String> ids, ResumeSourceRefDTO ref) {
        if (ref != null) addOccurrenceIds(ids, ref.getSourceOccurrenceIds());
    }

    private static void addOccurrenceIds(Set<String> ids, List<String> values) {
        if (values == null) {
            return;
        }
        if (values.stream().anyMatch(id -> !canonicalId(id))
                || new LinkedHashSet<>(values).size() != values.size()) {
            ids.add("");
        }
        ids.addAll(values);
    }

    private static void addStructuralIssues(
            ResumeDocumentDTO source,
            ResumeDocumentDTO target,
            FrozenOwnership sourceOwnership,
            Manifest manifest,
            List<SourceBlock> blocks,
            List<Node> targetNodes,
            Map<String, Node> authenticatedNodes,
            List<FidelityIssue> issues) {
        for (ResumeDocumentSectionDTO section : safe(target == null ? null : target.getSections())) {
            if (section != null && !hasText(section.getTitle()) && !safe(section.getEntries()).isEmpty()) {
                issues.add(issue("SECTION_HEADING_LOST", BLOCKER,
                        "章节标题为空但子内容仍存在，导出已阻止。", List.of(), List.of("section:" + safeId(section.getId()))));
            }
        }
        Set<String> targetProjectEntries = authenticatedProjectEntries(
                target, targetNodes, manifest, authenticatedNodes, sourceOwnership.owners());
        List<String> missingProjectOccurrences = new ArrayList<>();
        for (ResumeDocumentSectionDTO section : safe(source == null ? null : source.getSections())) {
            if (section == null || !"PROJECT".equals(section.getKind())) {
                continue;
            }
            for (ResumeDocumentEntryDTO entry : safe(section.getEntries())) {
                if (entry == null || targetProjectEntries.contains(boundaryKey(section.getId(), entry.getId()))) {
                    continue;
                }
                Set<String> occurrences = projectEntryOccurrenceIds(
                        section.getId(), entry.getId(), sourceOwnership, manifest);
                if (occurrences.isEmpty()) {
                    continue;
                }
                // A frozen PROJECT entry that disappears from TARGET is only a legitimate
                // job-targeted deletion when every server-validated source block owned by
                // that entry is already a confirmed omission. Partial confirmation,
                // accidental loss, wrong-ownership merges and forged confirmations all
                // keep the boundary blocker in force.
                List<SourceBlock> projectBlocks = blocks.stream()
                        .filter(block -> "PROJECT".equals(block.sourceSectionKind())
                                && Objects.equals(section.getId(), block.sourceSectionId())
                                && Objects.equals(entry.getId(), block.sourceEntryId()))
                        .toList();
                boolean fullyConfirmedOmitted = !projectBlocks.isEmpty()
                        && projectBlocks.stream().allMatch(SourceBlock::omissionConfirmed);
                if (fullyConfirmedOmitted) {
                    continue;
                }
                missingProjectOccurrences.addAll(occurrences);
            }
        }
        if (!missingProjectOccurrences.isEmpty()) {
            issues.add(issue("PROJECT_BOUNDARY_LOST", BLOCKER,
                    "冻结项目条目在当前简历中缺失，且未被完整确认省略。",
                    missingProjectOccurrences.stream().distinct().toList(), List.of()));
        }
        Map<String, Long> contacts = safe(target == null || target.getBasics() == null ? null : target.getBasics().getContacts())
                .stream().filter(Objects::nonNull).map(ResumeDocumentContactDTO::getValue)
                .filter(WorkspaceSourceReferenceAssemblerImpl::hasText).map(WorkspaceSourceReferenceAssemblerImpl::normalized)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        if (contacts.values().stream().anyMatch(count -> count > 1)) {
            issues.add(issue("DUPLICATE_CONTACT_SUSPECTED", BLOCKER,
                    "检测到疑似重复联系方式，导出已阻止。", List.of(), List.of()));
        }
        List<String> dates = safe(target == null ? null : target.getSections()).stream().filter(Objects::nonNull)
                .flatMap(section -> safe(section.getEntries()).stream()).filter(Objects::nonNull)
                .flatMap(entry -> java.util.stream.Stream.of(entry.getStartDate(), entry.getEndDate(), entry.getAwardDate()))
                .filter(Objects::nonNull).filter(WorkspaceSourceReferenceAssemblerImpl::hasText).map(WorkspaceSourceReferenceAssemblerImpl::normalized).toList();
        if (dates.size() > 2 && dates.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .values().stream().anyMatch(count -> count > 2)) {
            issues.add(issue("DUPLICATE_DATE_SUSPECTED", WARNING,
                    "多个位置出现相同日期，请确认不是结构恢复造成的重复。", List.of(), List.of()));
        }
    }

    private static int entryCount(ResumeDocumentDTO document, String kind) {
        return safe(document == null ? null : document.getSections()).stream().filter(Objects::nonNull)
                .filter(section -> kind.equalsIgnoreCase(section.getKind() == null ? "" : section.getKind()))
                .mapToInt(section -> safe(section.getEntries()).size()).sum();
    }

    private static FidelityIssue issue(String code, String severity, String message, List<String> sourceIds, List<String> targetIds) {
        return new FidelityIssue(code, severity, message, sourceIds, targetIds);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean canonicalId(String value) {
        return hasText(value) && value.equals(value.strip());
    }

    private static String safeId(String value) {
        return hasText(value) ? value : "unknown";
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static <T> List<T> immutableListAllowingNull(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static <T> Set<T> immutableSet(Set<T> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<String, List<String>> immutableAliases(Map<String, List<String>> aliases) {
        LinkedHashMap<String, List<String>> immutable = new LinkedHashMap<>();
        aliases.forEach((primary, ids) -> immutable.put(primary, List.copyOf(ids)));
        return Collections.unmodifiableMap(immutable);
    }

    private record Manifest(
            Map<String, List<String>> aliasesByPrimary,
            Map<String, String> aliasToPrimary,
            Map<String, String> texts,
            Set<String> knownIds,
            Map<String, ResumeSourceRefDTO> refs,
            boolean valid) {}
    private record ConfirmationState(Set<String> ids, boolean valid) {}
    private record FrozenOwnership(Map<String, FrozenOwner> owners, boolean valid) {}
    private record FrozenOwner(
            String nodeType, String sectionKind, String sectionId, String entryId, String bulletId,
            int depth, boolean eligible) {}
    private record Resolution(List<String> rawIds, List<String> primaryIds, boolean invalid) {}
    private record RestoreVerdict(WorkspaceSourceRestoreScope scope, boolean eligible, String reason) {
        static RestoreVerdict none() {
            return new RestoreVerdict(WorkspaceSourceRestoreScope.NONE, false, null);
        }
        static RestoreVerdict eligible(WorkspaceSourceRestoreScope scope) {
            return new RestoreVerdict(scope, true, null);
        }
        static RestoreVerdict blocked(WorkspaceSourceRestoreScope scope, String reason) {
            return new RestoreVerdict(scope, false, reason);
        }
    }
    private record PrimaryState(
            String primary, List<String> occurrenceIds, String text, SourceGeometry geometry,
            List<Node> inverse, WorkspaceSourceMappingStatus status, FrozenOwner owner,
            boolean omissionEligible, boolean omissionConfirmed,
            boolean ambiguousClaim) {}
    private record TargetIndex(
            Set<String> contactIds,
            Map<String, Integer> contactValues,
            Map<String, ResumeDocumentSectionDTO> sectionsById,
            Map<String, ResumeDocumentSectionDTO> sectionByEntryId,
            Set<String> bulletIds) {}
    private record RestoreContext(
            Manifest manifest,
            ResumeDocumentDTO source,
            TargetIndex targetIndex,
            Map<String, Node> targetIdentityIndex,
            Map<String, Node> frozenIdentityIndex) {}
    private record AuthenticationBoundary(
            String type, String sectionKind, String sectionId, String entryId, String bulletId) {}
    private record Node(String id, String type, String sectionKind, String sectionId, String entryId,
                        String bulletId, String text, List<String> occurrenceIds, int depth) {}
}
