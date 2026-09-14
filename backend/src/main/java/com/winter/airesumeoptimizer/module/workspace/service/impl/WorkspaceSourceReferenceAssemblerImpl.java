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
        List<Node> sourceNodes = flatten(source);
        List<Node> nodes = flatten(target);
        Map<String, Node> authenticatedNodes = sourceNodes.stream()
                .collect(Collectors.toMap(WorkspaceSourceReferenceAssemblerImpl::identity, Function.identity(), (left, right) -> left));

        Map<String, List<Node>> sourceTargets = deepestTargets(manifest, nodes, authenticatedNodes);
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
            boolean textChanged = resolution.primaryIds().size() == 1
                    && !normalized(node.text()).equals(normalized(manifest.texts().get(resolution.primaryIds().get(0))));
            mappings.add(new TargetMapping(
                    node.id(), node.type(), node.sectionId(), node.entryId(), node.bulletId(), node.text(),
                    resolution.primaryIds(), status, reliable, textChanged));
        }

        List<SourceBlock> blocks = new ArrayList<>();
        int order = 0;
        for (Map.Entry<String, List<String>> entry : manifest.aliasesByPrimary().entrySet()) {
            String primary = entry.getKey();
            List<Node> inverse = sourceTargets.getOrDefault(primary, List.of());
            WorkspaceSourceMappingStatus status = inverse.isEmpty()
                    ? WorkspaceSourceMappingStatus.UNMAPPED
                    : inverse.size() == 1 ? WorkspaceSourceMappingStatus.EXACT : WorkspaceSourceMappingStatus.SPLIT;
            if (inverse.isEmpty()) {
                issues.add(issue("SOURCE_CONTENT_UNMAPPED", BLOCKER,
                        "冻结原文仍有内容未进入当前结构，导出已阻止。", entry.getValue(), List.of()));
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
                    inverse.stream().map(Node::id).toList(), status, inverse.size() == 1));
        }

        addStructuralIssues(source, target, issues);
        EnumMap<WorkspaceSourceMappingStatus, Integer> counts = new EnumMap<>(WorkspaceSourceMappingStatus.class);
        for (WorkspaceSourceMappingStatus status : WorkspaceSourceMappingStatus.values()) counts.put(status, 0);
        for (TargetMapping mapping : mappings) counts.compute(mapping.status(), (key, value) -> value == null ? 1 : value + 1);
        boolean blocked = issues.stream().anyMatch(FidelityIssue::blocker);
        return new WorkspaceSourceReferenceVO(taskId, sourceVersionId, targetVersionId, targetRevision,
                sourceFilename, sourcePdfAvailable, blocks, mappings, issues, counts, blocked);
    }

    private static Manifest manifest(ResumeDocumentDTO source, List<FidelityIssue> issues) {
        List<String> order = source == null || source.getSourceOccurrenceIds() == null
                ? List.of() : source.getSourceOccurrenceIds().stream().filter(WorkspaceSourceReferenceAssemblerImpl::hasText).toList();
        Map<String, String> rawTexts = source == null || source.getSourceOccurrenceTexts() == null
                ? Map.of() : source.getSourceOccurrenceTexts();
        Map<String, String> rawPrimary = source == null || source.getSourceOccurrencePrimaryIds() == null
                ? Map.of() : source.getSourceOccurrencePrimaryIds();
        Map<String, ResumeSourceRefDTO> rawRefs = source == null || source.getSourceOccurrenceRefs() == null
                ? Map.of() : source.getSourceOccurrenceRefs();
        if (order.isEmpty() || rawTexts.isEmpty()) {
            issues.add(issue("SOURCE_MANIFEST_UNAVAILABLE", BLOCKER,
                    "该历史版本没有可验证的原文 occurrence 清单，无法证明结构完整性。", List.of(), List.of()));
            return new Manifest(Map.of(), Map.of(), Set.of(), Map.of());
        }
        LinkedHashMap<String, List<String>> aliases = new LinkedHashMap<>();
        LinkedHashMap<String, String> texts = new LinkedHashMap<>();
        LinkedHashMap<String, ResumeSourceRefDTO> refs = new LinkedHashMap<>();
        Set<String> known = new LinkedHashSet<>(order);
        boolean invalid = false;
        for (String occurrenceId : order) {
            String primary = rawPrimary.getOrDefault(occurrenceId, occurrenceId);
            if (!hasText(primary) || !rawTexts.containsKey(occurrenceId)) {
                invalid = true;
                continue;
            }
            aliases.computeIfAbsent(primary, ignored -> new ArrayList<>()).add(occurrenceId);
            texts.putIfAbsent(primary, rawTexts.get(occurrenceId));
            known.add(primary);
            ResumeSourceRefDTO ref = rawRefs.get(occurrenceId);
            if (ref != null) {
                boolean authenticRef = ref.getSourceOccurrenceIds() != null
                        && ref.getSourceOccurrenceIds().stream().filter(Objects::nonNull)
                        .map(String::strip).anyMatch(occurrenceId::equals)
                        && normalized(ref.getText()).equals(normalized(rawTexts.get(occurrenceId)));
                if (authenticRef) {
                    refs.put(occurrenceId, ref);
                } else {
                    invalid = true;
                }
            }
        }
        if (rawRefs.keySet().stream().anyMatch(id -> !order.contains(id))) {
            invalid = true;
        }
        if (invalid) {
            issues.add(issue("SOURCE_MANIFEST_INVALID", BLOCKER,
                    "冻结原文清单不完整，无法安全建立定位关系。", List.of(), List.of()));
        }
        return new Manifest(aliases, texts, known, refs);
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
            Manifest manifest, List<Node> nodes, Map<String, Node> authenticatedNodes) {
        Map<String, List<Node>> result = new HashMap<>();
        for (String primary : manifest.aliasesByPrimary().keySet()) {
            List<Node> candidates = nodes.stream()
                    .filter(node -> {
                        Resolution resolution = resolve(node.occurrenceIds(), manifest);
                        return !resolution.invalid()
                                && authenticatedLineage(node, resolution, manifest, authenticatedNodes)
                                && resolution.primaryIds().contains(primary);
                    })
                    .toList();
            int depth = candidates.stream().mapToInt(Node::depth).max().orElse(-1);
            result.put(primary, candidates.stream().filter(node -> node.depth() == depth).toList());
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
        return switch (node.type()) {
            case "SECTION" -> "SECTION:" + node.sectionId();
            case "ENTRY" -> "ENTRY:" + node.sectionId() + "/" + node.entryId();
            case "BULLET" -> "BULLET:" + node.sectionId() + "/" + node.entryId() + "/" + node.bulletId();
            default -> node.id();
        };
    }

    private static Resolution resolve(List<String> values, Manifest manifest) {
        List<String> raw = values == null ? List.of() : values.stream()
                .filter(WorkspaceSourceReferenceAssemblerImpl::hasText).map(String::strip).distinct().toList();
        LinkedHashSet<String> primary = new LinkedHashSet<>();
        boolean invalid = false;
        Map<String, String> aliases = new HashMap<>();
        manifest.aliasesByPrimary().forEach((key, ids) -> {
            aliases.put(key, key);
            ids.forEach(id -> aliases.put(id, key));
        });
        for (String id : raw) {
            String resolved = aliases.get(id);
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
            addField(nodes, "basics:name", "BASICS", null, null, null, basics.getName(), refIds(basics.getFieldSourceRefs(), "name"), 3);
            addField(nodes, "basics:jobIntention", "BASICS", null, null, null, basics.getJobIntention(), refIds(basics.getFieldSourceRefs(), "jobIntention"), 3);
            addField(nodes, "basics:highestEducation", "BASICS", null, null, null, basics.getHighestEducation(), refIds(basics.getFieldSourceRefs(), "highestEducation"), 3);
            for (ResumeDocumentContactDTO contact : safe(basics.getContacts())) {
                if (contact != null) addField(nodes, "contact:" + safeId(contact.getId()), "CONTACT", null, null, null,
                        contact.getValue(), firstIds(contact.getSourceOccurrenceIds(), contact.getSourceRef()), 3);
            }
        }
        for (ResumeDocumentSectionDTO section : safe(document.getSections())) {
            if (section == null) continue;
            String sectionId = safeId(section.getId());
            addField(nodes, "section:" + sectionId, "SECTION", sectionId, null, null, section.getTitle(),
                    firstIds(section.getSourceOccurrenceIds(), section.getSourceRef()), 1);
            for (ResumeDocumentEntryDTO entry : safe(section.getEntries())) {
                if (entry == null) continue;
                String entryId = safeId(entry.getId());
                addField(nodes, "section:" + sectionId + "/entry:" + entryId, "ENTRY", sectionId, entryId, null,
                        entryText(entry), firstIds(entry.getSourceOccurrenceIds(), entry.getSourceRef()), 2);
                for (ResumeDocumentBulletDTO bullet : safe(entry.getBullets())) {
                    if (bullet == null) continue;
                    String bulletId = safeId(bullet.getId());
                    addField(nodes, "section:" + sectionId + "/entry:" + entryId + "/bullet:" + bulletId,
                            "BULLET", sectionId, entryId, bulletId, bullet.getText(),
                            firstIds(bullet.getSourceOccurrenceIds(), bullet.getSourceRef()), 3);
                }
            }
        }
        return nodes;
    }

    private static void addField(List<Node> nodes, String id, String type, String sectionId, String entryId,
            String bulletId, String text, List<String> ids, int depth) {
        if (hasText(text) || !ids.isEmpty()) nodes.add(new Node(id, type, sectionId, entryId, bulletId, text, ids, depth));
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

    private static void addStructuralIssues(ResumeDocumentDTO source, ResumeDocumentDTO target, List<FidelityIssue> issues) {
        for (ResumeDocumentSectionDTO section : safe(target == null ? null : target.getSections())) {
            if (section != null && !hasText(section.getTitle()) && !safe(section.getEntries()).isEmpty()) {
                issues.add(issue("SECTION_HEADING_LOST", BLOCKER,
                        "章节标题为空但子内容仍存在，导出已阻止。", List.of(), List.of("section:" + safeId(section.getId()))));
            }
        }
        int sourceProjects = entryCount(source, "PROJECT");
        int targetProjects = entryCount(target, "PROJECT");
        if (sourceProjects > 1 && targetProjects < sourceProjects) {
            issues.add(issue("PROJECT_BOUNDARY_LOST", BLOCKER,
                    "项目条目数量少于冻结原文，可能发生项目边界合并。", List.of(), List.of()));
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
            Map<String, String> texts,
            Set<String> knownIds,
            Map<String, ResumeSourceRefDTO> refs) {}
    private record Resolution(List<String> rawIds, List<String> primaryIds, boolean invalid) {}
    private record Node(String id, String type, String sectionId, String entryId, String bulletId,
                        String text, List<String> occurrenceIds, int depth) {}
}
