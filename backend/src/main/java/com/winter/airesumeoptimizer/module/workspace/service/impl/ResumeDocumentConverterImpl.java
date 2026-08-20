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
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentSectionKind;
import com.winter.airesumeoptimizer.module.workspace.service.ResumeDocumentConverter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ResumeDocumentConverterImpl implements ResumeDocumentConverter {

    private static final int MAX_CONTACTS = 20;
    private static final int MAX_SECTIONS = 30;
    private static final int MAX_ENTRIES_PER_SECTION = 100;
    private static final int MAX_BULLETS_PER_ENTRY = 100;
    private static final int NAME_MAX_LENGTH = 100;
    private static final int CONTACT_FIELD_MAX_LENGTH = 200;
    private static final int SECTION_TITLE_MAX_LENGTH = 100;
    private static final int ENTRY_FIELD_MAX_LENGTH = 200;
    private static final int BULLET_MAX_LENGTH = 4000;

    /** V1 解析写入 basicInfo 的已知英文键 → 用户可见中文标签。其他扩展键原样保留。 */
    private static final Map<String, String> BASIC_INFO_LABELS = Map.of(
            "gender", "性别",
            "age", "年龄",
            "degree", "学历",
            "school", "学校",
            "location", "所在地",
            "workYears", "工作年限");

    /** 与根字段 name / phone / email / jobIntention 重复的键，不重复进入联系方式。 */
    private static final Set<String> BASIC_INFO_EXCLUDED_KEYS = Set.of(
            "name", "phone", "email", "jobintention", "resumetype",
            "姓名", "名字", "电话", "手机", "手机号", "邮箱", "电子邮件", "求职意向");

    private final ObjectMapper objectMapper;
    private final ObjectMapper strictObjectMapper;

    public ResumeDocumentConverterImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.strictObjectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    }

    @Override
    public ResumeDocumentDTO fromParsedSnapshot(String structuredJson) {
        if (structuredJson == null || structuredJson.isBlank()) {
            throw new BusinessException(500, "简历内容尚未就绪，请先完成简历解析");
        }
        JsonNode root;
        try {
            // 先按真实解析 DTO 做严格反序列化，未知字段或错误节点类型都拒绝整次转换。
            strictObjectMapper.readValue(structuredJson, ResumeStructuredContentDTO.class);
            root = objectMapper.readTree(structuredJson);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历结构化内容格式不正确");
        }
        if (root == null || !root.isObject()) {
            throw new BusinessException(500, "简历结构化内容格式不正确");
        }

        // 表示优先级不能成为校验旁路：即使 rawSections/rawText 足够完整，
        // 并存的合法旧版业务集合也必须先通过空值、类型和上限校验。
        validateLegacyTopLevelCollections(root);

        ResumeDocumentDTO document = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(buildBasics(root))
                .sections(buildSections(root))
                .build();
        assignDeterministicIds(document);
        return normalize(document);
    }

    /**
     * 快照转换使用位置派生的稳定 ID（如 s-1-e-2-b-1），
     * 保证同一输入重复转换（如恢复优化前版本）得到完全一致的文档。
     */
    private void assignDeterministicIds(ResumeDocumentDTO document) {
        if (document.getBasics() != null && document.getBasics().getContacts() != null) {
            List<ResumeDocumentContactDTO> contacts = document.getBasics().getContacts();
            for (int index = 0; index < contacts.size(); index++) {
                contacts.get(index).setId("c-" + (index + 1));
            }
        }
        if (document.getSections() == null) {
            return;
        }
        List<ResumeDocumentSectionDTO> sections = document.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            ResumeDocumentSectionDTO section = sections.get(sectionIndex);
            String sectionId = "s-" + (sectionIndex + 1);
            section.setId(sectionId);
            if (section.getEntries() == null) {
                continue;
            }
            List<ResumeDocumentEntryDTO> entries = section.getEntries();
            for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
                ResumeDocumentEntryDTO entry = entries.get(entryIndex);
                String entryId = sectionId + "-e-" + (entryIndex + 1);
                entry.setId(entryId);
                if (entry.getBullets() == null) {
                    continue;
                }
                List<ResumeDocumentBulletDTO> bullets = entry.getBullets();
                for (int bulletIndex = 0; bulletIndex < bullets.size(); bulletIndex++) {
                    bullets.get(bulletIndex).setId(entryId + "-b-" + (bulletIndex + 1));
                }
            }
        }
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

    private ResumeDocumentBasicsDTO buildBasics(JsonNode root) {
        List<ResumeDocumentContactDTO> contacts = new ArrayList<>();
        addContact(contacts, "电话", textOrNull(root.path("phone")));
        addContact(contacts, "邮箱", textOrNull(root.path("email")));

        JsonNode basicInfo = root.path("basicInfo");
        if (basicInfo.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = basicInfo.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = trimToNull(field.getKey());
                String value = textOrNull(field.getValue());
                if (key == null || value == null) {
                    continue;
                }
                String label = resolveBasicInfoLabel(key);
                if (label == null) {
                    continue;
                }
                addContact(contacts, label, value);
            }
        }
        String jobIntention = textOrNull(root.path("jobIntention"));
        if (jobIntention != null) {
            addContact(contacts, "求职意向", jobIntention);
        }
        String highestEducation = textOrNull(root.path("highestEducation"));
        if (highestEducation != null) {
            addContact(contacts, "最高学历", highestEducation);
        }

        return ResumeDocumentBasicsDTO.builder()
                .name(textOrNull(root.path("name")))
                .contacts(contacts)
                .build();
    }

    private List<ResumeDocumentSectionDTO> buildSections(JsonNode root) {
        JsonNode rawSections = root.path("rawSections");
        if (nonEmptyArray(rawSections)) {
            List<ResumeDocumentSectionDTO> sections = buildSectionsFromRawSections(rawSections);
            appendMissingRawTextLines(textOrNull(root.path("rawText")), sections);
            return sections;
        }
        String rawText = textOrNull(root.path("rawText"));
        if (rawText != null) {
            return buildSectionsFromRawText(rawText);
        }
        JsonNode structuredData = root.path("structuredData");
        List<ResumeDocumentSectionDTO> sections = hasStructuredDataContent(structuredData)
                ? buildSectionsFromStructuredData(structuredData)
                : new ArrayList<>();
        appendMissingLegacyTopLevelContent(root, sections);
        if (!sections.isEmpty()) {
            return sections;
        }
        JsonNode displayModel = root.path("displayModel");
        if (displayModel.isObject() && hasDisplayContent(displayModel)) {
            return buildSectionsFromDisplayModel(displayModel, root);
        }
        return new ArrayList<>();
    }

    private void validateLegacyTopLevelCollections(JsonNode root) {
        textList(root.path("education"));
        textList(root.path("skills"));
        textList(root.path("projects"));
        textList(root.path("workExperiences"));
        textList(root.path("internships"));
        textList(root.path("campusExperiences"));
        textList(root.path("awards"));
        textList(root.path("certificates"));
        textList(root.path("others"));
    }

    /** V1 顶层字段可能与 structuredData 并存；只补未被表示的值，但所有值仍先完整校验。 */
    private void appendMissingLegacyTopLevelContent(
            JsonNode root, List<ResumeDocumentSectionDTO> sections) {
        String summary = textOrNull(root.path("summary"));
        if (summary != null && !isRepresented(summary, sections)) {
            addTextSection(sections, ResumeDocumentSectionKind.SUMMARY, "个人总结", summary);
        }
        appendMissingLegacyLines(sections, ResumeDocumentSectionKind.EDUCATION, "教育经历", root.path("education"));

        List<String> skills = missingValues(textList(root.path("skills")), sections);
        if (!skills.isEmpty()) {
            addSection(sections, ResumeDocumentSectionKind.SKILL, "技能", List.of(entry(null, null, skills)));
        }
        appendMissingLegacyLines(sections, ResumeDocumentSectionKind.EXPERIENCE, "工作经历", root.path("workExperiences"));
        appendMissingLegacyLines(sections, ResumeDocumentSectionKind.EXPERIENCE, "实习经历", root.path("internships"));
        appendMissingLegacyLines(sections, ResumeDocumentSectionKind.EXPERIENCE, "校园经历", root.path("campusExperiences"));
        appendMissingLegacyLines(sections, ResumeDocumentSectionKind.PROJECT, "项目经历", root.path("projects"));
        appendMissingLegacyLines(sections, ResumeDocumentSectionKind.ACHIEVEMENT, "荣誉奖项", root.path("awards"));
        appendMissingLegacyLines(sections, ResumeDocumentSectionKind.CERTIFICATE, "证书", root.path("certificates"));
        appendMissingLegacyLines(sections, ResumeDocumentSectionKind.OTHER, "其他内容", root.path("others"));
    }

    private void appendMissingLegacyLines(
            List<ResumeDocumentSectionDTO> sections,
            ResumeDocumentSectionKind kind,
            String title,
            JsonNode valuesNode) {
        addLinesSection(sections, kind, title, missingValues(textList(valuesNode), sections));
    }

    private List<String> missingValues(
            List<String> values, List<ResumeDocumentSectionDTO> sections) {
        return values.stream().filter(value -> !isRepresented(value, sections)).toList();
    }

    private boolean isRepresented(String value, List<ResumeDocumentSectionDTO> sections) {
        String expected = value.strip();
        return sections.stream()
                .flatMap(section -> section.getEntries().stream())
                .flatMap(entry -> {
                    List<String> texts = new ArrayList<>();
                    if (entry.getHeading() != null) texts.add(entry.getHeading());
                    if (entry.getMeta() != null) texts.add(entry.getMeta());
                    entry.getBullets().stream().map(ResumeDocumentBulletDTO::getText).forEach(texts::add);
                    return texts.stream();
                })
                .filter(text -> text != null && !text.isBlank())
                .map(String::strip)
                .anyMatch(text -> text.contains(expected));
    }

    /** 原始章节是解析器保存的完整用户文本，优先使用它避免卡片投影遗漏内容。 */
    private List<ResumeDocumentSectionDTO> buildSectionsFromRawSections(JsonNode rawSections) {
        List<ResumeDocumentSectionDTO> sections = new ArrayList<>();
        for (JsonNode rawSection : rawSections) {
            String title = firstNonNull(
                    textOrNull(rawSection.path("originalTitle")),
                    textOrNull(rawSection.path("displayName")));
            if (title == null) {
                throw new BusinessException(500, "简历原始章节缺少标题，无法安全转换");
            }
            List<String> blockTexts = new ArrayList<>();
            JsonNode blocks = rawSection.path("blocks");
            if (!blocks.isArray()) {
                throw new BusinessException(500, "简历原始章节格式不正确");
            }
            for (JsonNode block : blocks) {
                String text = textOrNull(block.path("text"));
                if (text == null) {
                    throw new BusinessException(500, "简历原始章节存在空内容，无法安全转换");
                }
                blockTexts.add(text);
            }
            if (blockTexts.isEmpty()) {
                continue;
            }
            addSection(
                    sections,
                    sectionKind(textOrNull(rawSection.path("normalizedSection"))),
                    title,
                    List.of(entry(null, null, blockTexts)));
        }
        return sections;
    }

    /** rawSections 异常缺行时，用同一冻结快照的 rawText 补足而不是静默遗漏。 */
    private void appendMissingRawTextLines(
            String rawText, List<ResumeDocumentSectionDTO> sections) {
        if (rawText == null) {
            return;
        }
        List<String> represented = sections.stream()
                .flatMap(section -> section.getEntries().stream())
                .flatMap(entry -> entry.getBullets().stream())
                .map(ResumeDocumentBulletDTO::getText)
                .filter(text -> text != null && !text.isBlank())
                .map(String::strip)
                .toList();
        List<String> missing = rawText.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .filter(line -> represented.stream()
                        .noneMatch(value -> value.contains(line)))
                .distinct()
                .toList();
        if (!missing.isEmpty()) {
            addSection(
                    sections,
                    ResumeDocumentSectionKind.OTHER,
                    "其他原始内容",
                    List.of(entry(null, null, missing)));
        }
    }

    /** 历史解析结果可能没有 rawSections；rawText 是这种情况下不丢内容的保守回退。 */
    private List<ResumeDocumentSectionDTO> buildSectionsFromRawText(String rawText) {
        List<String> lines = rawText.lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .toList();
        if (lines.isEmpty()) {
            throw new BusinessException(500, "简历原始文本为空，无法安全转换");
        }
        List<ResumeDocumentSectionDTO> sections = new ArrayList<>();
        addSection(
                sections,
                ResumeDocumentSectionKind.OTHER,
                "原始简历内容",
                List.of(entry(null, null, lines)));
        return sections;
    }

    private ResumeDocumentSectionKind sectionKind(String value) {
        if (value == null) {
            return ResumeDocumentSectionKind.CUSTOM;
        }
        return switch (value) {
            case "EDUCATION" -> ResumeDocumentSectionKind.EDUCATION;
            case "SKILLS" -> ResumeDocumentSectionKind.SKILL;
            case "WORK", "INTERNSHIP", "CAMPUS" -> ResumeDocumentSectionKind.EXPERIENCE;
            case "PROJECTS" -> ResumeDocumentSectionKind.PROJECT;
            case "ACHIEVEMENTS" -> ResumeDocumentSectionKind.ACHIEVEMENT;
            case "CERTIFICATES" -> ResumeDocumentSectionKind.CERTIFICATE;
            case "SUMMARY" -> ResumeDocumentSectionKind.SUMMARY;
            case "OTHERS", "UNKNOWN", "BASIC_INFO" -> ResumeDocumentSectionKind.OTHER;
            default -> ResumeDocumentSectionKind.CUSTOM;
        };
    }

    private boolean hasStructuredDataContent(JsonNode structuredData) {
        if (!structuredData.isObject()) {
            return false;
        }
        return textOrNull(structuredData.path("summary")) != null
                || nonEmptyArray(structuredData.path("education"))
                || nonEmptyArray(structuredData.path("experiences"))
                || nonEmptyArray(structuredData.path("projects"))
                || nonEmptyArray(structuredData.path("achievements"))
                || nonEmptyArray(structuredData.path("certificates"))
                || nonEmptyArray(structuredData.path("others"))
                || nonEmptyArray(structuredData.path("skills").path("keywords"))
                || structuredData.path("skills").path("groups").size() > 0;
    }

    private boolean hasDisplayContent(JsonNode displayModel) {
        return nonEmptyArray(displayModel.path("educationCards"))
                || nonEmptyArray(displayModel.path("workExperienceCards"))
                || nonEmptyArray(displayModel.path("internshipCards"))
                || nonEmptyArray(displayModel.path("campusExperienceCards"))
                || nonEmptyArray(displayModel.path("projectCards"))
                || nonEmptyArray(displayModel.path("achievementCards"))
                || nonEmptyArray(displayModel.path("certificateTags"))
                || nonEmptyArray(displayModel.path("pendingItems"))
                || !displayModel.path("summaryCard").path("content").asText("").isBlank()
                || nonEmptyArray(displayModel.path("skillSummary").path("topSkills"))
                || nonEmptyArray(displayModel.path("skillSummary").path("groups"));
    }

    private List<ResumeDocumentSectionDTO> buildSectionsFromDisplayModel(JsonNode displayModel, JsonNode root) {
        List<ResumeDocumentSectionDTO> sections = new ArrayList<>();

        String summary = textOrNull(displayModel.path("summaryCard").path("content"));
        addTextSection(sections, ResumeDocumentSectionKind.SUMMARY, "个人总结", summary);

        addEducationSection(sections, displayModel.path("educationCards"));
        addExperienceSection(sections, ResumeDocumentSectionKind.EXPERIENCE, "工作经历",
                displayModel.path("workExperienceCards"));
        addExperienceSection(sections, ResumeDocumentSectionKind.EXPERIENCE, "实习经历",
                displayModel.path("internshipCards"));
        addExperienceSection(sections, ResumeDocumentSectionKind.EXPERIENCE, "校园经历",
                displayModel.path("campusExperienceCards"));
        addProjectSection(sections, displayModel.path("projectCards"));
        addSkillSectionFromDisplayModel(sections, displayModel.path("skillSummary"), root.path("structuredData"));
        addAchievementSection(sections, displayModel.path("achievementCards"));
        addCertificateSection(sections, displayModel.path("certificateTags"));
        addTextSection(sections, ResumeDocumentSectionKind.OTHER, "其他内容",
                joinTextBullets(displayModel.path("pendingItems")));

        return sections;
    }

    private List<ResumeDocumentSectionDTO> buildSectionsFromStructuredData(JsonNode structuredData) {
        List<ResumeDocumentSectionDTO> sections = new ArrayList<>();
        if (!structuredData.isObject()) {
            return sections;
        }

        addTextSection(sections, ResumeDocumentSectionKind.SUMMARY, "个人总结",
                textOrNull(structuredData.path("summary")));

        addLinesSection(sections, ResumeDocumentSectionKind.EDUCATION, "教育经历",
                structuredData.path("education"));

        JsonNode experiences = structuredData.path("experiences");
        if (nonEmptyArray(experiences)) {
            List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
            for (JsonNode experience : experiences) {
                List<String> bullets = new ArrayList<>();
                addBulletText(bullets, textOrNull(experience.path("description")));
                appendAllUnique(bullets, textList(experience.path("bullets")));
                appendAllUnique(bullets, textList(experience.path("evidence")));
                entries.add(entry(
                        joinNonBlank(" · ",
                                textOrNull(experience.path("organization")),
                                textOrNull(experience.path("role"))),
                        joinNonBlank(" - ",
                                textOrNull(experience.path("startDate")),
                                textOrNull(experience.path("endDate"))),
                        bullets));
            }
            addSection(sections, ResumeDocumentSectionKind.EXPERIENCE, "工作经历", entries);
        }

        JsonNode projects = structuredData.path("projects");
        if (nonEmptyArray(projects)) {
            List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
            for (JsonNode project : projects) {
                List<String> bullets = new ArrayList<>();
                addBulletText(bullets, textOrNull(project.path("description")));
                appendAllUnique(bullets, textList(project.path("responsibilities")));
                appendAllUnique(bullets, textList(project.path("evidence")));
                addLabeledBullet(bullets, "技术栈", joinNonBlank("、", textList(project.path("techStack"))));
                addLabeledBullet(bullets, "开发环境", textOrNull(project.path("environment")));
                addLabeledBullet(bullets, "导师", textOrNull(project.path("mentor")));
                entries.add(entry(
                        textOrNull(project.path("name")),
                        joinNonBlank(" · ",
                                textOrNull(project.path("role")),
                                firstNonNull(textOrNull(project.path("timeRange")),
                                        joinNonBlank(" - ",
                                                textOrNull(project.path("startDate")),
                                                textOrNull(project.path("endDate"))))),
                        bullets));
            }
            addSection(sections, ResumeDocumentSectionKind.PROJECT, "项目经历", entries);
        }

        addSkillSectionFromStructuredData(sections, structuredData.path("skills"));

        JsonNode achievements = structuredData.path("achievements");
        if (nonEmptyArray(achievements)) {
            List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
            for (JsonNode achievement : achievements) {
                String meta = joinNonBlank(" · ",
                        textOrNull(achievement.path("level")),
                        textOrNull(achievement.path("competition")),
                        textOrNull(achievement.path("ranking")),
                        firstNonNull(textOrNull(achievement.path("timeRange")),
                                textOrNull(achievement.path("date"))));
                entries.add(entry(
                        textOrNull(achievement.path("title")),
                        meta,
                        textList(achievement.path("evidence"))));
            }
            addSection(sections, ResumeDocumentSectionKind.ACHIEVEMENT, "荣誉奖项", entries);
        }

        addLinesSection(sections, ResumeDocumentSectionKind.CERTIFICATE, "证书",
                structuredData.path("certificates"));
        addLinesSection(sections, ResumeDocumentSectionKind.OTHER, "其他内容",
                structuredData.path("others"));

        return sections;
    }

    private void addEducationSection(List<ResumeDocumentSectionDTO> sections, JsonNode educationCards) {
        if (!nonEmptyArray(educationCards)) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (JsonNode card : educationCards) {
            List<String> bullets = new ArrayList<>();
            String degreeLine = joinNonBlank(" · ",
                    textOrNull(card.path("degree")), textOrNull(card.path("major")));
            addBulletText(bullets, degreeLine);
            addBulletText(bullets, textOrNull(card.path("summary")));
            entries.add(entry(textOrNull(card.path("school")),
                    textOrNull(card.path("timeRange")), bullets));
        }
        addSection(sections, ResumeDocumentSectionKind.EDUCATION, "教育经历", entries);
    }

    private void addExperienceSection(
            List<ResumeDocumentSectionDTO> sections,
            ResumeDocumentSectionKind kind,
            String title,
            JsonNode experienceCards) {
        if (!nonEmptyArray(experienceCards)) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (JsonNode card : experienceCards) {
            List<String> bullets = new ArrayList<>();
            appendAll(bullets, textList(card.path("responsibilities")));
            addBulletText(bullets, textOrNull(card.path("summary")));
            entries.add(entry(
                    joinNonBlank(" · ",
                            textOrNull(card.path("company")),
                            textOrNull(card.path("position"))),
                    textOrNull(card.path("timeRange")),
                    bullets));
        }
        addSection(sections, kind, title, entries);
    }

    private void addProjectSection(List<ResumeDocumentSectionDTO> sections, JsonNode projectCards) {
        if (!nonEmptyArray(projectCards)) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (JsonNode card : projectCards) {
            List<String> bullets = new ArrayList<>();
            String techStack = joinNonBlank("、", textList(card.path("techStack")));
            if (techStack != null) {
                bullets.add("技术栈：" + techStack);
            }
            appendAll(bullets, textList(card.path("responsibilities")));
            addBulletText(bullets, textOrNull(card.path("summary")));
            entries.add(entry(textOrNull(card.path("name")), null, bullets));
        }
        addSection(sections, ResumeDocumentSectionKind.PROJECT, "项目经历", entries);
    }

    private void addSkillSectionFromDisplayModel(
            List<ResumeDocumentSectionDTO> sections, JsonNode skillSummary, JsonNode structuredData) {
        List<String> bullets = new ArrayList<>();
        JsonNode groups = skillSummary.path("groups");
        if (nonEmptyArray(groups)) {
            for (JsonNode group : groups) {
                String skills = joinNonBlank("、", textList(group.path("skills")));
                String name = textOrNull(group.path("name"));
                if (skills == null) {
                    continue;
                }
                bullets.add(name == null ? skills : name + "：" + skills);
            }
        } else {
            bullets.addAll(textList(skillSummary.path("topSkills")));
        }
        if (bullets.isEmpty()) {
            addSkillSectionFromStructuredData(sections, structuredData.path("skills"));
            return;
        }
        addSection(sections, ResumeDocumentSectionKind.SKILL, "技能",
                List.of(entry(null, null, bullets)));
    }

    private void addSkillSectionFromStructuredData(List<ResumeDocumentSectionDTO> sections, JsonNode skills) {
        List<String> bullets = new ArrayList<>();
        JsonNode groups = skills.path("groups");
        if (groups.isObject() && groups.size() > 0) {
            Iterator<Map.Entry<String, JsonNode>> fields = groups.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> group = fields.next();
                String skillLine = joinNonBlank("、", textList(group.getValue()));
                if (skillLine == null) {
                    continue;
                }
                String label = trimToNull(group.getKey());
                bullets.add(label == null ? skillLine : label + "：" + skillLine);
            }
        }
        if (bullets.isEmpty()) {
            bullets.addAll(textList(skills.path("keywords")));
        }
        JsonNode evidence = skills.path("evidence");
        if (nonEmptyArray(evidence)) {
            for (JsonNode item : evidence) {
                addBulletTextUnique(bullets, textOrNull(item.path("sourceText")));
            }
        }
        if (bullets.isEmpty()) {
            return;
        }
        addSection(sections, ResumeDocumentSectionKind.SKILL, "技能",
                List.of(entry(null, null, bullets)));
    }

    private void addAchievementSection(List<ResumeDocumentSectionDTO> sections, JsonNode achievementCards) {
        if (!nonEmptyArray(achievementCards)) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (JsonNode card : achievementCards) {
            entries.add(entry(textOrNull(card.path("title")), textOrNull(card.path("meta")), List.of()));
        }
        addSection(sections, ResumeDocumentSectionKind.ACHIEVEMENT, "荣誉奖项", entries);
    }

    private void addCertificateSection(List<ResumeDocumentSectionDTO> sections, JsonNode certificateTags) {
        List<String> certificates = textList(certificateTags);
        if (certificates.isEmpty()) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (String certificate : certificates) {
            entries.add(entry(certificate, null, List.of()));
        }
        addSection(sections, ResumeDocumentSectionKind.CERTIFICATE, "证书", entries);
    }

    private void addLinesSection(
            List<ResumeDocumentSectionDTO> sections,
            ResumeDocumentSectionKind kind,
            String title,
            JsonNode linesNode) {
        addLinesSection(sections, kind, title, textList(linesNode));
    }

    private void addLinesSection(
            List<ResumeDocumentSectionDTO> sections,
            ResumeDocumentSectionKind kind,
            String title,
            List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (String line : lines) {
            entries.add(entry(line, null, List.of()));
        }
        addSection(sections, kind, title, entries);
    }

    private void addTextSection(
            List<ResumeDocumentSectionDTO> sections,
            ResumeDocumentSectionKind kind,
            String title,
            String text) {
        if (text == null) {
            return;
        }
        addSection(sections, kind, title, List.of(entry(null, null, List.of(text))));
    }

    private void addSection(
            List<ResumeDocumentSectionDTO> sections,
            ResumeDocumentSectionKind kind,
            String title,
            List<ResumeDocumentEntryDTO> entries) {
        if (entries.isEmpty()) {
            return;
        }
        sections.add(ResumeDocumentSectionDTO.builder()
                .kind(kind.name())
                .title(title)
                .entries(entries)
                .build());
    }

    private ResumeDocumentEntryDTO entry(String heading, String meta, List<String> bulletTexts) {
        List<ResumeDocumentBulletDTO> bullets = new ArrayList<>();
        for (String bulletText : bulletTexts) {
            if (bulletText == null || bulletText.isBlank()) {
                continue;
            }
            bullets.add(ResumeDocumentBulletDTO.builder().text(bulletText).build());
        }
        return ResumeDocumentEntryDTO.builder()
                .heading(heading)
                .meta(meta)
                .bullets(bullets)
                .build();
    }

    private ResumeDocumentBasicsDTO normalizeBasics(ResumeDocumentBasicsDTO basics, IdAllocator idAllocator) {
        if (basics == null || basics.getContacts() == null) {
            throw new BusinessException(400, "简历基础信息格式不正确");
        }
        if (basics.getContacts().size() > MAX_CONTACTS) {
            throw new BusinessException(400, "基础信息字段数量超出编辑上限");
        }
        List<ResumeDocumentContactDTO> contacts = new ArrayList<>();
        for (ResumeDocumentContactDTO contact : basics.getContacts()) {
            if (contact == null || contact.getLabel() == null || contact.getValue() == null) {
                throw new BusinessException(400, "简历基础信息格式不正确");
            }
            contacts.add(ResumeDocumentContactDTO.builder()
                    .id(idAllocator.allocate(contact.getId()))
                    .label(requireWithinLength(contact.getLabel(), CONTACT_FIELD_MAX_LENGTH, "基础信息字段名超出编辑上限"))
                    .value(requireWithinLength(contact.getValue(), CONTACT_FIELD_MAX_LENGTH, "基础信息字段值超出编辑上限"))
                    .build());
        }
        return ResumeDocumentBasicsDTO.builder()
                .name(requireWithinLength(basics.getName(), NAME_MAX_LENGTH, "姓名超出编辑上限"))
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
            normalized.add(ResumeDocumentEntryDTO.builder()
                    .id(idAllocator.allocate(entry.getId()))
                    .heading(requireWithinLength(entry.getHeading(), ENTRY_FIELD_MAX_LENGTH, "条目标题超出编辑上限"))
                    .meta(requireWithinLength(entry.getMeta(), ENTRY_FIELD_MAX_LENGTH, "条目辅助信息超出编辑上限"))
                    .bullets(normalizeBullets(entry.getBullets(), idAllocator))
                    .build());
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

    private void addContact(List<ResumeDocumentContactDTO> contacts, String label, String value) {
        if (value == null) {
            return;
        }
        if (contacts.size() >= MAX_CONTACTS) {
            // 无法完整保留解析内容时 fail closed，不静默丢字段。
            throw new BusinessException(500, "简历基础信息字段超出编辑上限，无法安全转换");
        }
        contacts.add(ResumeDocumentContactDTO.builder().label(label).value(value).build());
    }

    /** 已知键映射用户文案，其他扩展键原样保留；重复或内部字段显式排除。 */
    private String resolveBasicInfoLabel(String key) {
        String normalized = key.strip();
        if (BASIC_INFO_EXCLUDED_KEYS.contains(normalized)
                || BASIC_INFO_EXCLUDED_KEYS.contains(normalized.toLowerCase())) {
            return null;
        }
        String mapped = BASIC_INFO_LABELS.get(normalized);
        if (mapped != null) {
            return mapped;
        }
        // basicInfo 是解析器允许扩展的用户字段 Map；未知键也必须作为用户内容保留。
        return normalized;
    }

    private boolean nonEmptyArray(JsonNode node) {
        return node != null && node.isArray() && !node.isEmpty();
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isValueNode()) {
            return null;
        }
        return trimToNull(node.asText());
    }

    private List<String> textList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return values;
        }
        if (!node.isArray()) {
            throw new BusinessException(500, "简历结构化内容格式不正确");
        }
        if (node.size() > MAX_ENTRIES_PER_SECTION) {
            throw new BusinessException(500, "简历结构化内容超出安全转换上限");
        }
        for (JsonNode item : node) {
            String value = textOrNull(item);
            if (value == null) {
                throw new BusinessException(500, "简历结构化内容存在空值，无法安全转换");
            }
            values.add(value);
        }
        return values;
    }

    private String joinTextBullets(JsonNode node) {
        List<String> values = textList(node);
        return values.isEmpty() ? null : String.join("\n", values);
    }

    private void addBulletText(List<String> bullets, String text) {
        if (text != null && !text.isBlank()) {
            bullets.add(text);
        }
    }

    private void appendAll(List<String> bullets, List<String> values) {
        for (String value : values) {
            addBulletText(bullets, value);
        }
    }

    private void appendAllUnique(List<String> bullets, List<String> values) {
        for (String value : values) {
            addBulletTextUnique(bullets, value);
        }
    }

    private void addBulletTextUnique(List<String> bullets, String text) {
        if (text != null && !text.isBlank() && !bullets.contains(text)) {
            bullets.add(text);
        }
    }

    private void addLabeledBullet(List<String> bullets, String label, String value) {
        if (value != null && !value.isBlank()) {
            addBulletTextUnique(bullets, label + "：" + value);
        }
    }

    private String joinNonBlank(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.strip());
            }
        }
        return parts.isEmpty() ? null : String.join(separator, parts);
    }

    private String joinNonBlank(String separator, List<String> values) {
        return joinNonBlank(separator, values.toArray(new String[0]));
    }

    private String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.strip();
    }

    /**
     * 编辑上限是显式约束：超长内容直接拒绝保存而不是静默截断，避免丢失用户内容。
     */
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
