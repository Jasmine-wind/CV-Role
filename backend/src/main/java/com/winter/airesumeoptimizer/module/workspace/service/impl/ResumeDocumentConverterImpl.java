package com.winter.airesumeoptimizer.module.workspace.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
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
import java.util.List;
import java.util.Locale;
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

    private static final Pattern EMAIL_VALUE = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_VALUE = Pattern.compile("^\\+?[0-9][0-9\\s\\-()]{5,19}$");
    private static final Pattern URL_VALUE = Pattern.compile("^(https?://|www\\.).+", Pattern.CASE_INSENSITIVE);
    /** 日期区间：2022.07 - 至今 / 2018年9月-2022年6月 / 2018-2022 等原文形态。 */
    private static final Pattern DATE_RANGE = Pattern.compile(
            "((?:19|20)\\d{2}(?:\\s*[年./\\-]\\s*\\d{1,2}\\s*月?)?)"
                    + "\\s*(?:[-–—~～至到]+|[-–—~～])\\s*"
                    + "((?:19|20)\\d{2}(?:\\s*[年./\\-]\\s*\\d{1,2}\\s*月?)?|至今|今|现在|今)");

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
                .basics(normalizeBasics(document.getBasics(), idAllocator))
                .sections(normalizeSections(document.getSections(), idAllocator))
                .build();
    }

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
                ResumeDocumentDTO parsed = strictObjectMapper.readValue(legacyJson, ResumeDocumentDTO.class);
                ResumeDocumentDTO normalized = normalize(parsed);
                // 入库前已经归一化。若再次归一化会改变 ID/内容，说明持久化数据已损坏，必须 fail closed。
                if (!serialize(parsed).equals(serialize(normalized))) {
                    throw new BusinessException(500, "简历内容格式不正确，请重新解析");
                }
                return normalized;
            } catch (JsonProcessingException exception) {
                throw new BusinessException(500, "简历内容格式不正确，请重新解析");
            }
        }
        // 同一 V1 版本内的历史 generic shape：只读升级，不为它引入第二个 schema 版本。
        ResumeDocumentDTO upgraded = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
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
            if (!seenContactValues.add(contactKey)) {
                continue;
            }
            contacts.add(ResumeDocumentContactDTO.builder()
                    .id(normalizedId)
                    .type(type.name())
                    .label(requireWithinLength(label, CONTACT_FIELD_MAX_LENGTH, "基础信息字段名超出编辑上限"))
                    .value(value)
                    .build());
        }
        return ResumeDocumentBasicsDTO.builder()
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
                    .organization(entryField(entry.getOrganization(), "条目标题超出编辑上限"))
                    .role(entryField(entry.getRole(), "条目职位超出编辑上限"))
                    .school(entryField(entry.getSchool(), "学校名超出编辑上限"))
                    .degree(entryField(entry.getDegree(), "学历超出编辑上限"))
                    .major(entryField(entry.getMajor(), "专业超出编辑上限"))
                    .startDate(entryField(entry.getStartDate(), "开始时间超出编辑上限"))
                    .endDate(entryField(entry.getEndDate(), "结束时间超出编辑上限"))
                    .location(entryField(entry.getLocation(), "地点超出编辑上限"))
                    .group(entryField(entry.getGroup(), "技能组名超出编辑上限"))
                    .skillItems(normalizeSkillItems(entry.getSkillItems()))
                    .bullets(normalizeBullets(entry.getBullets(), idAllocator))
                    .build());
        }
        return normalized;
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
                    .build());
        }
        return normalized;
    }

    private boolean looksLikeSemanticDocument(JsonNode root) {
        JsonNode basicsNode = root.path("basics");
        if (basicsNode.has("jobIntention") || basicsNode.has("highestEducation")) {
            return true;
        }
        JsonNode contactsNode = basicsNode.path("contacts");
        if (contactsNode.isArray()) {
            for (JsonNode contact : contactsNode) {
                if (contact != null && contact.has("type")) {
                    return true;
                }
            }
        }
        JsonNode sectionsNode = root.path("sections");
        if (!sectionsNode.isArray()) {
            return false;
        }
        for (JsonNode section : sectionsNode) {
            JsonNode entriesNode = section.path("entries");
            if (!entriesNode.isArray()) {
                continue;
            }
            for (JsonNode entry : entriesNode) {
                if (textOrNull(entry.path("heading")) != null || textOrNull(entry.path("meta")) != null) {
                    return false;
                }
                if (entry.has("organization") || entry.has("school") || entry.has("skillItems")
                        || entry.has("startDate") || entry.has("endDate")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** V1 generic basics 升级：label/value 联系方式按值与标签确定性映射为类型化联系方式。 */
    private ResumeDocumentBasicsDTO upgradeBasics(JsonNode root) {
        JsonNode basicsNode = root.path("basics");
        List<ResumeDocumentContactDTO> contacts = new ArrayList<>();
        String jobIntention = null;
        String highestEducation = null;
        JsonNode contactsNode = basicsNode.path("contacts");
        if (!contactsNode.isMissingNode() && !contactsNode.isArray()) {
            throw new BusinessException(500, "简历联系方式格式不正确，请重新解析");
        }
        if (contactsNode.isArray()) {
            for (JsonNode contactNode : contactsNode) {
                if (contactNode == null || !contactNode.isObject()) {
                    throw new BusinessException(500, "简历联系方式格式不正确，请重新解析");
                }
                String label = textOrNull(contactNode.path("label"));
                String value = textOrNull(contactNode.path("value"));
                if (value == null) {
                    throw new BusinessException(500, "简历联系方式内容缺失，请重新解析");
                }
                if (label != null && label.contains("求职意向")) {
                    if (jobIntention == null) {
                        jobIntention = value;
                    }
                    continue;
                }
                if (label != null && (label.contains("最高学历") || label.equals("学历")
                        || label.equalsIgnoreCase("degree"))) {
                    if (highestEducation == null && isDegreeValue(value)) {
                        highestEducation = value;
                    }
                    continue;
                }
                if (label != null && (label.contains("学校") || label.contains("院校")
                        || label.equalsIgnoreCase("university") || label.contains("专业")
                        || label.contains("日期") || label.contains("时间"))) {
                    // 旧 V1 的自由 label 可能把教育/日期塞进 contacts；不要按值猜成电话。
                    continue;
                }
                contacts.add(ResumeDocumentContactDTO.builder()
                        .id(textOrNull(contactNode.path("id")))
                        .type(upgradeContactType(label, value).name())
                        .label(label)
                        .value(value)
                        .build());
            }
        }
        return ResumeDocumentBasicsDTO.builder()
                .name(textOrNull(basicsNode.path("name")))
                .jobIntention(jobIntention)
                .highestEducation(highestEducation)
                .contacts(contacts)
                .build();
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

    /** V1 sections 升级：heading/meta 按章节语义迁移到结构化字段，内容不猜测不丢弃。 */
    private List<ResumeDocumentSectionDTO> upgradeSections(JsonNode sectionsNode) {
        List<ResumeDocumentSectionDTO> sections = new ArrayList<>();
        if (!sectionsNode.isArray()) {
            throw new BusinessException(500, "简历内容格式不正确，请重新解析");
        }
        for (JsonNode sectionNode : sectionsNode) {
            if (sectionNode == null || !sectionNode.isObject()) {
                throw new BusinessException(500, "简历内容格式不正确，请重新解析");
            }
            String rawKind = textOrNull(sectionNode.path("kind"));
            ResumeDocumentSectionKind kind = ResumeDocumentSectionKind.fromValue(rawKind);
            if (kind == ResumeDocumentSectionKind.CUSTOM
                    && !ResumeDocumentSectionKind.CUSTOM.name().equalsIgnoreCase(rawKind)) {
                throw new BusinessException(500, "不支持的简历章节类型，请重新解析");
            }
            List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
            JsonNode entriesNode = sectionNode.path("entries");
            if (!entriesNode.isArray()) {
                throw new BusinessException(500, "简历章节条目格式不正确，请重新解析");
            }
            for (JsonNode entryNode : entriesNode) {
                entries.add(upgradeEntry(kind, entryNode));
            }
            sections.add(ResumeDocumentSectionDTO.builder()
                    .id(textOrNull(sectionNode.path("id")))
                    .kind(kind.name())
                    .title(textOrNull(sectionNode.path("title")))
                    .entries(entries)
                    .build());
        }
        return sections;
    }

    private ResumeDocumentEntryDTO upgradeEntry(ResumeDocumentSectionKind kind, JsonNode entryNode) {
        if (entryNode == null || !entryNode.isObject()) {
            throw new BusinessException(500, "简历条目格式不正确，请重新解析");
        }
        String heading = textOrNull(entryNode.path("heading"));
        String meta = textOrNull(entryNode.path("meta"));
        List<ResumeDocumentBulletDTO> bullets = new ArrayList<>();
        JsonNode bulletsNode = entryNode.path("bullets");
        if (!bulletsNode.isArray()) {
            throw new BusinessException(500, "简历要点格式不正确，请重新解析");
        }
        for (JsonNode bulletNode : bulletsNode) {
            if (bulletNode == null || !bulletNode.isObject()) {
                throw new BusinessException(500, "简历要点格式不正确，请重新解析");
            }
            String text = textOrNull(bulletNode.path("text"));
            if (text == null) {
                throw new BusinessException(500, "简历要点内容缺失，请重新解析");
            }
            bullets.add(ResumeDocumentBulletDTO.builder()
                    .id(textOrNull(bulletNode.path("id")))
                    .text(text)
                    .build());
        }

        ResumeDocumentEntryDTO.ResumeDocumentEntryDTOBuilder builder = ResumeDocumentEntryDTO.builder()
                .id(textOrNull(entryNode.path("id")));

        switch (kind) {
            case SKILL -> {
                // V1 技能被压扁成「分组：技能、技能」字符串；升级时还原为技能组。
                List<String> skillItems = new ArrayList<>();
                String group = heading;
                for (ResumeDocumentBulletDTO bullet : bullets) {
                    String text = bullet.getText();
                    int separator = indexOfGroupSeparator(text);
                    if (group == null && separator > 0 && separator < text.length() - 1) {
                        group = text.substring(0, separator).strip();
                        skillItems.addAll(splitSkillItems(text.substring(separator + 1)));
                    } else {
                        skillItems.addAll(splitSkillItems(text));
                    }
                }
                return builder.group(group).skillItems(skillItems).bullets(new ArrayList<>()).build();
            }
            case EDUCATION -> {
                builder.school(heading);
                applyDateRange(builder, meta);
                return builder.bullets(bullets).build();
            }
            case EXPERIENCE, PROJECT -> {
                builder.organization(heading);
                applyDateRangeOrMeta(builder, meta);
                return builder.bullets(bullets).build();
            }
            default -> {
                // SUMMARY/ACHIEVEMENT/CERTIFICATE/OTHER/CUSTOM：heading-only 条目还原为要点。
                if (bullets.isEmpty() && heading != null) {
                    bullets.add(ResumeDocumentBulletDTO.builder().text(heading).build());
                    heading = null;
                }
                if (meta != null) {
                    bullets.add(ResumeDocumentBulletDTO.builder().text(meta).build());
                }
                return builder.bullets(bullets).build();
            }
        }
    }

    private void applyDateRange(ResumeDocumentEntryDTO.ResumeDocumentEntryDTOBuilder builder, String meta) {
        if (meta == null) {
            return;
        }
        Matcher range = DATE_RANGE.matcher(meta);
        if (range.find()) {
            builder.startDate(range.group(1).strip()).endDate(range.group(2).strip());
        } else {
            builder.startDate(meta);
        }
    }

    /**
     * V1 经历 meta 形如「2022.07 - 至今」或「2022.07 - 至今 · Java 后端」。
     * 能确定拆出日期区间则拆分；剩余文本整体作为职位保留，不做进一步猜测。
     */
    private void applyDateRangeOrMeta(ResumeDocumentEntryDTO.ResumeDocumentEntryDTOBuilder builder, String meta) {
        if (meta == null) {
            return;
        }
        Matcher range = DATE_RANGE.matcher(meta);
        if (range.find()) {
            builder.startDate(range.group(1).strip()).endDate(range.group(2).strip());
            String remainder = (meta.substring(0, range.start()) + " " + meta.substring(range.end()))
                    .replaceAll("[\\s·•\\-–—~～]+", " ")
                    .strip();
            if (!remainder.isEmpty()) {
                builder.role(remainder);
            }
        } else {
            builder.startDate(meta);
        }
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

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        String value = node.asText().strip();
        return value.isEmpty() ? null : value;
    }

    private String serialize(ResumeDocumentDTO document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历内容保存失败");
        }
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
