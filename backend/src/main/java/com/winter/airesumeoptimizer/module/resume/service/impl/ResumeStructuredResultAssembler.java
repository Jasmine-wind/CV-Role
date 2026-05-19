package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeAchievementDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeExperienceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillEvidenceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillSetDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredDataDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.SourceSectionConfidence;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ResumeStructuredResultAssembler {

    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(?<start>(?:19|20)\\d{2}(?:\\s*[./年-]\\s*\\d{1,2}\\s*月?)?)\\s*(?:[-~—–至到]+)\\s*(?<end>(?:19|20)\\d{2}(?:\\s*[./年-]\\s*\\d{1,2}\\s*月?)?|至今|Present)",
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
        for (int index = 0; index < sections.size(); index++) {
            ResumeTextSectionDTO section = sections.get(index);
            if (section == null) {
                continue;
            }
            String sectionId = "section-%03d".formatted(result.size() + 1);
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
                    .blocks(buildRawBlocks(section))
                    .build());
        }
        return result;
    }

    private static List<ResumeRawSectionBlockDTO> buildRawBlocks(ResumeTextSectionDTO section) {
        if (section.getBlocks() != null && !section.getBlocks().isEmpty()) {
            return section.getBlocks().stream()
                    .filter(block -> block != null && hasText(block.getText()))
                    .map(block -> ResumeRawSectionBlockDTO.builder()
                            .index(block.getIndex())
                            .text(block.getText())
                            .iconType(block.getIconType())
                            .originalIndex(block.getOriginalIndex())
                            .displayOrder(block.getDisplayOrder())
                            .build())
                    .toList();
        }
        List<String> lines = section.getLines() == null ? List.of() : section.getLines();
        List<ResumeRawSectionBlockDTO> blocks = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!hasText(line)) {
                continue;
            }
            blocks.add(ResumeRawSectionBlockDTO.builder()
                    .index(index)
                    .text(line.strip())
                    .iconType(inferIconType(line))
                    .originalIndex(index)
                    .displayOrder(index)
                    .build());
        }
        return blocks;
    }

    private static String resolveSectionSource(ResumeTextSectionDTO section) {
        if (section.getBlocks() != null) {
            for (ResumeBlockDTO block : section.getBlocks()) {
                if (block != null && hasText(block.getFinalSectionSource())) {
                    return block.getFinalSectionSource();
                }
            }
        }
        return "RULE_SOURCE_SECTION";
    }

    private static ResumeStructuredDataDTO buildStructuredData(
            ResumeStructuredContentDTO content,
            List<ResumeRawSectionDTO> rawSections) {
        List<String> education = unique(content.getEducation());
        List<String> keywords = buildSkillKeywords(content, rawSections);
        List<ResumeExperienceDTO> experiences = buildExperiences(content, rawSections);
        List<ResumeProjectDTO> projects = buildProjects(content, experiences, rawSections);
        List<ResumeAchievementDTO> achievements = buildAchievements(content, experiences, rawSections);
        ResumeSkillSetDTO skills = ResumeSkillSetDTO.builder()
                .keywords(keywords)
                .groups(groupSkills(keywords))
                .evidence(buildSkillEvidence(keywords, rawSections))
                .build();
        return ResumeStructuredDataDTO.builder()
                .education(education)
                .skills(skills)
                .experiences(experiences)
                .projects(projects)
                .achievements(achievements)
                .certificates(unique(content.getCertificates()))
                .summary(blankToNull(content.getSummary()))
                .others(unique(content.getOthers()))
                .build();
    }

    private static List<String> buildSkillKeywords(ResumeStructuredContentDTO content, List<ResumeRawSectionDTO> rawSections) {
        Set<String> keywords = new LinkedHashSet<>(unique(content.getSkills()));
        for (String line : evidenceLines(rawSections)) {
            addSkillsFromLine(line, keywords);
        }
        return List.copyOf(keywords);
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

    private static List<ResumeSkillEvidenceDTO> buildSkillEvidence(List<String> keywords, List<ResumeRawSectionDTO> rawSections) {
        List<ResumeSkillEvidenceDTO> evidence = new ArrayList<>();
        for (String keyword : keywords) {
            for (ResumeRawSectionDTO section : rawSections) {
                for (ResumeRawSectionBlockDTO block : safeBlocks(section)) {
                    if (containsSkillAlias(block.getText(), keyword)) {
                        evidence.add(ResumeSkillEvidenceDTO.builder()
                                .skill(keyword)
                                .sourceSectionId(section.getId())
                                .sourceText(block.getText())
                                .build());
                        break;
                    }
                }
            }
        }
        return evidence;
    }

    private static List<ResumeExperienceDTO> buildExperiences(
            ResumeStructuredContentDTO content,
            List<ResumeRawSectionDTO> rawSections) {
        List<ResumeExperienceDTO> experiences = new ArrayList<>();
        appendExperiences(experiences, "WORK", content.getWorkExperiences(), "工作经历", 0.86,
                findSourceSectionId(rawSections, "WORK_EXPERIENCES"));
        appendExperiences(experiences, "INTERNSHIP", content.getInternships(), "实习经历", 0.84,
                findSourceSectionId(rawSections, "INTERNSHIPS"));
        appendExperiences(experiences, "CAMPUS", content.getCampusExperiences(), "校园经历", 0.78,
                findSourceSectionId(rawSections, "CAMPUS_EXPERIENCES"));
        return experiences;
    }

    private static void appendExperiences(
            List<ResumeExperienceDTO> target,
            String type,
            List<String> values,
            String sourceTitle,
            double confidence,
            String sourceSectionId) {
        for (String line : unique(values)) {
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

    private static List<ResumeProjectDTO> buildProjects(
            ResumeStructuredContentDTO content,
            List<ResumeExperienceDTO> experiences,
            List<ResumeRawSectionDTO> rawSections) {
        List<ResumeProjectDTO> projects = new ArrayList<>();
        projects.addAll(extractProjectsFromRawSections(rawSections));
        List<String> projectLines = unique(content.getProjects());
        projects.addAll(ProjectSourceTextExtractor.extractFromLines(projectLines, findSourceSectionId(rawSections, "PROJECTS")));
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
        List<String> evidence = unique(segment.lines());
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
                if (!hasText(item) || isProjectFieldLabel(item) || !isResponsibilityLine(item)) {
                    continue;
                }
                result.add(item);
            }
        }
        return unique(result);
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
        for (String award : unique(content.getAwards())) {
            achievements.add(buildAchievement(award, findSourceSectionId(rawSections, "AWARDS"), null, 0.86));
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
                achievements.add(buildAchievement(bullet, experience.getSourceSectionId(), index, 0.68));
            }
        }
        return achievements;
    }

    private static ResumeAchievementDTO buildAchievement(String line, String sourceSectionId, Integer parentExperienceIndex, double confidence) {
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
                .confidence(confidence)
                .build();
    }

    private static AchievementParts splitAchievement(String line) {
        String cleaned = line == null ? "" : line.strip();
        String timeRange = extractTimeRange(cleaned);
        String date = extractStartDate(cleaned);
        String withoutDate = DATE_RANGE_PATTERN.matcher(cleaned).replaceAll("").strip();
        String[] parts = withoutDate.split("[,，]", 2);
        if (parts.length == 2) {
            String level = parts[0].strip();
            String competition = parts[1].strip();
            String ranking = level.matches(".*(?:前\\s*\\d+%|Top\\s*\\d+%).*") ? level : null;
            return new AchievementParts(level + " " + competition, level, competition, ranking, timeRange, date);
        }
        return new AchievementParts(cleaned, null, null, null, timeRange, date);
    }

    private static void applyLegacyCompatibility(ResumeStructuredContentDTO content, ResumeStructuredDataDTO structuredData) {
        if (structuredData == null) {
            return;
        }
        content.setEducation(unique(structuredData.getEducation()));
        content.setSkills(structuredData.getSkills() == null ? List.of() : unique(structuredData.getSkills().getKeywords()));
        content.setWorkExperiences(experienceDescriptions(structuredData.getExperiences(), Set.of("WORK")));
        content.setInternships(experienceDescriptions(structuredData.getExperiences(), Set.of("INTERNSHIP")));
        content.setCampusExperiences(experienceDescriptions(structuredData.getExperiences(), Set.of("CAMPUS", "PRACTICE", "VOLUNTEER")));
        content.setProjects(projectDescriptions(structuredData.getProjects()));
        content.setAwards(unique(content.getAwards()));
        content.setCertificates(unique(structuredData.getCertificates()));
        content.setSummary(structuredData.getSummary());
        content.setOthers(unique(structuredData.getOthers()));
    }

    private static List<String> experienceDescriptions(List<ResumeExperienceDTO> experiences, Set<String> types) {
        return safeList(experiences).stream()
                .filter(item -> item != null && types.contains(item.getType()))
                .map(ResumeExperienceDTO::getDescription)
                .filter(ResumeStructuredResultAssembler::hasText)
                .distinct()
                .toList();
    }

    private static List<String> projectDescriptions(List<ResumeProjectDTO> projects) {
        List<String> values = new ArrayList<>();
        for (ResumeProjectDTO item : safeList(projects)) {
            if (item == null) {
                continue;
            }
            if (hasText(item.getName()) && !item.getName().matches("^项目经历\\s*\\d+$")) {
                values.add(item.getName());
            }
            if (hasText(item.getDescription())) {
                values.add(item.getDescription());
            }
            values.addAll(safeList(item.getResponsibilities()));
            values.addAll(safeList(item.getEvidence()));
        }
        return unique(values);
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
        Matcher matcher = DATE_RANGE_PATTERN.matcher(line);
        String cleaned = matcher.find() ? line.substring(matcher.end()).strip() : line.strip();
        String[] parts = cleaned.split("\\s+");
        for (String part : parts) {
            if (part.matches(".*(?:公司|集团|学校|学院|大学|中心|协会|社团|实验室).*")) {
                return part;
            }
        }
        return null;
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
}
