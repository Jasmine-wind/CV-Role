package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceBlockRole;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.SourceSectionConfidence;
import com.winter.airesumeoptimizer.module.resume.service.ResumeLayoutAwareTextCleanService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextCleanService;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ResumeTextCleanServiceImpl implements ResumeTextCleanService, ResumeLayoutAwareTextCleanService {

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
        SECTION_ALIASES.put("SKILLS", List.of("技能", "专业技能", "技术能力", "技术能力描述", "技能关键词", "技能清单", "技术栈", "核心技能", "核心能力", "个人技能", "IT技能", "IT 技能", "Technique", "Skills", "Technical Skills", "Core Competencies"));
        SECTION_ALIASES.put("WORK_EXPERIENCES", List.of("工作经历", "工作经验", "职业经历", "任职经历", "任职公司", "从业经历", "Work Experience", "Professional Experience", "Employment History", "Experience"));
        SECTION_ALIASES.put("INTERNSHIPS", List.of("实习经历", "实习经验", "Internship", "Internship Experience"));
        SECTION_ALIASES.put("PROJECTS", List.of("项目经历", "项目经验", "项目介绍", "项目实践", "项目作品", "项目名称", "参加项目描述", "实习/项目经历", "实习 / 项目经历", "科研项目", "研究项目", "Projects", "Project Experience"));
        SECTION_ALIASES.put("CAMPUS_EXPERIENCES", List.of("在校经历", "校园经历", "校园实践", "社会实践", "社团经历", "学生工作", "Campus Experience", "Activities"));
        SECTION_ALIASES.put("AWARDS", List.of("获奖经历", "荣誉奖项", "奖项荣誉", "荣誉奖励", "获奖情况", "竞赛获奖", "Awards", "Honors"));
        SECTION_ALIASES.put("CERTIFICATES", List.of("证书", "资格证书", "专业证书", "认证", "Certificates", "Certifications"));
        SECTION_ALIASES.put("SUMMARY", List.of("自我评价", "个人总结", "个人概述", "个人优势", "自我介绍", "个人评价", "职业总结", "About me", "Summary", "Self Evaluation"));
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

    @Override
    public ResumeTextCleanResultDTO cleanAndSplitSections(String extractedText, List<ResumeBlockDTO> sourceBlocks) {
        if (sourceBlocks == null || sourceBlocks.isEmpty()) {
            return cleanAndSplitSections(extractedText);
        }
        boolean layoutCandidate = sourceBlocks.stream()
                .anyMatch(block -> block != null && "pdf-layout-lite".equalsIgnoreCase(block.getSourceType()));
        // Classify the visual rows before recovery so a section or entry header cannot be
        // swallowed as the continuation of the preceding paragraph.
        markStructuralRoles(sourceBlocks);
        List<ResumeBlockDTO> logicalBlocks = layoutCandidate
                ? recoverWrappedLines(sourceBlocks)
                : sourceBlocks.stream().filter(block -> block != null).toList();
        markStructuralRoles(logicalBlocks);
        String visualText = layoutCandidate
                ? logicalBlocks.stream()
                        .map(ResumeBlockDTO::getText)
                        .filter(line -> line != null && !line.isBlank())
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse(extractedText)
                : extractedText;
        ResumeTextCleanResultDTO result = cleanAndSplitSections(visualText);
        result.setSourceBlocks(logicalBlocks);
        result.setExtractionCandidateType(layoutCandidate ? "LAYOUT_LITE" : "SOURCE_METADATA");
        attachSourceBlocks(result.getSections(), logicalBlocks);
        return result;
    }

    private void markStructuralRoles(List<ResumeBlockDTO> blocks) {
        for (ResumeBlockDTO block : blocks) {
            if (block == null || block.getText() == null || block.getText().isBlank()) {
                continue;
            }
            if (matchHeading(block.getText()) != null) {
                block.setRole(ResumeSourceBlockRole.SECTION_HEADING);
            } else if (block.getRole() == null || block.getRole() == ResumeSourceBlockRole.UNKNOWN) {
                block.setRole(classifySourceRole(block.getText()));
            }
        }
    }

    private List<ResumeBlockDTO> recoverWrappedLines(List<ResumeBlockDTO> sourceBlocks) {
        List<ResumeBlockDTO> ordered = sourceBlocks.stream()
                .filter(block -> block != null && block.getText() != null && !block.getText().isBlank())
                .sorted(java.util.Comparator.comparingInt(block -> block.getOriginalIndex() == null
                        ? block.getIndex() == null ? Integer.MAX_VALUE : block.getIndex()
                        : block.getOriginalIndex()))
                .toList();
        List<ResumeBlockDTO> result = new ArrayList<>();
        for (ResumeBlockDTO block : ordered) {
            if (!result.isEmpty() && canMergeWrappedLine(result.get(result.size() - 1), block)) {
                result.set(result.size() - 1, mergeWrappedLines(result.get(result.size() - 1), block));
            } else {
                result.add(block);
            }
        }
        for (int index = 0; index < result.size(); index++) {
            result.get(index).setDisplayOrder(index);
        }
        return List.copyOf(result);
    }

    private boolean canMergeWrappedLine(ResumeBlockDTO previous, ResumeBlockDTO current) {
        if (previous == null || current == null
                || previous.getText() == null || current.getText() == null
                || Boolean.TRUE.equals(current.getBulletHint())
                || Boolean.TRUE.equals(previous.getBoldHint())
                || Boolean.TRUE.equals(current.getBoldHint())
                || matchHeading(previous.getText()) != null
                || matchHeading(current.getText()) != null
                || isBoundaryRole(previous.getRole())
                || isBoundaryRole(current.getRole())) {
            return false;
        }
        if (previous.getPage() != null && current.getPage() != null && !previous.getPage().equals(current.getPage())) {
            return false;
        }
        if (previous.getX() != null && current.getX() != null
                && Math.abs(previous.getX() - current.getX()) > 6.0d) {
            return false;
        }
        if (previous.getY() != null && current.getY() != null) {
            double gap = current.getY() - previous.getY();
            double fontSize = previous.getFontSize() == null ? 10.0d : previous.getFontSize();
            if (gap < 0.0d || gap > Math.max(8.0d, fontSize * 1.65d)) {
                return false;
            }
        }
        String previousText = previous.getText().strip();
        String currentText = current.getText().strip();
        if (previousText.length() < 20 || currentText.length() < 3
                || endsSentence(previousText)
                || looksLikeNewStructuralRow(currentText)) {
            return false;
        }
        return !looksLikeBullet(previousText) && !looksLikeLabelValue(currentText);
    }

    private ResumeBlockDTO mergeWrappedLines(ResumeBlockDTO previous, ResumeBlockDTO current) {
        String previousText = previous.getText() == null ? "" : previous.getText().strip();
        String currentText = current.getText() == null ? "" : current.getText().strip();
        String separator = needsAsciiSpace(previousText, currentText) ? " " : "";
        List<String> sourceIds = new ArrayList<>();
        addSourceIds(sourceIds, previous);
        addSourceIds(sourceIds, current);
        sourceIds = sourceIds.stream()
                .filter(this::isUsableSourceId)
                .map(String::strip)
                .distinct()
                .toList();
        List<String> occurrenceIds = new ArrayList<>();
        addOccurrenceIds(occurrenceIds, previous);
        addOccurrenceIds(occurrenceIds, current);
        // Occurrence multiplicity is meaningful when an older source view reused an explicit
        // ID for two physical rows. Keep every usable value; the later occurrence index applies
        // deterministic collision suffixes instead of silently dropping a row here.
        occurrenceIds = occurrenceIds.stream()
                .filter(this::isUsableOccurrenceId)
                .map(String::strip)
                .toList();
        double left = min(previous.getX(), current.getX());
        double right = max(end(previous.getX(), previous.getWidth()), end(current.getX(), current.getWidth()));
        double top = min(top(previous), top(current));
        double bottom = max(bottom(previous), bottom(current));
        Double mergedY = Double.isNaN(top) ? previous.getY() : top;
        Double mergedHeight = Double.isNaN(top) || Double.isNaN(bottom) ? max(previous.getHeight(), current.getHeight()) : bottom - top;
        return ResumeBlockDTO.builder()
                .id(previous.getId() == null ? current.getId() : previous.getId())
                .index(minIndex(previous.getIndex(), current.getIndex()))
                .originalIndex(minIndex(previous.getOriginalIndex(), current.getOriginalIndex()))
                .displayOrder(minIndex(previous.getDisplayOrder(), current.getDisplayOrder()))
                .text(previousText + separator + currentText)
                .page(previous.getPage() == null ? current.getPage() : previous.getPage())
                .x(Double.isNaN(left) ? previous.getX() : left)
                .y(mergedY)
                .width(Double.isNaN(right) || Double.isNaN(left) ? max(previous.getWidth(), current.getWidth()) : right - left)
                .height(mergedHeight)
                .fontSize(max(previous.getFontSize(), current.getFontSize()))
                .fontName(previous.getFontName() == null ? current.getFontName() : previous.getFontName())
                .boldHint(Boolean.TRUE.equals(previous.getBoldHint()) || Boolean.TRUE.equals(current.getBoldHint()))
                .indent(previous.getIndent() == null ? current.getIndent() : previous.getIndent())
                .bulletHint(Boolean.TRUE.equals(previous.getBulletHint()) || Boolean.TRUE.equals(current.getBulletHint()))
                .role(ResumeSourceBlockRole.PARAGRAPH)
                .sourceBlockIds(sourceIds)
                .sourceOccurrenceIds(occurrenceIds)
                .sourceType(previous.getSourceType() == null ? current.getSourceType() : previous.getSourceType())
                .build();
    }

    private void attachSourceBlocks(List<ResumeTextSectionDTO> sections, List<ResumeBlockDTO> sourceBlocks) {
        int cursor = 0;
        int unmatched = 0;
        for (ResumeTextSectionDTO section : sections == null ? List.<ResumeTextSectionDTO>of() : sections) {
            List<ResumeBlockDTO> attached = new ArrayList<>();
            for (String line : section.getLines() == null ? List.<String>of() : section.getLines()) {
                SourceBlockMatch match = findMatchingSourceBlock(sourceBlocks, cursor, line);
                if (match.start() < 0) {
                    // The line remains visible and is later surfaced by the canonical unresolved
                    // sidecar. Give it a unique local identity; never reuse the search cursor.
                    int syntheticIndex = sourceBlocks.size() + unmatched++;
                    attached.add(ResumeBlockDTO.builder()
                            .id("unmatched-" + syntheticIndex)
                            .index(syntheticIndex)
                            .originalIndex(syntheticIndex)
                            .displayOrder(syntheticIndex)
                            .text(line)
                            .role(classifySourceRole(line))
                            .sourceType("cleanedText-unmatched")
                            .build());
                    continue;
                }
                ResumeBlockDTO block = sourceBlocks.get(match.start());
                for (int sourceIndex = match.start() + 1; sourceIndex <= match.end(); sourceIndex++) {
                    block = mergeWrappedLines(block, sourceBlocks.get(sourceIndex));
                }
                if (match.consumesSource()) {
                    cursor = match.end() + 1;
                }
                attached.add(toRawBlock(block, line));
            }
            section.setBlocks(attached);
        }
    }

    private SourceBlockMatch findMatchingSourceBlock(List<ResumeBlockDTO> blocks, int start, String line) {
        String target = normalizeSourceText(line);
        if (target.isBlank()) {
            return new SourceBlockMatch(-1, -1, false);
        }
        for (int index = Math.max(0, start); index < blocks.size(); index++) {
            ResumeBlockDTO block = blocks.get(index);
            if (block == null) {
                continue;
            }
            String candidate = normalizeSourceText(block.getText());
            if (candidate.equals(target)) {
                return new SourceBlockMatch(index, index, true);
            }
            // Header expansion (for example a contact value extracted from a mixed visual row)
            // may produce a strict projection of one source occurrence. Reuse that occurrence
            // without consuming the next source row, and never accept arbitrary token scattering.
            if (isConservativeProjection(candidate, target)) {
                return new SourceBlockMatch(index, index, false);
            }
            // The cleaner joins only an explicitly hyphenated line. Carry all source IDs into
            // the resulting logical block instead of attaching only the first prefix row.
            if (block.getText() != null && block.getText().strip().endsWith("-")) {
                StringBuilder joined = new StringBuilder(candidate);
                for (int end = index + 1; end < blocks.size(); end++) {
                    ResumeBlockDTO continuation = blocks.get(end);
                    if (continuation == null) {
                        break;
                    }
                    joined.append(normalizeSourceText(continuation.getText()));
                    if (joined.toString().equals(target)) {
                        return new SourceBlockMatch(index, end, true);
                    }
                    if (joined.length() >= target.length()) {
                        break;
                    }
                }
            }
        }
        return new SourceBlockMatch(-1, -1, false);
    }

    private boolean isConservativeProjection(String candidate, String target) {
        if (candidate == null || target == null || target.length() < 2 || candidate.length() <= target.length()) {
            return false;
        }
        if (isGithubProjection(target)) {
            // The cleaner adds a human-readable "GitHub:" label while PDF extraction often
            // contains only the URL in the mixed contact row. The URL is still a strict
            // projection of that one source occurrence; do not manufacture an unmatched block.
            String githubUrl = target.replaceFirst("(?i)^github:", "");
            return candidate.contains(githubUrl);
        }
        if (!candidate.contains(target)) {
            return false;
        }
        return hasAtLeastTwoHanCharacters(target)
                || isHeaderSkillProjection(target)
                || EMAIL_PATTERN.matcher(target).matches()
                || PHONE_PATTERN.matcher(target).matches();
    }

    private boolean hasAtLeastTwoHanCharacters(String value) {
        return value.codePoints()
                .filter(codePoint -> codePoint >= '\u4e00' && codePoint <= '\u9fa5')
                .count() >= 2;
    }

    private boolean isHeaderSkillProjection(String target) {
        return HEADER_SIDE_SKILLS.stream()
                .map(this::normalizeSourceText)
                .anyMatch(target::equalsIgnoreCase);
    }

    private boolean isGithubProjection(String target) {
        String github = target.replaceFirst("(?i)^github:", "");
        return !github.equals(target) && GITHUB_PATTERN.matcher(github).matches();
    }

    private record HeaderContact(int start, int end, String value) {
    }

    private record SourceBlockMatch(int start, int end, boolean consumesSource) {
    }

    private ResumeBlockDTO toRawBlock(ResumeBlockDTO block, String text) {
        return ResumeBlockDTO.builder()
                .id(block.getId())
                .index(block.getIndex())
                .text(text)
                .originalIndex(block.getOriginalIndex())
                .displayOrder(block.getDisplayOrder())
                .page(block.getPage())
                .x(block.getX())
                .y(block.getY())
                .width(block.getWidth())
                .height(block.getHeight())
                .fontSize(block.getFontSize())
                .fontName(block.getFontName())
                .boldHint(block.getBoldHint())
                .indent(block.getIndent())
                .bulletHint(block.getBulletHint())
                .role(block.getRole() == null ? classifySourceRole(text) : block.getRole())
                .sourceBlockIds(sanitizeSourceIds(block.getSourceBlockIds()))
                .sourceOccurrenceIds(sanitizeOccurrenceIds(block.getSourceOccurrenceIds()))
                .sourceType(block.getSourceType())
                .iconType(block.getIconType())
                .build();
    }

    private ResumeSourceBlockRole classifySourceRole(String line) {
        if (line == null || line.isBlank()) {
            return ResumeSourceBlockRole.UNKNOWN;
        }
        if (looksLikeBullet(line)) {
            return ResumeSourceBlockRole.BULLET;
        }
        if (looksLikeLabelValue(line)) {
            return ResumeSourceBlockRole.LABEL_VALUE;
        }
        if (line.matches(".*(?:19|20)\\d{2}.*(?:至今|Present|[-~—至到]).*")) {
            return ResumeSourceBlockRole.ENTRY_HEADER;
        }
        return line.length() <= 80 ? ResumeSourceBlockRole.PARAGRAPH : ResumeSourceBlockRole.PARAGRAPH;
    }

    private boolean isBoundaryRole(ResumeSourceBlockRole role) {
        return role == ResumeSourceBlockRole.SECTION_HEADING
                || role == ResumeSourceBlockRole.ENTRY_HEADER
                || role == ResumeSourceBlockRole.BULLET;
    }

    private boolean looksLikeNewStructuralRow(String line) {
        return line.length() <= 80 && (line.matches(".*(?:19|20)\\d{2}.*")
                || line.matches("^(?:教育|工作|项目|实习|技能|证书|奖项|经历|Experience|Projects|Education|Skills)\\b.*$"));
    }

    private boolean endsSentence(String text) {
        return text.matches(".*[。！？!?；;:：]$");
    }

    private boolean looksLikeBullet(String text) {
        return text != null && text.matches("^[\\s>*•·●▪■◆◇○◦▶►✓✔-]+.*$");
    }

    private boolean looksLikeLabelValue(String text) {
        return text != null && text.matches("^[^:：]{1,20}[:：]\\s*.+$");
    }

    private boolean needsAsciiSpace(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return false;
        }
        int leftCodePoint = left.codePointBefore(left.length());
        int rightCodePoint = right.codePointAt(0);
        if (!isAsciiWordCharacter(leftCodePoint) || !isAsciiWordCharacter(rightCodePoint)) {
            return false;
        }
        // A line break inside a CJK run never represents an omitted ASCII word separator.
        // ASCII-only boundaries are handled below so mixed-language text is not silently split.
        return !looksLikeWordContinuation(left, right);
    }

    private boolean isAsciiWordCharacter(int codePoint) {
        return (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= 'a' && codePoint <= 'z')
                || (codePoint >= '0' && codePoint <= '9');
    }

    private boolean looksLikeWordContinuation(String left, String right) {
        String normalizedLeft = left.toLowerCase(Locale.ROOT);
        String normalizedRight = right.toLowerCase(Locale.ROOT);
        if (!normalizedLeft.endsWith("-") && normalizedRight.startsWith("-")) {
            return false;
        }
        // PDF line extraction does not retain the logical space at a wrapped boundary. These
        // common English morphemes are a conservative guard against turning implementation into
        // "implemen tation" while ordinary word-to-word boundaries still receive one space.
        if (List.of("implemen", "develo", "applica", "communica", "configura", "documenta",
                        "organiza", "administra", "optimiza", "authentica", "presenta")
                .stream().anyMatch(normalizedLeft::endsWith)) {
            return true;
        }
        return List.of("tation", "tion", "sion", "ment", "ing", "ed", "er", "ly", "ity", "ive", "ous",
                        "ance", "ence", "able", "ible", "ize", "ise", "ness", "ship")
                .stream().anyMatch(normalizedRight::startsWith);
    }

    private double min(Double left, Double right) {
        if (left == null) {
            return right == null ? Double.NaN : right;
        }
        return right == null ? left : Math.min(left, right);
    }

    private double max(Double left, Double right) {
        if (left == null) {
            return right == null ? Double.NaN : right;
        }
        return right == null ? left : Math.max(left, right);
    }

    private void addSourceIds(List<String> target, ResumeBlockDTO block) {
        if (block == null) {
            return;
        }
        if (block.getSourceBlockIds() != null && !block.getSourceBlockIds().isEmpty()) {
            target.addAll(block.getSourceBlockIds());
        } else if (isUsableSourceId(block.getId())) {
            target.add(block.getId().strip());
        }
    }

    private void addOccurrenceIds(List<String> target, ResumeBlockDTO block) {
        if (block == null || block.getSourceOccurrenceIds() == null
                || block.getSourceOccurrenceIds().isEmpty()) {
            return;
        }
        target.addAll(block.getSourceOccurrenceIds().stream()
                .filter(this::isUsableOccurrenceId)
                .map(String::strip)
                .toList());
    }

    private List<String> sanitizeSourceIds(List<String> values) {
        if (values == null) {
            return null;
        }
        List<String> sanitized = values.stream()
                .filter(this::isUsableSourceId)
                .map(String::strip)
                .toList();
        return sanitized.isEmpty() ? List.of() : sanitized;
    }

    private List<String> sanitizeOccurrenceIds(List<String> values) {
        if (values == null) {
            return null;
        }
        List<String> sanitized = values.stream()
                .filter(this::isUsableOccurrenceId)
                .map(String::strip)
                .toList();
        return sanitized.isEmpty() ? List.of() : sanitized;
    }

    private boolean isUsableSourceId(String value) {
        return value != null && !value.isBlank()
                && !"null".equalsIgnoreCase(value.strip())
                && !"undefined".equalsIgnoreCase(value.strip());
    }

    private boolean isUsableOccurrenceId(String value) {
        return isUsableSourceId(value);
    }

    private int minIndex(Integer left, Integer right) {
        if (left == null) return right == null ? Integer.MAX_VALUE : right;
        if (right == null) return left;
        return Math.min(left, right);
    }

    private double top(ResumeBlockDTO block) {
        if (block == null || block.getY() == null) return Double.NaN;
        return block.getHeight() == null ? block.getY() : block.getY() - block.getHeight();
    }

    private double bottom(ResumeBlockDTO block) {
        if (block == null || block.getY() == null) return Double.NaN;
        return block.getHeight() == null ? block.getY() : block.getY();
    }

    private double end(Double left, Double width) {
        return left == null || width == null ? Double.NaN : left + width;
    }

    private String normalizeSourceText(String value) {
        return value == null ? "" : value.replaceAll("[\\s>*•·●▪■◆◇○◦▶►✓✔-]+", "").toLowerCase(Locale.ROOT);
    }

    private CleanLines cleanLines(String text) {
        if (text == null || text.isBlank()) {
            return new CleanLines(List.of(), 0, 0);
        }

        List<String> result = new ArrayList<>();
        String previousNormalizedLine = null;
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
                // Count adjacent duplicates as an extraction-quality signal, but retain the
                // occurrence. Text equality is not identity: the same source line may be a
                // deliberate repeated fact and downstream provenance must be able to distinguish
                // both occurrences.
                if (dedupeKey.equals(previousNormalizedLine)) {
                    duplicateCount++;
                }
                previousNormalizedLine = dedupeKey;
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
        List<HeaderContact> contacts = findHeaderContacts(remaining);
        if (!contacts.isEmpty()) {
            return projectHeaderContacts(remaining, contacts);
        }
        if ("LINKEDIN_ICON".equals(iconType)) {
            addHeaderTrailingContent(result, remaining.replaceFirst("^-+$", "").strip());
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

    private List<HeaderContact> findHeaderContacts(String line) {
        List<HeaderContact> contacts = new ArrayList<>();
        collectHeaderContacts(contacts, line, EMAIL_PATTERN, "");
        collectHeaderContacts(contacts, line, PHONE_PATTERN, "");
        collectHeaderContacts(contacts, line, GITHUB_PATTERN, "GitHub: ");
        contacts.sort(java.util.Comparator.comparingInt(HeaderContact::start)
                .thenComparingInt(HeaderContact::end));

        List<HeaderContact> nonOverlapping = new ArrayList<>();
        int lastEnd = -1;
        for (HeaderContact contact : contacts) {
            if (contact.start() >= lastEnd) {
                nonOverlapping.add(contact);
                lastEnd = contact.end();
            }
        }
        return List.copyOf(nonOverlapping);
    }

    private void collectHeaderContacts(
            List<HeaderContact> contacts, String line, Pattern pattern, String prefix) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            contacts.add(new HeaderContact(matcher.start(), matcher.end(), prefix + matcher.group().strip()));
        }
    }

    private List<String> projectHeaderContacts(String line, List<HeaderContact> contacts) {
        List<String> result = new ArrayList<>();
        int cursor = 0;
        for (HeaderContact contact : contacts) {
            addHeaderTrailingContent(result, line.substring(cursor, contact.start()));
            result.add(contact.value());
            cursor = contact.end();
        }
        addHeaderTrailingContent(result, line.substring(cursor));
        return result.isEmpty() ? List.of(line) : List.copyOf(result);
    }

    /**
     * 头部混合行抽出联系方式后的剩余内容不允许静默丢弃：
     * 命中头部技能词表则归一，否则整段保留，交由后续解析/确认链裁决归属。
     */
    private void addHeaderTrailingContent(List<String> result, String value) {
        String cleaned = value == null ? "" : value
                .replaceFirst("^[-:：|·/，,、\\s]+", "")
                .replaceFirst("[-:：|·/，,、\\s]+$", "")
                .strip();
        if (cleaned.isEmpty()) {
            return;
        }
        String skill = trailingHeaderSkill(cleaned);
        if (skill == null) {
            result.add(cleaned);
            return;
        }
        String prefix = cleaned.substring(0, cleaned.length() - skill.length()).strip();
        if (prefix.isBlank()) {
            result.add(skill);
        } else if (isHeaderNameCandidate(prefix)) {
            result.add(prefix);
            result.add(skill);
        } else {
            result.add(cleaned);
        }
    }

    private boolean isHeaderNameCandidate(String value) {
        return value != null && (value.matches("[\\u4e00-\\u9fa5]{2,6}")
                || value.matches("[A-Za-z]+(?:[ .·-][A-Za-z]+){1,3}"));
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
                    String projectContent = "PROJECTS".equals(headingMatch.sectionType())
                            ? removeHeading(line, headingMatch)
                            : line;
                    if (!projectContent.isBlank()) {
                        currentLines.add(projectContent);
                    }
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
                .lines(lines.stream().map(String::strip).filter(line -> !line.isBlank()).toList())
                .blocks(source.getBlocks())
                .build();
    }

    private List<String> mergeLines(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>(first);
        result.addAll(second);
        return result.stream().filter(line -> !line.isBlank()).toList();
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
        List<String> usefulLines = section.getLines().stream()
                .filter(line -> !"本人".equals(line.strip()))
                .toList();
        long skillLines = usefulLines.stream().filter(this::looksLikeSkillListLine).count();
        long narrativeLines = usefulLines.size() - skillLines;
        boolean legacySelfEvaluation = normalizeHeading(section.getHeading()).contains("自我评价")
                || normalizeHeading(section.getHeading()).contains("about me");
        // 旧模板的“自我评价 About me”常把一条分号技术串放在 Summary；只在该明确模板信号下修复。
        if (legacySelfEvaluation && usefulLines.stream().anyMatch(line ->
                techHintCount(line) >= 3 && line.matches(".*[；;].*"))) {
            return true;
        }
        // 至少两行明确的技能串、且叙述行不超过一行时才修复其它旧式模板；普通总结句保持 Summary。
        return skillLines >= 2 && narrativeLines <= 1;
    }

    private boolean isUsefulSkillSummaryLine(String line) {
        return !"本人".equals(line.strip()) && looksLikeSkillListLine(line);
    }

    private boolean looksLikeSkillListLine(String line) {
        if (!looksLikeSkillLine(line)) {
            return false;
        }
        // 叙述句默认仍属于 Summary；仅放行明显由多个技术词和分号组成的旧式技能串。
        if (SENTENCE_LIKE_PATTERN.matcher(line).find()) {
            String lower = line.toLowerCase(Locale.ROOT);
            boolean oldStyleTechnicalList = techHintCount(line) >= 3
                    && line.matches(".*[,，、/|；;].*")
                    && !lower.matches(".*(经验|参与|系统建设|稳定|可观测性|高并发|故障|工作年限|故障排查).*" );
            if (!oldStyleTechnicalList) {
                return false;
            }
        }
        return line.contains("：") || line.contains(":") || line.matches(".*[,，、/|；;].*");
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
        if (!"PROJECTS".equals(currentType)) {
            return false;
        }
        String normalizedLine = normalizeHeading(line);
        if ("SKILLS".equals(headingMatch.sectionType())) {
            return normalizedLine.startsWith("技术栈");
        }
        // 项目字段标签（尤其“项目名称”）不是新的章节；保留它们才能让后续
        // ProjectSourceTextExtractor 按项目一/项目二等强边界解析完整条目。
        return "PROJECTS".equals(headingMatch.sectionType())
                && (normalizedLine.startsWith("项目名称")
                || normalizedLine.startsWith("项目名")
                || normalizedLine.startsWith("项目描述")
                || normalizedLine.startsWith("项目简介")
                || normalizedLine.startsWith("项目介绍")
                || normalizedLine.startsWith("系统简介"));
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
