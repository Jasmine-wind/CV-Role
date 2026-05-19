package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.SourceSectionConfidence;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextCleanService;
import java.text.Normalizer;
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

@Service
public class ResumeTextCleanServiceImpl implements ResumeTextCleanService {

    private static final Pattern HORIZONTAL_SPACE_PATTERN = Pattern.compile("[\\t\\x0B\\f\\r 　]+");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^[\\s>*•·●▪■◆◇○◦▶►✓✔-]+");
    private static final Pattern PAGE_FOOTER_PATTERN = Pattern.compile("^(?:第\\s*\\d+\\s*页(?:\\s*/\\s*共\\s*\\d+\\s*页)?|Page\\s+\\d+(?:\\s+of\\s+\\d+)?)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBERING_PREFIX_PATTERN = Pattern.compile("^(?:(?:\\(?\\d{1,3}\\)?|[一二三四五六七八九十百]+)[、.．)）:：]|[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳])\\s*(?<body>.*)$");
    private static final Pattern SYMBOL_ONLY_PATTERN = Pattern.compile("^[\\s\\p{Punct}，。；：、（）【】《》“”‘’·•●○◆◇■□▪◦▶►✓✔①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳]+$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\(\\+?86\\)|\\+?86|86)?[-\\s]*1[3-9]\\d[-\\s]?\\d{4}[-\\s]?\\d{4}(?!\\d)");
    private static final Pattern GITHUB_PATTERN = Pattern.compile("(?i)(?:https?://)?github\\.com/[A-Za-z0-9_.-]+");
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(".*(?:\\d{4}[./年-]\\d{1,2}|\\d{4}\\s*[-~—至]\\s*\\d{4}|至今|Present).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE_LIKE_PATTERN = Pattern.compile(".*(?:熟悉|掌握|了解|负责|参与|完成|实现|开发|维护|优化|具备|能够|主要|项目|系统|模块|需求|客户|评价).*");
    private static final String GENERAL_SECTION = "GENERAL";
    private static final String ICON_CHARS = "\uf0e0\uf095\uf0e1\uf09b\uf19d\uf0c0\uf085\uf08a\uf129";

    private static final List<String> TECH_HINTS = List.of(
            "java", "spring", "spring boot", "springmvc", "spring mvc", "mybatis", "mysql",
            "redis", "docker", "vue", "javascript", "typescript", "python", "linux",
            "git", "maven", "rabbitmq", "kafka", "dubbo", "zookeeper", "kubernetes",
            "nginx", "sql", "fastapi", "langchain", "rag", "c++", "verilog",
            "opencv", "yolo", "transformer", "pytorch", "tensorflow", "scikit-learn",
            "pandas", "matlab", "detr");

    private static final Map<Character, IconMapping> ICON_MAPPINGS = new LinkedHashMap<>();
    private static final List<String> HEADER_SIDE_SKILLS = List.of(
            "Python", "C++", "C", "Verilog", "Linux", "Java", "JavaScript", "TypeScript",
            "Git", "Docker", "MATLAB", "OpenCV", "YOLO", "DETR", "Transformer",
            "PyTorch", "TensorFlow", "Scikit-learn", "Pandas");

    private static final Map<String, List<String>> SECTION_ALIASES = new LinkedHashMap<>();

    static {
        SECTION_ALIASES.put("BASIC_INFO", List.of("个人信息", "基本信息", "联系方式", "个人资料", "Profile", "Personal Info"));
        SECTION_ALIASES.put("EDUCATION", List.of("教育经历", "教育背景", "学习经历", "学历背景", "Education", "Educational Background"));
        SECTION_ALIASES.put("SKILLS", List.of("专业技能", "技术能力", "技术能力描述", "技能关键词", "技能清单", "技术栈", "核心技能", "核心能力", "个人技能", "IT技能", "IT 技能", "Technique", "Skills", "Technical Skills", "Core Competencies"));
        SECTION_ALIASES.put("WORK_EXPERIENCES", List.of("工作经历", "工作经验", "职业经历", "任职经历", "任职公司", "从业经历", "Work Experience", "Professional Experience", "Employment History", "Experience"));
        SECTION_ALIASES.put("INTERNSHIPS", List.of("实习经历", "实习经验", "Internship", "Internship Experience"));
        SECTION_ALIASES.put("PROJECTS", List.of("项目经历", "项目经验", "项目介绍", "项目实践", "项目作品", "项目名称", "参加项目描述", "实习/项目经历", "实习 / 项目经历", "科研项目", "研究项目", "Projects", "Project Experience"));
        SECTION_ALIASES.put("CAMPUS_EXPERIENCES", List.of("在校经历", "校园经历", "校园实践", "社会实践", "社团经历", "学生工作", "Campus Experience", "Activities"));
        SECTION_ALIASES.put("AWARDS", List.of("获奖经历", "荣誉奖项", "奖项荣誉", "荣誉奖励", "获奖情况", "竞赛获奖", "Awards", "Honors"));
        SECTION_ALIASES.put("CERTIFICATES", List.of("证书", "资格证书", "专业证书", "认证", "Certificates", "Certifications"));
        SECTION_ALIASES.put("SUMMARY", List.of("自我评价", "个人总结", "个人优势", "自我介绍", "个人评价", "职业总结", "About me", "Summary", "Self Evaluation"));
        SECTION_ALIASES.put("OTHERS", List.of("其他", "其他说明", "补充信息", "其他信息", "Additional Information", "Others"));

        ICON_MAPPINGS.put('\uf0e0', new IconMapping("EMAIL_ICON", "BASIC_INFO"));
        ICON_MAPPINGS.put('\uf095', new IconMapping("PHONE_ICON", "BASIC_INFO"));
        ICON_MAPPINGS.put('\uf0e1', new IconMapping("LINKEDIN_ICON", "BASIC_INFO"));
        ICON_MAPPINGS.put('\uf09b', new IconMapping("GITHUB_ICON", "BASIC_INFO"));
        ICON_MAPPINGS.put('\uf19d', new IconMapping("EDUCATION_ICON", "EDUCATION"));
        ICON_MAPPINGS.put('\uf0c0', new IconMapping("EXPERIENCE_ICON", "PROJECTS"));
        ICON_MAPPINGS.put('\uf085', new IconMapping("SKILLS_ICON", "SKILLS"));
        ICON_MAPPINGS.put('\uf08a', new IconMapping("AWARDS_ICON", "AWARDS"));
        ICON_MAPPINGS.put('\uf129', new IconMapping("INFO_ICON", "OTHERS"));
    }

    @Override
    public ResumeTextCleanResultDTO cleanAndSplitSections(String extractedText) {
        CleanLines cleanLines = cleanLines(extractedText);
        List<ResumeTextSectionDTO> sections = splitSections(cleanLines.lines());
        return ResumeTextCleanResultDTO.builder()
                .cleanedText(String.join("\n", cleanLines.lines()))
                .sections(sections)
                .duplicateLineCount(cleanLines.duplicateLineCount())
                .invalidLineCount(cleanLines.invalidLineCount())
                .build();
    }

    private CleanLines cleanLines(String text) {
        if (text == null || text.isBlank()) {
            return new CleanLines(List.of(), 0, 0);
        }

        List<String> result = new ArrayList<>();
        Set<String> seenNormalizedLines = new LinkedHashSet<>();
        int duplicateCount = 0;
        int invalidCount = 0;
        boolean beforeFirstHeading = true;
        for (String rawLine : normalizeUnicode(text).lines().toList()) {
            String rawIconType = detectLeadingIconType(rawLine);
            String line = normalizeLine(rawLine);
            if (line.isBlank()) {
                if (!rawLine.isBlank()) {
                    invalidCount++;
                }
                continue;
            }
            if (PAGE_FOOTER_PATTERN.matcher(line).matches()) {
                invalidCount++;
                continue;
            }
            NumberingCleanResult numberingCleanResult = removeInvalidNumberingPrefix(line);
            if (numberingCleanResult.invalid()) {
                invalidCount++;
                continue;
            }
            if (numberingCleanResult.changed()) {
                line = numberingCleanResult.line();
            }

            List<String> expandedLines = expandTopMixedHeaderLine(line, rawIconType, beforeFirstHeading);
            for (String expandedLine : expandedLines) {
                String dedupeKey = normalizeForDedupe(expandedLine);
                if (!seenNormalizedLines.add(dedupeKey)) {
                    duplicateCount++;
                    continue;
                }

                appendLine(result, expandedLine);
            }
            if (matchHeading(line) != null) {
                beforeFirstHeading = false;
            }
        }
        return new CleanLines(result, duplicateCount, invalidCount);
    }

    private String normalizeLine(String rawLine) {
        String line = HORIZONTAL_SPACE_PATTERN.matcher(normalizeUnicode(rawLine)).replaceAll(" ").strip();
        line = stripLeadingIcon(line).strip();
        line = BULLET_PATTERN.matcher(line).replaceFirst("").strip();
        line = line.replaceAll("\\s+([,，、；;:：])", "$1")
                .replaceAll("([,，、；;:：])\\s+", "$1 ")
                .replaceAll("\\s+", " ")
                .strip();
        return line;
    }

    private List<String> expandTopMixedHeaderLine(String line, String iconType, boolean beforeFirstHeading) {
        if (line == null || line.isBlank()) {
            return List.of();
        }
        if (!beforeFirstHeading || matchHeading(line) != null) {
            return List.of(line);
        }
        if (line.matches(".*(?:邮箱|电话|手机|年龄|姓名|性别|学历|院校|学校|求职|目标).*")) {
            return List.of(line);
        }

        List<String> result = new ArrayList<>();
        String remaining = line.strip();
        if ("EMAIL_ICON".equals(iconType) || EMAIL_PATTERN.matcher(remaining).find()) {
            Matcher matcher = EMAIL_PATTERN.matcher(remaining);
            if (matcher.find()) {
                result.add(matcher.group());
                remaining = removeSpan(remaining, matcher.start(), matcher.end());
            }
            addHeaderTrailingSkill(result, remaining);
            return result.isEmpty() ? List.of(line) : result;
        }
        if ("PHONE_ICON".equals(iconType) || PHONE_PATTERN.matcher(remaining).find()) {
            Matcher matcher = PHONE_PATTERN.matcher(remaining);
            if (matcher.find()) {
                result.add(matcher.group().strip());
                remaining = removeSpan(remaining, matcher.start(), matcher.end());
            }
            addHeaderTrailingSkill(result, remaining);
            return result.isEmpty() ? List.of(line) : result;
        }
        if ("GITHUB_ICON".equals(iconType) || GITHUB_PATTERN.matcher(remaining).find()) {
            Matcher matcher = GITHUB_PATTERN.matcher(remaining);
            if (matcher.find()) {
                result.add("GitHub: " + matcher.group().strip());
                remaining = removeSpan(remaining, matcher.start(), matcher.end());
            }
            addHeaderTrailingSkill(result, remaining);
            return result.isEmpty() ? List.of(line) : result;
        }
        if ("LINKEDIN_ICON".equals(iconType)) {
            addHeaderTrailingSkill(result, remaining.replaceFirst("^-+$", "").strip());
            return result.isEmpty() ? List.of(line) : result;
        }

        String trailingSkill = trailingHeaderSkill(remaining);
        if (trailingSkill != null) {
            String left = remaining.substring(0, remaining.length() - trailingSkill.length()).strip();
            if (left.matches("[\\u4e00-\\u9fa5]{2,6}") || left.matches("[A-Za-z]+(?:[ .·-][A-Za-z]+){1,3}")) {
                result.add(left);
                result.add(trailingSkill);
                return result;
            }
        }
        return List.of(line);
    }

    private void addHeaderTrailingSkill(List<String> result, String value) {
        String cleaned = value == null ? "" : value.replaceFirst("^[-:：\\s]+", "").strip();
        String skill = trailingHeaderSkill(cleaned);
        if (skill != null) {
            result.add(skill);
        }
    }

    private String trailingHeaderSkill(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String cleaned = line.replaceFirst("^[-:：\\s]+", "").strip();
        for (String skill : HEADER_SIDE_SKILLS) {
            if (cleaned.equalsIgnoreCase(skill)) {
                return canonicalHeaderSkill(skill);
            }
            if (cleaned.matches("(?i).*\\s+" + Pattern.quote(skill) + "$")) {
                return canonicalHeaderSkill(skill);
            }
        }
        return null;
    }

    private String canonicalHeaderSkill(String skill) {
        return switch (skill.toLowerCase(Locale.ROOT)) {
            case "pytorch" -> "PyTorch";
            case "tensorflow" -> "TensorFlow";
            case "opencv" -> "OpenCV";
            case "yolo" -> "YOLO";
            default -> skill;
        };
    }

    private String removeSpan(String value, int start, int end) {
        return (value.substring(0, start) + " " + value.substring(end)).replaceAll("\\s+", " ").strip();
    }

    private NumberingCleanResult removeInvalidNumberingPrefix(String line) {
        if (isInvalidContentLine(line)) {
            return new NumberingCleanResult("", false, true);
        }
        Matcher matcher = NUMBERING_PREFIX_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return new NumberingCleanResult(line, false, false);
        }
        String body = normalizeLine(matcher.group("body"));
        if (isInvalidContentLine(body)) {
            return new NumberingCleanResult("", true, true);
        }
        return new NumberingCleanResult(body, true, false);
    }

    private boolean isInvalidContentLine(String line) {
        return line == null
                || line.isBlank()
                || SYMBOL_ONLY_PATTERN.matcher(line).matches()
                || line.matches("^(?:\\d{1,3}|[一二三四五六七八九十百]+)$")
                || line.matches("^[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳]$");
    }

    private void appendLine(List<String> result, String line) {
        if (result.isEmpty()) {
            result.add(line);
            return;
        }

        int lastIndex = result.size() - 1;
        String previous = result.get(lastIndex);
        if (shouldMergeBrokenLine(previous, line)) {
            result.set(lastIndex, previous.substring(0, previous.length() - 1) + line);
            return;
        }
        result.add(line);
    }

    private boolean shouldMergeBrokenLine(String previous, String current) {
        return previous.endsWith("-")
                && previous.length() > 1
                && !isSectionHeading(current);
    }

    private List<ResumeTextSectionDTO> splitSections(List<String> lines) {
        List<ResumeTextSectionDTO> sections = new ArrayList<>();
        String currentType = GENERAL_SECTION;
        String currentHeading = "未识别章节";
        SourceSectionConfidence currentConfidence = SourceSectionConfidence.LOW;
        List<String> currentLines = new ArrayList<>();

        for (String line : lines) {
            HeadingMatch headingMatch = matchHeading(line);
            if (headingMatch != null) {
                if (shouldKeepAsProjectContent(currentType, headingMatch, line)) {
                    currentLines.add(line);
                    continue;
                }
                List<String> forwardLines = takeForwardAttachLines(headingMatch.sectionType(), currentLines);
                if (!forwardLines.isEmpty()) {
                    List<String> remainingLines = currentLines.subList(0, currentLines.size() - forwardLines.size());
                    addSection(sections, currentType, currentHeading, currentConfidence, remainingLines);
                    currentType = headingMatch.sectionType();
                    currentHeading = headingMatch.heading();
                    currentConfidence = headingMatch.confidence();
                    currentLines = new ArrayList<>(forwardLines);
                    String inlineContent = removeHeading(line, headingMatch);
                    if (!inlineContent.isBlank()) {
                        currentLines.add(inlineContent);
                    }
                    continue;
                }
                addSection(sections, currentType, currentHeading, currentConfidence, currentLines);
                currentType = headingMatch.sectionType();
                currentHeading = headingMatch.heading();
                currentConfidence = headingMatch.confidence();
                currentLines = new ArrayList<>();
                String inlineContent = removeHeading(line, headingMatch);
                if (!inlineContent.isBlank()) {
                    currentLines.add(inlineContent);
                }
                continue;
            }
            currentLines.add(line);
        }

        addSection(sections, currentType, currentHeading, currentConfidence, currentLines);
        sections = postProcessSections(sections);
        if (sections.isEmpty()) {
            sections.add(ResumeTextSectionDTO.builder()
                    .sectionType(GENERAL_SECTION)
                    .heading("未识别章节")
                    .sourceSectionConfidence(SourceSectionConfidence.LOW.name())
                    .lines(List.of())
                    .build());
        }
        return sections;
    }

    private List<ResumeTextSectionDTO> postProcessSections(List<ResumeTextSectionDTO> sections) {
        List<ResumeTextSectionDTO> result = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            ResumeTextSectionDTO section = sections.get(index);
            if (GENERAL_SECTION.equals(section.getSectionType())
                    && index + 1 < sections.size()
                    && "SKILLS".equals(sections.get(index + 1).getSectionType())
                    && section.getLines().stream().allMatch(this::looksLikeSkillLine)) {
                ResumeTextSectionDTO next = sections.get(index + 1);
                sections.set(index + 1, rebuildSection(next, "SKILLS", next.getHeading(), mergeLines(section.getLines(), next.getLines())));
                continue;
            }
            if ("SUMMARY".equals(section.getSectionType()) && looksLikeSkillSummarySection(section)) {
                result.add(rebuildSection(section, "SKILLS", section.getHeading(), section.getLines().stream()
                        .filter(this::isUsefulSkillSummaryLine)
                        .toList()));
                continue;
            }
            if ("CAMPUS_EXPERIENCES".equals(section.getSectionType()) && looksLikeEducationSection(section)) {
                result.add(rebuildSection(section, "EDUCATION", section.getHeading(), section.getLines()));
                continue;
            }
            if ("EDUCATION".equals(section.getSectionType()) && looksLikeMixedActivityAndTailBasicInfo(section)) {
                List<String> activityLines = new ArrayList<>();
                List<String> basicInfoLines = new ArrayList<>();
                boolean basicInfoStarted = false;
                for (String line : section.getLines()) {
                    if (isTailBasicInfoLine(line)) {
                        basicInfoStarted = true;
                    }
                    if (basicInfoStarted) {
                        if (!isLowValueTemplateLine(line)) {
                            basicInfoLines.add(line);
                        }
                    } else {
                        activityLines.add(line);
                    }
                }
                if (!activityLines.isEmpty()) {
                    result.add(rebuildSection(section, "CAMPUS_EXPERIENCES", "教育背景", activityLines));
                }
                if (!basicInfoLines.isEmpty()) {
                    result.add(rebuildSection(section, "BASIC_INFO", "尾部个人信息", basicInfoLines));
                }
                continue;
            }
            result.add(section);
        }
        return mergeAdjacentSameTypeSections(result);
    }

    private ResumeTextSectionDTO rebuildSection(ResumeTextSectionDTO source, String sectionType, String heading, List<String> lines) {
        return ResumeTextSectionDTO.builder()
                .sectionType(sectionType)
                .heading(heading)
                .sourceSectionConfidence(SourceSectionConfidence.HIGH.name())
                .iconType(source.getIconType())
                .lines(lines.stream().map(String::strip).filter(line -> !line.isBlank()).distinct().toList())
                .blocks(source.getBlocks())
                .build();
    }

    private List<String> mergeLines(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>(first);
        result.addAll(second);
        return result.stream().filter(line -> !line.isBlank()).distinct().toList();
    }

    private List<ResumeTextSectionDTO> mergeAdjacentSameTypeSections(List<ResumeTextSectionDTO> sections) {
        List<ResumeTextSectionDTO> result = new ArrayList<>();
        for (ResumeTextSectionDTO section : sections) {
            if (!result.isEmpty()) {
                ResumeTextSectionDTO previous = result.get(result.size() - 1);
                if (previous.getSectionType().equals(section.getSectionType())) {
                    result.set(result.size() - 1, rebuildSection(previous, previous.getSectionType(),
                            previous.getHeading(), mergeLines(previous.getLines(), section.getLines())));
                    continue;
                }
            }
            if (!section.getLines().isEmpty()) {
                result.add(section);
            }
        }
        return result;
    }

    private boolean looksLikeSkillSummarySection(ResumeTextSectionDTO section) {
        long skillLines = section.getLines().stream()
                .filter(line -> !"本人".equals(line.strip()))
                .filter(this::looksLikeSkillLine)
                .count();
        return skillLines > 0 && skillLines >= Math.max(1, (section.getLines().size() - 1) / 2);
    }

    private boolean isUsefulSkillSummaryLine(String line) {
        return !"本人".equals(line.strip()) && (looksLikeSkillLine(line) || techHintCount(line) > 0);
    }

    private boolean looksLikeEducationSection(ResumeTextSectionDTO section) {
        boolean hasSchool = section.getLines().stream().anyMatch(line -> line.matches(".*(?:大学|学院|学校).*"));
        boolean hasMajorOrCourse = section.getLines().stream().anyMatch(line -> line.matches(".*(?:专业|主修课程|本科|专科|大专|硕士|博士).*"));
        return hasSchool && hasMajorOrCourse;
    }

    private boolean looksLikeMixedActivityAndTailBasicInfo(ResumeTextSectionDTO section) {
        boolean hasActivity = section.getLines().stream().anyMatch(line -> line.matches(".*(?:参加|组织|获得|协助|研究|学习|兼职|项目).*"));
        boolean hasTailBasicInfo = section.getLines().stream().anyMatch(this::isTailBasicInfoLine);
        return hasActivity && hasTailBasicInfo;
    }

    private boolean isTailBasicInfoLine(String line) {
        String normalized = normalizeHeading(line);
        return normalized.equals("personal resume")
                || normalized.startsWith("邮箱")
                || normalized.startsWith("email")
                || normalized.startsWith("求职意向")
                || normalized.startsWith("学历")
                || normalized.startsWith("电话")
                || EMAIL_PATTERN.matcher(line).find()
                || PHONE_PATTERN.matcher(line).find();
    }

    private boolean isLowValueTemplateLine(String line) {
        String normalized = normalizeHeading(line);
        return normalized.equals("personal resume")
                || normalized.equals("邮箱")
                || normalized.equals("email")
                || normalized.equals("邮箱:")
                || normalized.equals("email:");
    }

    private List<String> takeForwardAttachLines(String sectionType, List<String> currentLines) {
        List<String> result = new ArrayList<>();
        for (int index = currentLines.size() - 1; index >= 0; index--) {
            String line = currentLines.get(index);
            if (!matchesForwardSection(sectionType, line)) {
                break;
            }
            result.add(0, line);
        }
        return result;
    }

    private boolean matchesForwardSection(String sectionType, String line) {
        return switch (sectionType) {
            case "SKILLS" -> looksLikeSkillLine(line);
            case "SUMMARY" -> looksLikeSummaryLine(line);
            case "CAMPUS_EXPERIENCES" -> looksLikeCampusExperienceLine(line);
            case "EDUCATION" -> looksLikeEducationLine(line);
            default -> false;
        };
    }

    private boolean looksLikeSkillLine(String line) {
        String normalized = line.toLowerCase();
        return techHintCount(normalized) > 0
                || normalized.matches(".*(?:熟悉|掌握|精通|了解|具备|具有).*(?:开发|框架|数据库|语言|技术|工具|平台|编程|文档).*")
                || normalized.matches(".*(?:编程技巧|文档编写能力|开发工具|应用服务器).*");
    }

    private boolean looksLikeSummaryLine(String line) {
        return line.length() >= 12
                && line.matches(".*(?:本人|自我|性格|沟通|学习|责任心|团队|认真|积极|热爱|具备|能够|熟悉).*");
    }

    private boolean looksLikeCampusExperienceLine(String line) {
        return line.matches(".*(?:学生会|社团|班级|团委|校内|校园|在校|协会|志愿|活动|竞赛|组织|策划|干部|干事).*");
    }

    private boolean looksLikeEducationLine(String line) {
        return line.matches(".*(?:大学|学院|学校|本科|专科|大专|硕士|博士|学士|专业|学历|毕业).*");
    }

    private void addSection(
            List<ResumeTextSectionDTO> sections,
            String sectionType,
            String heading,
            SourceSectionConfidence confidence,
            List<String> lines) {
        List<String> nonBlankLines = lines.stream()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .toList();
        if (GENERAL_SECTION.equals(sectionType) && nonBlankLines.isEmpty() && !sections.isEmpty()) {
            return;
        }
        if (!GENERAL_SECTION.equals(sectionType) || !nonBlankLines.isEmpty()) {
            sections.add(ResumeTextSectionDTO.builder()
                    .sectionType(sectionType)
                    .heading(heading)
                    .sourceSectionConfidence(confidence == null ? SourceSectionConfidence.LOW.name() : confidence.name())
                    .iconType(iconTypeForSection(sectionType, heading))
                    .lines(nonBlankLines)
                    .build());
        }
    }

    private HeadingMatch matchHeading(String line) {
        if (!isLikelyHeadingLine(line)) {
            return null;
        }

        String normalizedLine = normalizeHeading(line);
        for (Map.Entry<String, List<String>> entry : SECTION_ALIASES.entrySet()) {
            for (String heading : entry.getValue()) {
                String normalizedHeading = normalizeHeading(heading);
                if (normalizedLine.equals(normalizedHeading)
                        || normalizedLine.startsWith(normalizedHeading + ":")
                        || normalizedLine.startsWith(normalizedHeading + "：")) {
                    return new HeadingMatch(entry.getKey(), heading, SourceSectionConfidence.HIGH);
                }
                if (allowInlineHeading(normalizedLine, normalizedHeading)) {
                    return new HeadingMatch(entry.getKey(), line.strip(), SourceSectionConfidence.MEDIUM);
                }
            }
        }
        return null;
    }

    private boolean shouldKeepAsProjectContent(String currentType, HeadingMatch headingMatch, String line) {
        return "PROJECTS".equals(currentType)
                && "SKILLS".equals(headingMatch.sectionType())
                && normalizeHeading(line).startsWith("技术栈");
    }

    private boolean allowInlineHeading(String normalizedLine, String normalizedHeading) {
        return normalizedLine.startsWith(normalizedHeading + " ")
                && normalizedLine.length() <= normalizedHeading.length() + 28;
    }

    private boolean isLikelyHeadingLine(String line) {
        String stripped = line.strip();
        if (stripped.length() > 60
                || EMAIL_PATTERN.matcher(stripped).find()
                || PHONE_PATTERN.matcher(stripped).find()
                || DATE_RANGE_PATTERN.matcher(stripped).matches()) {
            return false;
        }
        if (techHintCount(stripped) > 3) {
            return false;
        }
        return stripped.length() <= 18 || !SENTENCE_LIKE_PATTERN.matcher(stripped).matches();
    }

    private int techHintCount(String line) {
        String lower = line.toLowerCase();
        int count = 0;
        for (String techHint : TECH_HINTS) {
            if (lower.contains(techHint)) {
                count++;
            }
        }
        return count;
    }

    private boolean isSectionHeading(String line) {
        return matchHeading(line) != null;
    }

    private String removeHeading(String line, HeadingMatch headingMatch) {
        String stripped = line.strip();
        String pattern = "^" + Pattern.quote(headingMatch.heading()) + "\\s*[:：]?\\s*";
        String remaining = stripped.replaceFirst("(?i)" + pattern, "").strip();
        if (isHeadingAlias(headingMatch.sectionType(), remaining)) {
            return "";
        }
        return remaining;
    }

    private boolean isHeadingAlias(String sectionType, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = normalizeHeading(value);
        if (("SKILLS".equals(sectionType) && "technique".equals(normalized))
                || ("SUMMARY".equals(sectionType) && "about me".equals(normalized))
                || ("CAMPUS_EXPERIENCES".equals(sectionType) && "experience".equals(normalized))
                || ("EDUCATION".equals(sectionType) && "education".equals(normalized))) {
            return true;
        }
        return SECTION_ALIASES.getOrDefault(sectionType, List.of()).stream()
                .map(this::normalizeHeading)
                .anyMatch(normalized::equals);
    }

    private String normalizeHeading(String text) {
        return normalizeUnicode(text)
                .replaceAll("^[" + ICON_CHARS + "]+", "")
                .replaceAll("[\\s]+", " ")
                .replace('：', ':')
                .strip()
                .toLowerCase();
    }

    private String stripLeadingIcon(String line) {
        return line == null ? "" : line.replaceAll("^[" + ICON_CHARS + "]+\\s*", "");
    }

    private String detectLeadingIconType(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String stripped = normalizeUnicode(line).strip();
        if (stripped.isEmpty()) {
            return null;
        }
        IconMapping mapping = ICON_MAPPINGS.get(stripped.charAt(0));
        return mapping == null ? null : mapping.iconType();
    }

    private String iconTypeForSection(String sectionType, String heading) {
        if (heading != null) {
            String iconType = detectLeadingIconType(heading);
            if (iconType != null) {
                return iconType;
            }
        }
        return switch (sectionType == null ? "" : sectionType) {
            case "EDUCATION" -> "EDUCATION_ICON";
            case "PROJECTS", "INTERNSHIPS", "WORK_EXPERIENCES", "CAMPUS_EXPERIENCES" -> "EXPERIENCE_ICON";
            case "SKILLS" -> "SKILLS_ICON";
            case "AWARDS" -> "AWARDS_ICON";
            case "OTHERS" -> "INFO_ICON";
            default -> null;
        };
    }

    private String normalizeUnicode(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        text.replace('\u00A0', ' ').codePoints().forEach(codePoint -> {
            String value = new String(Character.toChars(codePoint));
            if (shouldNormalizeCjkCompatibility(codePoint)) {
                builder.append(Normalizer.normalize(value, Normalizer.Form.NFKC));
            } else {
                builder.append(value);
            }
        });
        return builder.toString()
                .replace('⻩', '黄')
                .replace('⼾', '户')
                .replace('⻔', '门')
                .replace('⻚', '页');
    }

    private boolean shouldNormalizeCjkCompatibility(int codePoint) {
        return (codePoint >= 0x2E80 && codePoint <= 0x2EFF)
                || (codePoint >= 0x2F00 && codePoint <= 0x2FDF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF);
    }

    private String normalizeForDedupe(String line) {
        return normalizeLine(line)
                .replaceAll("[\\s,，、；;:：.。]+", "")
                .toLowerCase();
    }

    private record HeadingMatch(String sectionType, String heading, SourceSectionConfidence confidence) {
    }

    private record IconMapping(String iconType, String sectionType) {
    }

    private record CleanLines(List<String> lines, int duplicateLineCount, int invalidLineCount) {
    }

    private record NumberingCleanResult(String line, boolean changed, boolean invalid) {
    }
}
