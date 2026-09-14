package com.winter.airesumeoptimizer.module.workspace.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentContactType;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentSectionKind;
import com.winter.airesumeoptimizer.module.workspace.service.ResumeDocumentConverter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * RESUME_DOCUMENT_V1 归一化与历史 generic V1 文档的只读兼容升级。
 *
 * <p>编辑上限是显式约束：超长内容直接拒绝保存而不是静默截断，避免丢失用户内容。
 * Slice A 之前保存的 generic V1 文档按确定性规则升级为同一 V1 语义形态；
 * 无法安全升级时显式失败并引导重新解析，不做降级产出。
 */
@Service
public class ResumeDocumentConverterImpl implements ResumeDocumentConverter {

    private static final int MAX_CONTACTS = 20;
    private static final int MAX_SECTIONS = 30;
    private static final int MAX_ENTRIES_PER_SECTION = 100;
    private static final int MAX_BULLETS_PER_ENTRY = 100;
    private static final int MAX_SKILL_ITEMS_PER_ENTRY = 100;
    private static final int NAME_MAX_LENGTH = 100;
    private static final int BASICS_FIELD_MAX_LENGTH = 200;
    private static final int CONTACT_FIELD_MAX_LENGTH = 200;
    private static final int SECTION_TITLE_MAX_LENGTH = 100;
    private static final int ENTRY_FIELD_MAX_LENGTH = 200;
    private static final int SKILL_ITEM_MAX_LENGTH = 200;
    private static final int BULLET_MAX_LENGTH = 4000;
    private static final Set<String> PROVENANCE_FIELDS = Set.of(
            "sourceRef", "sourceOccurrenceIds", "sourceOccurrenceTexts",
            "sourceOccurrencePrimaryIds", "sourceOccurrenceRefs", "confirmedSourceOmissionIds", "fieldSourceRefs",
            "techStackSourceRefs", "skillItemSourceRefs", "skillDescriptionSourceRefs");

    private static final Pattern EMAIL_VALUE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_VALUE = Pattern.compile("^\\+?[0-9][0-9\\s\\-()]{5,19}$");
    private static final Pattern URL_VALUE = Pattern.compile("^(https?://|www\\.).+", Pattern.CASE_INSENSITIVE);
    /** 日期区间：2022.07 - 至今 / 2018年9月-2022年6月 / 2018-2022 等原文形态。 */
    private static final Pattern DATE_RANGE = Pattern.compile(
            "((?:19|20)\\d{2}(?:\\s*[年./\\-]\\s*\\d{1,2}\\s*月?)?)"
                    + "\\s*(?:[-–—~～至到]+|[-–—~～])\\s*"
                    + "((?:19|20)\\d{2}(?:\\s*[年./\\-]\\s*\\d{1,2}\\s*月?)?|至今|今|现在|present)");
    private static final Pattern STANDALONE_DATE = Pattern.compile(
            "(?i)^(?:(?:19|20)\\d{2}(?:\\s*[年./\\-]\\s*\\d{1,2}\\s*月?)?|至今|今|现在|present)\\s*$");

    private final ObjectMapper objectMapper;
    private final ObjectMapper strictObjectMapper;

    public ResumeDocumentConverterImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.strictObjectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    }

    @Override
    public ResumeDocumentDTO normalize(ResumeDocumentDTO document) {
        if (document == null) {
            throw new BusinessException(400, "简历内容不能为空");
        }
        String schemaVersion = document.getSchemaVersion();
        if (!ResumeDocumentDTO.SCHEMA_VERSION.equals(schemaVersion)) {
            throw new BusinessException(400, "不支持的简历内容格式");
        }

        IdAllocator idAllocator = new IdAllocator();
        return ResumeDocumentDTO.builder()
                .schemaVersion(schemaVersion)
                .sourceRef(copySourceRef(document.getSourceRef()))
                .sourceOccurrenceIds(copyStrings(document.getSourceOccurrenceIds()))
                .sourceOccurrenceTexts(copySourceOccurrenceTexts(document.getSourceOccurrenceTexts()))
                .sourceOccurrencePrimaryIds(copySourceOccurrenceTexts(document.getSourceOccurrencePrimaryIds()))
                .sourceOccurrenceRefs(copySourceOccurrenceRefs(document.getSourceOccurrenceRefs()))
                .confirmedSourceOmissionIds(copyStrings(document.getConfirmedSourceOmissionIds()))
                .basics(normalizeBasics(document.getBasics(), idAllocator))
                .sections(normalizeSections(document.getSections(), idAllocator))
                .build();
    }

    @Override
    public ResumeDocumentDTO normalizeWorkspaceSave(
            ResumeDocumentDTO submitted,
            ResumeDocumentDTO currentTarget,
            ResumeDocumentDTO frozenSource) {
        if (submitted == null || currentTarget == null || frozenSource == null) {
            throw new BusinessException(400, "简历内容不能为空");
        }
        ResumeDocumentDTO candidate = withoutSubmittedProvenance(submitted);
        Map<String, NodeLocation> currentTopology = topology(currentTarget);
        FrozenNodes frozen = frozenNodes(frozenSource);

        restoreRootProvenance(candidate, frozenSource);
        // Intentional omission is task-local server state. A normal PUT cannot add/remove it.
        candidate.setConfirmedSourceOmissionIds(copyStrings(currentTarget.getConfirmedSourceOmissionIds()));
        restoreBasicsProvenance(candidate.getBasics(), frozenSource.getBasics());
        for (ResumeDocumentContactDTO contact : safeList(candidate.getBasics() == null
                ? null : candidate.getBasics().getContacts())) {
            NodeLocation contactLocation = new NodeLocation("CONTACT", "BASICS");
            rejectFrozenIdResurrection(contact.getId(), contactLocation, currentTopology, frozen.topology());
            if (existingAt(contact.getId(), contactLocation, currentTopology)
                    && frozenAt(contact.getId(), contactLocation, frozen.topology())) {
                restoreContactProvenance(contact, frozen.contacts().get(contact.getId()));
            }
        }
        for (ResumeDocumentSectionDTO section : safeList(candidate.getSections())) {
            NodeLocation sectionLocation = new NodeLocation("SECTION", "ROOT:" + section.getKind());
            rejectFrozenIdResurrection(section.getId(), sectionLocation, currentTopology, frozen.topology());
            boolean existingSection = existingAt(section.getId(), sectionLocation, currentTopology);
            ResumeDocumentSectionDTO frozenSection = existingSection
                    && frozenAt(section.getId(), sectionLocation, frozen.topology())
                    ? frozen.sections().get(section.getId()) : null;
            restoreSectionProvenance(section, frozenSection);
            for (ResumeDocumentEntryDTO entry : safeList(section.getEntries())) {
                NodeLocation entryLocation = new NodeLocation("ENTRY", "SECTION:" + section.getId());
                rejectFrozenIdResurrection(entry.getId(), entryLocation, currentTopology, frozen.topology());
                boolean existingEntry = existingAt(entry.getId(), entryLocation, currentTopology);
                ResumeDocumentEntryDTO frozenEntry = existingEntry
                        && frozenAt(entry.getId(), entryLocation, frozen.topology())
                        ? frozen.entries().get(entry.getId()) : null;
                restoreEntryProvenance(entry, frozenEntry);
                for (ResumeDocumentBulletDTO bullet : safeList(entry.getBullets())) {
                    NodeLocation bulletLocation = new NodeLocation(
                            "BULLET", "SECTION:" + section.getId() + "/ENTRY:" + entry.getId());
                    rejectFrozenIdResurrection(bullet.getId(), bulletLocation, currentTopology, frozen.topology());
                    if (existingAt(bullet.getId(), bulletLocation, currentTopology)
                            && frozenAt(bullet.getId(), bulletLocation, frozen.topology())) {
                        restoreBulletProvenance(bullet, frozen.bullets().get(bullet.getId()));
                    }
                }
            }
        }
        return normalize(candidate);
    }

    private ResumeDocumentDTO withoutSubmittedProvenance(ResumeDocumentDTO submitted) {
        try {
            JsonNode tree = objectMapper.valueToTree(submitted);
            removeProvenance(tree);
            return strictObjectMapper.treeToValue(tree, ResumeDocumentDTO.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException(400, "简历内容格式不正确");
        }
    }

    private void removeProvenance(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node instanceof ObjectNode object) {
            PROVENANCE_FIELDS.forEach(object::remove);
            object.elements().forEachRemaining(this::removeProvenance);
            return;
        }
        if (node.isArray()) {
            node.elements().forEachRemaining(this::removeProvenance);
        }
    }

    private Map<String, NodeLocation> topology(ResumeDocumentDTO document) {
        Map<String, NodeLocation> result = new LinkedHashMap<>();
        for (ResumeDocumentContactDTO contact : safeList(document == null || document.getBasics() == null
                ? null : document.getBasics().getContacts())) {
            addLocation(result, contact == null ? null : contact.getId(), new NodeLocation("CONTACT", "BASICS"));
        }
        for (ResumeDocumentSectionDTO section : safeList(document == null ? null : document.getSections())) {
            if (section == null) {
                continue;
            }
            addLocation(result, section.getId(), new NodeLocation("SECTION", "ROOT:" + section.getKind()));
            for (ResumeDocumentEntryDTO entry : safeList(section.getEntries())) {
                if (entry == null) {
                    continue;
                }
                addLocation(result, entry.getId(), new NodeLocation("ENTRY", "SECTION:" + section.getId()));
                for (ResumeDocumentBulletDTO bullet : safeList(entry.getBullets())) {
                    addLocation(result, bullet == null ? null : bullet.getId(), new NodeLocation(
                            "BULLET", "SECTION:" + section.getId() + "/ENTRY:" + entry.getId()));
                }
            }
        }
        return result;
    }

    private void addLocation(Map<String, NodeLocation> locations, String id, NodeLocation location) {
        if (id == null || id.isBlank()) {
            return;
        }
        NodeLocation previous = locations.putIfAbsent(id, location);
        if (previous != null) {
            throw new BusinessException(400, "简历节点 ID 重复");
        }
    }

    private boolean existingAt(String id, NodeLocation submitted, Map<String, NodeLocation> current) {
        if (id == null || id.isBlank()) {
            return false;
        }
        NodeLocation server = current.get(id);
        if (server == null) {
            return false;
        }
        if (!server.equals(submitted)) {
            throw new BusinessException(400, "简历节点不能改变原有归属");
        }
        return true;
    }

    private boolean frozenAt(String id, NodeLocation expected, Map<String, NodeLocation> frozenTopology) {
        NodeLocation frozen = id == null ? null : frozenTopology.get(id);
        if (frozen == null) {
            return false;
        }
        if (!frozen.equals(expected)) {
            throw new BusinessException(400, "简历节点来源归属不一致");
        }
        return true;
    }

    private FrozenNodes frozenNodes(ResumeDocumentDTO source) {
        Map<String, ResumeDocumentContactDTO> contacts = new LinkedHashMap<>();
        Map<String, ResumeDocumentSectionDTO> sections = new LinkedHashMap<>();
        Map<String, ResumeDocumentEntryDTO> entries = new LinkedHashMap<>();
        Map<String, ResumeDocumentBulletDTO> bullets = new LinkedHashMap<>();
        for (ResumeDocumentContactDTO contact : safeList(source == null || source.getBasics() == null
                ? null : source.getBasics().getContacts())) {
            if (contact != null && contact.getId() != null) contacts.put(contact.getId(), contact);
        }
        for (ResumeDocumentSectionDTO section : safeList(source == null ? null : source.getSections())) {
            if (section == null) continue;
            if (section.getId() != null) sections.put(section.getId(), section);
            for (ResumeDocumentEntryDTO entry : safeList(section.getEntries())) {
                if (entry == null) continue;
                if (entry.getId() != null) entries.put(entry.getId(), entry);
                for (ResumeDocumentBulletDTO bullet : safeList(entry.getBullets())) {
                    if (bullet != null && bullet.getId() != null) bullets.put(bullet.getId(), bullet);
                }
            }
        }
        return new FrozenNodes(contacts, sections, entries, bullets, topology(source));
    }

    private void rejectFrozenIdResurrection(
            String id,
            NodeLocation expected,
            Map<String, NodeLocation> currentTopology,
            Map<String, NodeLocation> frozenTopology) {
        if (id == null || id.isBlank()) return;
        NodeLocation frozen = frozenTopology.get(id);
        if (frozen != null && frozen.equals(expected) && !currentTopology.containsKey(id)) {
            throw new BusinessException(400, "已删除的来源节点不能复用原 ID，请恢复优化前版本后重试");
        }
    }

    private void restoreRootProvenance(ResumeDocumentDTO target, ResumeDocumentDTO source) {
        target.setSourceRef(copySourceRef(source.getSourceRef()));
        target.setSourceOccurrenceIds(copyStrings(source.getSourceOccurrenceIds()));
        target.setSourceOccurrenceTexts(copySourceOccurrenceTexts(source.getSourceOccurrenceTexts()));
        target.setSourceOccurrencePrimaryIds(copySourceOccurrenceTexts(source.getSourceOccurrencePrimaryIds()));
        target.setSourceOccurrenceRefs(copySourceOccurrenceRefs(source.getSourceOccurrenceRefs()));
    }

    private void restoreBasicsProvenance(ResumeDocumentBasicsDTO target, ResumeDocumentBasicsDTO source) {
        if (target == null || source == null) return;
        target.setSourceRef(copySourceRef(source.getSourceRef()));
        target.setSourceOccurrenceIds(copyStrings(source.getSourceOccurrenceIds()));
        target.setFieldSourceRefs(copyFieldSourceRefs(source.getFieldSourceRefs()));
    }

    private void restoreContactProvenance(ResumeDocumentContactDTO target, ResumeDocumentContactDTO source) {
        if (target == null || source == null) return;
        target.setSourceRef(copySourceRef(source.getSourceRef()));
        target.setSourceOccurrenceIds(copyStrings(source.getSourceOccurrenceIds()));
    }

    private void restoreSectionProvenance(ResumeDocumentSectionDTO target, ResumeDocumentSectionDTO source) {
        if (target == null || source == null) return;
        target.setSourceRef(copySourceRef(source.getSourceRef()));
        target.setSourceOccurrenceIds(copyStrings(source.getSourceOccurrenceIds()));
    }

    private void restoreEntryProvenance(ResumeDocumentEntryDTO target, ResumeDocumentEntryDTO source) {
        if (target == null || source == null) return;
        target.setSourceRef(copySourceRef(source.getSourceRef()));
        target.setSourceOccurrenceIds(copyStrings(source.getSourceOccurrenceIds()));
        target.setFieldSourceRefs(copyFieldSourceRefs(source.getFieldSourceRefs()));
        target.setTechStackSourceRefs(Objects.equals(target.getTechStack(), source.getTechStack())
                ? copySourceRefs(source.getTechStackSourceRefs()) : null);
        target.setSkillItemSourceRefs(Objects.equals(target.getSkillItems(), source.getSkillItems())
                ? copySourceRefs(source.getSkillItemSourceRefs()) : null);
        target.setSkillDescriptionSourceRefs(Objects.equals(target.getSkillDescriptions(), source.getSkillDescriptions())
                ? copySourceRefs(source.getSkillDescriptionSourceRefs()) : null);
    }

    private void restoreBulletProvenance(ResumeDocumentBulletDTO target, ResumeDocumentBulletDTO source) {
        if (target == null || source == null) return;
        target.setSourceRef(copySourceRef(source.getSourceRef()));
        target.setSourceOccurrenceIds(copyStrings(source.getSourceOccurrenceIds()));
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record NodeLocation(String type, String parent) {}
    private record FrozenNodes(
            Map<String, ResumeDocumentContactDTO> contacts,
            Map<String, ResumeDocumentSectionDTO> sections,
            Map<String, ResumeDocumentEntryDTO> entries,
            Map<String, ResumeDocumentBulletDTO> bullets,
            Map<String, NodeLocation> topology) {}

    @Override
    public ResumeDocumentDTO upgradeLegacyDocument(String legacyJson) {
        if (legacyJson == null || legacyJson.isBlank()) {
            throw new BusinessException(500, "简历内容尚未就绪，请先完成简历解析");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(legacyJson);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历内容格式不正确，请重新解析");
        }
        if (root == null || !root.isObject()) {
            throw new BusinessException(500, "简历内容格式不正确，请重新解析");
        }
        String schemaVersion = textOrNull(root.path("schemaVersion"));
        if (!ResumeDocumentDTO.SCHEMA_VERSION.equals(schemaVersion)) {
            throw new BusinessException(500, "不支持的简历内容格式，请重新解析");
        }
        if (looksLikeSemanticDocument(root)) {
            try {
                // Historical semantic V1 snapshots may omit generated IDs or optional arrays.
                // Materialize only those harmless structural defaults before strict binding;
                // unknown fields and scalar type mismatches still fail closed.
                ResumeDocumentDTO parsed = strictObjectMapper.treeToValue(
                        withHistoricalOptionalArrayDefaults(root), ResumeDocumentDTO.class);
                return normalize(parsed);
            } catch (JsonProcessingException exception) {
                throw new BusinessException(500, "简历内容格式不正确，请重新解析");
            }
        }
        // 同一 V1 版本内的历史 generic shape：只读升级，不为它引入第二个 schema 版本。
        ResumeDocumentDTO upgraded = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .sourceRef(readSourceRef(root.path("sourceRef")))
                .sourceOccurrenceIds(readStringList(root.path("sourceOccurrenceIds"),
                        "简历来源 occurrence 格式不正确，请重新解析"))
                .sourceOccurrenceTexts(readSourceOccurrenceTexts(root.path("sourceOccurrenceTexts")))
                .sourceOccurrencePrimaryIds(readSourceOccurrenceTexts(root.path("sourceOccurrencePrimaryIds")))
                .sourceOccurrenceRefs(readSourceOccurrenceRefs(root.path("sourceOccurrenceRefs")))
                .confirmedSourceOmissionIds(readStringList(root.path("confirmedSourceOmissionIds"),
                        "确认省略 occurrence 格式不正确，请重新解析"))
                .basics(upgradeBasics(root))
                .sections(upgradeSections(root.path("sections")))
                .build();
        return normalize(upgraded);
    }

    private ResumeDocumentBasicsDTO normalizeBasics(ResumeDocumentBasicsDTO basics, IdAllocator idAllocator) {
        if (basics == null || basics.getContacts() == null) {
            throw new BusinessException(400, "简历基础信息格式不正确");
        }
        if (basics.getContacts().size() > MAX_CONTACTS) {
            throw new BusinessException(400, "基础信息字段数量超出编辑上限");
        }
        List<ResumeDocumentContactDTO> contacts = new ArrayList<>();
        Set<String> seenContactValues = new HashSet<>();
        for (ResumeDocumentContactDTO contact : basics.getContacts()) {
            if (contact == null || contact.getValue() == null) {
                throw new BusinessException(400, "简历基础信息格式不正确");
            }
            ResumeDocumentContactType type = ResumeDocumentContactType.fromValue(contact.getType());
            if (contact.getType() != null
                    && !contact.getType().isBlank()
                    && type == ResumeDocumentContactType.OTHER
                    && !ResumeDocumentContactType.OTHER.name().equalsIgnoreCase(contact.getType().strip())) {
                throw new BusinessException(400, "不支持的联系方式类型");
            }
            String label = contact.getLabel();
            if (label == null || label.isBlank()) {
                label = type.getDefaultLabel();
            }
            String value = requireWithinLength(
                    contact.getValue(), CONTACT_FIELD_MAX_LENGTH, "基础信息字段值超出编辑上限");
            String normalizedId = idAllocator.allocate(contact.getId());
            String contactKey = type.name() + "\u0000" + (value == null ? "" : value.strip());
            if (!seenContactValues.add(contactKey) && !hasContactProvenance(contact)) {
                // Keep the historical cleanup for two unreferenced compatibility rows, but
                // never collapse source-backed duplicate occurrences merely because their text
                // and type are equal.
                continue;
            }
            contacts.add(ResumeDocumentContactDTO.builder()
                    .id(normalizedId)
                    .type(type.name())
                    .label(requireWithinLength(label, CONTACT_FIELD_MAX_LENGTH, "基础信息字段名超出编辑上限"))
                    .value(value)
                    .sourceRef(copySourceRef(contact.getSourceRef()))
                    .sourceOccurrenceIds(copyStrings(contact.getSourceOccurrenceIds()))
                    .build());
        }
        return ResumeDocumentBasicsDTO.builder()
                .sourceRef(copySourceRef(basics.getSourceRef()))
                .sourceOccurrenceIds(copyStrings(basics.getSourceOccurrenceIds()))
                .fieldSourceRefs(copyFieldSourceRefs(basics.getFieldSourceRefs()))
                .name(requireWithinLength(basics.getName(), NAME_MAX_LENGTH, "姓名超出编辑上限"))
                .jobIntention(requireWithinLength(basics.getJobIntention(), BASICS_FIELD_MAX_LENGTH, "求职意向超出编辑上限"))
                .highestEducation(requireWithinLength(basics.getHighestEducation(), BASICS_FIELD_MAX_LENGTH, "最高学历超出编辑上限"))
                .contacts(contacts)
                .build();
    }

    private List<ResumeDocumentSectionDTO> normalizeSections(
            List<ResumeDocumentSectionDTO> sections, IdAllocator idAllocator) {
        List<ResumeDocumentSectionDTO> normalized = new ArrayList<>();
        if (sections == null) {
            throw new BusinessException(400, "简历章节格式不正确");
        }
        if (sections.size() > MAX_SECTIONS) {
            throw new BusinessException(400, "章节数量超出编辑上限");
        }
        for (ResumeDocumentSectionDTO section : sections) {
            if (section == null || section.getKind() == null || section.getTitle() == null) {
                throw new BusinessException(400, "简历章节格式不正确");
            }
            ResumeDocumentSectionKind kind = ResumeDocumentSectionKind.fromValue(section.getKind());
            if (kind == ResumeDocumentSectionKind.CUSTOM
                    && !ResumeDocumentSectionKind.CUSTOM.name().equalsIgnoreCase(section.getKind())) {
                throw new BusinessException(400, "不支持的简历章节类型");
            }
            normalized.add(ResumeDocumentSectionDTO.builder()
                    .id(idAllocator.allocate(section.getId()))
                    .kind(kind.name())
                    .title(requireWithinLength(section.getTitle(), SECTION_TITLE_MAX_LENGTH, "章节标题超出编辑上限"))
                    .sourceRef(copySourceRef(section.getSourceRef()))
                    .sourceOccurrenceIds(copyStrings(section.getSourceOccurrenceIds()))
                    .entries(normalizeEntries(section.getEntries(), idAllocator))
                    .build());
        }
        return normalized;
    }

    private List<ResumeDocumentEntryDTO> normalizeEntries(
            List<ResumeDocumentEntryDTO> entries, IdAllocator idAllocator) {
        List<ResumeDocumentEntryDTO> normalized = new ArrayList<>();
        if (entries == null) {
            throw new BusinessException(400, "简历条目格式不正确");
        }
        if (entries.size() > MAX_ENTRIES_PER_SECTION) {
            throw new BusinessException(400, "单个章节的条目数量超出编辑上限");
        }
        for (ResumeDocumentEntryDTO entry : entries) {
            if (entry == null) {
                throw new BusinessException(400, "简历条目格式不正确");
            }
            if ((entry.getHeading() != null && !entry.getHeading().isBlank())
                    || (entry.getMeta() != null && !entry.getMeta().isBlank())) {
                throw new BusinessException(400, "历史简历内容格式已过期，请刷新后保存");
            }
            normalized.add(ResumeDocumentEntryDTO.builder()
                    .id(idAllocator.allocate(entry.getId()))
                    .sourceRef(copySourceRef(entry.getSourceRef()))
                    .sourceOccurrenceIds(copyStrings(entry.getSourceOccurrenceIds()))
                    .fieldSourceRefs(copyFieldSourceRefs(entry.getFieldSourceRefs()))
                    .skillItemSourceRefs(copySourceRefs(entry.getSkillItemSourceRefs()))
                    .skillDescriptionSourceRefs(copySourceRefs(entry.getSkillDescriptionSourceRefs()))
                    .organization(entryField(entry.getOrganization(), "条目标题超出编辑上限"))
                    .role(entryField(entry.getRole(), "条目职位超出编辑上限"))
                    .school(entryField(entry.getSchool(), "学校名超出编辑上限"))
                    .degree(entryField(entry.getDegree(), "学历超出编辑上限"))
                    .major(entryField(entry.getMajor(), "专业超出编辑上限"))
                    .startDate(entryField(entry.getStartDate(), "开始时间超出编辑上限"))
                    .endDate(entryField(entry.getEndDate(), "结束时间超出编辑上限"))
                    .location(entryField(entry.getLocation(), "地点超出编辑上限"))
                    .environment(entryField(entry.getEnvironment(), "开发环境超出编辑上限"))
                    .mentor(entryField(entry.getMentor(), "导师超出编辑上限"))
                    .techStack(normalizeSkillItems(entry.getTechStack()))
                    .techStackSourceRefs(copySourceRefs(entry.getTechStackSourceRefs()))
                    .group(entryField(entry.getGroup(), "技能组名超出编辑上限"))
                    .awardTitle(entryField(entry.getAwardTitle(), "获奖标题超出编辑上限"))
                    .awardLevel(entryField(entry.getAwardLevel(), "获奖级别超出编辑上限"))
                    .awardCompetition(entryField(entry.getAwardCompetition(), "竞赛名称超出编辑上限"))
                    .awardRanking(entryField(entry.getAwardRanking(), "获奖名次超出编辑上限"))
                    .awardDate(entryField(entry.getAwardDate(), "获奖日期超出编辑上限"))
                    .skillItems(normalizeSkillItems(entry.getSkillItems()))
                    .skillDescriptions(normalizeSkillDescriptions(entry.getSkillDescriptions()))
                    .bullets(normalizeBullets(entry.getBullets(), idAllocator))
                    .build());
        }
        return normalized;
    }

    private boolean hasContactProvenance(ResumeDocumentContactDTO contact) {
        return contact != null
                && (contact.getSourceRef() != null
                || contact.getSourceOccurrenceIds() != null && !contact.getSourceOccurrenceIds().isEmpty());
    }

    private String entryField(String value, String message) {
        return requireWithinLength(value, ENTRY_FIELD_MAX_LENGTH, message);
    }

    private List<String> normalizeSkillItems(List<String> skillItems) {
        if (skillItems == null) {
            return null;
        }
        if (skillItems.size() > MAX_SKILL_ITEMS_PER_ENTRY) {
            throw new BusinessException(400, "单个技能组的技能数量超出编辑上限");
        }
        List<String> normalized = new ArrayList<>();
        for (String item : skillItems) {
            if (item == null) {
                throw new BusinessException(400, "技能组内容格式不正确");
            }
            normalized.add(requireWithinLength(item, SKILL_ITEM_MAX_LENGTH, "技能内容超出编辑上限"));
        }
        return normalized;
    }

    private List<ResumeDocumentBulletDTO> normalizeBullets(
            List<ResumeDocumentBulletDTO> bullets, IdAllocator idAllocator) {
        List<ResumeDocumentBulletDTO> normalized = new ArrayList<>();
        if (bullets == null) {
            throw new BusinessException(400, "简历要点格式不正确");
        }
        if (bullets.size() > MAX_BULLETS_PER_ENTRY) {
            throw new BusinessException(400, "单个条目的要点数量超出编辑上限");
        }
        for (ResumeDocumentBulletDTO bullet : bullets) {
            if (bullet == null || bullet.getText() == null) {
                throw new BusinessException(400, "简历要点格式不正确");
            }
            normalized.add(ResumeDocumentBulletDTO.builder()
                    .id(idAllocator.allocate(bullet.getId()))
                    .text(requireWithinLength(bullet.getText(), BULLET_MAX_LENGTH, "要点内容超出编辑上限"))
                    .sourceRef(copySourceRef(bullet.getSourceRef()))
                    .sourceOccurrenceIds(copyStrings(bullet.getSourceOccurrenceIds()))
                    .build());
        }
        return normalized;
    }

    /**
     * Decide whether the payload is already semantic V1. Presence of a JSON property is not
     * enough: old generic payloads commonly contain the same keys with null/blank values. A
     * nonblank heading/meta anywhere deliberately selects the compatibility path, even when a
     * different entry already contains semantic fields; the compatibility path is what can
     * retain both shapes in one historical document.
     */
    private boolean looksLikeSemanticDocument(JsonNode root) {
        if (root == null || !root.isObject()) {
            return false;
        }

        JsonNode basicsNode = root.path("basics");
        JsonNode contactsNode = basicsNode.path("contacts");
        JsonNode sectionsNode = root.path("sections");
        boolean hasLegacyEntryFields = false;
        if (sectionsNode.isArray()) {
            for (JsonNode section : sectionsNode) {
                if (section == null || !section.isObject()) {
                    continue;
                }
                JsonNode entriesNode = section.path("entries");
                if (!entriesNode.isArray()) {
                    continue;
                }
                for (JsonNode entry : entriesNode) {
                    if (entry != null && entry.isObject()
                            && (textOrNull(entry.path("heading")) != null
                            || textOrNull(entry.path("meta")) != null)) {
                        hasLegacyEntryFields = true;
                    }
                }
            }
        }
        if (hasLegacyEntryFields) {
            return false;
        }
        if (hasLegacyContactFields(contactsNode)) {
            // A contact without a semantic type is a legacy label/value row. Route the whole
            // mixed snapshot through the compatibility mapper so labels such as 学校、专业、日期
            // and 时间 are retained as OTHER rather than silently discarded by normalization.
            return false;
        }

        if (hasNonBlank(root, "sourceRef") || hasNonBlank(root, "sourceOccurrenceIds")) {
            return true;
        }
        if (hasNonBlank(basicsNode, "jobIntention")
                || hasNonBlank(basicsNode, "highestEducation")
                || hasNonBlank(basicsNode, "sourceRef")
                || hasNonBlank(basicsNode, "sourceOccurrenceIds")
                || hasNonBlank(basicsNode, "fieldSourceRefs")) {
            return true;
        }
        if (contactsNode.isArray()) {
            for (JsonNode contact : contactsNode) {
                // A typed contact is semantic only when its type is actually usable. JSON null
                // and whitespace must not make a generic contact look semantic.
                if (hasNonBlank(contact, "type")) {
                    return true;
                }
            }
        }
        if (!sectionsNode.isArray()) {
            return false;
        }
        for (JsonNode section : sectionsNode) {
            if (section == null || !section.isObject()) {
                continue;
            }
            if (hasNonBlank(section, "sourceRef") || hasNonBlank(section, "sourceOccurrenceIds")) {
                return true;
            }
            JsonNode entriesNode = section.path("entries");
            if (!entriesNode.isArray()) {
                continue;
            }
            boolean summarySection = isSummaryKind(textOrNull(section.path("kind")));
            for (JsonNode entry : entriesNode) {
                if (entry == null || !entry.isObject()) {
                    continue;
                }
                if (hasNonBlankCanonicalEntryField(entry)
                        || hasNonBlank(entry, "sourceRef")
                        || hasNonBlank(entry, "sourceOccurrenceIds")
                        || hasNonBlank(entry, "fieldSourceRefs")
                        || hasNonBlank(entry, "skillItemSourceRefs")
                        || hasNonBlank(entry, "skillDescriptionSourceRefs")
                        || (summarySection && hasNonBlankBulletText(entry.path("bullets")))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasLegacyContactFields(JsonNode contactsNode) {
        if (contactsNode == null || !contactsNode.isArray()) {
            return false;
        }
        for (JsonNode contact : contactsNode) {
            if (contact != null && contact.isObject()
                    && hasNonBlank(contact, "value")
                    && !hasNonBlank(contact, "type")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNonBlankCanonicalEntryField(JsonNode entry) {
        String[] scalarFields = {
            "organization", "role", "school", "degree", "major", "startDate", "endDate", "location",
            "environment", "mentor", "group", "awardTitle", "awardLevel", "awardCompetition",
            "awardRanking", "awardDate"
        };
        for (String field : scalarFields) {
            if (hasNonBlank(entry, field)) {
                return true;
            }
        }
        return hasNonBlank(entry, "techStack")
                || hasNonBlank(entry, "skillItems")
                || hasNonBlank(entry, "skillDescriptions");
    }

    private boolean hasNonBlankBulletText(JsonNode bulletsNode) {
        if (bulletsNode == null || !bulletsNode.isArray()) {
            return false;
        }
        for (JsonNode bullet : bulletsNode) {
            if (bullet != null && bullet.isObject() && textOrNull(bullet.path("text")) != null) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNonBlank(JsonNode parent, String field) {
        return parent != null && !parent.isMissingNode() && hasNonBlankNode(parent.get(field));
    }

    private boolean hasNonBlankNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isTextual()) {
            return !node.asText().isBlank();
        }
        if (node.isArray() || node.isObject()) {
            for (JsonNode child : node) {
                if (hasNonBlankNode(child)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private boolean isSummaryKind(String kind) {
        if (kind == null) {
            return false;
        }
        return "SUMMARY".equalsIgnoreCase(kind.strip());
    }

    /**
     * V1 generic basics 升级。Known semantic basics are copied first, then legacy label/value
     * rows are mapped. A row that cannot safely become a basic scalar remains an OTHER contact;
     * it is never discarded merely because its label resembles education or a date.
     */
    private ResumeDocumentBasicsDTO upgradeBasics(JsonNode root) {
        JsonNode basicsNode = root.path("basics");
        if (!basicsNode.isMissingNode() && !basicsNode.isNull() && !basicsNode.isObject()) {
            throw new BusinessException(500, "简历基础信息格式不正确，请重新解析");
        }
        ResumeDocumentBasicsDTO parsed = basicsNode.isObject()
                ? readKnownValue(basicsNode, ResumeDocumentBasicsDTO.class, "简历基础信息格式不正确，请重新解析")
                : new ResumeDocumentBasicsDTO();
        List<ResumeDocumentContactDTO> contacts = new ArrayList<>();
        String jobIntention = textOrNull(basicsNode.path("jobIntention"));
        String highestEducation = textOrNull(basicsNode.path("highestEducation"));
        java.util.Map<String, ResumeSourceRefDTO> fieldSourceRefs =
                copyFieldSourceRefs(parsed.getFieldSourceRefs());
        JsonNode contactsNode = basicsNode.path("contacts");
        if (!contactsNode.isMissingNode() && !contactsNode.isNull() && !contactsNode.isArray()) {
            throw new BusinessException(500, "简历联系方式格式不正确，请重新解析");
        }
        if (contactsNode.isArray()) {
            for (JsonNode contactNode : contactsNode) {
                if (contactNode == null || !contactNode.isObject()) {
                    throw new BusinessException(500, "简历联系方式格式不正确，请重新解析");
                }
                ResumeDocumentContactDTO parsedContact = readKnownValue(
                        contactNode, ResumeDocumentContactDTO.class, "简历联系方式格式不正确，请重新解析");
                String label = textOrNull(contactNode.path("label"));
                String value = textOrNull(contactNode.path("value"));
                if (value == null) {
                    throw new BusinessException(500, "简历联系方式内容缺失，请重新解析");
                }
                String explicitType = textOrNull(contactNode.path("type"));
                ResumeDocumentContactType type = explicitType == null
                        ? upgradeContactType(label, value)
                        : parseContactType(explicitType);
                ResumeSourceRefDTO contactSourceRef = sourceRefWithOccurrences(
                        parsedContact.getSourceRef(), parsedContact.getSourceOccurrenceIds());

                // A typed contact is already semantic. Only an untyped legacy row is allowed to
                // promote a label into jobIntention/highestEducation.
                boolean untypedLegacy = explicitType == null;
                if (untypedLegacy && isJobIntentionLabel(label)) {
                    if (jobIntention == null) {
                        jobIntention = value;
                        preserveFieldSourceRef(fieldSourceRefs, "jobIntention", contactSourceRef);
                    } else if (sameText(jobIntention, value)) {
                        preserveFieldSourceRef(fieldSourceRefs, "jobIntention", contactSourceRef);
                    } else {
                        addLegacyContact(contacts, parsedContact, type, label, value);
                    }
                    continue;
                }
                if (untypedLegacy && isHighestEducationLabel(label) && isDegreeValue(value)) {
                    if (highestEducation == null) {
                        highestEducation = value;
                        preserveFieldSourceRef(fieldSourceRefs, "highestEducation", contactSourceRef);
                    } else if (sameText(highestEducation, value)) {
                        preserveFieldSourceRef(fieldSourceRefs, "highestEducation", contactSourceRef);
                    } else {
                        addLegacyContact(contacts, parsedContact, type, label, value);
                    }
                    continue;
                }

                // School/major/date labels, conflicting scalar rows, and otherwise ambiguous
                // values remain visible as typed OTHER contacts instead of being lost.
                addLegacyContact(contacts, parsedContact, type, label, value);
            }
        }
        return ResumeDocumentBasicsDTO.builder()
                .sourceRef(copySourceRef(parsed.getSourceRef()))
                .sourceOccurrenceIds(copyStrings(parsed.getSourceOccurrenceIds()))
                .fieldSourceRefs(fieldSourceRefs)
                .name(textOrNull(basicsNode.path("name")))
                .jobIntention(jobIntention)
                .highestEducation(highestEducation)
                .contacts(contacts)
                .build();
    }

    private ResumeDocumentContactType parseContactType(String value) {
        ResumeDocumentContactType type = ResumeDocumentContactType.fromValue(value);
        if (type == ResumeDocumentContactType.OTHER
                && !ResumeDocumentContactType.OTHER.name().equalsIgnoreCase(value.strip())) {
            throw new BusinessException(500, "不支持的联系方式类型，请重新解析");
        }
        return type;
    }

    private boolean isJobIntentionLabel(String label) {
        return label != null && label.contains("求职意向");
    }

    private boolean isHighestEducationLabel(String label) {
        return label != null && (label.contains("最高学历") || label.equals("学历")
                || label.equalsIgnoreCase("degree"));
    }

    private void addLegacyContact(
            List<ResumeDocumentContactDTO> contacts,
            ResumeDocumentContactDTO parsed,
            ResumeDocumentContactType type,
            String label,
            String value) {
        String effectiveLabel = label == null || label.isBlank() ? type.getDefaultLabel() : label;
        contacts.add(ResumeDocumentContactDTO.builder()
                .id(textOrNull(parsed.getId()))
                .type(type.name())
                .label(effectiveLabel)
                .value(value)
                .sourceRef(copySourceRef(parsed.getSourceRef()))
                .sourceOccurrenceIds(copyStrings(parsed.getSourceOccurrenceIds()))
                .build());
    }

    private boolean isDegreeValue(String value) {
        return value != null && value.matches(".*(博士后|博士|硕士|研究生|本科|大专|专科|学士|高中|中专|MBA).*");
    }

    private ResumeDocumentContactType upgradeContactType(String label, String value) {
        if (label != null && (label.contains("学历") || label.contains("学校") || label.contains("院校")
                || label.contains("专业") || label.contains("日期") || label.contains("时间"))) {
            return ResumeDocumentContactType.OTHER;
        }
        String normalizedLabel = label == null ? "" : label.strip().toLowerCase(Locale.ROOT);
        // Historical labels are stronger evidence than a numeric-looking value:
        // QQ IDs, WeChat IDs and postcodes must never become PHONE contacts.
        if (normalizedLabel.contains("qq")) {
            return ResumeDocumentContactType.QQ;
        }
        if (normalizedLabel.contains("微信") || normalizedLabel.contains("wechat")) {
            return ResumeDocumentContactType.WECHAT;
        }
        if (normalizedLabel.contains("github")) {
            return ResumeDocumentContactType.GITHUB;
        }
        if (normalizedLabel.contains("linkedin") || normalizedLabel.contains("领英")) {
            return ResumeDocumentContactType.LINKEDIN;
        }
        if (normalizedLabel.contains("网站") || normalizedLabel.contains("website")) {
            return ResumeDocumentContactType.WEBSITE;
        }
        if (normalizedLabel.contains("邮编") || normalizedLabel.contains("邮政编码")
                || normalizedLabel.contains("postal")) {
            return ResumeDocumentContactType.OTHER;
        }
        if (normalizedLabel.contains("所在地") || normalizedLabel.contains("城市") || normalizedLabel.contains("location")) {
            return ResumeDocumentContactType.LOCATION;
        }
        if (normalizedLabel.contains("电话") || normalizedLabel.contains("手机") || normalizedLabel.contains("phone")) {
            return ResumeDocumentContactType.PHONE;
        }
        if (normalizedLabel.contains("邮箱") || normalizedLabel.contains("email")) {
            return ResumeDocumentContactType.EMAIL;
        }
        if (EMAIL_VALUE.matcher(value).matches()) {
            return ResumeDocumentContactType.EMAIL;
        }
        if (PHONE_VALUE.matcher(value).matches()) {
            return ResumeDocumentContactType.PHONE;
        }
        String lowerValue = value.toLowerCase(Locale.ROOT);
        if (lowerValue.contains("github.com")) {
            return ResumeDocumentContactType.GITHUB;
        }
        if (lowerValue.contains("linkedin.com") || lowerValue.contains("领英")) {
            return ResumeDocumentContactType.LINKEDIN;
        }
        if (URL_VALUE.matcher(value).matches()) {
            return ResumeDocumentContactType.WEBSITE;
        }
        return ResumeDocumentContactType.OTHER;
    }

    /** V1 sections 升级：保留 section/entry 的语义字段与全部来源元数据，再迁移 heading/meta。 */
    private List<ResumeDocumentSectionDTO> upgradeSections(JsonNode sectionsNode) {
        List<ResumeDocumentSectionDTO> sections = new ArrayList<>();
        if (sectionsNode == null || sectionsNode.isMissingNode() || sectionsNode.isNull()) {
            return sections;
        }
        if (!sectionsNode.isArray()) {
            throw new BusinessException(500, "简历内容格式不正确，请重新解析");
        }
        for (JsonNode sectionNode : sectionsNode) {
            if (sectionNode == null || !sectionNode.isObject()) {
                throw new BusinessException(500, "简历内容格式不正确，请重新解析");
            }
            JsonNode entriesNode = sectionNode.path("entries");
            if (entriesNode.isMissingNode() || entriesNode.isNull()) {
                entriesNode = objectMapper.createArrayNode();
            }
            if (!entriesNode.isArray()) {
                throw new BusinessException(500, "简历章节条目格式不正确，请重新解析");
            }
            ResumeDocumentSectionDTO parsed = readKnownValue(
                    sectionNode, ResumeDocumentSectionDTO.class, "简历章节格式不正确，请重新解析");
            String rawKind = textOrNull(sectionNode.path("kind"));
            ResumeDocumentSectionKind kind = ResumeDocumentSectionKind.fromValue(rawKind);
            if (kind == ResumeDocumentSectionKind.CUSTOM
                    && !ResumeDocumentSectionKind.CUSTOM.name().equalsIgnoreCase(rawKind)) {
                throw new BusinessException(500, "不支持的简历章节类型，请重新解析");
            }
            List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
            for (JsonNode entryNode : entriesNode) {
                entries.add(upgradeEntry(kind, entryNode));
            }
            sections.add(ResumeDocumentSectionDTO.builder()
                    .id(textOrNull(sectionNode.path("id")))
                    .kind(kind.name())
                    .title(textOrNull(sectionNode.path("title")))
                    .sourceRef(copySourceRef(parsed.getSourceRef()))
                    .sourceOccurrenceIds(copyStrings(parsed.getSourceOccurrenceIds()))
                    .entries(entries)
                    .build());
        }
        return sections;
    }

    private ResumeDocumentEntryDTO upgradeEntry(ResumeDocumentSectionKind kind, JsonNode entryNode) {
        if (entryNode == null || !entryNode.isObject()) {
            throw new BusinessException(500, "简历条目格式不正确，请重新解析");
        }
        ResumeDocumentEntryDTO parsed = readKnownValue(
                entryNode, ResumeDocumentEntryDTO.class, "简历条目格式不正确，请重新解析");
        String heading = textOrNull(entryNode.path("heading"));
        String meta = textOrNull(entryNode.path("meta"));
        List<ResumeDocumentBulletDTO> bullets = readLegacyBullets(entryNode.path("bullets"));
        ResumeDocumentEntryDTO.ResumeDocumentEntryDTOBuilder builder = copyEntryFields(parsed);

        switch (kind) {
            case SKILL -> {
                List<String> skillItems = copyStrings(parsed.getSkillItems());
                if (skillItems == null) {
                    skillItems = new ArrayList<>();
                }
                List<ResumeSourceRefDTO> itemRefs = copySourceRefs(parsed.getSkillItemSourceRefs());
                if (itemRefs == null) {
                    itemRefs = new ArrayList<>();
                }
                String group = firstNonBlank(parsed.getGroup(), heading);
                boolean headingConsumed = heading == null || sameText(group, heading);
                boolean hasSemanticSkillValues = hasNonBlank(parsed.getSkillItems())
                        || hasNonBlank(parsed.getSkillDescriptions());
                List<ResumeDocumentBulletDTO> retainedBullets = new ArrayList<>();
                if (!hasSemanticSkillValues) {
                    for (ResumeDocumentBulletDTO bullet : bullets) {
                        String text = bullet.getText();
                        int separator = indexOfGroupSeparator(text);
                        String itemText = text;
                        if ((group == null || group.isBlank()) && separator > 0 && separator < text.length() - 1) {
                            group = text.substring(0, separator).strip();
                            itemText = text.substring(separator + 1);
                        } else if (separator > 0 && separator < text.length() - 1
                                && sameText(group, text.substring(0, separator))) {
                            itemText = text.substring(separator + 1);
                        }
                        List<String> splitItems = splitSkillItems(itemText);
                        if (splitItems.isEmpty()) {
                            splitItems = List.of(text);
                        }
                        for (String item : splitItems) {
                            skillItems.add(item);
                            itemRefs.add(sourceRefFor(bullet, parsed));
                        }
                    }
                } else {
                    // A mixed entry may contain canonical skill fields and old bullets. Keep the
                    // bullets instead of treating them all as disposable legacy compression.
                    retainedBullets.addAll(bullets);
                }
                List<String> descriptions = copyStrings(parsed.getSkillDescriptions());
                if (descriptions == null) {
                    descriptions = new ArrayList<>();
                }
                List<ResumeSourceRefDTO> descriptionRefs = copySourceRefs(parsed.getSkillDescriptionSourceRefs());
                if (descriptionRefs == null) {
                    descriptionRefs = new ArrayList<>();
                }
                if (meta != null) {
                    descriptions.add(meta);
                    descriptionRefs.add(sourceRefFor(parsed));
                }
                if (!headingConsumed) {
                    appendLegacyBullet(retainedBullets, heading, parsed);
                }
                return builder.group(group)
                        .skillItems(skillItems)
                        .skillItemSourceRefs(itemRefs.isEmpty() ? null : itemRefs)
                        .skillDescriptions(descriptions.isEmpty() ? null : descriptions)
                        .skillDescriptionSourceRefs(descriptionRefs.isEmpty() ? null : descriptionRefs)
                        .bullets(retainedBullets)
                        .build();
            }
            case EDUCATION -> {
                String school = firstNonBlank(parsed.getSchool(), heading);
                boolean headingConsumed = heading == null || sameText(school, heading);
                String startDate = parsed.getStartDate();
                String endDate = parsed.getEndDate();
                boolean metaConsumed = true;
                if (meta != null) {
                    Matcher range = DATE_RANGE.matcher(meta);
                    if (range.find()) {
                        String rangeStart = range.group(1).strip();
                        String rangeEnd = range.group(2).strip();
                        if (startDate == null) {
                            startDate = rangeStart;
                        } else if (!sameText(startDate, rangeStart)) {
                            metaConsumed = false;
                        }
                        if (endDate == null) {
                            endDate = rangeEnd;
                        } else if (!sameText(endDate, rangeEnd)) {
                            metaConsumed = false;
                        }
                        // Any text outside the date range remains part of the original meta
                        // value. Preserve that value once below rather than splitting it into a
                        // remainder and the full string (which would duplicate content).
                        if (!removeDateRangeRemainder(meta, range).isEmpty()) {
                            metaConsumed = false;
                        }
                    } else if (looksLikeDate(meta)) {
                        if (startDate == null) {
                            startDate = meta;
                        } else if (!sameText(startDate, meta) && !sameText(endDate, meta)) {
                            metaConsumed = false;
                        }
                    } else {
                        metaConsumed = false;
                    }
                }
                if (!headingConsumed) {
                    appendLegacyBullet(bullets, heading, parsed);
                }
                // Keep an unprojectable metadata value once. In particular, a date range with
                // a non-date remainder must not be appended both as the remainder and as the
                // original meta string.
                if (!metaConsumed && meta != null
                        && bullets.stream().noneMatch(bullet -> sameText(bullet.getText(), meta))) {
                    appendLegacyBullet(bullets, meta, parsed);
                }
                return builder.school(school)
                        .startDate(startDate)
                        .endDate(endDate)
                        .bullets(bullets)
                        .build();
            }
            case EXPERIENCE, PROJECT -> {
                String organization = firstNonBlank(parsed.getOrganization(), heading);
                boolean headingConsumed = heading == null || sameText(organization, heading);
                String role = parsed.getRole();
                String startDate = parsed.getStartDate();
                String endDate = parsed.getEndDate();
                boolean metaConsumed = true;
                if (meta != null) {
                    Matcher range = DATE_RANGE.matcher(meta);
                    if (range.find()) {
                        String rangeStart = range.group(1).strip();
                        String rangeEnd = range.group(2).strip();
                        if (startDate == null) {
                            startDate = rangeStart;
                        } else if (!sameText(startDate, rangeStart)) {
                            metaConsumed = false;
                        }
                        if (endDate == null) {
                            endDate = rangeEnd;
                        } else if (!sameText(endDate, rangeEnd)) {
                            metaConsumed = false;
                        }
                        String remainder = removeDateRangeRemainder(meta, range);
                        if (!remainder.isEmpty()) {
                            if (role == null) {
                                role = remainder;
                            } else if (!sameText(role, remainder)) {
                                metaConsumed = false;
                            }
                        }
                    } else if (role == null && !looksLikeDate(meta)) {
                        // A legacy meta value containing only a role is still a role, not a
                        // fabricated start date.
                        role = meta;
                    } else if (looksLikeDate(meta)) {
                        if (startDate == null) {
                            startDate = meta;
                        } else if (!sameText(role, meta) && !sameText(startDate, meta)
                                && !sameText(endDate, meta)) {
                            metaConsumed = false;
                        }
                    } else if (!sameText(role, meta) && !sameText(startDate, meta)
                            && !sameText(endDate, meta)) {
                        metaConsumed = false;
                    }
                }
                if (!headingConsumed) {
                    appendLegacyBullet(bullets, heading, parsed);
                }
                if (!metaConsumed && meta != null) {
                    appendLegacyBullet(bullets, meta, parsed);
                }
                return builder.organization(organization)
                        .role(role)
                        .startDate(startDate)
                        .endDate(endDate)
                        .bullets(bullets)
                        .build();
            }
            case ACHIEVEMENT -> {
                // Achievement has two safe semantic slots in V1: heading is the award title,
                // while a metadata value that is only a date/range is the award date. Any
                // ambiguous metadata remains a bullet instead of being guessed into level,
                // competition, or ranking.
                String awardTitle = firstNonBlank(parsed.getAwardTitle(), heading);
                boolean headingConsumed = heading == null || sameText(awardTitle, heading);
                String awardDate = parsed.getAwardDate();
                boolean metaConsumed = meta == null;
                if (meta != null) {
                    if (awardDate == null && isStandaloneDate(meta)) {
                        awardDate = meta;
                        metaConsumed = true;
                    } else if (sameText(awardDate, meta)) {
                        metaConsumed = true;
                    } else {
                        metaConsumed = false;
                    }
                }
                if (!headingConsumed) {
                    appendLegacyBullet(bullets, heading, parsed);
                }
                if (!metaConsumed) {
                    appendLegacyBullet(bullets, meta, parsed);
                }
                return builder.awardTitle(awardTitle)
                        .awardDate(awardDate)
                        .bullets(bullets)
                        .build();
            }
            default -> {
                // SUMMARY/CERTIFICATE/OTHER/CUSTOM have no generic heading/meta slots in
                // canonical V1. Preserve both values even when bullets already exist.
                appendLegacyBullet(bullets, heading, parsed);
                appendLegacyBullet(bullets, meta, parsed);
                return builder.bullets(bullets).build();
            }
        }
    }

    private ResumeDocumentEntryDTO.ResumeDocumentEntryDTOBuilder copyEntryFields(ResumeDocumentEntryDTO parsed) {
        return ResumeDocumentEntryDTO.builder()
                .id(parsed.getId())
                .sourceRef(copySourceRef(parsed.getSourceRef()))
                .sourceOccurrenceIds(copyStrings(parsed.getSourceOccurrenceIds()))
                .fieldSourceRefs(copyFieldSourceRefs(parsed.getFieldSourceRefs()))
                .skillItemSourceRefs(copySourceRefs(parsed.getSkillItemSourceRefs()))
                .skillDescriptionSourceRefs(copySourceRefs(parsed.getSkillDescriptionSourceRefs()))
                .organization(parsed.getOrganization())
                .role(parsed.getRole())
                .school(parsed.getSchool())
                .degree(parsed.getDegree())
                .major(parsed.getMajor())
                .startDate(parsed.getStartDate())
                .endDate(parsed.getEndDate())
                .location(parsed.getLocation())
                .environment(parsed.getEnvironment())
                .mentor(parsed.getMentor())
                .techStack(copyStrings(parsed.getTechStack()))
                .techStackSourceRefs(copySourceRefs(parsed.getTechStackSourceRefs()))
                .group(parsed.getGroup())
                .awardTitle(parsed.getAwardTitle())
                .awardLevel(parsed.getAwardLevel())
                .awardCompetition(parsed.getAwardCompetition())
                .awardRanking(parsed.getAwardRanking())
                .awardDate(parsed.getAwardDate())
                .skillItems(copyStrings(parsed.getSkillItems()))
                .skillDescriptions(copyStrings(parsed.getSkillDescriptions()));
    }

    private List<ResumeDocumentBulletDTO> readLegacyBullets(JsonNode bulletsNode) {
        List<ResumeDocumentBulletDTO> bullets = new ArrayList<>();
        if (bulletsNode == null || bulletsNode.isMissingNode() || bulletsNode.isNull()) {
            return bullets;
        }
        if (!bulletsNode.isArray()) {
            throw new BusinessException(500, "简历要点格式不正确，请重新解析");
        }
        for (JsonNode bulletNode : bulletsNode) {
            if (bulletNode == null || !bulletNode.isObject()) {
                throw new BusinessException(500, "简历要点格式不正确，请重新解析");
            }
            ResumeDocumentBulletDTO parsed = readKnownValue(
                    bulletNode, ResumeDocumentBulletDTO.class, "简历要点格式不正确，请重新解析");
            String text = textOrNull(bulletNode.path("text"));
            if (text == null) {
                throw new BusinessException(500, "简历要点内容缺失，请重新解析");
            }
            bullets.add(ResumeDocumentBulletDTO.builder()
                    .id(textOrNull(bulletNode.path("id")))
                    .text(text)
                    .sourceRef(copySourceRef(parsed.getSourceRef()))
                    .sourceOccurrenceIds(copyStrings(parsed.getSourceOccurrenceIds()))
                    .build());
        }
        return bullets;
    }

    private void appendLegacyBullet(
            List<ResumeDocumentBulletDTO> bullets, String value, ResumeDocumentEntryDTO source) {
        String text = textOrNull(value == null ? null : objectMapper.getNodeFactory().textNode(value));
        if (text == null) {
            return;
        }
        bullets.add(ResumeDocumentBulletDTO.builder()
                .text(text)
                .sourceRef(copySourceRef(source.getSourceRef()))
                .sourceOccurrenceIds(copyStrings(source.getSourceOccurrenceIds()))
                .build());
    }

    private boolean containsSameText(List<?> values, String value) {
        if (values == null || value == null) {
            return false;
        }
        for (Object item : values) {
            String text = item instanceof ResumeDocumentBulletDTO bullet
                    ? bullet.getText() : item instanceof String string ? string : null;
            if (sameText(text, value)) {
                return true;
            }
        }
        return false;
    }

    private String removeDateRangeRemainder(String meta, Matcher range) {
        return (meta.substring(0, range.start()) + " " + meta.substring(range.end()))
                .replaceAll("[\\s·•\\-–—~～]+", " ")
                .strip();
    }

    private boolean looksLikeDate(String value) {
        return value != null && (value.matches(".*(?:19|20)\\d{2}.*")
                || value.matches("(?i)^(?:至今|今|现在|present)$"));
    }

    private boolean isStandaloneDate(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.strip();
        return STANDALONE_DATE.matcher(normalized).matches()
                || DATE_RANGE.matcher(normalized).matches();
    }

    private boolean hasNonBlank(List<String> values) {
        return values != null && values.stream().anyMatch(value -> value != null && !value.isBlank());
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private boolean sameText(String left, String right) {
        return left != null && right != null && left.strip().equals(right.strip());
    }

    private ResumeSourceRefDTO sourceRefFor(ResumeDocumentEntryDTO source) {
        return copySourceRef(source.getSourceRef());
    }

    private ResumeSourceRefDTO sourceRefFor(
            ResumeDocumentBulletDTO bullet, ResumeDocumentEntryDTO fallback) {
        if (bullet.getSourceRef() != null) {
            return copySourceRef(bullet.getSourceRef());
        }
        List<String> occurrenceIds = copyStrings(bullet.getSourceOccurrenceIds());
        if (occurrenceIds != null && !occurrenceIds.isEmpty()) {
            // A bullet can carry a precise occurrence anchor without the older, richer sourceRef
            // object. Do not widen it to the parent entry: that would make a child claim appear
            // supported by an unrelated parent span.
            return ResumeSourceRefDTO.builder()
                    .text(bullet.getText())
                    .sourceOccurrenceIds(occurrenceIds)
                    .build();
        }
        return sourceRefFor(fallback);
    }

    private int indexOfGroupSeparator(String text) {
        int full = text.indexOf('：');
        int half = text.indexOf(':');
        if (full < 0) {
            return half;
        }
        if (half < 0) {
            return full;
        }
        return Math.min(full, half);
    }

    private List<String> splitSkillItems(String text) {
        List<String> items = new ArrayList<>();
        for (String part : text.split("[、，,/]")) {
            String trimmed = part == null ? null : part.strip();
            if (trimmed != null && !trimmed.isEmpty()) {
                items.add(trimmed);
            }
        }
        return items;
    }

    private ResumeSourceRefDTO copySourceRef(ResumeSourceRefDTO sourceRef) {
        if (sourceRef == null) {
            return null;
        }
        return ResumeSourceRefDTO.builder()
                .startLine(sourceRef.getStartLine())
                .endLine(sourceRef.getEndLine())
                .text(sourceRef.getText())
                .sourceBlockIds(copyStrings(sourceRef.getSourceBlockIds()))
                .sourceOccurrenceIds(copyStrings(sourceRef.getSourceOccurrenceIds()))
                .page(sourceRef.getPage())
                .x(sourceRef.getX())
                .y(sourceRef.getY())
                .width(sourceRef.getWidth())
                .height(sourceRef.getHeight())
                .fontSize(sourceRef.getFontSize())
                .fontName(sourceRef.getFontName())
                .boldHint(sourceRef.getBoldHint())
                .indent(sourceRef.getIndent())
                .bulletHint(sourceRef.getBulletHint())
                .role(sourceRef.getRole())
                .sourceType(sourceRef.getSourceType())
                .build();
    }

    private List<String> copyStrings(List<String> values) {
        return values == null ? null : new ArrayList<>(values);
    }

    private Map<String, String> copySourceOccurrenceTexts(Map<String, String> values) {
        return values == null ? null : new LinkedHashMap<>(values);
    }

    private Map<String, ResumeSourceRefDTO> copySourceOccurrenceRefs(Map<String, ResumeSourceRefDTO> values) {
        if (values == null) {
            return null;
        }
        Map<String, ResumeSourceRefDTO> copied = new LinkedHashMap<>();
        values.forEach((key, value) -> copied.put(key, copySourceRef(value)));
        return copied;
    }

    private Map<String, ResumeSourceRefDTO> readSourceOccurrenceRefs(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new BusinessException(500, "简历来源 occurrence 坐标清单格式不正确，请重新解析");
        }
        Map<String, ResumeSourceRefDTO> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw new BusinessException(500, "简历来源 occurrence 坐标清单格式不正确，请重新解析");
            }
            values.put(entry.getKey(), readSourceRef(entry.getValue()));
        });
        return values;
    }

    private Map<String, String> readSourceOccurrenceTexts(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new BusinessException(500, "简历来源 occurrence 清单格式不正确，请重新解析");
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (key == null || key.isBlank() || value == null || !value.isTextual()
                    || value.textValue().isBlank()) {
                throw new BusinessException(500, "简历来源 occurrence 清单格式不正确，请重新解析");
            }
            values.put(key, value.textValue());
        });
        return values;
    }

    private <T> T readKnownValue(JsonNode node, Class<T> type, String message) {
        try {
            return strictObjectMapper.treeToValue(node, type);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException(500, message);
        }
    }

    /**
     * Null/missing arrays were not meaningful in historical V1 snapshots. Convert only the
     * optional container shape to an empty array before strict semantic binding; a scalar or
     * object in any of these positions remains malformed and is rejected by the normal path.
     */
    private JsonNode withHistoricalOptionalArrayDefaults(JsonNode root) {
        ObjectNode copy = (ObjectNode) root.deepCopy();
        ObjectNode basics = objectOrNull(copy.get("basics"));
        if (basics == null) {
            if (copy.get("basics") == null || copy.get("basics").isNull()) {
                basics = objectMapper.createObjectNode();
                copy.set("basics", basics);
            }
        }
        if (basics != null) {
            defaultNullArray(basics, "contacts");
        }

        JsonNode sectionsNode = copy.get("sections");
        if (sectionsNode == null || sectionsNode.isNull()) {
            copy.set("sections", objectMapper.createArrayNode());
            sectionsNode = copy.get("sections");
        }
        if (sectionsNode.isArray()) {
            for (JsonNode sectionNode : sectionsNode) {
                if (!(sectionNode instanceof ObjectNode section)) {
                    continue;
                }
                defaultNullArray(section, "entries");
                JsonNode entriesNode = section.get("entries");
                if (entriesNode != null && entriesNode.isArray()) {
                    for (JsonNode entryNode : entriesNode) {
                        if (entryNode instanceof ObjectNode entry) {
                            defaultNullArray(entry, "bullets");
                        }
                    }
                }
            }
        }
        return copy;
    }

    private ObjectNode objectOrNull(JsonNode node) {
        return node instanceof ObjectNode object ? object : null;
    }

    private void defaultNullArray(ObjectNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) {
            parent.set(field, objectMapper.createArrayNode());
        }
    }

    private ResumeSourceRefDTO readSourceRef(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isObject()) {
            throw new BusinessException(500, "简历来源引用格式不正确，请重新解析");
        }
        return copySourceRef(readKnownValue(
                node, ResumeSourceRefDTO.class, "简历来源引用格式不正确，请重新解析"));
    }

    private List<String> readStringList(JsonNode node, String message) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            throw new BusinessException(500, message);
        }
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            if (value == null || value.isNull()) {
                values.add(null);
            } else if (!value.isTextual()) {
                throw new BusinessException(500, message);
            } else {
                values.add(value.textValue());
            }
        }
        return values;
    }

    private ResumeSourceRefDTO sourceRefWithOccurrences(
            ResumeSourceRefDTO sourceRef, List<String> occurrenceIds) {
        ResumeSourceRefDTO copied = copySourceRef(sourceRef);
        List<String> copiedOccurrences = copyStrings(occurrenceIds);
        if (copiedOccurrences == null || copiedOccurrences.isEmpty()) {
            return copied;
        }
        if (copied == null) {
            return ResumeSourceRefDTO.builder()
                    .sourceOccurrenceIds(copiedOccurrences)
                    .build();
        }
        List<String> merged = copied.getSourceOccurrenceIds() == null
                ? new ArrayList<>() : copyStrings(copied.getSourceOccurrenceIds());
        for (String occurrenceId : copiedOccurrences) {
            if (occurrenceId != null && !merged.contains(occurrenceId)) {
                merged.add(occurrenceId);
            }
        }
        copied.setSourceOccurrenceIds(merged);
        return copied;
    }

    private void preserveFieldSourceRef(
            java.util.Map<String, ResumeSourceRefDTO> fieldSourceRefs,
            String field,
            ResumeSourceRefDTO sourceRef) {
        if (fieldSourceRefs == null || field == null || sourceRef == null) {
            return;
        }
        ResumeSourceRefDTO existing = fieldSourceRefs.get(field);
        fieldSourceRefs.put(field, existing == null ? sourceRef : mergeSourceRefs(existing, sourceRef));
    }

    private ResumeSourceRefDTO mergeSourceRefs(ResumeSourceRefDTO first, ResumeSourceRefDTO second) {
        if (first == null) {
            return copySourceRef(second);
        }
        if (second == null) {
            return copySourceRef(first);
        }
        List<String> blockIds = new ArrayList<>();
        appendDistinct(blockIds, first.getSourceBlockIds());
        appendDistinct(blockIds, second.getSourceBlockIds());
        List<String> occurrenceIds = new ArrayList<>();
        appendDistinct(occurrenceIds, first.getSourceOccurrenceIds());
        appendDistinct(occurrenceIds, second.getSourceOccurrenceIds());
        return ResumeSourceRefDTO.builder()
                .startLine(min(first.getStartLine(), second.getStartLine()))
                .endLine(max(first.getEndLine(), second.getEndLine()))
                .text(joinSourceText(first.getText(), second.getText()))
                .sourceBlockIds(blockIds.isEmpty() ? null : blockIds)
                .sourceOccurrenceIds(occurrenceIds.isEmpty() ? null : occurrenceIds)
                .page(first.getPage() != null && first.getPage().equals(second.getPage())
                        ? first.getPage() : null)
                .x(first.getX())
                .y(first.getY())
                .width(first.getWidth())
                .height(first.getHeight())
                .fontSize(first.getFontSize())
                .fontName(first.getFontName())
                .boldHint(first.getBoldHint())
                .indent(first.getIndent())
                .bulletHint(first.getBulletHint())
                .role(first.getRole())
                .sourceType(first.getSourceType())
                .build();
    }

    private void appendDistinct(List<String> target, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !target.contains(value)) {
                target.add(value);
            }
        }
    }

    private Integer min(Integer first, Integer second) {
        return first == null ? second : second == null ? first : Math.min(first, second);
    }

    private Integer max(Integer first, Integer second) {
        return first == null ? second : second == null ? first : Math.max(first, second);
    }

    private String joinSourceText(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank() || first.equals(second)) {
            return first;
        }
        return first + "\n" + second;
    }

    private List<ResumeSourceRefDTO> copySourceRefs(List<ResumeSourceRefDTO> references) {
        if (references == null) {
            return null;
        }
        List<ResumeSourceRefDTO> copied = new ArrayList<>();
        for (ResumeSourceRefDTO reference : references) {
            copied.add(copySourceRef(reference));
        }
        return copied;
    }

    private java.util.Map<String, ResumeSourceRefDTO> copyFieldSourceRefs(
            java.util.Map<String, ResumeSourceRefDTO> references) {
        if (references == null) {
            return null;
        }
        java.util.Map<String, ResumeSourceRefDTO> copied = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, ResumeSourceRefDTO> entry : references.entrySet()) {
            copied.put(entry.getKey(), copySourceRef(entry.getValue()));
        }
        return copied;
    }

    private List<String> normalizeSkillDescriptions(List<String> descriptions) {
        if (descriptions == null) {
            return null;
        }
        if (descriptions.size() > MAX_BULLETS_PER_ENTRY) {
            throw new BusinessException(400, "单个技能组的描述数量超出编辑上限");
        }
        List<String> normalized = new ArrayList<>();
        for (String description : descriptions) {
            if (description == null) {
                throw new BusinessException(400, "技能组描述格式不正确");
            }
            normalized.add(requireWithinLength(
                    description, BULLET_MAX_LENGTH, "技能组描述超出编辑上限"));
        }
        return normalized;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new BusinessException(500, "简历内容中的文本字段格式不正确，请重新解析");
        }
        return textOrNull(node.textValue());
    }

    private String textOrNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private String requireWithinLength(String value, int maxLength, String message) {
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(400, message);
        }
        return value;
    }

    /** 缺失 ID 在首次保存时补齐并随响应返回；已有 ID 必须全局唯一，不能静默改写。 */
    private static final class IdAllocator {

        private final Set<String> used = new HashSet<>();

        String allocate(String requested) {
            if (requested == null || requested.isBlank()) {
                String generated;
                do {
                    generated = UUID.randomUUID().toString();
                } while (!used.add(generated));
                return generated;
            }
            String candidate = requested.strip();
            if (!used.add(candidate)) {
                throw new BusinessException(400, "简历元素 ID 重复");
            }
            return candidate;
        }
    }
}
