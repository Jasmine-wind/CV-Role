package com.winter.airesumeoptimizer.module.workspace.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.enums.WorkspaceSourceMappingStatus;
import com.winter.airesumeoptimizer.module.workspace.service.WorkspaceSourceReferenceAssembler;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO.FidelityIssue;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO.SourceBlock;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO.SourceGeometry;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO.TargetMapping;
import java.util.ArrayList;
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
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Occurrence-ID-only implementation; ambiguous and dangling links fail closed. */
@Component
public class WorkspaceSourceReferenceAssemblerImpl implements WorkspaceSourceReferenceAssembler {

    private static final String BLOCKER = "BLOCKER";
    private static final String WARNING = "WARNING";

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
        Map<String, Node> authenticatedNodes = sourceNodes.stream()
                .collect(Collectors.toMap(WorkspaceSourceReferenceAssemblerImpl::identity, Function.identity(), (left, right) -> left));
        FrozenOwnership sourceOwnership = frozenOwners(manifest, sourceNodes);
        if (!sourceOwnership.valid()) {
            issues.add(issue("SOURCE_MANIFEST_INVALID", BLOCKER,
                    "冻结原文归属关系不完整，无法安全建立定位关系。", List.of(), List.of()));
        }
        Map<String, FrozenOwner> sourceOwners = sourceOwnership.owners();

        Map<String, List<Node>> sourceTargets = deepestTargets(
                manifest, nodes, authenticatedNodes, sourceOwners);
        List<TargetMapping> mappings = new ArrayList<>();
        for (Node node : nodes) {
            Resolution resolution = resolve(node.occurrenceIds(), manifest);
            boolean lineageMismatch = !authenticatedLineage(node, resolution, manifest, authenticatedNodes);
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

        List<SourceBlock> blocks = new ArrayList<>();
        int order = 0;
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
            boolean omissionEligible = status == WorkspaceSourceMappingStatus.UNMAPPED
                    && manifest.valid() && sourceOwnership.valid()
                    && owner != null && owner.eligible()
                    && !hasAmbiguousTargetFor(primary, nodes, manifest, authenticatedNodes);
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
            blocks.add(new SourceBlock(primary, order++, manifest.texts().get(primary), entry.getValue(),
                    sourceGeometryFor(primary, entry.getValue(), manifest.refs()),
                    inverse.stream().map(Node::id).toList(), status, inverse.size() == 1,
                    owner == null ? null : owner.nodeType(), owner == null ? null : owner.sectionKind(),
                    owner == null ? null : owner.sectionId(), owner == null ? null : owner.entryId(),
                    owner == null ? null : owner.bulletId(), omissionConfirmed, omissionEligible));
        }

        addStructuralIssues(source, target, blocks, issues);
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
        List<String> order = rawOrder.stream().filter(WorkspaceSourceReferenceAssemblerImpl::hasText)
                .map(String::strip).toList();
        Map<String, String> rawTexts = source == null || source.getSourceOccurrenceTexts() == null
                ? Map.of() : source.getSourceOccurrenceTexts();
        Map<String, String> rawPrimary = source == null || source.getSourceOccurrencePrimaryIds() == null
                ? Map.of() : source.getSourceOccurrencePrimaryIds();
        Map<String, ResumeSourceRefDTO> rawRefs = source == null || source.getSourceOccurrenceRefs() == null
                ? Map.of() : source.getSourceOccurrenceRefs();
        if (order.isEmpty() || rawTexts.isEmpty()) {
            issues.add(issue("SOURCE_MANIFEST_UNAVAILABLE", BLOCKER,
                    "该历史版本没有可验证的原文 occurrence 清单，无法证明结构完整性。", List.of(), List.of()));
            return new Manifest(Map.of(), Map.of(), Map.of(), Set.of(), Map.of(), false);
        }
        LinkedHashMap<String, List<String>> aliases = new LinkedHashMap<>();
        LinkedHashMap<String, String> aliasToPrimary = new LinkedHashMap<>();
        LinkedHashMap<String, String> texts = new LinkedHashMap<>();
        LinkedHashMap<String, ResumeSourceRefDTO> refs = new LinkedHashMap<>();
        Set<String> known = new LinkedHashSet<>(order);
        boolean invalid = rawOrder.size() != order.size()
                || known.size() != order.size()
                || !rawTexts.keySet().equals(known)
                || !rawPrimary.keySet().equals(known)
                || rawRefs.keySet().stream().anyMatch(id -> !known.contains(id));
        for (String occurrenceId : order) {
            String primary = rawPrimary.get(occurrenceId);
            String text = rawTexts.get(occurrenceId);
            if (!hasText(primary) || !known.contains(primary.strip()) || !hasText(text)) {
                invalid = true;
                continue;
            }
            primary = primary.strip();
            String primaryRoot = rawPrimary.get(primary);
            if (!hasText(primaryRoot) || !primary.equals(primaryRoot.strip())) {
                invalid = true;
                continue;
            }
            String primaryText = rawTexts.get(primary);
            if (!hasText(primaryText) || !normalized(primaryText).equals(normalized(text))) {
                invalid = true;
                continue;
            }
            aliases.computeIfAbsent(primary, ignored -> new ArrayList<>()).add(occurrenceId);
            if (aliasToPrimary.putIfAbsent(occurrenceId, primary) != null) {
                invalid = true;
            }
            texts.putIfAbsent(primary, primaryText);
            ResumeSourceRefDTO ref = rawRefs.get(occurrenceId);
            if (ref != null) {
                boolean authenticRef = ref.getSourceOccurrenceIds() != null
                        && ref.getSourceOccurrenceIds().stream().filter(Objects::nonNull)
                        .map(String::strip).anyMatch(occurrenceId::equals)
                        && normalized(ref.getText()).equals(normalized(text));
                if (authenticRef) {
                    refs.put(occurrenceId, ref);
                } else {
                    invalid = true;
                }
            }
        }
        if (aliasToPrimary.size() != known.size()) {
            invalid = true;
        }
        if (invalid) {
            issues.add(issue("SOURCE_MANIFEST_INVALID", BLOCKER,
                    "冻结原文清单不完整，无法安全建立定位关系。", List.of(), List.of()));
        }
        return new Manifest(aliases, aliasToPrimary, texts, known, refs, !invalid);
    }

    private static ConfirmationState confirmations(
            ResumeDocumentDTO target, Manifest manifest, List<FidelityIssue> issues) {
        List<String> raw = target == null || target.getConfirmedSourceOmissionIds() == null
                ? List.of() : target.getConfirmedSourceOmissionIds();
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        boolean valid = manifest.valid();
        for (String id : raw) {
            if (!hasText(id) || !manifest.knownIds().contains(id.strip()) || !ids.add(id.strip())) {
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

    private static FrozenOwnership frozenOwners(Manifest manifest, List<Node> sourceNodes) {
        Map<String, FrozenOwner> result = new HashMap<>();
        boolean valid = sourceNodes.stream().map(Node::occurrenceIds)
                .map(ids -> resolve(ids, manifest)).noneMatch(Resolution::invalid);
        Set<String> identities = new LinkedHashSet<>();
        if (sourceNodes.stream().map(WorkspaceSourceReferenceAssemblerImpl::identity)
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
            if (deepest.size() > 1) {
                // One physical contact line can legitimately materialize as several typed contact
                // fields. It remains a SPLIT relationship and is not omission-eligible, but does
                // not make the frozen manifest corrupt. Other duplicate deepest owners are invalid.
                if (deepest.stream().anyMatch(node -> !"CONTACT".equals(node.type()))) {
                    valid = false;
                }
                continue;
            }
            if (deepest.size() != 1 || !hasText(manifest.texts().get(primary))) {
                continue;
            }
            Node node = deepest.get(0);
            boolean eligible = Set.of("SECTION", "ENTRY", "BULLET", "BASICS", "CONTACT").contains(node.type());
            result.put(primary, new FrozenOwner(
                    node.type(), node.sectionKind(), node.sectionId(), node.entryId(), node.bulletId(),
                    node.depth(), eligible));
        }
        return new FrozenOwnership(Map.copyOf(result), valid);
    }

    private static boolean hasAmbiguousTargetFor(
            String primary, List<Node> nodes, Manifest manifest, Map<String, Node> authenticatedNodes) {
        for (Node node : nodes) {
            Resolution resolution = resolve(node.occurrenceIds(), manifest);
            if (resolution.primaryIds().contains(primary)
                    && (resolution.invalid() || !authenticatedLineage(node, resolution, manifest, authenticatedNodes))) {
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

    private static boolean authenticatedLineage(
            Node node, Resolution resolution, Manifest manifest, Map<String, Node> authenticatedNodes) {
        if (resolution.primaryIds().isEmpty()) return true;
        Node frozenNode = authenticatedNodes.get(identity(node));
        if (frozenNode == null) return false;
        Resolution frozen = resolve(frozenNode.occurrenceIds(), manifest);
        return !frozen.invalid() && new LinkedHashSet<>(frozen.primaryIds())
                .equals(new LinkedHashSet<>(resolution.primaryIds()));
    }

    private static String identity(Node node) {
        if (node == null) return "UNKNOWN";
        String sectionKind = normalized(node.sectionKind());
        return switch (node.type()) {
            case "SECTION" -> "SECTION:" + sectionKind + "/" + node.sectionId();
            case "ENTRY" -> "ENTRY:" + sectionKind + "/" + node.sectionId() + "/" + node.entryId();
            case "BULLET" -> "BULLET:" + sectionKind + "/" + node.sectionId() + "/" + node.entryId() + "/" + node.bulletId();
            default -> node.id();
        };
    }

    private static Resolution resolve(List<String> values, Manifest manifest) {
        List<String> raw = values == null ? List.of() : values.stream()
                .filter(WorkspaceSourceReferenceAssemblerImpl::hasText).map(String::strip).distinct().toList();
        LinkedHashSet<String> primary = new LinkedHashSet<>();
        boolean invalid = false;
        for (String id : raw) {
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
        if (ids != null && ids.stream().anyMatch(WorkspaceSourceReferenceAssemblerImpl::hasText)) return ids;
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
        LinkedHashSet<String> ids = new LinkedHashSet<>(safe(entry.getSourceOccurrenceIds()));
        addRefOccurrenceIds(ids, entry.getSourceRef());
        if (entry.getFieldSourceRefs() != null) {
            entry.getFieldSourceRefs().values().forEach(ref -> addRefOccurrenceIds(ids, ref));
        }
        safe(entry.getTechStackSourceRefs()).forEach(ref -> addRefOccurrenceIds(ids, ref));
        safe(entry.getSkillItemSourceRefs()).forEach(ref -> addRefOccurrenceIds(ids, ref));
        safe(entry.getSkillDescriptionSourceRefs()).forEach(ref -> addRefOccurrenceIds(ids, ref));
        ids.removeIf(id -> !hasText(id));
        return ids;
    }

    private static LinkedHashSet<String> projectEntryOccurrenceIds(ResumeDocumentEntryDTO entry) {
        LinkedHashSet<String> ids = entryOwnOccurrenceIds(entry);
        for (ResumeDocumentBulletDTO bullet : safe(entry.getBullets())) {
            if (bullet == null) continue;
            ids.addAll(safe(bullet.getSourceOccurrenceIds()));
            addRefOccurrenceIds(ids, bullet.getSourceRef());
        }
        ids.removeIf(id -> !hasText(id));
        return ids;
    }

    private static void addRefOccurrenceIds(Set<String> ids, ResumeSourceRefDTO ref) {
        if (ref != null) ids.addAll(safe(ref.getSourceOccurrenceIds()));
    }

    private static void addStructuralIssues(
            ResumeDocumentDTO source,
            ResumeDocumentDTO target,
            List<SourceBlock> sourceBlocks,
            List<FidelityIssue> issues) {
        for (ResumeDocumentSectionDTO section : safe(target == null ? null : target.getSections())) {
            if (section != null && !hasText(section.getTitle()) && !safe(section.getEntries()).isEmpty()) {
                issues.add(issue("SECTION_HEADING_LOST", BLOCKER,
                        "章节标题为空但子内容仍存在，导出已阻止。", List.of(), List.of("section:" + safeId(section.getId()))));
            }
        }
        Map<String, SourceBlock> blocksByOccurrence = new HashMap<>();
        for (SourceBlock block : sourceBlocks) {
            blocksByOccurrence.put(block.id(), block);
            for (String occurrenceId : block.occurrenceIds()) {
                blocksByOccurrence.put(occurrenceId, block);
            }
        }
        Set<String> targetProjectEntries = safe(target == null ? null : target.getSections()).stream()
                .filter(Objects::nonNull)
                .filter(section -> "PROJECT".equalsIgnoreCase(section.getKind() == null ? "" : section.getKind()))
                .flatMap(section -> safe(section.getEntries()).stream()
                        .filter(Objects::nonNull)
                        .map(entry -> safeId(section.getId()) + "/" + safeId(entry.getId())))
                .collect(Collectors.toSet());
        List<String> unconfirmedMissingProjectOccurrences = new ArrayList<>();
        boolean unconfirmedMissingProject = false;
        for (ResumeDocumentSectionDTO section : safe(source == null ? null : source.getSections())) {
            if (section == null || !"PROJECT".equalsIgnoreCase(section.getKind() == null ? "" : section.getKind())) {
                continue;
            }
            for (ResumeDocumentEntryDTO entry : safe(section.getEntries())) {
                if (entry == null || targetProjectEntries.contains(safeId(section.getId()) + "/" + safeId(entry.getId()))) {
                    continue;
                }
                LinkedHashSet<String> meaningful = projectEntryOccurrenceIds(entry);
                boolean allConfirmed = !meaningful.isEmpty() && meaningful.stream().allMatch(id -> {
                    SourceBlock block = blocksByOccurrence.get(id);
                    return block != null && block.omissionEligible() && block.omissionConfirmed();
                });
                if (!allConfirmed) {
                    unconfirmedMissingProject = true;
                    unconfirmedMissingProjectOccurrences.addAll(meaningful);
                }
            }
        }
        if (unconfirmedMissingProject) {
            issues.add(issue("PROJECT_BOUNDARY_LOST", BLOCKER,
                    "冻结项目条目缺失且尚未完整确认省略，可能发生项目边界合并。",
                    unconfirmedMissingProjectOccurrences.stream().distinct().toList(), List.of()));
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

    private static String safeId(String value) {
        return hasText(value) ? value.strip() : "unknown";
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
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
    private record Node(String id, String type, String sectionKind, String sectionId, String entryId,
                        String bulletId, String text, List<String> occurrenceIds, int depth) {}
}
