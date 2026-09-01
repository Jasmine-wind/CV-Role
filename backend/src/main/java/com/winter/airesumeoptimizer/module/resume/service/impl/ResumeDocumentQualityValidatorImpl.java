package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeQualityIssueDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeUnresolvedItemDTO;
import com.winter.airesumeoptimizer.module.resume.enums.ResumeQualityStatus;
import com.winter.airesumeoptimizer.module.resume.service.ResumeDocumentQualityValidator;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentContactType;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentSectionKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 确定性验证实现：只检测可代码判定的结构问题，不追求完美解析。
 * 可确定 → 接受；不确定 → 未决项（由调用方计数）；明显错误 → 阻止 READY。
 */
@Component
public class ResumeDocumentQualityValidatorImpl implements ResumeDocumentQualityValidator {

    static final String CODE_MISSING_NAME = "MISSING_NAME";
    static final String CODE_MISSING_REACHABLE_CONTACT = "MISSING_REACHABLE_CONTACT";
    static final String CODE_INVALID_CONTACT_FORMAT = "INVALID_CONTACT_FORMAT";
    static final String CODE_ENTRY_MISSING_TITLE = "ENTRY_MISSING_TITLE_WITH_MANY_BULLETS";
    static final String CODE_CROSS_SECTION_DUPLICATE = "CROSS_SECTION_DUPLICATE";
    static final String CODE_FIELD_TYPE_ANOMALY = "FIELD_TYPE_ANOMALY";
    static final String CODE_LINE_FRAGMENTATION = "LINE_FRAGMENTATION";
    static final String CODE_SYSTEM_ARTIFACT_PRESENT = "SYSTEM_ARTIFACT_PRESENT";
    static final String CODE_EMPTY_DOCUMENT_STRUCTURE = "EMPTY_DOCUMENT_STRUCTURE";
    static final String CODE_SINGLE_CONTACT_TYPE = "SINGLE_CONTACT_TYPE";
    static final String CODE_DUPLICATE_CONTACT = "DUPLICATE_CONTACT";
    static final String CODE_NO_EDUCATION_SECTION = "NO_EDUCATION_SECTION";
    static final String CODE_NO_SKILL_SECTION = "NO_SKILL_SECTION";
    static final String CODE_OVERLONG_BULLET = "OVERLONG_BULLET";
    static final String CODE_FEW_SECTIONS = "FEW_SECTIONS";
    static final String CODE_INVALID_SCHEMA_VERSION = "INVALID_SCHEMA_VERSION";
    static final String CODE_EMPTY_SECTION = "EMPTY_SECTION";
    static final String CODE_EMPTY_ENTRY = "EMPTY_ENTRY";
    static final String CODE_EMPTY_BULLET = "EMPTY_BULLET";
    static final String CODE_EMPTY_SKILL_ITEM = "EMPTY_SKILL_ITEM";
    static final String CODE_ENTRY_FIELD_MISMATCH = "ENTRY_FIELD_MISMATCH";

    private static final Pattern DATE_LIKE = Pattern.compile("^\\s*(?:19|20)\\d{2}\\s*[年.\\-/]");
    private static final Pattern SENTENCE_ENDING = Pattern.compile("[。！？!?；;，,、]");
    private static final Set<String> SYSTEM_SECTION_TITLES = Set.of(
            "未识别章节", "其他原始内容", "原始简历内容");
    private static final int DUPLICATE_MIN_LENGTH = 15;
    private static final int FRAGMENT_MIN_RUN = 3;
    private static final int FRAGMENT_MAX_LENGTH = 12;
    private static final int OVERLONG_BULLET_LENGTH = 300;

    @Override
    public ValidationResult validate(
            ResumeDocumentDTO document, List<ResumeUnresolvedItemDTO> unresolvedItems) {
        List<ResumeQualityIssueDTO> issues = new ArrayList<>();
        List<ResumeDocumentSectionDTO> sections = document == null ? null : document.getSections();
        boolean hasUnresolved = unresolvedItems != null && !unresolvedItems.isEmpty();
        if (document == null || !ResumeDocumentDTO.SCHEMA_VERSION.equals(document.getSchemaVersion())) {
            issues.add(blocker(CODE_INVALID_SCHEMA_VERSION, "文档结构版本不受支持"));
            return new ValidationResult(ResumeQualityStatus.QUALITY_NEEDS_REVIEW, List.copyOf(issues));
        }
        if (sections == null || sections.isEmpty()) {
            if (!hasUnresolved) {
                // 既无章节也无待确认内容：无法形成可编辑文档。
                issues.add(blocker(CODE_MISSING_REACHABLE_CONTACT, "文档没有可编辑章节，无法形成可投递文档"));
                return new ValidationResult(ResumeQualityStatus.QUALITY_FAILED, List.copyOf(issues));
            }
            // 内容保留在未决候选中：结构不可靠，必须由用户确认归位，不能静默宜称可投递。
            issues.add(blocker(CODE_EMPTY_DOCUMENT_STRUCTURE, "尚未形成章节结构，需确认待处理内容"));
            return new ValidationResult(ResumeQualityStatus.QUALITY_NEEDS_REVIEW, List.copyOf(issues));
        }

        checkBasics(document, issues);
        checkSections(sections, issues);

        boolean hasBlocker = issues.stream()
                .anyMatch(issue -> ResumeQualityIssueDTO.SEVERITY_BLOCKER.equals(issue.getSeverity()));
        String status = hasBlocker || hasUnresolved
                ? ResumeQualityStatus.QUALITY_NEEDS_REVIEW
                : ResumeQualityStatus.QUALITY_READY;
        return new ValidationResult(status, List.copyOf(issues));
    }

    private void checkBasics(ResumeDocumentDTO document, List<ResumeQualityIssueDTO> issues) {
        String name = document.getBasics() == null ? null : document.getBasics().getName();
        if (name == null || name.isBlank()) {
            issues.add(blocker(CODE_MISSING_NAME, "缺少姓名"));
        }
        if (document.getBasics() != null
                && (looksLikePhone(document.getBasics().getHighestEducation())
                || isValidEmail(document.getBasics().getHighestEducation())
                || isDateLike(document.getBasics().getHighestEducation()))) {
            issues.add(blocker(CODE_FIELD_TYPE_ANOMALY, "最高学历字段的类型不正确"));
        }
        List<ResumeDocumentContactDTO> contacts =
                document.getBasics() == null ? null : document.getBasics().getContacts();
        int reachable = 0;
        boolean invalidFormat = false;
        Set<String> contactKeys = new HashSet<>();
        if (contacts != null) {
            for (ResumeDocumentContactDTO contact : contacts) {
                if (contact == null) {
                    invalidFormat = true;
                    continue;
                }
                ResumeDocumentContactType type = ResumeDocumentContactType.fromValue(contact.getType());
                String value = contact.getValue() == null ? "" : contact.getValue().strip();
                if (value.isBlank()) {
                    invalidFormat = true;
                    continue;
                }
                String contactKey = type.name() + "|" + normalize(value);
                if (!contactKeys.add(contactKey)) {
                    issues.add(blocker(CODE_DUPLICATE_CONTACT, "联系方式重复"));
                }
                if (type == ResumeDocumentContactType.PHONE) {
                    if (ResumeDocumentQualityValidator.isValidPhone(value)) {
                        reachable++;
                    } else {
                        invalidFormat = true;
                    }
                } else if (type == ResumeDocumentContactType.EMAIL) {
                    if (ResumeDocumentQualityValidator.isValidEmail(value)) {
                        reachable++;
                    } else {
                        invalidFormat = true;
                    }
                }
            }
        }
        if (reachable == 0) {
            issues.add(blocker(CODE_MISSING_REACHABLE_CONTACT, "缺少可用的电话或邮箱"));
        } else if (reachable == 1) {
            issues.add(warning(CODE_SINGLE_CONTACT_TYPE, "仅有一种可联系方式"));
        }
        if (invalidFormat) {
            issues.add(blocker(CODE_INVALID_CONTACT_FORMAT, "电话或邮箱格式不正确"));
        }
    }

    private void checkSections(List<ResumeDocumentSectionDTO> sections, List<ResumeQualityIssueDTO> issues) {
        if (sections.size() < 3) {
            issues.add(warning(CODE_FEW_SECTIONS, "章节数量少于 3"));
        }
        boolean hasEducation = false;
        boolean hasSkill = false;
        Map<String, Set<String>> duplicates = new HashMap<>();
        Set<String> sectionTitles = new HashSet<>();
        for (ResumeDocumentSectionDTO section : sections) {
            if (section == null || section.getKind() == null || section.getTitle() == null
                    || section.getTitle().isBlank()) {
                issues.add(blocker(CODE_ENTRY_FIELD_MISMATCH, "存在格式不完整的章节"));
                continue;
            }
            String titleKey = normalize(section.getTitle());
            if (!sectionTitles.add(titleKey)) {
                issues.add(blocker(CODE_CROSS_SECTION_DUPLICATE, "存在重复章节标题"));
            }
            ResumeDocumentSectionKind kind = ResumeDocumentSectionKind.fromValue(section.getKind());
            if (kind == ResumeDocumentSectionKind.EDUCATION) {
                hasEducation = true;
            }
            if (kind == ResumeDocumentSectionKind.SKILL) {
                hasSkill = true;
            }
            if (section.getTitle() != null && SYSTEM_SECTION_TITLES.contains(section.getTitle().strip())) {
                issues.add(blocker(CODE_SYSTEM_ARTIFACT_PRESENT, "文档包含系统兜底章节：" + section.getTitle()));
            }
            List<ResumeDocumentEntryDTO> entries =
                    section.getEntries() == null ? List.of() : section.getEntries();
            if (entries.isEmpty()) {
                issues.add(blocker(CODE_EMPTY_SECTION, "章节「" + section.getTitle() + "」没有可交付内容"));
            }
            for (ResumeDocumentEntryDTO entry : entries) {
                checkEntry(section, kind, entry, issues);
                collectDuplicates(section, entry, duplicates);
            }
        }
        if (!hasEducation) {
            issues.add(warning(CODE_NO_EDUCATION_SECTION, "缺少教育经历章节"));
        }
        if (!hasSkill) {
            issues.add(warning(CODE_NO_SKILL_SECTION, "缺少技能章节"));
        }
        // 只有同一文本出现在两个及以上章节才算重复；单章节内的长文本不构成阻断。
        List<String> duplicated = duplicates.entrySet().stream()
                .filter(entry -> entry.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .toList();
        if (!duplicated.isEmpty()) {
            issues.add(blocker(CODE_CROSS_SECTION_DUPLICATE,
                    "同一内容出现在多个章节：" + String.join("；", duplicated)));
        }
    }

    private void checkEntry(
            ResumeDocumentSectionDTO section,
            ResumeDocumentSectionKind kind,
            ResumeDocumentEntryDTO entry,
            List<ResumeQualityIssueDTO> issues) {
        if (entry == null) {
            issues.add(blocker(CODE_EMPTY_ENTRY, "章节「" + section.getTitle() + "」存在空条目"));
            return;
        }
        int nonBlankBulletCount = 0;
        boolean hasBlankBullet = false;
        if (entry.getBullets() != null) {
            for (ResumeDocumentBulletDTO bullet : entry.getBullets()) {
                if (bullet == null || bullet.getText() == null || bullet.getText().isBlank()) {
                    hasBlankBullet = true;
                } else {
                    nonBlankBulletCount++;
                }
            }
        }
        if (hasBlankBullet) {
            issues.add(blocker(CODE_EMPTY_BULLET, "章节「" + section.getTitle() + "」存在空白要点"));
        }
        if (!hasStructuredFields(entry) && nonBlankBulletCount == 0) {
            issues.add(blocker(CODE_EMPTY_ENTRY, "章节「" + section.getTitle() + "」存在空条目"));
        }
        if (kind == ResumeDocumentSectionKind.EXPERIENCE || kind == ResumeDocumentSectionKind.PROJECT) {
            if (isBlank(entry.getOrganization())) {
                issues.add(blocker(CODE_ENTRY_MISSING_TITLE,
                        "章节「" + section.getTitle() + "」存在缺少公司或项目名的条目"));
            }
            if (hasEducationFields(entry) || hasSkillFields(entry)) {
                issues.add(blocker(CODE_ENTRY_FIELD_MISMATCH,
                        "章节「" + section.getTitle() + "」的条目字段与经历语义不匹配"));
            }
            if (isDateLike(entry.getOrganization())) {
                issues.add(blocker(CODE_FIELD_TYPE_ANOMALY, "公司/项目名字段是日期形态"));
            }
        } else if (kind == ResumeDocumentSectionKind.EDUCATION) {
            if (isBlank(entry.getSchool())) {
                issues.add(blocker(CODE_ENTRY_MISSING_TITLE, "教育经历存在缺少学校名的条目"));
            }
            if (hasExperienceFields(entry) || hasSkillFields(entry)) {
                issues.add(blocker(CODE_ENTRY_FIELD_MISMATCH, "教育经历条目字段与教育语义不匹配"));
            }
            if (isDateLike(entry.getSchool())) {
                issues.add(blocker(CODE_FIELD_TYPE_ANOMALY, "学校字段是日期形态"));
            }
            if (looksLikePhone(entry.getDegree())) {
                issues.add(blocker(CODE_FIELD_TYPE_ANOMALY, "学历字段是电话形态"));
            }
        } else if (kind == ResumeDocumentSectionKind.SKILL) {
            boolean hasBlankSkillItem = entry.getSkillItems() != null
                    && entry.getSkillItems().stream().anyMatch(item -> item == null || item.isBlank());
            boolean hasNonBlankSkillItem = entry.getSkillItems() != null
                    && entry.getSkillItems().stream().anyMatch(item -> item != null && !item.isBlank());
            if (hasBlankSkillItem) {
                issues.add(blocker(CODE_EMPTY_SKILL_ITEM, "技能章节存在空白技能项"));
            }
            if (hasNonSkillFields(entry) || nonBlankBulletCount > 0
                    || !hasNonBlankSkillItem) {
                issues.add(blocker(CODE_ENTRY_FIELD_MISMATCH, "技能章节必须使用技能组字段"));
            }
        } else if (kind == ResumeDocumentSectionKind.SUMMARY
                || kind == ResumeDocumentSectionKind.ACHIEVEMENT
                || kind == ResumeDocumentSectionKind.CERTIFICATE
                || kind == ResumeDocumentSectionKind.OTHER
                || kind == ResumeDocumentSectionKind.CUSTOM) {
            if (hasStructuredFields(entry)) {
                issues.add(blocker(CODE_ENTRY_FIELD_MISMATCH, "通用章节只能使用要点内容"));
            }
        }
        if (entry.getBullets() != null) {
            int fragmentRun = 0;
            boolean fragmentation = false;
            String previousText = null;
            for (ResumeDocumentBulletDTO bullet : entry.getBullets()) {
                String text = bullet == null || bullet.getText() == null ? "" : bullet.getText().strip();
                if (text.length() > OVERLONG_BULLET_LENGTH) {
                    issues.add(warning(CODE_OVERLONG_BULLET, "存在超长要点"));
                }
                boolean shortUnpunctuated = !text.isEmpty()
                        && text.length() < FRAGMENT_MAX_LENGTH
                        && !endsWithPunctuation(text);
                if (isLikelyContinuation(previousText, text)) {
                    fragmentation = true;
                }
                if (shortUnpunctuated) {
                    fragmentRun++;
                    if (fragmentRun >= FRAGMENT_MIN_RUN) {
                        fragmentation = true;
                    }
                } else {
                    fragmentRun = 0;
                }
                previousText = text;
            }
            if (fragmentation) {
                issues.add(blocker(CODE_LINE_FRAGMENTATION,
                        "章节「" + section.getTitle() + "」存在疑似被换行拆碎的连续短行"));
            }
        }
    }

    private boolean hasExperienceFields(ResumeDocumentEntryDTO entry) {
        return !isBlank(entry.getOrganization()) || !isBlank(entry.getRole());
    }

    private boolean hasEducationFields(ResumeDocumentEntryDTO entry) {
        return !isBlank(entry.getSchool())
                || !isBlank(entry.getDegree())
                || !isBlank(entry.getMajor());
    }

    private boolean hasSkillFields(ResumeDocumentEntryDTO entry) {
        return !isBlank(entry.getGroup())
                || (entry.getSkillItems() != null && !entry.getSkillItems().isEmpty());
    }

    private boolean hasNonSkillFields(ResumeDocumentEntryDTO entry) {
        return hasExperienceFields(entry)
                || hasEducationFields(entry)
                || !isBlank(entry.getStartDate())
                || !isBlank(entry.getEndDate())
                || !isBlank(entry.getLocation());
    }

    private boolean hasStructuredFields(ResumeDocumentEntryDTO entry) {
        return hasNonSkillFields(entry) || hasSkillFields(entry);
    }

    private boolean isLikelyContinuation(String previous, String current) {
        if (previous == null || previous.isBlank() || current == null || current.isBlank()) {
            return false;
        }
        if (previous.strip().length() <= 2) {
            return true;
        }
        String first = current.strip().substring(0, 1);
        return "均至动了和与的等到从由并而在为将把对至".contains(first);
    }

    private void collectDuplicates(
            ResumeDocumentSectionDTO section,
            ResumeDocumentEntryDTO entry,
            Map<String, Set<String>> duplicates) {
        List<String> texts = new ArrayList<>();
        // 只把完整 bullet 作为跨章节重复候选；公司名、学校名和技能词在多个章节出现是合法的。
        if (entry != null && entry.getBullets() != null) {
            entry.getBullets().forEach(bullet -> {
                if (bullet != null) {
                    appendText(texts, bullet.getText());
                }
            });
        }
        String sectionKey = section.getId() == null ? section.getTitle() : section.getId();
        for (String text : texts) {
            String normalized = normalize(text);
            if (normalized.length() < DUPLICATE_MIN_LENGTH) {
                continue;
            }
            Set<String> owners = duplicates.computeIfAbsent(normalized, key -> new HashSet<>());
            owners.add(sectionKey);
        }
    }

    static boolean isValidPhone(String value) {
        return ResumeDocumentQualityValidator.isValidPhone(value);
    }

    static boolean isValidEmail(String value) {
        return ResumeDocumentQualityValidator.isValidEmail(value);
    }

    private static boolean isDateLike(String value) {
        return value != null && DATE_LIKE.matcher(value).find();
    }

    private static boolean looksLikePhone(String value) {
        return value != null && !value.isBlank() && PHONE_FORMAT.matcher(value.strip()).matches();
    }

    private static boolean endsWithPunctuation(String text) {
        return SENTENCE_ENDING.matcher(text.substring(text.length() - 1)).find();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void appendText(List<String> texts, String text) {
        if (text != null && !text.isBlank()) {
            texts.add(text);
        }
    }

    private static String normalize(String text) {
        return text == null
                ? ""
                : text.replaceAll("[\\s\\p{Punct}、，。·．：:；;（）()\\[\\]【】]", "").toLowerCase(Locale.ROOT);
    }

    private static ResumeQualityIssueDTO blocker(String code, String message) {
        return ResumeQualityIssueDTO.builder()
                .code(code)
                .severity(ResumeQualityIssueDTO.SEVERITY_BLOCKER)
                .message(message)
                .build();
    }

    private static ResumeQualityIssueDTO warning(String code, String message) {
        return ResumeQualityIssueDTO.builder()
                .code(code)
                .severity(ResumeQualityIssueDTO.SEVERITY_WARNING)
                .message(message)
                .build();
    }
}
