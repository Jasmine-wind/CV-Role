package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeAchievementDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeExperienceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillEvidenceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceBlockRole;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillSetDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredDataDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.SourceSectionConfidence;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ResumeStructuredResultAssembler {

    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(?<start>(?:19|20)\\d{2}(?:\\s*[./年-]\\s*\\d{1,2}\\s*月?)?)\\s*(?:[-~—–至到]+)\\s*(?<end>(?:19|20)\\d{2}(?:\\s*[./年-]\\s*\\d{1,2}\\s*月?)?|至今|Present)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_ONLY_PATTERN = Pattern.compile(
            "(?<!\\d)(?<date>(?:19|20)\\d{2}(?:[./—–-]\\d{1,2}(?:[./—–-]\\d{1,2})?|年\\s*\\d{1,2}月(?:\\s*\\d{1,2}日)?|年))(?!\\d)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_NAME_PATTERN = Pattern.compile("^(?:项目(?:名称)?|项目名|系统名称|Project)\\s*[:：-]?\\s*(?<name>.+)$|^(?<research>SRTP\\s*\\([^)]*\\)|SRTP（[^）]*）|[A-Za-z0-9_ -]{2,30}(?:项目|系统|平台|研究))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TECH_STACK_PATTERN = Pattern.compile("^(?:技术栈|开发环境|开发工具|环境|技术选型|使用技术|开发框架|软件架构|软件构架)\\s*[:：]\\s*(?<tech>.+)$");
    private static final Pattern PROJECT_INDEX_PATTERN = Pattern.compile("^项目\\s*(?:[一二三四五六七八九十]+|\\d+)\\s*[:：.、-]?\\s*(?<tail>.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_NAME_LABEL_PATTERN = Pattern.compile("^(?:项目名称|项目名|项目|系统名称)\\s*[:：]\\s*(?<value>.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_DESCRIPTION_LABEL_PATTERN = Pattern.compile("^(?:项目描述|项目简介|系统简介|项目介绍|项目经历)\\s*[:：]\\s*(?<value>.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_RESPONSIBILITY_LABEL_PATTERN = Pattern.compile("^(?:责任描述|主要职责|负责模块|主要工作|工作内容|主要工作和业绩)\\s*[:：]\\s*(?<value>.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_TECH_LABEL_PATTERN = Pattern.compile("^(?:技术选型|技术栈|使用技术|开发框架|软件架构|软件构架)\\s*[:：]\\s*(?<value>.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_ENV_LABEL_PATTERN = Pattern.compile("^(?:开发环境|开发工具|环境)\\s*[:：]\\s*(?<value>.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MENTOR_PATTERN = Pattern.compile("导师\\s*[:：]\\s*(?<mentor>[\\u4e00-\\u9fa5A-Za-z .·-]{2,20})");
    private static final Pattern ROLE_PATTERN = Pattern.compile("(?<role>项目组组长|项目负责人|负责人|组长|核心成员|成员|开发者)");
    private static final Pattern SOURCE_CHECK_TOKEN = Pattern.compile("[\\u4e00-\\u9fa5]+|[A-Za-z0-9]+");
    private static final Map<String, List<String>> TECH_SKILL_ALIASES = new LinkedHashMap<>();

    static {
        putSkill("Java", "Java", "JavaSE", "JavaEE");
        putSkill("Spring", "Spring");
        putSkill("Spring Boot", "Spring Boot", "SpringBoot");
        putSkill("Spring Data", "Spring Data", "SpringData");
        putSkill("Spring MVC", "Spring MVC", "SpringMVC");
        putSkill("Spring Cloud", "Spring Cloud", "SpringCloud");
        putSkill("Spring Security", "Spring Security");
        putSkill("MyBatis-Plus", "MyBatis-Plus", "MyBatis Plus");
        putSkill("MyBatis", "MyBatis");
        putSkill("MySQL", "MySQL");
        putSkill("PostgreSQL", "PostgreSQL");
        putSkill("Redis", "Redis");
        putSkill("MongoDB", "MongoDB", "Mongodb");
        putSkill("Oracle", "Oracle");
        putSkill("SQL", "SQL");
        putSkill("Docker", "Docker");
        putSkill("Kubernetes", "Kubernetes", "K8s");
        putSkill("Vue", "Vue", "Vue.js");
        putSkill("JavaScript", "JavaScript", "JS");
        putSkill("TypeScript", "TypeScript");
        putSkill("HTML", "HTML", "HTML5");
        putSkill("CSS", "CSS", "CSS3");
        putSkill("jQuery", "jQuery", "JQuery");
        putSkill("Bootstrap", "Bootstrap", "BootStrap");
        putSkill("Element UI", "ElementUI", "Element UI");
        putSkill("Python", "Python");
        putSkill("C++", "C++");
        putSkill("C", "C");
        putSkill("Verilog", "Verilog");
        putSkill("OpenCV", "OpenCV", "Opencv");
        putSkill("YOLO", "YOLO", "Yolo", "yolo");
        putSkill("DETR", "DETR");
        putSkill("Transformer", "Transformer");
        putSkill("PyTorch", "PyTorch", "Pytorch");
        putSkill("TensorFlow", "TensorFlow", "Tensorflow");
        putSkill("Scikit-learn", "Scikit-learn", "sklearn");
        putSkill("Pandas", "Pandas");
        putSkill("MATLAB", "MATLAB", "Matlab");
        putSkill("Linux", "Linux");
        putSkill("Git", "Git");
        putSkill("Maven", "Maven");
        putSkill("Gradle", "Gradle");
        putSkill("RabbitMQ", "RabbitMQ");
        putSkill("RocketMQ", "RocketMQ");
        putSkill("Kafka", "Kafka");
        putSkill("Dubbo", "Dubbo");
        putSkill("Zookeeper", "Zookeeper", "ZooKeeper");
        putSkill("Eureka", "Eureka");
        putSkill("Nginx", "Nginx");
        putSkill("Tomcat", "Tomcat", "tomcat7", "tomcat8", "tomcat9", "tomcat10");
        putSkill("Elasticsearch", "Elasticsearch", "ElasticSearch", "ES");
        putSkill("Lucene", "Lucene");
        putSkill("FreeMarker", "FreeMarker", "freemarker");
        putSkill("FFmpeg", "FFmpeg", "ffmpeg");
        putSkill("IDEA", "IDEA", "IntelliJ IDEA", "Intellij IDEA");
        putSkill("ECharts", "ECharts", "echarts");
        putSkill("FastDFS", "FastDFS");
        putSkill("Quartz", "Quartz");
        putSkill("Shiro", "Shiro");
        putSkill("JDK", "JDK", "JDK1.8", "JDK8", "JDK 8");
        putSkill("Apache POI", "Apache POI", "POI");
        putSkill("PowerDesigner", "PowerDesigner");
        putSkill("RESTful", "RESTful", "REST API");
        putSkill("JWT", "JWT");
    }

    private ResumeStructuredResultAssembler() {
    }

    static ResumeStructuredContentDTO enrich(ResumeStructuredContentDTO content) {
        if (content == null) {
            return null;
        }
        List<ResumeRawSectionDTO> rawSections = buildRawSections(content.getSections());
        ResumeStructuredDataDTO structuredData = buildStructuredData(content, rawSections);
        content.setRawSections(rawSections);
        content.setStructuredData(structuredData);
        applyLegacyCompatibility(content, structuredData);
        Map<String, Object> debug = content.getDebug() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(content.getDebug());
        debug.put("rawSectionCount", rawSections.size());
        debug.put("structuredDataVersion", "resume-structured-data-v2.9.17");
        content.setDebug(debug);
        return content;
    }

    private static void putSkill(String canonical, String... aliases) {
        TECH_SKILL_ALIASES.put(canonical, List.of(aliases));
    }

    private static List<ResumeRawSectionDTO> buildRawSections(List<ResumeTextSectionDTO> sections) {
        if (sections == null || sections.isEmpty()) {
            return List.of();
        }
        List<ResumeRawSectionDTO> result = new ArrayList<>();
        Set<String> usedOccurrenceIds = new LinkedHashSet<>();
        Map<String, ResumeBlockDTO> firstOccurrenceBlocks = new LinkedHashMap<>();
        int nextFallbackOriginalIndex = 0;
        for (int index = 0; index < sections.size(); index++) {
            ResumeTextSectionDTO section = sections.get(index);
            if (section == null) {
                continue;
            }
            String sectionId = "section-%03d".formatted(result.size() + 1);
            List<ResumeRawSectionBlockDTO> blocks = buildRawBlocks(
                    section,
                    nextFallbackOriginalIndex,
                    result.size() + 1,
                    usedOccurrenceIds,
                    firstOccurrenceBlocks);
            result.add(ResumeRawSectionDTO.builder()
                    .id(sectionId)
                    .originalTitle(nonBlank(section.getHeading(), displayName(section.getSectionType())))
                    .normalizedSection(normalizedRawSection(section.getSectionType()))
                    .displayName(displayName(section.getSectionType()))
                    .iconType(section.getIconType())
                    .confidence(confidence(section.getSourceSectionConfidence()))
                    .source(resolveSectionSource(section))
                    .originalOrder(index)
                    .displayOrder(index)
                    .blocks(blocks)
                    .build());
            nextFallbackOriginalIndex = nextOriginalIndex(blocks, nextFallbackOriginalIndex, section);
        }
        return result;
    }

    private static List<ResumeRawSectionBlockDTO> buildRawBlocks(
            ResumeTextSectionDTO section,
            int fallbackOriginalIndex,
            int sectionPosition,
            Set<String> usedOccurrenceIds,
            Map<String, ResumeBlockDTO> firstOccurrenceBlocks) {
        if (section.getBlocks() != null && !section.getBlocks().isEmpty()) {
            List<ResumeRawSectionBlockDTO> blocks = new ArrayList<>();
            int blockPosition = 0;
            for (ResumeBlockDTO block : section.getBlocks()) {
                if (block == null || !hasText(block.getText())) {
                    continue;
                }
                List<String> occurrenceIds = occurrenceIdsForBlock(
                        block,
                        syntheticSourceOccurrenceId(sectionPosition, blockPosition),
                        usedOccurrenceIds,
                        firstOccurrenceBlocks);
                blocks.add(ResumeRawSectionBlockDTO.builder()
                        .id(safeSourceBlockId(block.getId(), sectionPosition, blockPosition))
                        .index(block.getIndex())
                        .text(block.getText())
                        .iconType(block.getIconType())
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
                        .role(block.getRole())
                        .sourceBlockIds(block.getSourceBlockIds())
                        .sourceOccurrenceIds(occurrenceIds)
                        .sourceType(block.getSourceType())
                        .build());
                for (String occurrenceId : occurrenceIds) {
                    firstOccurrenceBlocks.putIfAbsent(occurrenceId, block);
                }
                blockPosition++;
            }
            return List.copyOf(blocks);
        }
        List<String> lines = section.getLines() == null ? List.of() : section.getLines();
        List<ResumeRawSectionBlockDTO> blocks = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!hasText(line)) {
                continue;
            }
            int originalIndex = fallbackOriginalIndex + index;
            blocks.add(ResumeRawSectionBlockDTO.builder()
                    .id("source-line-" + originalIndex)
                    .index(index)
                    .text(line.strip())
                    .iconType(inferIconType(line))
                    .originalIndex(originalIndex)
                    .displayOrder(originalIndex)
                    .role(ResumeSourceBlockRole.PARAGRAPH)
                    // This block is generated from the deterministic section/line view. Give
                    // it separate synthetic block and occurrence identities so the structuredData
                    // references and the later text-section compatibility view resolve to the
                    // same row without promoting a logical block ID to occurrence identity.
                    .sourceBlockIds(List.of("source-line-" + originalIndex))
                    .sourceOccurrenceIds(uniqueOccurrenceIds(
                            List.of(), "source-occurrence-" + originalIndex, usedOccurrenceIds))
                    .build());
        }
        return blocks;
    }

    private static String safeSourceBlockId(String candidate, int sectionPosition, int blockPosition) {
        if (isUsableSourceId(candidate)) {
            return candidate.strip();
        }
        return "source-block-%03d-%03d".formatted(sectionPosition, blockPosition + 1);
    }

    private static int nextOriginalIndex(
            List<ResumeRawSectionBlockDTO> blocks,
            int fallbackOriginalIndex,
            ResumeTextSectionDTO section) {
        int max = safeList(blocks).stream()
                .filter(block -> block != null && block.getOriginalIndex() != null)
                .mapToInt(ResumeRawSectionBlockDTO::getOriginalIndex)
                .max()
                .orElse(fallbackOriginalIndex - 1);
        if (max >= fallbackOriginalIndex) {
            return max + 1;
        }
        return fallbackOriginalIndex + safeList(section.getLines()).size();
    }

    private static String resolveSectionSource(ResumeTextSectionDTO section) {
        return "RULE_SOURCE_SECTION";
    }

    private static ResumeStructuredDataDTO buildStructuredData(
            ResumeStructuredContentDTO content,
            List<ResumeRawSectionDTO> rawSections) {
        List<String> education = preserve(content.getEducation());
        List<String> keywords = buildSkillKeywords(content, rawSections);
        List<ResumeExperienceDTO> experiences = buildExperiences(content, rawSections);
        List<ResumeProjectDTO> projects = buildProjects(content, experiences, rawSections);
        List<ResumeAchievementDTO> achievements = buildAchievements(content, experiences, rawSections);
        ResumeSkillSetDTO skills = ResumeSkillSetDTO.builder()
                .keywords(keywords)
                .groups(groupSkills(keywords))
                .descriptions(buildSkillDescriptions(rawSections))
                .evidence(buildSkillEvidence(keywords, rawSections))
                .build();
        return ResumeStructuredDataDTO.builder()
                .education(education)
                .educationSourceRefs(sourceRefsForValues(rawSections, "EDUCATION", education))
                .skills(skills)
                .experiences(experiences)
                .projects(projects)
                .achievements(achievements)
                .certificates(preserve(content.getCertificates()))
                .summary(blankToNull(content.getSummary()))
                .summarySourceRef(sourceRef(findSourceBlocks(rawSections, findSourceSectionId(rawSections, "SUMMARY"))))
                .others(preserve(content.getOthers()))
                .build();
    }

    private static List<String> buildSkillKeywords(ResumeStructuredContentDTO content, List<ResumeRawSectionDTO> rawSections) {
        List<String> keywords = new ArrayList<>();
        boolean foundSkillSource = false;
        for (ResumeRawSectionDTO section : safeList(rawSections)) {
            if (!isSkillSourceSection(section)) {
                continue;
            }
            for (ResumeRawSectionBlockDTO block : safeBlocks(section)) {
                if (block == null || !hasText(block.getText()) || isSectionHeading(block.getText())) {
                    continue;
                }
                List<String> lineSkills = new ArrayList<>();
                addSkillsFromLine(block.getText(), lineSkills);
                if (!lineSkills.isEmpty()) {
                    foundSkillSource = true;
                    // One source row may legitimately contribute the same keyword occurrence as
                    // another row. Keep that multiplicity; only aliases within one row are
                    // collapsed by addSkillsFromLine(List).
                    keywords.addAll(lineSkills);
                }
            }
        }
        if (!foundSkillSource) {
            // Misordered and historical inputs often put the only skill evidence in a summary,
            // header, or experience row. Derive one occurrence per source row rather than losing
            // those facts merely because no dedicated skills heading was recovered.
            for (String line : evidenceLines(rawSections)) {
                addSkillsFromLine(line, keywords);
            }
        }
        // The legacy flat list is assembled by scanning broad compatibility evidence and may
        // include technologies mentioned only in work or projects. It is a fallback only: once a
        // dedicated Skills section yielded technical terms, that section owns canonical skills.
        if (!foundSkillSource) {
            for (String skill : preserve(content.getSkills())) {
                if (!keywords.contains(skill)) {
                    keywords.add(skill);
                }
            }
        }
        return List.copyOf(keywords);
    }

    private static void addSkillsFromLine(String line, List<String> skills) {
        if (!hasText(line)) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : TECH_SKILL_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                if (containsSkillAlias(line, alias)) {
                    if (!skills.contains(entry.getKey())) {
                        skills.add(entry.getKey());
                    }
                    break;
                }
            }
        }
    }

    private static void addSkillsFromLine(String line, Set<String> skills) {
        if (!hasText(line)) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : TECH_SKILL_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                if (containsSkillAlias(line, alias)) {
                    skills.add(entry.getKey());
                    break;
                }
            }
        }
    }

    private static Map<String, List<String>> groupSkills(List<String> keywords) {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String group : List.of("language", "framework", "database", "frontend", "middleware", "cv", "ai", "tool", "data", "other")) {
            groups.put(group, new ArrayList<>());
        }
        for (String keyword : keywords) {
            groups.get(skillGroup(keyword)).add(keyword);
        }
        return groups;
    }

    private static String skillGroup(String skill) {
        return switch (skill) {
            case "Java", "Python", "C++", "C", "Verilog", "JavaScript", "TypeScript", "SQL", "HTML", "CSS" -> "language";
            case "Spring", "Spring Boot", "Spring Data", "Spring MVC", "Spring Cloud", "Spring Security", "MyBatis-Plus", "MyBatis",
                    "Dubbo", "Vue", "jQuery", "Bootstrap", "Element UI", "FastAPI" -> "framework";
            case "MySQL", "PostgreSQL", "Redis", "MongoDB", "Oracle", "Elasticsearch" -> "database";
            case "OpenCV", "YOLO", "DETR" -> "cv";
            case "Transformer", "PyTorch", "TensorFlow", "Scikit-learn" -> "ai";
            case "RabbitMQ", "RocketMQ", "Kafka", "Zookeeper", "Eureka", "Nginx", "Tomcat", "FastDFS" -> "middleware";
            case "Git", "Maven", "Gradle", "Docker", "Kubernetes", "Linux", "IDEA", "PowerDesigner", "Apache POI", "MATLAB" -> "tool";
            case "Pandas" -> "data";
            default -> "other";
        };
    }

    private static ResumeSourceRefDTO sourceRef(ResumeRawSectionBlockDTO block) {
        if (block == null || !hasText(block.getText())) {
            return null;
        }
        Integer line = block.getOriginalIndex() == null ? block.getIndex() : block.getOriginalIndex();
        return ResumeSourceRefDTO.builder()
                .startLine(line == null ? null : line + 1)
                .endLine(line == null ? null : line + 1)
                .text(block.getText())
                .sourceBlockIds(sourceBlockIds(block))
                .sourceOccurrenceIds(sourceOccurrenceIds(block))
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
                .role(block.getRole())
                .sourceType(block.getSourceType())
                .build();
    }

    private static List<String> buildSkillDescriptions(List<ResumeRawSectionDTO> rawSections) {
        List<String> descriptions = new ArrayList<>();
        for (ResumeRawSectionDTO section : safeList(rawSections)) {
            if (section == null || !"SKILLS".equals(section.getNormalizedSection())) {
                continue;
            }
            for (ResumeRawSectionBlockDTO block : safeBlocks(section)) {
                if (block != null && hasText(block.getText())
                        && !isSectionHeading(block.getText())
                        && !isSkillOnlyLine(block.getText())) {
                    descriptions.add(block.getText().strip());
                }
            }
        }
        return List.copyOf(descriptions);
    }

    private static boolean isSkillOnlyLine(String line) {
        if (!hasText(line)) {
            return false;
        }
        String remaining = line.strip().replaceFirst(
                "^(?:技能|专业技能|技术能力|技能关键词|技术栈|核心技能|编程语言|框架|数据库|工具)\\s*[:：]\\s*", "");
        List<String> aliases = new ArrayList<>();
        for (List<String> values : TECH_SKILL_ALIASES.values()) {
            aliases.addAll(values);
        }
        aliases.sort((left, right) -> Integer.compare(right.length(), left.length()));
        for (String alias : aliases) {
            remaining = remaining.replaceAll(
                    "(?i)(?<![A-Za-z0-9+#.])" + Pattern.quote(alias) + "(?![A-Za-z0-9+#.])", " ");
        }
        remaining = remaining.replaceAll("[\\s,，、/|;；:：()（）·•]+", "").strip();
        return remaining.isEmpty();
    }

    private static List<ResumeSkillEvidenceDTO> buildSkillEvidence(List<String> keywords, List<ResumeRawSectionDTO> rawSections) {
        List<ResumeSkillEvidenceDTO> evidence = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ResumeRawSectionDTO section : safeList(rawSections)) {
            if (!isSkillSourceSection(section)) {
                continue;
            }
            List<ResumeRawSectionBlockDTO> blocks = safeBlocks(section);
            for (int blockPosition = 0; blockPosition < blocks.size(); blockPosition++) {
                ResumeRawSectionBlockDTO block = blocks.get(blockPosition);
                if (block == null || !hasText(block.getText()) || isSectionHeading(block.getText())) {
                    continue;
                }
                List<String> matched = safeList(keywords).stream()
                        .filter(keyword -> containsSkillKeyword(block.getText(), keyword))
                        .toList();
                String sourceKey = sourceIdentity(section.getId(), block, blockPosition);
                if (matched.isEmpty()) {
                    if (seen.add(sourceKey + "|description")) {
                        evidence.add(ResumeSkillEvidenceDTO.builder()
                                .sourceSectionId(section.getId())
                                .sourceText(block.getText())
                                .description(block.getText())
                                .keywords(List.of())
                                .sourceRef(sourceRef(block))
                                .build());
                    }
                    continue;
                }
                for (String keyword : matched) {
                    if (seen.add(sourceKey + "|" + keyword)) {
                        evidence.add(ResumeSkillEvidenceDTO.builder()
                                .skill(keyword)
                                .sourceSectionId(section.getId())
                                .sourceText(block.getText())
                                .description(isSkillOnlyLine(block.getText()) ? null : block.getText())
                                .keywords(List.of(keyword))
                                .sourceRef(sourceRef(block))
                                .build());
                    }
                }
            }
        }
        return evidence;
    }

    /**
     * Skill groups and descriptions are authoritative only in a dedicated skills section. The
     * flat compatibility skill list may still contain rule-detected keywords from other rows,
     * but those rows must not be reclassified wholesale as skill evidence merely because they
     * mention a technology.
     */
    private static boolean isSkillSourceSection(ResumeRawSectionDTO section) {
        return section != null
                && "SKILLS".equalsIgnoreCase(section.getNormalizedSection())
                && safeBlocks(section).stream()
                .anyMatch(block -> block != null && hasText(block.getText()));
    }

    private static boolean isSectionHeading(String text) {
        return hasText(text) && text.strip().matches("(?i)^(?:个人信息|基本信息|联系方式|教育经历|教育背景|专业技能|技能|技术能力|工作经历|工作经验|职业经历|实习经历|项目经历|项目经验|校园经历|在校经历|获奖经历|荣誉奖项|证书|自我评价|个人总结|个人概述|profile|education|skills|experience|projects|summary|others)$");
    }

    private static List<ResumeExperienceDTO> buildExperiences(
            ResumeStructuredContentDTO content,
            List<ResumeRawSectionDTO> rawSections) {
        List<ResumeExperienceDTO> experiences = new ArrayList<>();
        appendExperiences(experiences, "WORK", content.getWorkExperiences(), "工作经历", 0.86,
                findSourceSectionId(rawSections, "WORK_EXPERIENCES"), rawSections);
        appendExperiences(experiences, "INTERNSHIP", content.getInternships(), "实习经历", 0.84,
                findSourceSectionId(rawSections, "INTERNSHIPS"), rawSections);
        appendExperiences(experiences, "CAMPUS", content.getCampusExperiences(), "校园经历", 0.78,
                findSourceSectionId(rawSections, "CAMPUS_EXPERIENCES"), rawSections);
        return experiences;
    }

    private static void appendExperiences(
            List<ResumeExperienceDTO> target,
            String type,
            List<String> values,
            String sourceTitle,
            double confidence,
            String sourceSectionId,
            List<ResumeRawSectionDTO> rawSections) {
        List<ResumeRawSectionBlockDTO> sourceBlocks = findSourceBlocks(rawSections, sourceSectionId);
        int targetSizeBefore = target.size();
        if (!sourceBlocks.isEmpty()) {
            List<ResumeEntryBoundaryDetector.EntryGroup> groups = new ResumeEntryBoundaryDetector().group(sourceBlocks);
            for (ResumeEntryBoundaryDetector.EntryGroup group : groups) {
                List<String> evidence = preserve(group.texts());
                if (evidence.isEmpty()) {
                    continue;
                }
                String header = firstNonBlank(group.headerText(), String.join(" ", evidence));
                List<String> body = group.lines().stream()
                        .filter(block -> !group.headers().contains(block))
                        .map(ResumeRawSectionBlockDTO::getText)
                        .filter(ResumeStructuredResultAssembler::hasText)
                        .map(String::strip)
                        .toList();
                if (group.headers().size() == 1 && !body.isEmpty()
                        && extractStartDate(header) == null && extractEndDate(header) == null) {
                    // Keep the legacy candidate granularity for undated rows. The canonical
                    // assembler will merge these continuation candidates into one entry, while
                    // callers of the compatibility structured view still see each source line.
                    for (ResumeRawSectionBlockDTO block : group.lines()) {
                        String text = block.getText().strip();
                        target.add(ResumeExperienceDTO.builder()
                                .type(type)
                                .organization(group.headers().contains(block) ? extractOrganization(text) : null)
                                .role(group.headers().contains(block) ? extractRole(text) : null)
                                .startDate(group.headers().contains(block) ? extractStartDate(text) : null)
                                .endDate(group.headers().contains(block) ? extractEndDate(text) : null)
                                .description(text)
                                .bullets(List.of(text))
                                .sourceSectionId(sourceSectionId)
                                .sourceTitle(sourceTitle)
                                .evidence(List.of(text))
                                .sourceRef(sourceRef(List.of(block)))
                                .confidence(group.headers().contains(block) ? confidence : confidence - 0.12)
                                .build());
                    }
                } else {
                    String description = String.join(" ", evidence);
                    target.add(ResumeExperienceDTO.builder()
                            .type(type)
                            .organization(extractOrganization(header))
                            .role(extractRole(header))
                            .startDate(extractStartDate(header))
                            .endDate(extractEndDate(header))
                            .description(description)
                            .bullets(preserve(body))
                            .sourceSectionId(sourceSectionId)
                            .sourceTitle(sourceTitle)
                            .evidence(evidence)
                            .sourceRef(sourceRef(group.lines()))
                            .confidence(group.hasHeader() ? confidence : confidence - 0.12)
                            .build());
                }
            }
            if (target.size() > targetSizeBefore) {
                return;
            }
        }
        // Historical/generic snapshots may not carry raw blocks. Preserve the old projection
        // rather than inventing a boundary from sentence wording.
        for (String line : preserve(values)) {
            target.add(ResumeExperienceDTO.builder()
                    .type(type)
                    .organization(extractOrganization(line))
                    .role(extractRole(line))
                    .startDate(extractStartDate(line))
                    .endDate(extractEndDate(line))
                    .description(line)
                    .bullets(List.of(line))
                    .sourceSectionId(sourceSectionId)
                    .sourceTitle(sourceTitle)
                    .evidence(List.of(line))
                    .confidence(confidence)
                    .build());
        }
    }

    private static List<String> sourceBlockIds(ResumeRawSectionBlockDTO block) {
        if (block == null) {
            return List.of();
        }
        List<String> ids = cleanSourceIds(block.getSourceBlockIds());
        if (!ids.isEmpty()) {
            return ids;
        }
        return isUsableSourceId(block.getId()) ? List.of(block.getId().strip()) : List.of();
    }

    private static List<String> sourceOccurrenceIds(ResumeRawSectionBlockDTO block) {
        if (block == null) {
            return List.of();
        }
        return cleanSourceIds(block.getSourceOccurrenceIds());
    }

    private static String syntheticSourceOccurrenceId(int sectionPosition, int blockPosition) {
        return "source-occurrence-s%03d-b%04d".formatted(sectionPosition, blockPosition + 1);
    }

    private static List<String> cleanSourceIds(List<String> values) {
        return safeList(values).stream()
                .filter(ResumeStructuredResultAssembler::isUsableSourceId)
                .map(String::strip)
                .distinct()
                .toList();
    }

    private static List<String> occurrenceIdsForBlock(
            ResumeBlockDTO block,
            String fallback,
            Set<String> usedOccurrenceIds,
            Map<String, ResumeBlockDTO> firstOccurrenceBlocks) {
        List<String> result = new ArrayList<>();
        List<String> candidates = safeList(block == null ? null : block.getSourceOccurrenceIds()).stream()
                .filter(ResumeStructuredResultAssembler::isUsableSourceId)
                .map(String::strip)
                .toList();
        if (candidates.isEmpty() && isUsableSourceId(fallback)) {
            candidates = List.of(fallback.strip());
        }
        for (String base : candidates) {
            ResumeBlockDTO previous = firstOccurrenceBlocks.get(base);
            if (previous != null && sameSourceProjection(block, previous)) {
                result.add(base);
                continue;
            }
            String candidate = base;
            int suffix = 2;
            while (usedOccurrenceIds.contains(candidate) || result.contains(candidate)) {
                candidate = base + "~" + suffix++;
            }
            usedOccurrenceIds.add(candidate);
            result.add(candidate);
        }
        return List.copyOf(result);
    }

    private static boolean sameSourceProjection(
            ResumeBlockDTO current, ResumeBlockDTO previous) {
        if (current == null || previous == null) {
            return false;
        }
        List<String> currentIds = sourceBlockIdentity(current);
        List<String> previousIds = sourceBlockIdentity(previous);
        for (String currentId : currentIds) {
            for (String previousId : previousIds) {
                if (currentId.equals(previousId)
                        || fragmentBase(currentId).equals(fragmentBase(previousId))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> sourceBlockIdentity(ResumeBlockDTO block) {
        List<String> ids = cleanSourceIds(block == null ? null : block.getSourceBlockIds());
        if (!ids.isEmpty()) {
            return ids;
        }
        return isUsableSourceId(block == null ? null : block.getId())
                ? List.of(block.getId().strip()) : List.of();
    }

    private static String fragmentBase(String value) {
        return value == null ? "" : value.replaceFirst("#fragment-\\d+$", "");
    }

    private static List<String> uniqueOccurrenceIds(
            List<String> requested, String fallback, Set<String> usedOccurrenceIds) {
        List<String> result = new ArrayList<>();
        List<String> candidates = safeList(requested).stream()
                .filter(ResumeStructuredResultAssembler::isUsableSourceId)
                .map(String::strip)
                .toList();
        if (candidates.isEmpty() && isUsableSourceId(fallback)) {
            candidates = List.of(fallback.strip());
        }
        for (String base : candidates) {
            String candidate = base;
            int suffix = 2;
            while (usedOccurrenceIds.contains(candidate) || result.contains(candidate)) {
                candidate = base + "~" + suffix++;
            }
            usedOccurrenceIds.add(candidate);
            result.add(candidate);
        }
        return List.copyOf(result);
    }

    private static boolean isUsableSourceId(String value) {
        return hasText(value)
                && !"null".equalsIgnoreCase(value.strip())
                && !"undefined".equalsIgnoreCase(value.strip());
    }

    private static Double minCoordinate(List<Double> values) {
        return values.stream().filter(value -> value != null).min(Double::compareTo).orElse(null);
    }

    private static Double boundingWidth(List<ResumeRawSectionBlockDTO> blocks) {
        Double left = minCoordinate(blocks.stream().map(ResumeRawSectionBlockDTO::getX).toList());
        double right = blocks.stream()
                .mapToDouble(block -> block.getX() == null || block.getWidth() == null
                        ? Double.NaN : block.getX() + block.getWidth())
                .filter(value -> !Double.isNaN(value))
                .max()
                .orElse(Double.NaN);
        return left == null || Double.isNaN(right) ? null : right - left;
    }

    private static Double boundingHeight(List<ResumeRawSectionBlockDTO> blocks) {
        Double top = minCoordinate(blocks.stream().map(ResumeRawSectionBlockDTO::getY).toList());
        double bottom = blocks.stream()
                .mapToDouble(block -> block.getY() == null || block.getHeight() == null
                        ? Double.NaN : block.getY() + block.getHeight())
                .filter(value -> !Double.isNaN(value))
                .max()
                .orElse(Double.NaN);
        return top == null || Double.isNaN(bottom) ? null : bottom - top;
    }

    private static List<ResumeSourceRefDTO> sourceRefsForValues(
            List<ResumeRawSectionDTO> rawSections,
            String sectionType,
            List<String> values) {
        String sectionId = findSourceSectionId(rawSections, sectionType);
        List<ResumeRawSectionBlockDTO> blocks = findSourceBlocks(rawSections, sectionId);
        List<ResumeSourceRefDTO> refs = new ArrayList<>();
        Set<String> consumed = new LinkedHashSet<>();
        for (String value : safeList(values)) {
            if (!hasText(value)) {
                continue;
            }
            ResumeSourceRefDTO selected = null;
            String selectedIdentity = null;
            for (int blockPosition = 0; blockPosition < blocks.size(); blockPosition++) {
                ResumeRawSectionBlockDTO block = blocks.get(blockPosition);
                if (block == null || !hasText(block.getText())
                        || !sourceContains(block.getText(), value)) {
                    continue;
                }
                String identity = sourceIdentity(sectionId, block, blockPosition);
                // A repeated projected value may consume another matching occurrence, but it must
                // never point back to the first occurrence once all matching rows are consumed.
                if (consumed.contains(identity)) {
                    continue;
                }
                selected = sourceRef(block);
                selectedIdentity = identity;
                break;
            }
            if (selected != null) {
                refs.add(selected);
                consumed.add(selectedIdentity);
            }
        }
        return List.copyOf(refs);
    }

    private static List<ResumeRawSectionBlockDTO> findSourceBlocks(List<ResumeRawSectionDTO> rawSections, String sourceSectionId) {
        if (!hasText(sourceSectionId)) {
            return List.of();
        }
        for (ResumeRawSectionDTO section : safeList(rawSections)) {
            if (section != null && sourceSectionId.equals(section.getId())) {
                return safeBlocks(section);
            }
        }
        return List.of();
    }

    private static ResumeSourceRefDTO sourceRef(List<ResumeRawSectionBlockDTO> blocks) {
        List<ResumeRawSectionBlockDTO> meaningful = safeList(blocks).stream()
                .filter(block -> block != null && hasText(block.getText()))
                .toList();
        if (meaningful.isEmpty()) {
            return null;
        }
        Integer start = meaningful.stream().map(ResumeRawSectionBlockDTO::getOriginalIndex)
                .filter(value -> value != null).min(Integer::compareTo).orElse(null);
        Integer end = meaningful.stream().map(ResumeRawSectionBlockDTO::getOriginalIndex)
                .filter(value -> value != null).max(Integer::compareTo).orElse(start);
        ResumeRawSectionBlockDTO first = meaningful.get(0);
        boolean samePage = meaningful.stream()
                .map(ResumeRawSectionBlockDTO::getPage)
                .allMatch(page -> Objects.equals(page, first.getPage()));
        return ResumeSourceRefDTO.builder()
                .startLine(start == null ? null : start + 1)
                .endLine(end == null ? null : end + 1)
                .text(String.join("\n", meaningful.stream().map(ResumeRawSectionBlockDTO::getText).toList()))
                .sourceBlockIds(meaningful.stream()
                        .flatMap(block -> sourceBlockIds(block).stream())
                        .distinct()
                        .toList())
                .sourceOccurrenceIds(meaningful.stream()
                        .flatMap(block -> sourceOccurrenceIds(block).stream())
                        .distinct()
                        .toList())
                .page(samePage ? first.getPage() : null)
                .x(samePage ? minCoordinate(meaningful.stream().map(ResumeRawSectionBlockDTO::getX).toList()) : null)
                .y(samePage ? minCoordinate(meaningful.stream().map(ResumeRawSectionBlockDTO::getY).toList()) : null)
                .width(samePage ? boundingWidth(meaningful) : null)
                .height(samePage ? boundingHeight(meaningful) : null)
                .fontSize(first.getFontSize())
                .fontName(first.getFontName())
                .boldHint(meaningful.stream().anyMatch(block -> Boolean.TRUE.equals(block.getBoldHint())))
                .indent(first.getIndent())
                .bulletHint(meaningful.stream().anyMatch(block -> Boolean.TRUE.equals(block.getBulletHint())))
                .role(first.getRole())
                .sourceType(first.getSourceType())
                .build();
    }

    private static List<ResumeProjectDTO> buildProjects(
            ResumeStructuredContentDTO content,
            List<ResumeExperienceDTO> experiences,
            List<ResumeRawSectionDTO> rawSections) {
        List<ResumeProjectDTO> projects = new ArrayList<>(extractProjectsFromRawSections(rawSections));
        List<String> projectLines = preserve(content.getProjects());
        // The raw section is the source-backed boundary. The legacy flat list is a lossy
        // compatibility projection and can reorder project names away from their dated
        // headers; only use it when no raw project candidate was recovered.
        if (projects.isEmpty()) {
            projects.addAll(ProjectSourceTextExtractor.extractFromLines(
                    projectLines, findSourceSectionId(rawSections, "PROJECTS")));
        }
        if (!projects.isEmpty()) {
            return ProjectSourceTextExtractor.expandProjects(projects);
        }
        if (looksLikeSingleProjectSection(projectLines)) {
            projects.add(buildCompositeProject(projectLines, rawSections));
        } else {
            for (String line : projectLines) {
                ProjectNameAndDescription project = splitProjectLine(line);
                projects.add(ResumeProjectDTO.builder()
                        .name(project.name())
                        .description(project.description())
                        .role(extractProjectRole(List.of(line)))
                        .mentor(extractMentor(List.of(line)))
                        .timeRange(extractTimeRange(line))
                        .techStack(extractSkillList(line))
                        .responsibilities(isResponsibilityLine(line) ? List.of(line) : List.of())
                        .startDate(extractStartDate(line))
                        .endDate(extractEndDate(line))
                        .sourceType("INDEPENDENT")
                        .sourceSectionId(findSourceSectionId(rawSections, "PROJECTS"))
                        .evidence(List.of(line))
                        .confidence(0.82)
                        .build());
            }
        }
        for (int index = 0; index < experiences.size(); index++) {
            ResumeExperienceDTO experience = experiences.get(index);
            if (!"WORK".equals(experience.getType()) && !"INTERNSHIP".equals(experience.getType())) {
                continue;
            }
            for (String bullet : safeList(experience.getBullets())) {
                if (!hasExplicitProjectTitle(bullet)) {
                    continue;
                }
                ProjectNameAndDescription project = splitProjectLine(bullet);
                projects.add(ResumeProjectDTO.builder()
                        .name(project.name())
                        .description(project.description())
                        .techStack(extractSkillList(bullet))
                        .responsibilities(List.of())
                        .sourceType("WORK".equals(experience.getType()) ? "WORK_EXPERIENCE" : "INTERNSHIP")
                        .parentExperienceIndex(index)
                        .sourceSectionId(experience.getSourceSectionId())
                        .evidence(List.of(bullet))
                        .confidence(0.72)
                        .build());
            }
        }
        return projects;
    }

    private static List<ResumeProjectDTO> extractProjectsFromRawSections(List<ResumeRawSectionDTO> rawSections) {
        return ProjectSourceTextExtractor.extractFromRawSections(rawSections);
    }

    private static boolean isProjectPrefixOnly(List<String> lines) {
        List<String> usefulLines = unique(lines);
        return !usefulLines.isEmpty()
                && usefulLines.stream().allMatch(line -> PROJECT_ENV_LABEL_PATTERN.matcher(line).matches()
                || PROJECT_TECH_LABEL_PATTERN.matcher(line).matches());
    }

    private static List<ProjectSegment> splitProjectSegments(ResumeRawSectionDTO section) {
        List<ProjectSegment> segments = new ArrayList<>();
        ProjectSegment current = null;
        for (ResumeRawSectionBlockDTO block : safeBlocks(section)) {
            String line = block.getText() == null ? "" : block.getText().strip();
            if (!hasText(line)) {
                continue;
            }
            if (isProjectSectionHeading(line)) {
                continue;
            }
            Matcher indexMatcher = PROJECT_INDEX_PATTERN.matcher(line);
            Matcher nameMatcher = PROJECT_NAME_LABEL_PATTERN.matcher(line);
            boolean startsByIndex = indexMatcher.matches();
            boolean startsByRepeatedName = nameMatcher.matches() && current != null && current.hasProjectFieldContent();
            if (startsByIndex || startsByRepeatedName) {
                if (current != null && current.hasMeaningfulContent()) {
                    segments.add(current);
                }
                current = new ProjectSegment(section.getId());
                if (startsByIndex) {
                    String tail = indexMatcher.group("tail");
                    if (hasText(tail) && !isProjectFieldLabel(tail)) {
                        current.add(block, tail.strip());
                    }
                } else {
                    current.add(block, line);
                }
                continue;
            }
            if (current == null) {
                current = new ProjectSegment(section.getId());
            }
            current.add(block, line);
        }
        if (current != null && current.hasMeaningfulContent()) {
            segments.add(current);
        }
        return segments;
    }

    private static ResumeProjectDTO buildProjectFromSegment(ProjectSegment segment, int index) {
        ProjectFields fields = parseProjectFields(segment.lines());
        List<String> evidence = preserve(segment.lines());
        if (evidence.isEmpty()) {
            return null;
        }
        String sourceText = String.join("\n", evidence);
        String name = cleanProjectEntityName(firstNonBlank(
                fields.name(),
                evidence.stream().filter(ResumeStructuredResultAssembler::looksLikeProjectNameLine).findFirst().orElse(null)));
        if (!hasText(name) && !hasText(fields.description()) && fields.responsibilities().isEmpty()) {
            return null;
        }
        if (!hasText(name)) {
            name = "项目经历 " + (index + 1);
        }
        String description = firstNonBlank(firstSentenceSummary(fields.description()), fallbackProjectSummary(evidence, name));
        List<String> responsibilities = normalizeResponsibilities(fields.responsibilities());
        Set<String> techStack = new LinkedHashSet<>();
        addSkillsFromLine(fields.techText(), techStack);
        addSkillsFromLine(fields.environment(), techStack);
        addSkillsFromLine(fields.description(), techStack);
        responsibilities.forEach(line -> addSkillsFromLine(line, techStack));
        addSkillsFromLine(sourceText, techStack);
        return ResumeProjectDTO.builder()
                .name(name)
                .description(description)
                .environment(blankToNull(fields.environment()))
                .role(extractProjectRole(evidence))
                .mentor(extractMentor(evidence))
                .timeRange(extractTimeRange(sourceText))
                .techStack(List.copyOf(techStack))
                .responsibilities(responsibilities)
                .startDate(extractStartDate(sourceText))
                .endDate(extractEndDate(sourceText))
                .sourceType("INDEPENDENT")
                .sourceSectionId(segment.sourceSectionId())
                .evidence(evidence)
                .confidence(0.9)
                .build();
    }

    private static ProjectFields parseProjectFields(List<String> lines) {
        String name = null;
        List<String> descriptions = new ArrayList<>();
        List<String> responsibilities = new ArrayList<>();
        List<String> techTexts = new ArrayList<>();
        List<String> environments = new ArrayList<>();
        ProjectField activeField = ProjectField.DESCRIPTION;
        for (String rawLine : safeList(lines)) {
            String line = rawLine == null ? "" : rawLine.strip();
            if (!hasText(line) || PROJECT_INDEX_PATTERN.matcher(line).matches()) {
                continue;
            }
            LabelValue labelValue = parseProjectLabel(line);
            if (labelValue != null) {
                activeField = labelValue.field();
                String value = labelValue.value();
                if (!hasText(value)) {
                    continue;
                }
                switch (activeField) {
                    case NAME -> name = firstNonBlank(name, value);
                    case DESCRIPTION -> descriptions.add(value);
                    case RESPONSIBILITY -> responsibilities.add(value);
                    case TECH -> techTexts.add(value);
                    case ENVIRONMENT -> environments.add(value);
                }
                continue;
            }
            if (activeField == ProjectField.NAME) {
                name = firstNonBlank(name, line);
                activeField = ProjectField.DESCRIPTION;
            } else if (activeField == ProjectField.RESPONSIBILITY || isResponsibilityLine(line)) {
                responsibilities.add(line);
            } else if (activeField == ProjectField.TECH) {
                techTexts.add(line);
            } else if (activeField == ProjectField.ENVIRONMENT) {
                environments.add(line);
            } else if (!hasText(name) && looksLikeProjectNameLine(line)) {
                name = line;
            } else {
                descriptions.add(line);
            }
        }
        return new ProjectFields(name,
                String.join(" ", descriptions).strip(),
                String.join("，", techTexts).strip(),
                String.join("，", environments).strip(),
                responsibilities);
    }

    private static LabelValue parseProjectLabel(String line) {
        for (Map.Entry<Pattern, ProjectField> entry : Map.of(
                PROJECT_NAME_LABEL_PATTERN, ProjectField.NAME,
                PROJECT_DESCRIPTION_LABEL_PATTERN, ProjectField.DESCRIPTION,
                PROJECT_RESPONSIBILITY_LABEL_PATTERN, ProjectField.RESPONSIBILITY,
                PROJECT_TECH_LABEL_PATTERN, ProjectField.TECH,
                PROJECT_ENV_LABEL_PATTERN, ProjectField.ENVIRONMENT).entrySet()) {
            Matcher matcher = entry.getKey().matcher(line);
            if (matcher.matches()) {
                return new LabelValue(entry.getValue(), matcher.group("value") == null ? "" : matcher.group("value").strip());
            }
        }
        return null;
    }

    private static boolean isProjectFieldLabel(String line) {
        return parseProjectLabel(line == null ? "" : line.strip()) != null
                || line.matches("^(项目名称|项目名|项目描述|项目简介|系统简介|项目介绍|开发环境|开发工具|环境|技术选型|技术栈|使用技术|开发框架|软件架构|软件构架|责任描述|主要职责|负责模块|主要工作|工作内容|主要工作和业绩)\\s*[:：]?$");
    }

    private static boolean isProjectSectionHeading(String line) {
        return line != null && line.strip().matches("^(项目经历|项目经验|项目实践|项目介绍|参加项目描述|Projects|Project Experience)\\s*$");
    }

    private static boolean looksLikeProjectNameLine(String line) {
        if (!hasText(line) || isProjectFieldLabel(line)) {
            return false;
        }
        String cleaned = line.strip();
        if (cleaned.length() > 48 && cleaned.matches(".*[。；;].*")) {
            return false;
        }
        return cleaned.matches(".*(?:系统|平台|项目|中心|网站|商城|管理|SRTP|研究).*");
    }

    private static String cleanProjectEntityName(String value) {
        if (!hasText(value)) {
            return "";
        }
        String cleaned = value.strip();
        Matcher nameLabelMatcher = PROJECT_NAME_LABEL_PATTERN.matcher(cleaned);
        if (nameLabelMatcher.matches()) {
            cleaned = nameLabelMatcher.group("value") == null ? "" : nameLabelMatcher.group("value").strip();
        }
        cleaned = DATE_RANGE_PATTERN.matcher(cleaned).replaceAll("").strip();
        cleaned = cleaned.replaceAll("^项目\\s*(?:[一二三四五六七八九十]+|\\d+)\\s*[:：.、-]?\\s*", "").strip();
        if (!hasText(cleaned) || isProjectFieldLabel(cleaned)) {
            return "";
        }
        if (cleaned.length() > 48 && cleaned.matches(".*[。；;].*")) {
            return "";
        }
        return cleaned;
    }

    private static String firstSentenceSummary(String value) {
        if (!hasText(value)) {
            return "";
        }
        String cleaned = removeProjectFieldLabels(value);
        String[] parts = cleaned.split("(?<=[。！？!?；;])");
        String summary = "";
        for (String part : parts) {
            if (!hasText(part)) {
                continue;
            }
            summary = (summary + part.strip()).strip();
            if (summary.length() >= 60 || summary.endsWith("。") || summary.endsWith("；")) {
                break;
            }
        }
        if (!hasText(summary)) {
            summary = cleaned;
        }
        return summary.length() > 180 ? summary.substring(0, 180) + "..." : summary;
    }

    private static String fallbackProjectSummary(List<String> evidence, String name) {
        return safeList(evidence).stream()
                .map(ResumeStructuredResultAssembler::removeProjectFieldLabels)
                .filter(ResumeStructuredResultAssembler::hasText)
                .filter(line -> !line.equals(name))
                .filter(line -> !isProjectFieldLabel(line))
                .filter(line -> !TECH_STACK_PATTERN.matcher(line).matches())
                .findFirst()
                .map(ResumeStructuredResultAssembler::firstSentenceSummary)
                .orElse("");
    }

    private static List<String> normalizeResponsibilities(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : safeList(values)) {
            String cleaned = removeProjectFieldLabels(value);
            for (String part : cleaned.split("[；;]\\s*|(?<=。)")) {
                String item = part.strip();
                if (!hasText(item) || isProjectFieldLabel(item)) {
                    continue;
                }
                // 保留责任描述中的结果/量化尾句，不因缺少“负责/实现”等谓语而丢失事实。
                result.add(item);
            }
        }
        return preserve(result);
    }

    private static String removeProjectFieldLabels(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.strip()
                .replaceFirst("^(项目名称|项目名|项目描述|项目简介|系统简介|项目介绍|开发环境|开发工具|环境|技术选型|技术栈|使用技术|开发框架|软件架构|软件构架|责任描述|主要职责|负责模块|主要工作|工作内容|主要工作和业绩)\\s*[:：]\\s*", "")
                .strip();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.strip();
            }
        }
        return "";
    }

    private static boolean looksLikeSingleProjectSection(List<String> lines) {
        if (lines == null || lines.size() < 3) {
            return false;
        }
        return hasExplicitProjectTitle(lines.get(0)) || lines.get(0).matches("(?i)^SRTP\\s*[（(].*");
    }

    private static ResumeProjectDTO buildCompositeProject(List<String> lines, List<ResumeRawSectionDTO> rawSections) {
        String firstLine = lines.get(0);
        ProjectNameAndDescription project = splitProjectLine(firstLine);
        List<String> evidence = new ArrayList<>(lines);
        List<String> responsibilities = lines.stream()
                .skip(2)
                .filter(ResumeStructuredResultAssembler::isResponsibilityLine)
                .toList();
        String description = lines.stream()
                .skip(2)
                .filter(line -> !isResponsibilityLine(line))
                .findFirst()
                .orElse(project.description());
        Set<String> techStack = new LinkedHashSet<>();
        lines.forEach(line -> addSkillsFromLine(line, techStack));
        return ResumeProjectDTO.builder()
                .name(project.name())
                .description(description)
                .role(extractProjectRole(lines))
                .mentor(extractMentor(lines))
                .timeRange(extractTimeRange(firstLine))
                .techStack(List.copyOf(techStack))
                .responsibilities(responsibilities)
                .startDate(extractStartDate(firstLine))
                .endDate(extractEndDate(firstLine))
                .sourceType("INDEPENDENT")
                .sourceSectionId(findSourceSectionId(rawSections, "PROJECTS"))
                .evidence(evidence)
                .confidence(0.88)
                .build();
    }

    private static String extractProjectRole(List<String> lines) {
        for (String line : safeList(lines)) {
            Matcher matcher = ROLE_PATTERN.matcher(line == null ? "" : line);
            if (matcher.find()) {
                return matcher.group("role");
            }
        }
        return null;
    }

    private static String extractMentor(List<String> lines) {
        for (String line : safeList(lines)) {
            Matcher matcher = MENTOR_PATTERN.matcher(line == null ? "" : line);
            if (matcher.find()) {
                return matcher.group("mentor").strip();
            }
        }
        return null;
    }

    private static String extractTimeRange(String line) {
        Matcher matcher = DATE_RANGE_PATTERN.matcher(line == null ? "" : line);
        if (matcher.find()) {
            return normalizeDate(matcher.group("start")) + " – " + normalizeDate(matcher.group("end"));
        }
        return null;
    }

    private static String normalizeDate(String value) {
        return value == null ? null : value.replaceAll("\\s+", "").strip();
    }

    private static List<ResumeAchievementDTO> buildAchievements(
            ResumeStructuredContentDTO content,
            List<ResumeExperienceDTO> experiences,
            List<ResumeRawSectionDTO> rawSections) {
        List<ResumeAchievementDTO> achievements = new ArrayList<>();
        Set<String> consumedSourceOccurrences = new LinkedHashSet<>();
        String awardsSectionId = findSourceSectionId(rawSections, "AWARDS");
        for (String award : preserve(content.getAwards())) {
            SourceOccurrenceMatch match = allocateSourceOccurrence(
                    rawSections, awardsSectionId, award, consumedSourceOccurrences);
            if (match.matched() && !match.allocated()) {
                continue;
            }
            achievements.add(buildAchievement(
                    award,
                    awardsSectionId,
                    null,
                    0.86,
                    match.sourceRef()));
        }
        for (int index = 0; index < experiences.size(); index++) {
            ResumeExperienceDTO experience = experiences.get(index);
            if (!"CAMPUS".equals(experience.getType())) {
                continue;
            }
            for (String bullet : safeList(experience.getBullets())) {
                if (!looksLikeAchievement(bullet)) {
                    continue;
                }
                SourceOccurrenceMatch match = allocateSourceOccurrence(
                        rawSections,
                        experience.getSourceSectionId(),
                        bullet,
                        consumedSourceOccurrences);
                if (match.matched() && !match.allocated()) {
                    continue;
                }
                achievements.add(buildAchievement(
                        bullet,
                        experience.getSourceSectionId(),
                        index,
                        0.68,
                        match.sourceRef()));
            }
        }
        return mergeEquivalentAchievements(achievements);
    }

    private static List<ResumeAchievementDTO> mergeEquivalentAchievements(List<ResumeAchievementDTO> achievements) {
        List<ResumeAchievementDTO> merged = new ArrayList<>();
        Map<String, Integer> indexesByKey = new LinkedHashMap<>();
        for (ResumeAchievementDTO achievement : safeList(achievements)) {
            if (achievement == null) {
                continue;
            }
            String key = achievementSemanticKey(achievement);
            Integer existingIndex = indexesByKey.get(key);
            if (existingIndex == null) {
                indexesByKey.put(key, merged.size());
                merged.add(achievement);
                continue;
            }
            ResumeAchievementDTO existing = merged.get(existingIndex);
            if (!punctuationEquivalentDateDuplicate(existing, achievement)) {
                // Identical source rows remain distinct occurrences. Collapse only the known
                // extraction artifact where the same dated award differs by dash typography.
                merged.add(achievement);
                continue;
            }
            merged.set(existingIndex, ResumeAchievementDTO.builder()
                    .title(existing.getTitle())
                    .level(existing.getLevel())
                    .competition(existing.getCompetition())
                    .ranking(existing.getRanking())
                    .timeRange(existing.getTimeRange())
                    .date(existing.getDate())
                    .parentExperienceIndex(existing.getParentExperienceIndex())
                    .sourceSectionId(existing.getSourceSectionId())
                    .evidence(preserve(concat(existing.getEvidence(), achievement.getEvidence())))
                    .sourceRef(mergeSourceRefs(existing.getSourceRef(), achievement.getSourceRef()))
                    .confidence(existing.getConfidence())
                    .build());
        }
        return List.copyOf(merged);
    }

    private static boolean punctuationEquivalentDateDuplicate(
            ResumeAchievementDTO left, ResumeAchievementDTO right) {
        String leftDate = firstNonBlank(left.getDate(), left.getTimeRange());
        String rightDate = firstNonBlank(right.getDate(), right.getTimeRange());
        if (!hasText(leftDate) || !normalizeAchievementDate(leftDate).equals(normalizeAchievementDate(rightDate))) {
            return false;
        }
        String leftEvidence = safeList(left.getEvidence()).stream().findFirst().orElse("");
        String rightEvidence = safeList(right.getEvidence()).stream().findFirst().orElse("");
        return !normalizeSource(leftEvidence).equals(normalizeSource(rightEvidence))
                && normalizeAchievementDate(normalizeSource(leftEvidence))
                .equals(normalizeAchievementDate(normalizeSource(rightEvidence)));
    }

    private static List<String> concat(List<String> left, List<String> right) {
        List<String> values = new ArrayList<>(left == null ? List.of() : left);
        values.addAll(right == null ? List.of() : right);
        return values;
    }

    private static String achievementSemanticKey(ResumeAchievementDTO achievement) {
        return normalizeSource(achievement.getTitle()).toLowerCase()
                + "|" + normalizeAchievementDate(firstNonBlank(achievement.getDate(), achievement.getTimeRange()));
    }

    private static String normalizeAchievementDate(String value) {
        return value == null ? "" : value.replaceAll("[—–]", "-").replaceAll("\\s+", "").toLowerCase();
    }

    private static ResumeSourceRefDTO mergeSourceRefs(ResumeSourceRefDTO left, ResumeSourceRefDTO right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        List<String> occurrenceIds = preserve(concat(left.getSourceOccurrenceIds(), right.getSourceOccurrenceIds()));
        List<String> blockIds = preserve(concat(left.getSourceBlockIds(), right.getSourceBlockIds()));
        boolean samePage = Objects.equals(left.getPage(), right.getPage());
        Double x = samePage ? minCoordinate(Arrays.asList(left.getX(), right.getX())) : null;
        Double y = samePage ? minCoordinate(Arrays.asList(left.getY(), right.getY())) : null;
        Double rightEdge = samePage ? maxCoordinate(Arrays.asList(edge(left.getX(), left.getWidth()), edge(right.getX(), right.getWidth()))) : null;
        Double bottomEdge = samePage ? maxCoordinate(Arrays.asList(edge(left.getY(), left.getHeight()), edge(right.getY(), right.getHeight()))) : null;
        return ResumeSourceRefDTO.builder()
                .startLine(minInteger(left.getStartLine(), right.getStartLine()))
                .endLine(maxInteger(left.getEndLine(), right.getEndLine()))
                .text(joinDistinctLines(left.getText(), right.getText()))
                .sourceBlockIds(blockIds)
                .sourceOccurrenceIds(occurrenceIds)
                .page(samePage ? left.getPage() : null)
                .x(x)
                .y(y)
                .width(x == null || rightEdge == null ? null : rightEdge - x)
                .height(y == null || bottomEdge == null ? null : bottomEdge - y)
                .fontSize(left.getFontSize())
                .fontName(left.getFontName())
                .boldHint(Boolean.TRUE.equals(left.getBoldHint()) || Boolean.TRUE.equals(right.getBoldHint()))
                .indent(left.getIndent())
                .bulletHint(Boolean.TRUE.equals(left.getBulletHint()) || Boolean.TRUE.equals(right.getBulletHint()))
                .role(left.getRole())
                .sourceType(left.getSourceType())
                .build();
    }

    private static Double edge(Double origin, Double size) {
        return origin == null || size == null ? null : origin + size;
    }

    private static Double maxCoordinate(List<Double> values) {
        return values.stream().filter(Objects::nonNull).max(Double::compareTo).orElse(null);
    }

    private static Integer minInteger(Integer left, Integer right) {
        return left == null ? right : right == null ? left : Math.min(left, right);
    }

    private static Integer maxInteger(Integer left, Integer right) {
        return left == null ? right : right == null ? left : Math.max(left, right);
    }

    private static String joinDistinctLines(String left, String right) {
        if (!hasText(left)) {
            return right;
        }
        if (!hasText(right) || left.equals(right)) {
            return left;
        }
        return left + "\n" + right;
    }

    private static SourceOccurrenceMatch allocateSourceOccurrence(
            List<ResumeRawSectionDTO> rawSections,
            String sectionId,
            String value,
            Set<String> consumedSourceOccurrences) {
        List<ResumeRawSectionBlockDTO> blocks = findSourceBlocks(rawSections, sectionId);
        boolean matched = false;
        for (int blockPosition = 0; blockPosition < blocks.size(); blockPosition++) {
            ResumeRawSectionBlockDTO block = blocks.get(blockPosition);
            if (block == null || !hasText(block.getText()) || !sourceContains(block.getText(), value)) {
                continue;
            }
            matched = true;
            String identity = sourceIdentity(sectionId, block, blockPosition);
            if (consumedSourceOccurrences.add(identity)) {
                return new SourceOccurrenceMatch(true, true, sourceRef(block));
            }
        }
        return new SourceOccurrenceMatch(matched, false, null);
    }

    private static ResumeAchievementDTO buildAchievement(
            String line,
            String sourceSectionId,
            Integer parentExperienceIndex,
            double confidence,
            ResumeSourceRefDTO sourceRef) {
        AchievementParts parts = splitAchievement(line);
        return ResumeAchievementDTO.builder()
                .title(parts.title())
                .level(parts.level())
                .competition(parts.competition())
                .ranking(parts.ranking())
                .timeRange(parts.timeRange())
                .date(parts.date())
                .parentExperienceIndex(parentExperienceIndex)
                .sourceSectionId(sourceSectionId)
                .evidence(List.of(line))
                .sourceRef(sourceRef)
                .confidence(confidence)
                .build();
    }

    private static String normalizeSource(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").strip();
    }

    private static boolean sourceContains(String source, String value) {
        if (!hasText(source) || !hasText(value)) {
            return false;
        }
        if (normalizeSource(source).equalsIgnoreCase(normalizeSource(value))) {
            return true;
        }
        List<String> expected = sourceTokens(value);
        List<String> available = sourceTokens(source);
        if (expected.isEmpty() || available.isEmpty()) {
            return false;
        }
        return containsTokenSubsequence(expected, available);
    }

    private static boolean containsTokenSubsequence(List<String> expected, List<String> available) {
        if (expected == null || expected.isEmpty() || available == null || available.size() < expected.size()) {
            return false;
        }
        for (int start = 0; start <= available.size() - expected.size(); start++) {
            boolean matches = true;
            for (int offset = 0; offset < expected.size(); offset++) {
                String expectedToken = expected.get(offset);
                String availableToken = available.get(start + offset);
                if (!expectedToken.equalsIgnoreCase(availableToken)
                        && !(isChineseToken(expectedToken)
                        && (expectedToken.length() >= 2 || expectedToken.matches("[年月日]"))
                        && availableToken.contains(expectedToken))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static List<String> sourceTokens(String value) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = SOURCE_CHECK_TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) {
            String token = matcher.group();
            if (isChineseToken(token) && token.length() > 1 && token.matches("[年月日].+")) {
                tokens.add(token.substring(0, 1));
                tokens.add(token.substring(1));
            } else {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static boolean isChineseToken(String value) {
        return value != null && value.matches("[\\u4e00-\\u9fa5]+");
    }

    private static AchievementParts splitAchievement(String line) {
        String cleaned = line == null ? "" : line.strip();
        String timeRange = extractTimeRange(cleaned);
        String date = null;
        if (timeRange == null) {
            Matcher dateMatcher = DATE_ONLY_PATTERN.matcher(cleaned);
            date = dateMatcher.find()
                    ? normalizeAchievementDate(dateMatcher.group("date"))
                    : null;
        }
        String withoutDate = DATE_RANGE_PATTERN.matcher(cleaned).replaceAll("").strip();
        if (date != null) {
            withoutDate = DATE_ONLY_PATTERN.matcher(withoutDate).replaceAll("").strip();
        }
        String[] parts = withoutDate.split("[,，]", 2);
        if (parts.length == 2) {
            String level = parts[0].strip();
            String competition = parts[1].strip();
            String ranking = level.matches(".*(?:前\\s*\\d+%|Top\\s*\\d+%).*") ? level : null;
            return new AchievementParts(level + " " + competition, level, competition, ranking, timeRange, date);
        }
        return new AchievementParts(firstNonBlank(withoutDate, cleaned), null, null, null, timeRange, date);
    }

    private static void applyLegacyCompatibility(ResumeStructuredContentDTO content, ResumeStructuredDataDTO structuredData) {
        if (structuredData == null) {
            return;
        }
        content.setEducation(preserve(structuredData.getEducation()));
        content.setSkills(structuredData.getSkills() == null ? List.of() : preserve(structuredData.getSkills().getKeywords()));
        content.setWorkExperiences(experienceDescriptions(structuredData.getExperiences(), Set.of("WORK")));
        content.setInternships(experienceDescriptions(structuredData.getExperiences(), Set.of("INTERNSHIP")));
        content.setCampusExperiences(experienceDescriptions(structuredData.getExperiences(), Set.of("CAMPUS", "PRACTICE", "VOLUNTEER")));
        content.setProjects(projectDescriptions(structuredData.getProjects()));
        content.setAwards(preserve(content.getAwards()));
        content.setCertificates(preserve(structuredData.getCertificates()));
        content.setSummary(structuredData.getSummary());
        content.setOthers(preserve(structuredData.getOthers()));
    }

    private static List<String> experienceDescriptions(List<ResumeExperienceDTO> experiences, Set<String> types) {
        return safeList(experiences).stream()
                .filter(item -> item != null && types.contains(item.getType()))
                .map(ResumeExperienceDTO::getDescription)
                .filter(ResumeStructuredResultAssembler::hasText)
                .toList();
    }

    private static List<String> projectDescriptions(List<ResumeProjectDTO> projects) {
        List<String> values = new ArrayList<>();
        for (ResumeProjectDTO item : safeList(projects)) {
            if (item == null) {
                continue;
            }
            // A legacy project row is commonly copied into name, description, and evidence by
            // the compatibility parser. Collapse that generated echo within this one project,
            // but never across project objects: two source occurrences with equal text remain
            // independently visible.
            List<String> projectValues = new ArrayList<>();
            if (hasText(item.getName()) && !item.getName().matches("^项目经历\\s*\\d+$")) {
                projectValues.add(item.getName());
            }
            if (hasText(item.getDescription())) {
                projectValues.add(item.getDescription());
            }
            projectValues.addAll(safeList(item.getResponsibilities()));
            projectValues.addAll(safeList(item.getEvidence()));
            Set<String> seen = new LinkedHashSet<>();
            for (String value : projectValues) {
                if (hasText(value) && seen.add(normalizeSource(value))) {
                    values.add(value);
                }
            }
        }
        return preserve(values);
    }

    private static List<String> achievementTitles(List<ResumeAchievementDTO> achievements) {
        return safeList(achievements).stream()
                .filter(item -> item != null)
                .map(ResumeAchievementDTO::getTitle)
                .filter(ResumeStructuredResultAssembler::hasText)
                .distinct()
                .toList();
    }

    private static List<String> extractSkillList(String line) {
        Set<String> skills = new LinkedHashSet<>();
        addSkillsFromLine(line, skills);
        Matcher matcher = TECH_STACK_PATTERN.matcher(line == null ? "" : line);
        if (matcher.find()) {
            addSkillsFromLine(matcher.group("tech"), skills);
        }
        return List.copyOf(skills);
    }

    private static ProjectNameAndDescription splitProjectLine(String line) {
        String cleaned = line == null ? "" : line.strip();
        if (looksLikeJsonObject(cleaned)) {
            return new ProjectNameAndDescription("", cleaned);
        }
        Matcher matcher = PROJECT_NAME_PATTERN.matcher(cleaned);
        if (matcher.find()) {
            String name = matcher.group("name") != null ? matcher.group("name").strip() : matcher.group("research").strip();
            name = DATE_RANGE_PATTERN.matcher(name).replaceAll("").strip();
            return new ProjectNameAndDescription(name, cleaned);
        }
        int separatorIndex = firstSeparatorIndex(cleaned);
        if (separatorIndex > 0 && separatorIndex <= 40) {
            return new ProjectNameAndDescription(cleaned.substring(0, separatorIndex).strip(), cleaned);
        }
        return new ProjectNameAndDescription(cleaned, cleaned);
    }

    private static boolean looksLikeJsonObject(String value) {
        return hasText(value) && value.startsWith("{") && value.endsWith("}");
    }

    private static int firstSeparatorIndex(String text) {
        int result = -1;
        for (String separator : List.of("，", ",", "：", ":", " - ", "，")) {
            int index = text.indexOf(separator);
            if (index > 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private static boolean hasExplicitProjectTitle(String line) {
        return hasText(line) && PROJECT_NAME_PATTERN.matcher(line.strip()).find();
    }

    private static boolean isResponsibilityLine(String line) {
        return hasText(line) && line.matches(".*(?:负责|参与|完成|实现|开发|维护|优化|设计).*");
    }

    private static boolean looksLikeAchievement(String line) {
        return hasText(line) && line.matches(".*(?:获得|获|奖|荣誉|优秀|最佳|第一名|第二名|第三名|一等奖|二等奖|三等奖).*");
    }

    private static String findSourceSectionId(List<ResumeRawSectionDTO> rawSections, String sectionType) {
        String normalized = normalizedRawSection(sectionType);
        return safeList(rawSections).stream()
                .filter(section -> section != null && normalized.equals(section.getNormalizedSection()))
                .map(ResumeRawSectionDTO::getId)
                .findFirst()
                .orElse(null);
    }

    private static String extractOrganization(String line) {
        if (!hasText(line)) {
            return null;
        }
        String value = line.strip();
        Matcher date = DATE_RANGE_PATTERN.matcher(value);
        if (date.find()) {
            String before = value.substring(0, date.start()).strip();
            if (hasText(before) && !looksLikeRole(before)) {
                return lastHeaderPart(before);
            }
            String after = value.substring(date.end()).strip();
            if (hasText(after) && !looksLikeRole(after)) {
                return firstHeaderPart(after);
            }
        }
        // 组织名可能出现在日期区间之前或之后； suffixes are only weak signals, never
        // required for a boundary. A short non-sentence segment beside a date is safer.
        String[] parts = value.split("\\s+");
        for (String part : parts) {
            if (part.matches(".*(?:公司|集团|学校|学院|大学|中心|协会|社团|实验室|Labs?|Inc\\.?|LLC).*")) {
                return part;
            }
        }
        return null;
    }

    private static boolean looksLikeRole(String value) {
        return hasText(value) && value.matches("(?i).*(?:工程师|开发|实习生|经理|总监|专员|负责人|成员|组长|engineer|developer|intern|manager|lead|member).*" );
    }

    private static String firstHeaderPart(String value) {
        return splitHeaderParts(value).stream().filter(part -> !looksLikeRole(part)).findFirst().orElse(null);
    }

    private static String lastHeaderPart(String value) {
        List<String> parts = splitHeaderParts(value);
        for (int index = parts.size() - 1; index >= 0; index--) {
            if (!looksLikeRole(parts.get(index))) return parts.get(index);
        }
        return null;
    }

    private static List<String> splitHeaderParts(String value) {
        return List.of(value.split("\\s*(?:[|｜·•/])\\s*|\\s{2,}"));
    }

    private static String extractRole(String line) {
        if (!hasText(line)) {
            return null;
        }
        String[] candidates = line.strip().split("\\s+");
        for (int index = candidates.length - 1; index >= 0; index--) {
            String candidate = candidates[index];
            if (candidate.matches(".*(?:工程师|开发|实习生|负责人|成员|干事|干部|经理|专员).*")) {
                return candidate;
            }
        }
        return null;
    }

    private static String extractStartDate(String line) {
        Matcher matcher = DATE_RANGE_PATTERN.matcher(line == null ? "" : line);
        return matcher.find() ? matcher.group("start") : null;
    }

    private static String extractEndDate(String line) {
        Matcher matcher = DATE_RANGE_PATTERN.matcher(line == null ? "" : line);
        return matcher.find() ? matcher.group("end") : null;
    }

    private static List<String> evidenceLines(List<ResumeRawSectionDTO> rawSections) {
        List<String> lines = new ArrayList<>();
        for (ResumeRawSectionDTO section : rawSections) {
            for (ResumeRawSectionBlockDTO block : safeBlocks(section)) {
                if (hasText(block.getText())) {
                    lines.add(block.getText());
                }
            }
        }
        return lines;
    }

    private static List<ResumeRawSectionBlockDTO> safeBlocks(ResumeRawSectionDTO section) {
        return section == null || section.getBlocks() == null ? List.of() : section.getBlocks();
    }

    private static String inferIconType(String line) {
        if (!hasText(line)) {
            return null;
        }
        if (Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(line).find()) {
            return "EMAIL_ICON";
        }
        if (Pattern.compile("(?<!\\d)(?:\\(\\+?86\\)|\\+?86|86)?[-\\s]*1[3-9]\\d[-\\s]?\\d{4}[-\\s]?\\d{4}(?!\\d)").matcher(line).find()) {
            return "PHONE_ICON";
        }
        if (Pattern.compile("(?i)(?:https?://)?github\\.com/[A-Za-z0-9_.-]+").matcher(line).find()) {
            return "GITHUB_ICON";
        }
        return null;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    /** Preserve source rows; text equality is not occurrence identity. */
    private static List<String> preserve(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : safeList(values)) {
            if (hasText(value)) {
                result.add(value.strip());
            }
        }
        return result;
    }

    private static List<String> unique(List<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : safeList(values)) {
            if (!hasText(value)) {
                continue;
            }
            String cleaned = value.strip();
            if (seen.add(cleaned.toLowerCase(Locale.ROOT))) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private static String sourceIdentity(
            String sectionId,
            ResumeRawSectionBlockDTO block,
            int position) {
        String sectionKey = hasText(sectionId) ? sectionId.strip() : "section-unknown";
        if (block == null) {
            return sectionKey + "|position-" + position;
        }
        List<String> occurrenceIds = cleanSourceIds(block.getSourceOccurrenceIds());
        if (!occurrenceIds.isEmpty()) {
            return String.join("|", occurrenceIds);
        }
        // Block IDs deliberately do not participate in occurrence allocation. If an upstream
        // block omitted sourceOccurrenceIds, use its source order plus list position only; this
        // prevents two rows sharing one logical block ID from being folded together.
        if (block.getOriginalIndex() != null) {
            return sectionKey + "|original-index-" + block.getOriginalIndex() + "|position-" + position;
        }
        if (block.getIndex() != null) {
            return sectionKey + "|index-" + block.getIndex() + "|position-" + position;
        }
        // A source block can be valid even when all parser positions are absent. The caller's
        // stable list position is the last deterministic identity; never stringify null here.
        return sectionKey + "|position-" + position;
    }

    private static boolean containsSkillKeyword(String line, String keyword) {
        if (!hasText(line) || !hasText(keyword)) {
            return false;
        }
        return skillAliases(keyword).stream().anyMatch(alias -> containsSkillAlias(line, alias));
    }

    private static List<String> skillAliases(String keyword) {
        String normalized = keyword.strip();
        for (Map.Entry<String, List<String>> entry : TECH_SKILL_ALIASES.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(normalized)
                    || entry.getValue().stream().anyMatch(alias -> alias.equalsIgnoreCase(normalized))) {
                return entry.getValue();
            }
        }
        return List.of(normalized);
    }

    private static boolean containsSkillAlias(String line, String alias) {
        if (!hasText(line) || !hasText(alias)) {
            return false;
        }
        if ("Spring".equals(alias)) {
            return Pattern.compile("(?i)(?<![A-Za-z0-9+#.])Spring(?!\\s*(?:Boot|Data|MVC|Cloud|Security)|[A-Za-z0-9+#.])")
                    .matcher(line)
                    .find();
        }
        if (containsChinese(alias)) {
            return line.contains(alias);
        }
        String pattern = "(?i)(?<![A-Za-z0-9+#.])" + Pattern.quote(alias) + "(?![A-Za-z0-9+#.])";
        return Pattern.compile(pattern).matcher(line).find();
    }

    private static boolean containsChinese(String value) {
        return value.chars().anyMatch(ch -> Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN);
    }

    private static String normalizedRawSection(String sectionType) {
        if (!hasText(sectionType) || "GENERAL".equals(sectionType)) {
            return "UNKNOWN";
        }
        return switch (sectionType) {
            case "WORK_EXPERIENCES" -> "WORK";
            case "INTERNSHIPS" -> "INTERNSHIP";
            case "CAMPUS_EXPERIENCES" -> "CAMPUS";
            case "AWARDS" -> "ACHIEVEMENTS";
            default -> sectionType;
        };
    }

    private static String displayName(String sectionType) {
        return switch (sectionType == null ? "" : sectionType) {
            case "BASIC_INFO" -> "基础信息";
            case "EDUCATION" -> "教育经历";
            case "SKILLS" -> "技能";
            case "WORK_EXPERIENCES" -> "工作经历";
            case "INTERNSHIPS" -> "实习经历";
            case "PROJECTS" -> "项目经历";
            case "CAMPUS_EXPERIENCES" -> "在校经历";
            case "AWARDS" -> "获奖经历";
            case "CERTIFICATES" -> "证书";
            case "SUMMARY" -> "自我评价";
            case "OTHERS" -> "其他内容";
            default -> "未识别章节";
        };
    }

    private static double confidence(String confidence) {
        SourceSectionConfidence sourceConfidence = SourceSectionConfidence.from(confidence);
        return switch (sourceConfidence) {
            case HIGH -> 0.95;
            case MEDIUM -> 0.72;
            case LOW -> 0.35;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nonBlank(String preferred, String fallback) {
        return hasText(preferred) ? preferred.strip() : fallback;
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.strip() : null;
    }

    private record ProjectNameAndDescription(String name, String description) {
    }

    private enum ProjectField {
        NAME,
        DESCRIPTION,
        RESPONSIBILITY,
        TECH,
        ENVIRONMENT
    }

    private record LabelValue(ProjectField field, String value) {
    }

    private record ProjectFields(String name, String description, String techText, String environment, List<String> responsibilities) {
    }

    private static final class ProjectSegment {
        private final String sourceSectionId;
        private final List<String> lines = new ArrayList<>();

        private ProjectSegment(String sourceSectionId) {
            this.sourceSectionId = sourceSectionId;
        }

        private void add(ResumeRawSectionBlockDTO block, String line) {
            if (hasText(line)) {
                lines.add(line.strip());
            } else if (block != null && hasText(block.getText())) {
                lines.add(block.getText().strip());
            }
        }

        private void prepend(List<String> prefixLines) {
            List<String> merged = new ArrayList<>();
            merged.addAll(safeList(prefixLines));
            merged.addAll(lines);
            lines.clear();
            lines.addAll(merged);
        }

        private boolean hasMeaningfulContent() {
            return lines.stream().anyMatch(line -> hasText(line) && !PROJECT_INDEX_PATTERN.matcher(line).matches());
        }

        private boolean hasProjectFieldContent() {
            return lines.stream().anyMatch(line -> {
                if (!hasText(line)) {
                    return false;
                }
                Matcher nameMatcher = PROJECT_NAME_LABEL_PATTERN.matcher(line);
                if (nameMatcher.matches() && hasText(nameMatcher.group("value"))) {
                    return true;
                }
                return PROJECT_DESCRIPTION_LABEL_PATTERN.matcher(line).matches()
                        || PROJECT_RESPONSIBILITY_LABEL_PATTERN.matcher(line).matches();
            });
        }

        private String sourceSectionId() {
            return sourceSectionId;
        }

        private List<String> lines() {
            return lines;
        }
    }

    private record AchievementParts(String title, String level, String competition, String ranking, String timeRange, String date) {
    }

    private record SourceOccurrenceMatch(
            boolean matched,
            boolean allocated,
            ResumeSourceRefDTO sourceRef) {
    }
}
