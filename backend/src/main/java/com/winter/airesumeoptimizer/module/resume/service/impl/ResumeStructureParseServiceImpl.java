package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeStructureParseService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ResumeStructureParseServiceImpl implements ResumeStructureParseService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?86[-\\s]?)?1[3-9]\\d[-\\s]?\\d{4}[-\\s]?\\d{4}(?!\\d)");
    private static final Pattern NAME_LABEL_PATTERN = Pattern.compile("^(?:姓名|名字)[:：\\s]+(.{2,20})$");
    private static final List<String> SECTION_HEADINGS = List.of(
            "个人信息", "基本信息", "教育经历", "教育背景", "项目经历", "项目经验",
            "实习经历", "实习经验", "工作经历", "工作经验", "专业技能", "技能清单",
            "技能", "自我评价", "校园经历", "荣誉奖项");
    private static final List<String> SKILL_KEYWORDS = List.of(
            "Java", "Spring Boot", "Spring", "Spring Security", "MyBatis", "MySQL",
            "PostgreSQL", "Redis", "Docker", "Vue", "TypeScript", "JavaScript",
            "Python", "Linux", "Git", "Maven", "RESTful", "JWT", "HTML", "CSS");

    @Override
    public ResumeStructuredContentDTO parse(String rawText) {
        String normalizedText = normalizeRawText(rawText);
        List<String> lines = splitLines(normalizedText);

        return ResumeStructuredContentDTO.builder()
                .name(extractName(lines))
                .phone(extractPhone(normalizedText))
                .email(extractEmail(normalizedText))
                .education(extractSection(lines, List.of("教育经历", "教育背景")))
                .skills(extractSkills(normalizedText))
                .projects(extractSection(lines, List.of("项目经历", "项目经验")))
                .internships(extractSection(lines, List.of("实习经历", "实习经验", "工作经历", "工作经验")))
                .rawText(normalizedText)
                .build();
    }

    private String normalizeRawText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new BusinessException(400, "简历原始文本不能为空");
        }
        return rawText.strip();
    }

    private List<String> splitLines(String rawText) {
        return rawText.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private String extractEmail(String rawText) {
        Matcher matcher = EMAIL_PATTERN.matcher(rawText);
        return matcher.find() ? matcher.group() : null;
    }

    private String extractPhone(String rawText) {
        Matcher matcher = PHONE_PATTERN.matcher(rawText);
        if (!matcher.find()) {
            return null;
        }

        String phone = matcher.group().replaceAll("[\\s-]", "");
        if (phone.startsWith("+86")) {
            return phone.substring(3);
        }
        if (phone.startsWith("86") && phone.length() == 13) {
            return phone.substring(2);
        }
        return phone;
    }

    private String extractName(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = NAME_LABEL_PATTERN.matcher(line);
            if (matcher.find()) {
                return cleanName(matcher.group(1));
            }
        }

        return lines.stream()
                .limit(15)
                .filter(this::isLikelyNameLine)
                .map(this::cleanName)
                .findFirst()
                .orElse(null);
    }

    private boolean isLikelyNameLine(String line) {
        if (line.length() < 2 || line.length() > 20) {
            return false;
        }
        if (line.contains("@") || PHONE_PATTERN.matcher(line).find() || line.matches(".*\\d.*")) {
            return false;
        }
        if (isSectionHeading(line) || line.contains("简历") || line.contains("求职") || line.contains("应聘")) {
            return false;
        }
        if (isKnownSkill(line)) {
            return false;
        }
        return line.matches("[\\u4e00-\\u9fa5A-Za-z\\s·.]+");
    }

    private String cleanName(String name) {
        return name == null ? null : name.strip().replaceAll("\\s+", "");
    }

    private List<String> extractSkills(String rawText) {
        String lowerText = rawText.toLowerCase(Locale.ROOT);
        return SKILL_KEYWORDS.stream()
                .filter(skill -> lowerText.contains(skill.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
    }

    private boolean isKnownSkill(String line) {
        String normalizedLine = line.strip().toLowerCase(Locale.ROOT);
        return SKILL_KEYWORDS.stream()
                .map(skill -> skill.toLowerCase(Locale.ROOT))
                .anyMatch(normalizedLine::equals);
    }

    private List<String> extractSection(List<String> lines, List<String> headings) {
        List<String> sectionLines = new ArrayList<>();
        boolean collecting = false;

        for (String line : lines) {
            if (containsAnyHeading(line, headings)) {
                collecting = true;
                String inlineContent = removeHeadingPrefix(line, headings);
                if (!inlineContent.isBlank()) {
                    sectionLines.add(inlineContent);
                }
                continue;
            }

            if (collecting && isSectionHeading(line)) {
                break;
            }

            if (collecting) {
                sectionLines.add(cleanSectionLine(line));
            }
        }

        return sectionLines.stream()
                .filter(line -> !line.isBlank())
                .toList();
    }

    private boolean containsAnyHeading(String line, List<String> headings) {
        return headings.stream().anyMatch(heading -> line.startsWith(heading));
    }

    private String removeHeadingPrefix(String line, List<String> headings) {
        String result = line;
        for (String heading : headings) {
            if (result.startsWith(heading)) {
                result = result.substring(heading.length());
                break;
            }
        }
        return cleanSectionLine(result);
    }

    private boolean isSectionHeading(String line) {
        String normalizedLine = line.replaceAll("[:：\\s]", "");
        return SECTION_HEADINGS.stream().anyMatch(heading -> normalizedLine.equals(heading));
    }

    private String cleanSectionLine(String line) {
        return line.replaceFirst("^[-*•·\\s:：]+", "").strip();
    }
}
