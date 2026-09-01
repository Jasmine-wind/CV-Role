package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAchievementDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeExperienceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillSetDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredDataDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeUnresolvedItemDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeCanonicalDocumentService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeDocumentQualityValidator;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentContactType;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentSectionKind;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * canonical 文档构建实现（Slice A）。
 *
 * <p>输入只是候选解析：只有能通过结构白名单的内容才进入正式文档；
 * 无法可靠判定归属的内容显式成为未决候选项，由用户确认，绝不机械追加兜底章节。
 */
@Service
public class ResumeCanonicalDocumentServiceImpl implements ResumeCanonicalDocumentService {

    private static final int MAX_CONTACTS = 20;
    private static final int MAX_SECTIONS = 30;
    private static final int MAX_ENTRIES_PER_SECTION = 100;
    private static final int MAX_BULLETS_PER_ENTRY = 100;
    private static final int MAX_OTHERS_CANDIDATES = 20;
    private static final int MAX_UNRESOLVED_ITEMS = 60;
    private static final int MIN_COVERAGE_LINE_LENGTH = 2;
    private static final int MIN_TOKEN_LENGTH = 2;
    /** “Email: x@y.z”一类短标签前缀的最大残差长度（归一化后）。 */
    private static final int CONTACT_LABEL_RESIDUE_MAX_LENGTH = 8;
    /** 有意义 token：连续中文或连续字母数字；未表示行必须全部 token 命中才算覆盖。 */
    private static final Pattern MEANINGFUL_TOKEN_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fa5]+|[A-Za-z0-9]+");
    /** Source-backed 校验使用更严格的词边界，避免 Java 被 JavaScript 的前缀误证明。 */
    private static final Pattern SOURCE_TOKEN_PATTERN =
            Pattern.compile("[\\u4e00-\\u9fa5]+|[A-Za-z0-9+#.-]+");

    /** 教育经历日期区间：原文字符串提取，不做语义解析。 */
    private static final Pattern EDUCATION_DATE_RANGE = Pattern.compile(
            "((?:19|20)\\d{2}(?:\\s*[年./\\-]\\s*\\d{1,2}\\s*月?)?)"
                    + "\\s*(?:[-–—~～至到]+|[-–—~～])\\s*"
                    + "((?:19|20)\\d{2}(?:\\s*[年./\\-]\\s*\\d{1,2}\\s*月?)?|至今|今|现在|今)");

    private static final Set<String> DEGREE_WORDS = Set.of(
            "本科", "硕士", "博士", "大专", "专科", "学士", "研究生", "高中", "中专", "博士后", "MBA");
    private static final Set<String> STRUCTURAL_HEADINGS = Set.of(
            "个人信息", "基本信息", "联系方式", "教育经历", "教育背景", "专业技能", "技术能力", "技能", "技能关键词",
            "技术栈", "工作经历", "工作经验", "职业经历", "实习经历", "项目经历", "项目经验", "校园经历", "在校经历",
            "获奖经历", "荣誉奖项", "证书", "自我评价", "个人总结", "个人概述", "个人优势", "自我介绍", "profile",
            "education", "skills", "experience", "projects", "summary");

    private static final Map<String, String> SKILL_GROUP_LABELS = Map.ofEntries(
            Map.entry("language", "编程语言"),
            Map.entry("framework", "框架"),
            Map.entry("database", "数据库"),
            Map.entry("frontend", "前端技术"),
            Map.entry("middleware", "中间件"),
            Map.entry("cv", "计算机视觉"),
            Map.entry("ai", "AI / 机器学习"),
            Map.entry("tool", "工具"),
            Map.entry("data", "数据分析"),
            Map.entry("other", "其他技能"));

    /** basicInfo 中与根字段重复的键，不重复进入文档。 */
    private static final Set<String> BASIC_INFO_EXCLUDED_KEYS = Set.of(
            "name", "phone", "email", "jobintention", "resumetype",
            "姓名", "名字", "电话", "手机", "手机号", "邮箱", "电子邮件", "求职意向");

    private final ObjectMapper objectMapper;

    public ResumeCanonicalDocumentServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public BuildResult build(ResumeStructuredContentDTO structuredContent) {
        List<ResumeUnresolvedItemDTO> unresolved = new ArrayList<>();
        if (structuredContent == null) {
            return new BuildResult(emptyDocument(), unresolved);
        }
        // 先构建章节，再基于已表示内容裁决基础信息，避免学校等字段重复成为未决项。
        String sourceText = structuredContent.getRawText();
        List<ResumeDocumentSectionDTO> sections = buildSections(structuredContent, unresolved, sourceText);
        String representedText = collectSectionText(sections);
        ResumeDocumentBasicsDTO basics = buildBasics(structuredContent, unresolved, representedText, sourceText);
        appendUnrepresentedLines(structuredContent, basics, sections, unresolved);
        ResumeDocumentDTO document = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(basics)
                .sections(sections)
                .build();
        assignDeterministicIds(document, unresolved);
        return new BuildResult(document, unresolved);
    }

    @Override
    public BuildResult buildFromStructuredJson(String structuredJson) {
        if (structuredJson == null || structuredJson.isBlank()) {
            throw new BusinessException(500, "简历内容尚未就绪，请先完成简历解析");
        }
        try {
            ResumeStructuredContentDTO content =
                    objectMapper.readValue(structuredJson, ResumeStructuredContentDTO.class);
            // Historical task snapshots may contain only the legacy top-level collections.
            // Rebuild their compatibility projection before applying the canonical builder;
            // this is read-only and never becomes the new parse/AI Source of Truth.
            if (content.getStructuredData() == null) {
                ResumeStructuredResultAssembler.enrich(content);
            }
            BuildResult result = build(content);
            if (result.document().getSections() == null || result.document().getSections().isEmpty()) {
                throw new BusinessException(500, "历史简历内容无法形成可编辑章节，请重新解析");
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历结构化内容格式不正确");
        }
    }

    private ResumeDocumentBasicsDTO buildBasics(
            ResumeStructuredContentDTO content,
            List<ResumeUnresolvedItemDTO> unresolved,
            String representedText,
            String sourceText) {
        List<ResumeDocumentContactDTO> contacts = new ArrayList<>();
        addTypedContact(contacts, unresolved, ResumeDocumentContactType.PHONE, content.getPhone(), sourceText);
        addTypedContact(contacts, unresolved, ResumeDocumentContactType.EMAIL, content.getEmail(), sourceText);

        String jobIntention = sourceBackedOrNull(content.getJobIntention(), sourceText);
        String highestEducation = sourceBackedOrNull(content.getHighestEducation(), sourceText);

        Map<String, String> basicInfo = content.getBasicInfo();
        if (basicInfo != null) {
            for (Map.Entry<String, String> field : basicInfo.entrySet()) {
                String key = trimToNull(field.getKey());
                String value = trimToNull(field.getValue());
                if (key == null || value == null) {
                    continue;
                }
                String normalizedKey = key.toLowerCase(Locale.ROOT);
                if (BASIC_INFO_EXCLUDED_KEYS.contains(key) || BASIC_INFO_EXCLUDED_KEYS.contains(normalizedKey)) {
                    continue;
                }
                if (normalizedKey.equals("location") || key.equals("所在地") || key.equals("城市")) {
                    if (sourceBacked(value, sourceText)) {
                        addContact(contacts, ResumeDocumentContactType.LOCATION, value);
                    }
                } else if (normalizedKey.equals("github") || normalizedKey.equals("linkedin")
                        || normalizedKey.equals("wechat") || normalizedKey.equals("qq")
                        || normalizedKey.equals("website")) {
                    if (sourceBacked(value, sourceText)) {
                        addContact(contacts, contactTypeForKey(normalizedKey), value);
                    }
                } else if (normalizedKey.equals("degree") || normalizedKey.equals("highesteducation")
                        || key.equals("学历") || key.equals("最高学历")) {
                    if (highestEducation == null && sourceBacked(value, sourceText)) {
                        highestEducation = value;
                    }
                } else if (normalizedKey.equals("jobintention") || key.equals("求职意向")) {
                    if (jobIntention == null && sourceBacked(value, sourceText)) {
                        jobIntention = value;
                    }
                } else if ((normalizedKey.equals("school") || normalizedKey.equals("university")
                        || key.equals("学校") || key.equals("院校"))
                        && represented(representedText, value)) {
                    // 学校已出现在教育经历等正式章节中，不重复进入未决候选。
                    continue;
                } else if (sourceBacked(key + "：" + value, sourceText)) {
                    // 性别/年龄/工作年限等非投递必需字段不自动进入正式文档，交由用户确认。
                    addFragment(unresolved, key + "：" + value, "无法安全归类的基础信息，请确认是否保留");
                }
            }
        }

        if (!hasReachableContact(contacts) && !hasContactCandidate(unresolved)) {
            addRequiredContactCandidate(unresolved);
        }
        String name = sourceBackedOrNull(content.getName(), sourceText);
        if (name == null) {
            addNameCandidate(unresolved, findNameCandidate(sourceText));
        }
        return ResumeDocumentBasicsDTO.builder()
                .name(name)
                .jobIntention(jobIntention)
                .highestEducation(highestEducation)
                .contacts(contacts)
                .build();
    }

    private boolean hasReachableContact(List<ResumeDocumentContactDTO> contacts) {
        if (contacts == null) {
            return false;
        }
        for (ResumeDocumentContactDTO contact : contacts) {
            if (contact == null || contact.getValue() == null) {
                continue;
            }
            ResumeDocumentContactType type = ResumeDocumentContactType.fromValue(contact.getType());
            if ((type == ResumeDocumentContactType.PHONE
                    && ResumeDocumentQualityValidator.isValidPhone(contact.getValue()))
                    || (type == ResumeDocumentContactType.EMAIL
                    && ResumeDocumentQualityValidator.isValidEmail(contact.getValue()))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasContactCandidate(List<ResumeUnresolvedItemDTO> unresolved) {
        return unresolved != null && unresolved.stream()
                .anyMatch(item -> item != null
                        && (ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE.equals(item.getKind())
                        || ResumeUnresolvedItemDTO.KIND_REQUIRED_CONTACT_CANDIDATE.equals(item.getKind())));
    }

    private void addRequiredContactCandidate(List<ResumeUnresolvedItemDTO> unresolved) {
        if (unresolved.size() >= MAX_UNRESOLVED_ITEMS) {
            throw new BusinessException(500, "未表示内容超过审查上限，请重新整理或重新解析简历");
        }
        unresolved.add(ResumeUnresolvedItemDTO.builder()
                .kind(ResumeUnresolvedItemDTO.KIND_REQUIRED_CONTACT_CANDIDATE)
                .canonicalDraft("{\"type\":\"PHONE\",\"label\":\"电话\",\"value\":\"\"}")
                .reason("缺少可用电话或邮箱，请补录后接受")
                .build());
    }

    private void addNameCandidate(List<ResumeUnresolvedItemDTO> unresolved, String sourceLine) {
        String candidate = trimToNull(sourceLine);
        if (unresolved.size() >= MAX_UNRESOLVED_ITEMS) {
            throw new BusinessException(500, "未表示内容超过审查上限，请重新整理或重新解析简历");
        }
        unresolved.add(ResumeUnresolvedItemDTO.builder()
                .kind(ResumeUnresolvedItemDTO.KIND_NAME_CANDIDATE)
                .canonicalDraft("{\"text\":" + jsonString(candidate == null ? "" : candidate) + "}")
                .sourceRef(candidate)
                .reason(candidate == null
                        ? "未能识别姓名，请手动填写并接受或删除"
                        : "未能安全确认姓名，请核对后编辑并接受或删除")
                .build());
    }

    private String findNameCandidate(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return null;
        }
        for (String line : sourceText.split("\\R")) {
            String candidate = trimToNull(line);
            if (candidate == null || candidate.length() > 40 || isStructuralHeading(candidate)
                    || ResumeDocumentQualityValidator.isValidEmail(candidate)
                    || ResumeDocumentQualityValidator.isValidPhone(candidate)
                    || candidate.matches(".*(?:19|20)\\d{2}.*")) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private void addTypedContact(
            List<ResumeDocumentContactDTO> contacts,
            List<ResumeUnresolvedItemDTO> unresolved,
            ResumeDocumentContactType type,
            String value,
            String sourceText) {
        String trimmed = trimToNull(value);
        if (trimmed == null || !sourceBacked(trimmed, sourceText)) {
            // AI 候选中不在原文出现的联系方式不是用户事实，直接丢弃，不进入审查候选。
            return;
        }
        boolean valid = type == ResumeDocumentContactType.PHONE
                ? ResumeDocumentQualityValidator.isValidPhone(trimmed)
                : ResumeDocumentQualityValidator.isValidEmail(trimmed);
        if (valid) {
            addContact(contacts, type, trimmed);
        } else {
            addContactCandidate(unresolved, type, trimmed);
        }
    }

    private void addContact(List<ResumeDocumentContactDTO> contacts, ResumeDocumentContactType type, String value) {
        boolean duplicate = contacts.stream()
                .anyMatch(contact -> type.name().equals(contact.getType()) && value.equals(contact.getValue()));
        if (duplicate) {
            return;
        }
        if (contacts.size() >= MAX_CONTACTS) {
            throw new BusinessException(500, "简历联系方式超出编辑上限，无法安全转换");
        }
        contacts.add(ResumeDocumentContactDTO.builder()
                .type(type.name())
                .label(type.getDefaultLabel())
                .value(value)
                .build());
    }

    private List<ResumeDocumentSectionDTO> buildSections(
            ResumeStructuredContentDTO content, List<ResumeUnresolvedItemDTO> unresolved, String sourceText) {
        ResumeStructuredDataDTO data = content.getStructuredData();
        List<ResumeDocumentSectionDTO> sections = new ArrayList<>();
        if (data == null) {
            return sections;
        }

        String summary = trimToNull(data.getSummary());
        if (summary != null && sourceBacked(summary, sourceText)) {
            addSection(sections, ResumeDocumentSectionKind.SUMMARY, "个人总结",
                    List.of(genericEntry(List.of(summary))));
        }

        // New canonical projections use the recruiter reading order. The V1 section list
        // remains the persisted display-order contract after this point; templates never
        // silently reorder an explicitly edited TARGET document.
        addExperienceSections(sections, data.getExperiences(), sourceText, unresolved);
        addProjectSection(sections, data.getProjects(), sourceText, unresolved);
        addEducationSection(sections, data.getEducation(), sourceText, unresolved);
        addSkillSection(sections, data.getSkills(), sourceText);
        addAchievementSection(sections, data.getAchievements(), sourceText);
        addCertificateSection(sections, data.getCertificates(), sourceText);

        if (data.getOthers() != null) {
            int added = 0;
            for (String other : data.getOthers()) {
                String text = trimToNull(other);
                if (text == null || !sourceBacked(text, sourceText)) {
                    // structured parser/LLM 可能输出原文之外的候选；它不是用户事实，不进入 sidecar。
                    continue;
                }
                if (added >= MAX_OTHERS_CANDIDATES) {
                    break;
                }
                addFragment(unresolved, text, "未归类内容，请确认归属章节或删除");
                added++;
            }
        }
        return sections;
    }

    private void addEducationSection(
            List<ResumeDocumentSectionDTO> sections,
            List<String> educationLines,
            String sourceText,
            List<ResumeUnresolvedItemDTO> unresolved) {
        if (educationLines == null || educationLines.isEmpty()) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (String line : educationLines) {
            String text = trimToNull(line);
            if (text == null || !sourceBacked(text, sourceText)) {
                continue;
            }
            ResumeDocumentEntryDTO entry = parseEducationEntry(text);
            if (entry.getSchool() == null || entry.getSchool().isBlank()) {
                addEntryCandidate(unresolved, ResumeDocumentSectionKind.EDUCATION, entry, text);
            } else {
                entries.add(entry);
            }
        }
        addSection(sections, ResumeDocumentSectionKind.EDUCATION, "教育经历", entries);
    }

    /**
     * 教育行的确定性拆分：先提取原文日期区间，再识别学校/学历/专业。
     * 无法可靠识别学校时不猜字段，整行保留为要点，内容不丢。
     */
    private ResumeDocumentEntryDTO parseEducationEntry(String line) {
        String startDate = null;
        String endDate = null;
        String remainder = line;
        Matcher range = EDUCATION_DATE_RANGE.matcher(line);
        if (range.find()) {
            startDate = range.group(1).strip();
            endDate = range.group(2).strip();
            remainder = (line.substring(0, range.start()) + " " + line.substring(range.end()))
                    .replaceAll("[\\s|｜·、,，:：\\-–—~～]+", " ")
                    .strip();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : remainder.split("[\\s|｜·、,，]+")) {
            String trimmed = trimToNull(token);
            if (trimmed != null) {
                tokens.add(trimmed);
            }
        }
        String school = null;
        String degree = null;
        List<String> majorParts = new ArrayList<>();
        for (String token : tokens) {
            if (school == null && (token.contains("大学") || token.contains("学院") || token.contains("学校"))) {
                school = token;
            } else if (degree == null && DEGREE_WORDS.contains(token)) {
                degree = token;
            } else {
                majorParts.add(token);
            }
        }
        if (school == null) {
            return genericEntry(List.of(line));
        }
        String major = majorParts.isEmpty() ? null : String.join(" ", majorParts);
        return ResumeDocumentEntryDTO.builder()
                .school(school)
                .degree(degree)
                .major(major)
                .startDate(startDate)
                .endDate(endDate)
                .bullets(new ArrayList<>())
                .build();
    }

    private void addExperienceSections(
            List<ResumeDocumentSectionDTO> sections,
            List<ResumeExperienceDTO> experiences,
            String sourceText,
            List<ResumeUnresolvedItemDTO> unresolved) {
        if (experiences == null || experiences.isEmpty()) {
            return;
        }
        Map<String, List<ResumeExperienceDTO>> grouped = new LinkedHashMap<>();
        for (ResumeExperienceDTO experience : experiences) {
            if (experience == null) {
                continue;
            }
            grouped.computeIfAbsent(experienceTypeOf(experience.getType()), key -> new ArrayList<>())
                    .add(experience);
        }
        for (Map.Entry<String, List<ResumeExperienceDTO>> group : grouped.entrySet()) {
            List<ExperienceBucket> buckets = groupExperiences(group.getValue());
            List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
            for (ExperienceBucket bucket : buckets) {
                List<String> bullets = sourceBackedDistinct(bucket.bullets, sourceText);
                String organization = sourceBackedOrNull(bucket.organization, sourceText);
                String role = sourceBackedOrNull(bucket.role, sourceText);
                String startDate = sourceBackedOrNull(bucket.startDate, sourceText);
                String endDate = sourceBackedOrNull(bucket.endDate, sourceText);
                if (isBlank(organization) && isBlank(role) && bullets.isEmpty()) {
                    continue;
                }
                ResumeDocumentEntryDTO entry = ResumeDocumentEntryDTO.builder()
                        .organization(organization)
                        .role(role)
                        .startDate(startDate)
                        .endDate(endDate)
                        .bullets(toBullets(bullets))
                        .build();
                if (isBlank(organization)) {
                    addEntryCandidate(unresolved, ResumeDocumentSectionKind.EXPERIENCE, entry, bucket.sourceRef());
                } else {
                    entries.add(entry);
                }
            }
            addSection(sections, ResumeDocumentSectionKind.EXPERIENCE, experienceTitle(group.getKey()), entries);
        }
    }

    /**
     * 规则解析的旧兼容模型可能一行一个 experience：只有带组织/日期的行是新条目，后续行并入前一条。
     * 这里不根据语气猜新公司；没有可识别标题的首条仍保留为无标题条目，由质量门阻止 READY。
     */
    private List<ExperienceBucket> groupExperiences(List<ResumeExperienceDTO> candidates) {
        List<ExperienceBucket> result = new ArrayList<>();
        for (ResumeExperienceDTO candidate : candidates == null ? List.<ResumeExperienceDTO>of() : candidates) {
            if (candidate == null) {
                continue;
            }
            boolean header = isExperienceHeader(candidate);
            if (header || result.isEmpty()) {
                result.add(new ExperienceBucket(candidate, header));
            } else {
                result.get(result.size() - 1).addContinuation(candidate);
            }
        }
        return result;
    }

    private boolean isExperienceHeader(ResumeExperienceDTO candidate) {
        return !isBlank(candidate.getOrganization())
                || !isBlank(candidate.getStartDate())
                || !isBlank(candidate.getEndDate());
    }

    /** 项目解析也可能退化为一行一个候选；明确日期是最小可靠条目边界。 */
    private List<ProjectBucket> groupProjects(List<ResumeProjectDTO> candidates) {
        List<ProjectBucket> result = new ArrayList<>();
        for (ResumeProjectDTO candidate : candidates == null ? List.<ResumeProjectDTO>of() : candidates) {
            if (candidate == null) {
                continue;
            }
            boolean header = result.isEmpty() || isProjectHeader(candidate);
            if (header) {
                result.add(new ProjectBucket(candidate, isProjectHeader(candidate)));
            } else {
                result.get(result.size() - 1).addContinuation(candidate);
            }
        }
        return result;
    }

    private boolean isProjectHeader(ResumeProjectDTO candidate) {
        return !isBlank(candidate.getStartDate())
                || !isBlank(candidate.getEndDate())
                || (candidate.getTimeRange() != null && candidate.getTimeRange().matches(".*(?:19|20)\\d{2}.*"));
    }

    private ResumeDocumentContactType contactTypeForKey(String key) {
        return switch (key) {
            case "github" -> ResumeDocumentContactType.GITHUB;
            case "linkedin" -> ResumeDocumentContactType.LINKEDIN;
            case "wechat" -> ResumeDocumentContactType.WECHAT;
            case "qq" -> ResumeDocumentContactType.QQ;
            case "website" -> ResumeDocumentContactType.WEBSITE;
            default -> ResumeDocumentContactType.OTHER;
        };
    }

    private String sourceBackedOrNull(String value, String sourceText) {
        String trimmed = trimToNull(value);
        return trimmed == null || !sourceBacked(trimmed, sourceText) ? null : trimmed;
    }

    private List<String> sourceBackedDistinct(List<String> values, String sourceText) {
        List<String> result = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null && sourceBacked(trimmed, sourceText)) {
                appendUnique(result, trimmed);
            }
        }
        return result;
    }

    /** 没有原文时保留单元测试/兼容投影的形状；生产解析在 ResumeService 入口会 fail closed。 */
    private boolean sourceBacked(String value, String sourceText) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (sourceText == null || sourceText.isBlank()) {
            return true;
        }
        List<String> expectedTokens = sourceTokens(value);
        List<String> sourceTokens = sourceTokens(sourceText);
        if (expectedTokens.isEmpty() || sourceTokens.isEmpty()) {
            return false;
        }
        // 允许 “Spring Boot” 与原文 “SpringBoot” 这类确定性空格差异，
        // 但不接受更长 token 的前缀（Java 不得从 JavaScript 推出）。
        String compactExpected = compactSourceToken(expectedTokens);
        if (sourceTokens.stream().anyMatch(token -> compactSourceToken(List.of(token)).equals(compactExpected))) {
            return true;
        }
        for (String expectedToken : expectedTokens) {
            if (sourceTokens.contains(expectedToken)) {
                continue;
            }
            // PDF 视觉换行可能把一个中文事实拆到相邻行。只接受非常保守的
            // “单字落在换行另一侧”情形；不要把两个完整相邻词（如“北京”+“上海”）
            // 拼成原文从未出现过的组织名或项目名。ASCII token 始终要求整 token。
            if (isChineseToken(expectedToken) && isChineseTokenBackedAcrossLineBreak(expectedToken, sourceText)) {
                continue;
            }
            return false;
        }
        return true;
    }

    private boolean isChineseToken(String token) {
        return token != null && token.matches("[\\u4e00-\\u9fa5]+");
    }

    private boolean isChineseTokenBackedAcrossLineBreak(String expectedToken, String sourceText) {
        String[] lines = sourceText == null ? new String[0] : sourceText.split("\\R", -1);
        for (int index = 0; index + 1 < lines.length; index++) {
            String left = normalize(lines[index]);
            String right = normalize(lines[index + 1]);
            if (left.isEmpty() || right.isEmpty()) {
                continue;
            }
            String joined = left + right;
            int boundary = left.length();
            int searchFrom = 0;
            while (searchFrom < joined.length()) {
                int start = joined.indexOf(expectedToken, searchFrom);
                if (start < 0) {
                    break;
                }
                int end = start + expectedToken.length();
                if (start < boundary && end > boundary
                        && Math.min(boundary - start, end - boundary) <= 1) {
                    return true;
                }
                searchFrom = start + 1;
            }
        }
        return false;
    }

    private List<String> sourceTokens(String value) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = SOURCE_TOKEN_PATTERN.matcher(value == null ? "" : value);
        while (matcher.find()) {
            tokens.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return tokens;
    }

    private String compactSourceToken(List<String> tokens) {
        return String.join("", tokens).replaceAll("[^a-z0-9+#\\u4e00-\\u9fa5]", "");
    }

    private String experienceTypeOf(String type) {
        return "INTERNSHIP".equals(type) ? "INTERNSHIP" : "CAMPUS".equals(type) ? "CAMPUS" : "WORK";
    }

    private String experienceTitle(String type) {
        return switch (type) {
            case "INTERNSHIP" -> "实习经历";
            case "CAMPUS" -> "校园经历";
            default -> "工作经历";
        };
    }

    private void addProjectSection(
            List<ResumeDocumentSectionDTO> sections,
            List<ResumeProjectDTO> projects,
            String sourceText,
            List<ResumeUnresolvedItemDTO> unresolved) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        List<ProjectBucket> buckets = groupProjects(projects);
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (ProjectBucket bucket : buckets) {
            List<String> bullets = sourceBackedDistinct(bucket.bullets, sourceText);
            List<String> skills = sourceBackedDistinct(bucket.techStack, sourceText);
            if (!skills.isEmpty()) {
                appendUnique(bullets, "技术栈：" + String.join("、", skills));
            }
            appendLabeled(bullets, "开发环境", sourceBackedOrNull(bucket.environment, sourceText));
            appendLabeled(bullets, "导师", sourceBackedOrNull(bucket.mentor, sourceText));
            String organization = sourceBackedOrNull(bucket.name, sourceText);
            String role = sourceBackedOrNull(bucket.role, sourceText);
            String startDate = sourceBackedOrNull(bucket.startDate, sourceText);
            String endDate = sourceBackedOrNull(bucket.endDate, sourceText);
            if (isBlank(organization) && isBlank(role) && bullets.isEmpty()) {
                continue;
            }
            ResumeDocumentEntryDTO entry = ResumeDocumentEntryDTO.builder()
                    .organization(organization)
                    .role(role)
                    .startDate(startDate)
                    .endDate(endDate)
                    .bullets(toBullets(bullets))
                    .build();
            if (isBlank(organization)) {
                addEntryCandidate(unresolved, ResumeDocumentSectionKind.PROJECT, entry, bucket.sourceRef());
            } else {
                entries.add(entry);
            }
        }
        addSection(sections, ResumeDocumentSectionKind.PROJECT, "项目经历", entries);
    }

    private void addSkillSection(
            List<ResumeDocumentSectionDTO> sections,
            ResumeSkillSetDTO skills,
            String sourceText) {
        if (skills == null) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        if (skills.getGroups() != null) {
            for (Map.Entry<String, List<String>> group : skills.getGroups().entrySet()) {
                List<String> items = sourceBackedDistinct(nonBlankDistinct(group.getValue()), sourceText);
                if (items.isEmpty()) {
                    continue;
                }
                entries.add(ResumeDocumentEntryDTO.builder()
                        .group(SKILL_GROUP_LABELS.getOrDefault(group.getKey(), group.getKey()))
                        .skillItems(items)
                        .bullets(new ArrayList<>())
                        .build());
            }
        }
        if (entries.isEmpty()) {
            List<String> keywords = sourceBackedDistinct(nonBlankDistinct(skills.getKeywords()), sourceText);
            if (!keywords.isEmpty()) {
                entries.add(ResumeDocumentEntryDTO.builder()
                        .skillItems(keywords)
                        .bullets(new ArrayList<>())
                        .build());
            }
        }
        addSection(sections, ResumeDocumentSectionKind.SKILL, "技能", entries);
    }

    private void addAchievementSection(
            List<ResumeDocumentSectionDTO> sections,
            List<ResumeAchievementDTO> achievements,
            String sourceText) {
        if (achievements == null || achievements.isEmpty()) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (ResumeAchievementDTO achievement : achievements) {
            if (achievement == null) {
                continue;
            }
            String joined = joinNonBlank(" · ",
                    achievement.getTitle(),
                    achievement.getLevel(),
                    achievement.getCompetition(),
                    achievement.getRanking(),
                    firstNonBlank(achievement.getTimeRange(), achievement.getDate()));
            if (joined == null || !sourceBacked(joined, sourceText)) {
                continue;
            }
            entries.add(genericEntry(List.of(joined)));
        }
        addSection(sections, ResumeDocumentSectionKind.ACHIEVEMENT, "荣誉奖项", entries);
    }

    private void addCertificateSection(
            List<ResumeDocumentSectionDTO> sections,
            List<String> certificates,
            String sourceText) {
        List<String> values = sourceBackedDistinct(nonBlankDistinct(certificates), sourceText);
        if (values.isEmpty()) {
            return;
        }
        List<ResumeDocumentEntryDTO> entries = new ArrayList<>();
        for (String certificate : values) {
            entries.add(genericEntry(List.of(certificate)));
        }
        addSection(sections, ResumeDocumentSectionKind.CERTIFICATE, "证书", entries);
    }

    /**
     * 覆盖检查：原材料中未被正式文档表示的行进入未决候选项。
     * 表示判定支持整行包含与逐 token 包含（姓名+电话、学校+专业+日期等被拆字段分别存在时不重复报警）。
     */
    private void appendUnrepresentedLines(
            ResumeStructuredContentDTO content,
            ResumeDocumentBasicsDTO basics,
            List<ResumeDocumentSectionDTO> sections,
            List<ResumeUnresolvedItemDTO> unresolved) {
        String rawText = trimToNull(content.getRawText());
        if (rawText == null) {
            return;
        }
        String normalizedDocument = normalize(collectDocumentText(basics, sections));
        List<String> contactValues = basics == null || basics.getContacts() == null
                ? List.of()
                : basics.getContacts().stream()
                        .map(ResumeDocumentContactDTO::getValue)
                        .filter(value -> value != null && !value.isBlank())
                        .toList();
        Set<String> seen = new LinkedHashSet<>();
        int overflow = 0;
        for (String line : rawText.split("\\R")) {
            String text = trimToNull(line);
            if (text == null || text.length() < MIN_COVERAGE_LINE_LENGTH
                    || isStructuralHeading(text)) {
                continue;
            }
            if (lineRepresented(normalizedDocument, contactValues, text)
                    || unresolvedLineRepresented(unresolved, text)
                    || !seen.add(normalize(text))) {
                continue;
            }
            if (unresolved.size() >= MAX_UNRESOLVED_ITEMS) {
                overflow++;
                continue;
            }
            addFragment(unresolved, text, "该内容未被正式文档包含，请确认归属章节或删除");
        }
        if (overflow > 0) {
            // A generated count is not user material and must never be accepted into
            // a formal section. Once the review sidecar cannot represent every line,
            // fail closed instead of presenting an incomplete review as exhaustive.
            throw new BusinessException(500, "未表示内容超过审查上限，请重新整理或重新解析简历");
        }
    }

    private boolean isStructuralHeading(String text) {
        return STRUCTURAL_HEADINGS.contains(normalize(text));
    }

    private boolean lineRepresented(String normalizedDocument, List<String> contactValues, String line) {
        String normalizedLine = normalize(line);
        if (normalizedLine.isEmpty()) {
            return true;
        }
        if (normalizedDocument.contains(normalizedLine)) {
            return true;
        }
        // 联系方式带短标签前缀（如 “Email: x@y.z”）：值已入文档且剩余残差很短时视为已覆盖。
        for (String contactValue : contactValues) {
            String normalizedValue = normalize(contactValue);
            if (normalizedValue.isEmpty() || !normalizedLine.contains(normalizedValue)) {
                continue;
            }
            int residueLength = normalizedLine.length() - normalizedValue.length();
            if (residueLength <= CONTACT_LABEL_RESIDUE_MAX_LENGTH) {
                return true;
            }
        }
        // 结构化字段常会把“技能组：”等标签替换为规范标签；标签后所有事实 token
        // 都必须被表示，不能用比例掩盖遗漏。
        int fullWidthColon = line.indexOf('：');
        int asciiColon = line.indexOf(':');
        int colon = fullWidthColon < 0
                ? asciiColon
                : asciiColon < 0 ? fullWidthColon : Math.min(fullWidthColon, asciiColon);
        if (colon > 0 && colon < line.length() - 1
                && allTokensRepresented(normalizedDocument, line.substring(colon + 1))) {
            return true;
        }
        // 被拆到不同字段或被重建的行（如“学校 专业 日期”、技术栈枚举）：
        // 只有行内所有有意义 token 都已出现在文档中，才视为内容已表示；缺一个也进入未决。
        List<String> tokens = meaningfulTokens(line);
        if (tokens.isEmpty()) {
            return false;
        }
        if (tokens.size() == 1) {
            return normalizedDocument.contains(tokens.get(0));
        }
        int present = 0;
        for (String token : tokens) {
            if (normalizedDocument.contains(token)) {
                present++;
            }
        }
        return present == tokens.size();
    }

    private boolean allTokensRepresented(String normalizedDocument, String value) {
        List<String> tokens = meaningfulTokens(value);
        return !tokens.isEmpty() && tokens.stream().allMatch(normalizedDocument::contains);
    }

    /** 提取行内有意义的内容片段：连续中文或连续字母数字。分隔符/标点不作为 token。 */
    private List<String> meaningfulTokens(String line) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = MEANINGFUL_TOKEN_PATTERN.matcher(line);
        while (matcher.find()) {
            String token = normalize(matcher.group());
            if (token.length() >= MIN_TOKEN_LENGTH) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private String collectDocumentText(ResumeDocumentBasicsDTO basics, List<ResumeDocumentSectionDTO> sections) {
        return collectBasicsText(basics) + collectSectionText(sections);
    }

    /** 基础信息表示文本：求职意向/最高学历携带标签紧邻值，保证带标签的原始行可被覆盖判定。 */
    private String collectBasicsText(ResumeDocumentBasicsDTO basics) {
        StringBuilder text = new StringBuilder();
        if (basics == null) {
            return "";
        }
        appendIfPresent(text, basics.getName());
        if (basics.getContacts() != null) {
            basics.getContacts().forEach(contact -> appendIfPresent(text, contact.getValue()));
        }
        if (basics.getJobIntention() != null) {
            text.append("求职意向").append(basics.getJobIntention()).append('\n');
        }
        if (basics.getHighestEducation() != null) {
            text.append("最高学历").append(basics.getHighestEducation()).append('\n');
        }
        return text.toString();
    }

    private String collectSectionText(List<ResumeDocumentSectionDTO> sections) {
        StringBuilder text = new StringBuilder();
        if (sections == null) {
            return "";
        }
        for (ResumeDocumentSectionDTO section : sections) {
            appendIfPresent(text, section.getTitle());
            if (section.getEntries() == null) {
                continue;
            }
            for (ResumeDocumentEntryDTO entry : section.getEntries()) {
                appendIfPresent(text, entry.getOrganization());
                appendIfPresent(text, entry.getRole());
                appendIfPresent(text, entry.getSchool());
                appendIfPresent(text, entry.getDegree());
                appendIfPresent(text, entry.getMajor());
                appendIfPresent(text, entry.getStartDate());
                appendIfPresent(text, entry.getEndDate());
                appendIfPresent(text, entry.getLocation());
                appendIfPresent(text, entry.getGroup());
                if (entry.getSkillItems() != null) {
                    entry.getSkillItems().forEach(item -> appendIfPresent(text, item));
                }
                if (entry.getBullets() != null) {
                    entry.getBullets().forEach(bullet -> appendIfPresent(text, bullet.getText()));
                }
            }
        }
        return text.toString();
    }

    private boolean represented(String representedText, String value) {
        String normalizedValue = normalize(value);
        return !normalizedValue.isEmpty() && normalize(representedText).contains(normalizedValue);
    }

    private void addFragment(List<ResumeUnresolvedItemDTO> unresolved, String text, String reason) {
        String draft = "{\"text\":" + jsonString(text) + "}";
        boolean duplicate = unresolved.stream()
                .anyMatch(item -> ResumeUnresolvedItemDTO.KIND_TEXT_FRAGMENT.equals(item.getKind())
                        && draft.equals(item.getCanonicalDraft()));
        if (duplicate) {
            return;
        }
        unresolved.add(ResumeUnresolvedItemDTO.builder()
                .kind(ResumeUnresolvedItemDTO.KIND_TEXT_FRAGMENT)
                .canonicalDraft(draft)
                .reason(reason)
                .build());
    }

    private boolean unresolvedLineRepresented(List<ResumeUnresolvedItemDTO> unresolved, String line) {
        String normalizedLine = normalize(line);
        if (normalizedLine.isEmpty() || unresolved == null) {
            return false;
        }
        for (ResumeUnresolvedItemDTO item : unresolved) {
            if (item == null || item.getSourceRef() == null) {
                continue;
            }
            for (String sourceLine : item.getSourceRef().split("\\R")) {
                if (normalizedLine.equals(normalize(sourceLine))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void addEntryCandidate(
            List<ResumeUnresolvedItemDTO> unresolved,
            ResumeDocumentSectionKind kind,
            ResumeDocumentEntryDTO entry,
            String sourceRef) {
        if (unresolved.size() >= MAX_UNRESOLVED_ITEMS) {
            throw new BusinessException(500, "未表示内容超过审查上限，请重新整理或重新解析简历");
        }
        try {
            ObjectNode draft = objectMapper.valueToTree(entry);
            draft.put("kind", kind.name());
            unresolved.add(ResumeUnresolvedItemDTO.builder()
                    .kind(ResumeUnresolvedItemDTO.KIND_ENTRY_CANDIDATE)
                    .canonicalDraft(objectMapper.writeValueAsString(draft))
                    .sourceRef(trimToNull(sourceRef))
                    .reason(missingEntryTitleReason(kind))
                    .build());
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException(500, "候选条目序列化失败");
        }
    }

    private String missingEntryTitleReason(ResumeDocumentSectionKind kind) {
        return switch (kind) {
            case EDUCATION -> "教育经历缺少可确认的学校名，请补充后接受或删除";
            case PROJECT -> "项目经历缺少可确认的项目名，请补充后接受或删除";
            default -> "工作经历缺少可确认的公司名，请补充后接受或删除";
        };
    }

    private void addContactCandidate(
            List<ResumeUnresolvedItemDTO> unresolved, ResumeDocumentContactType type, String value) {
        unresolved.add(ResumeUnresolvedItemDTO.builder()
                .kind(ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE)
                .canonicalDraft("{\"type\":\"" + type.name() + "\",\"label\":\"" + type.getDefaultLabel()
                        + "\",\"value\":" + jsonString(value) + "}")
                .reason("该" + type.getDefaultLabel() + "格式无法确认，请核对后接受或删除")
                .build());
    }

    private String jsonString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "候选内容序列化失败");
        }
    }

    private void addSection(
            List<ResumeDocumentSectionDTO> sections,
            ResumeDocumentSectionKind kind,
            String title,
            List<ResumeDocumentEntryDTO> entries) {
        if (entries.isEmpty()) {
            return;
        }
        if (sections.size() >= MAX_SECTIONS) {
            throw new BusinessException(500, "简历章节数量超出编辑上限，无法安全转换");
        }
        if (entries.size() > MAX_ENTRIES_PER_SECTION) {
            throw new BusinessException(500, "单个章节的条目数量超出编辑上限，无法安全转换");
        }
        sections.add(ResumeDocumentSectionDTO.builder()
                .kind(kind.name())
                .title(title)
                .entries(entries)
                .build());
    }

    private ResumeDocumentEntryDTO genericEntry(List<String> bulletTexts) {
        return ResumeDocumentEntryDTO.builder()
                .bullets(toBullets(bulletTexts))
                .build();
    }

    private List<ResumeDocumentBulletDTO> toBullets(List<String> texts) {
        if (texts.size() > MAX_BULLETS_PER_ENTRY) {
            throw new BusinessException(500, "单个条目的要点数量超出编辑上限，无法安全转换");
        }
        List<ResumeDocumentBulletDTO> bullets = new ArrayList<>();
        for (String text : texts) {
            bullets.add(ResumeDocumentBulletDTO.builder().text(text).build());
        }
        return bullets;
    }

    /**
     * 构建结果使用位置派生的稳定 ID（c-1 / s-1 / s-1-e-1 / u-1），
     * 保证同一候选解析重复构建（含恢复优化前版本）得到完全一致的文档。
     */
    private void assignDeterministicIds(ResumeDocumentDTO document, List<ResumeUnresolvedItemDTO> unresolved) {
        if (document.getBasics() != null && document.getBasics().getContacts() != null) {
            List<ResumeDocumentContactDTO> contacts = document.getBasics().getContacts();
            for (int index = 0; index < contacts.size(); index++) {
                contacts.get(index).setId("c-" + (index + 1));
            }
        }
        if (document.getSections() != null) {
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
                    if (entry.getBullets() != null) {
                        List<ResumeDocumentBulletDTO> bullets = entry.getBullets();
                        for (int bulletIndex = 0; bulletIndex < bullets.size(); bulletIndex++) {
                            bullets.get(bulletIndex).setId(entryId + "-b-" + (bulletIndex + 1));
                        }
                    }
                }
            }
        }
        for (int index = 0; index < unresolved.size(); index++) {
            unresolved.get(index).setId("u-" + (index + 1));
        }
    }

    private static final class ExperienceBucket {

        private final String organization;
        private final String role;
        private final String startDate;
        private final String endDate;
        private final List<String> bullets = new ArrayList<>();
        private final List<String> sourceLines = new ArrayList<>();

        private ExperienceBucket(ResumeExperienceDTO source, boolean header) {
            this.organization = trimToNull(source.getOrganization());
            this.role = trimToNull(source.getRole());
            this.startDate = trimToNull(source.getStartDate());
            this.endDate = trimToNull(source.getEndDate());
            add(source, header);
        }

        private void addContinuation(ResumeExperienceDTO source) {
            add(source, false);
        }

        private void appendBodyText(String text) {
            String trimmed = trimToNull(text);
            if (trimmed == null) {
                return;
            }
            if (!bullets.isEmpty() && (bullets.get(bullets.size() - 1).equals(trimmed)
                    || bullets.get(bullets.size() - 1).endsWith(trimmed))) {
                return;
            }
            if (!bullets.isEmpty() && shouldMergeWrappedLine(bullets.get(bullets.size() - 1), trimmed)) {
                bullets.set(bullets.size() - 1, bullets.get(bullets.size() - 1) + trimmed);
            } else {
                appendUnique(bullets, trimmed);
            }
        }

        private void add(ResumeExperienceDTO source, boolean header) {
            appendSourceLine(source.getOrganization());
            appendSourceLine(source.getRole());
            appendSourceLine(source.getStartDate());
            appendSourceLine(source.getEndDate());
            String description = trimToNull(source.getDescription());
            appendSourceLine(description);
            boolean headerEcho = header && isExperienceHeaderEcho(source, description);
            if (!header || !headerEcho) {
                appendBodyText(description);
            }
            for (String bullet : source.getBullets() == null ? List.<String>of() : source.getBullets()) {
                appendSourceLine(bullet);
                if (!headerEcho || !sameText(description, bullet)) {
                    appendBodyText(bullet);
                }
            }
        }

        private void appendSourceLine(String text) {
            String trimmed = trimToNull(text);
            if (trimmed != null) {
                appendUnique(sourceLines, trimmed);
            }
        }

        private String sourceRef() {
            return String.join("\n", sourceLines);
        }

        private static boolean isExperienceHeaderEcho(ResumeExperienceDTO source, String description) {
            if (description == null || description.isBlank() || source.getOrganization() == null
                    || source.getOrganization().isBlank()) {
                return false;
            }
            String normalizedDescription = normalize(description);
            boolean hasOrganization = normalizedDescription.contains(normalize(source.getOrganization()));
            boolean hasDate = (!isBlank(source.getStartDate()) && normalizedDescription.contains(normalize(source.getStartDate())))
                    || (!isBlank(source.getEndDate()) && normalizedDescription.contains(normalize(source.getEndDate())));
            return hasOrganization && (hasDate || (!isBlank(source.getRole())
                    && normalizedDescription.contains(normalize(source.getRole()))));
        }
    }

    private static final class ProjectBucket {

        private final String name;
        private final String role;
        private final String startDate;
        private final String endDate;
        private String environment;
        private String mentor;
        private final List<String> bullets = new ArrayList<>();
        private final List<String> techStack = new ArrayList<>();
        private final List<String> sourceLines = new ArrayList<>();

        private ProjectBucket(ResumeProjectDTO source, boolean header) {
            this.name = safeProjectName(source.getName());
            this.role = trimToNull(source.getRole());
            this.startDate = firstNonBlank(source.getStartDate(), source.getTimeRange());
            this.endDate = trimToNull(source.getEndDate());
            this.environment = trimToNull(source.getEnvironment());
            this.mentor = trimToNull(source.getMentor());
            add(source, header);
        }

        private void addContinuation(ResumeProjectDTO source) {
            if (environment == null) {
                environment = trimToNull(source.getEnvironment());
            }
            if (mentor == null) {
                mentor = trimToNull(source.getMentor());
            }
            add(source, false);
        }

        private void add(ResumeProjectDTO source, boolean header) {
            appendSourceLine(source.getName());
            appendSourceLine(source.getRole());
            appendSourceLine(source.getStartDate());
            appendSourceLine(source.getEndDate());
            appendSourceLine(source.getTimeRange());
            appendSourceLine(source.getEnvironment());
            appendSourceLine(source.getMentor());
            String description = trimToNull(source.getDescription());
            appendSourceLine(description);
            boolean headerEcho = header && isProjectHeaderEcho(source, description, name);
            if (!headerEcho && !isProjectFieldOnly(description) && !sameText(description, name)) {
                appendBodyText(description);
            }
            for (String responsibility : source.getResponsibilities() == null
                    ? List.<String>of()
                    : source.getResponsibilities()) {
                appendSourceLine(responsibility);
                if (!sameText(responsibility, description) && !sameText(responsibility, name)) {
                    appendBodyText(responsibility);
                }
            }
            for (String item : source.getTechStack() == null ? List.<String>of() : source.getTechStack()) {
                appendSourceLine(item);
                appendUnique(techStack, item);
            }
        }

        private void appendSourceLine(String text) {
            String trimmed = trimToNull(text);
            if (trimmed != null) {
                appendUnique(sourceLines, trimmed);
            }
        }

        private String sourceRef() {
            return String.join("\n", sourceLines);
        }

        private void appendBodyText(String text) {
            String trimmed = trimToNull(text);
            if (trimmed == null) {
                return;
            }
            if (!bullets.isEmpty() && (bullets.get(bullets.size() - 1).equals(trimmed)
                    || bullets.get(bullets.size() - 1).endsWith(trimmed))) {
                return;
            }
            if (!bullets.isEmpty() && shouldMergeWrappedLine(bullets.get(bullets.size() - 1), trimmed)) {
                bullets.set(bullets.size() - 1, bullets.get(bullets.size() - 1) + trimmed);
            } else {
                appendUnique(bullets, trimmed);
            }
        }

        private boolean hasDate(ResumeProjectDTO source) {
            return !isBlank(source.getStartDate())
                    || !isBlank(source.getEndDate())
                    || !isBlank(source.getTimeRange());
        }
    }

    private static boolean shouldMergeWrappedLine(String previous, String current) {
        if (previous == null || current == null || previous.isBlank() || current.isBlank()
                || endsWithSentencePunctuation(previous)) {
            return false;
        }
        String first = current.strip().substring(0, 1);
        return "均动至性了和与的等到".contains(first);
    }

    private static boolean endsWithSentencePunctuation(String value) {
        String trimmed = value == null ? "" : value.strip();
        return !trimmed.isEmpty() && trimmed.matches(".*[。！？!?；;]$");
    }

    private static boolean isProjectHeaderEcho(
            ResumeProjectDTO source, String description, String name) {
        if (description == null || description.isBlank() || name == null || name.isBlank()
                || description.length() > 100 || !normalize(description).contains(normalize(name))) {
            return false;
        }
        boolean hasStart = !isBlank(source.getStartDate())
                && normalize(description).contains(normalize(source.getStartDate()));
        boolean hasEnd = !isBlank(source.getEndDate())
                && normalize(description).contains(normalize(source.getEndDate()));
        return hasStart || hasEnd;
    }

    private static String safeProjectName(String value) {
        String candidate = trimToNull(value);
        if (candidate == null || candidate.length() > 100
                || candidate.matches("^(负责|参与|使用|采用|通过|实现|开发|编写|维护|优化|设计|管理|完成|基于|做|对|是一个|该系统|该项目|主要).*")) {
            return null;
        }
        return candidate;
    }

    private static boolean isProjectFieldOnly(String value) {
        return value != null && value.strip().matches(
                "^(技术栈|技术选型|使用技术|开发框架|开发环境|开发工具|环境|项目周期|开发时间|时间)\\s*[:：].*");
    }

    private static boolean sameText(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(normalizedRight);
    }

    private ResumeDocumentDTO emptyDocument() {
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder().contacts(new ArrayList<>()).build())
                .sections(new ArrayList<>())
                .build();
    }

    private static void appendUnique(List<String> bullets, String text) {
        String trimmed = trimToNull(text);
        if (trimmed != null && !bullets.contains(trimmed)) {
            bullets.add(trimmed);
        }
    }

    private static void appendLabeled(List<String> bullets, String label, String value) {
        String trimmed = trimToNull(value);
        if (trimmed != null) {
            appendUnique(bullets, label + "：" + trimmed);
        }
    }

    private static List<String> nonBlankDistinct(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                appendUnique(result, value);
            }
        }
        return result;
    }

    private static String joinNonBlank(String separator, String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                parts.add(trimmed);
            }
        }
        return parts.isEmpty() ? null : String.join(separator, parts);
    }

    private static String firstNonBlank(String first, String second) {
        return trimToNull(first) != null ? first : second;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void appendIfPresent(StringBuilder builder, String text) {
        if (text != null && !text.isBlank()) {
            builder.append(text).append('\n');
        }
    }

    private static String normalize(String text) {
        return text == null
                ? ""
                : text.replaceAll("[\\s\\p{Punct}、，。·．：:；;（）()\\[\\]【】]", "").toLowerCase(Locale.ROOT);
    }
}
