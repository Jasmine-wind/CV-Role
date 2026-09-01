package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ProjectSourceTextExtractor {

    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(
            "(?<start>(?:19|20)\\d{2}(?:\\s*[./年-]\\s*\\d{1,2}\\s*月?)?)\\s*(?:[-~—–至到]+)\\s*(?<end>(?:19|20)\\d{2}(?:\\s*[./年-]\\s*\\d{1,2}\\s*月?)?|至今|Present)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_INDEX_PATTERN = Pattern.compile("^项目\\s*(?:[一二三四五六七八九十]+|\\d+)\\s*[:：.、-]?\\s*(?<tail>.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_NAME_LABEL_PATTERN = Pattern.compile("^(?:项目名称|项目名|项目|系统名称)\\s*[:：]\\s*(?<value>.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_NAME_INLINE_PATTERN = Pattern.compile("^(?:项目名称|项目名|系统名称)\\s+(?<value>\\S.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_DESCRIPTION_LABEL_PATTERN = Pattern.compile("^(?:项目描述|项目简介|项目介绍|系统简介)\\s*[:：]\\s*(?<value>.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_RESPONSIBILITY_LABEL_PATTERN = Pattern.compile("^(?:责任描述|主要职责|负责模块|主要工作|工作内容|主要工作和业绩)\\s*[:：]\\s*(?<value>.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_TECH_LABEL_PATTERN = Pattern.compile("^(?:技术选型|技术栈|使用技术|开发框架|软件架构|软件构架|技术架构)\\s*[:：]\\s*(?<value>.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_ENV_LABEL_PATTERN = Pattern.compile("^(?:开发环境|开发工具|环境)\\s*[:：]\\s*(?<value>.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROJECT_TIME_LABEL_PATTERN = Pattern.compile("^(?:开发时间|开发周期|项目周期|时间)\\s*[:：]\\s*(?<value>.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ROLE_PATTERN = Pattern.compile("(?<role>项目组组长|项目负责人|负责人|组长|核心成员|成员|开发者)");
    private static final Pattern MENTOR_PATTERN = Pattern.compile("导师\\s*[:：]\\s*(?<mentor>[\\u4e00-\\u9fa5A-Za-z .·-]{2,20})");
    private static final Map<String, List<String>> TECH_ALIASES = new LinkedHashMap<>();

    static {
        putSkill("Java", "Java", "JavaSE", "JavaEE");
        putSkill("Spring", "Spring");
        putSkill("Spring Boot", "Spring Boot", "SpringBoot", "springboot");
        putSkill("Spring Data", "Spring Data", "SpringData");
        putSkill("Spring MVC", "Spring MVC", "SpringMVC", "springmvc");
        putSkill("Spring Security", "Spring Security", "Spring Scuretiry", "SpringSecurity");
        putSkill("MyBatis", "MyBatis", "Mybatis", "mybatis");
        putSkill("Dubbo", "Dubbo", "dubbo");
        putSkill("RabbitMQ", "RabbitMQ");
        putSkill("Kafka", "Kafka");
        putSkill("Redis", "Redis", "redis");
        putSkill("MySQL", "MySQL", "mysql");
        putSkill("Oracle", "Oracle", "oracle");
        putSkill("MongoDB", "MongoDB", "Mongodb");
        putSkill("Elasticsearch", "Elasticsearch", "ElasticSearch", "ES");
        putSkill("Maven", "Maven", "maven");
        putSkill("Tomcat", "Tomcat", "tomcat", "tomcat7", "tomcat8", "tomcat9", "tomcat10");
        putSkill("JDK", "JDK", "JDK1.8", "JDK8", "JDK 8");
        putSkill("Vue", "Vue", "vue", "Vue.js");
        putSkill("jQuery", "jQuery", "JQuery");
        putSkill("Ajax", "Ajax", "AJAX");
        putSkill("Linux", "Linux");
        putSkill("FastDFS", "FastDFS");
        putSkill("FreeMarker", "FreeMarker", "freemarker");
        putSkill("ECharts", "ECharts", "echarts");
        putSkill("Quartz", "Quartz");
        putSkill("Shiro", "Shiro", "Apache shiro", "Apache Shiro");
        putSkill("Element UI", "ElementUI", "elementUi", "Element UI");
        putSkill("Zookeeper", "Zookeeper", "ZooKeeper", "zookeeper");
        putSkill("SSM", "SSM");
        putSkill("Druid", "Druid", "druid");
        putSkill("Python", "Python", "python");
        putSkill("C++", "C++");
        putSkill("C", "C");
        putSkill("Verilog", "Verilog");
        putSkill("YOLO", "YOLO", "Yolo", "yolo");
        putSkill("OpenCV", "OpenCV", "Opencv", "opencv");
        putSkill("DETR", "DETR");
        putSkill("Transformer", "Transformer");
        putSkill("MATLAB", "MATLAB", "Matlab");
        putSkill("PyTorch", "PyTorch", "Pytorch");
        putSkill("TensorFlow", "TensorFlow", "Tensorflow");
        putSkill("Scikit-learn", "Scikit-learn", "Sklearn");
        putSkill("Pandas", "Pandas");
    }

    private ProjectSourceTextExtractor() {
    }

    static List<ResumeProjectDTO> extractFromRawSections(List<ResumeRawSectionDTO> rawSections) {
        List<ResumeProjectDTO> projects = new ArrayList<>();
        List<SourceLine> pendingPrefixLines = new ArrayList<>();
        for (ResumeRawSectionDTO section : rawSections == null ? List.<ResumeRawSectionDTO>of() : rawSections) {
            if (section == null || !"PROJECTS".equals(section.getNormalizedSection())) {
                continue;
            }
            List<ProjectSegment> segments = splitSegments(linesFromSection(section), section.getId(), null);
            for (ProjectSegment segment : segments) {
                if (isProjectPrefixOnly(segment.lines())) {
                    pendingPrefixLines.addAll(segment.lines());
                    continue;
                }
                if (!pendingPrefixLines.isEmpty()) {
                    segment.prepend(pendingPrefixLines);
                    pendingPrefixLines.clear();
                }
                ResumeProjectDTO project = buildProject(segment, projects.size(), null);
                if (project != null) {
                    projects.add(project);
                }
            }
        }
        return deduplicateProjects(projects);
    }

    static List<ResumeProjectDTO> extractFromLines(List<String> lines, String sourceSectionId) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<SourceLine> sourceLines = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (hasText(line)) {
                sourceLines.add(new SourceLine(line.strip(), index + 1, index + 1));
            }
        }
        List<ResumeProjectDTO> projects = new ArrayList<>();
        List<SourceLine> pendingPrefixLines = new ArrayList<>();
        for (ProjectSegment segment : splitSegments(sourceLines, sourceSectionId, null)) {
            if (isProjectPrefixOnly(segment.lines())) {
                pendingPrefixLines.addAll(segment.lines());
                continue;
            }
            if (!pendingPrefixLines.isEmpty()) {
                segment.prepend(pendingPrefixLines);
                pendingPrefixLines.clear();
            }
            ResumeProjectDTO project = buildProject(segment, projects.size(), null);
            if (project != null) {
                projects.add(project);
            }
        }
        return deduplicateProjects(projects);
    }

    static List<ResumeProjectDTO> expandProjects(List<ResumeProjectDTO> projects) {
        if (projects == null || projects.isEmpty()) {
            return List.of();
        }
        List<ResumeProjectDTO> expanded = new ArrayList<>();
        Set<String> expandedSourceKeys = new LinkedHashSet<>();
        for (ResumeProjectDTO project : projects) {
            if (project == null) {
                continue;
            }
            String sourceKey = sourceKey(project.getSourceRef());
            if (!sourceKey.isBlank() && expandedSourceKeys.contains(sourceKey)) {
                continue;
            }
            List<ResumeProjectDTO> fromSource = extractFromProjectSource(project, expanded.size());
            if (fromSource.size() > 1) {
                expanded.addAll(fromSource);
                if (!sourceKey.isBlank()) {
                    expandedSourceKeys.add(sourceKey);
                }
            } else if (fromSource.size() == 1) {
                expanded.add(mergeProject(project, fromSource.get(0)));
            } else {
                expanded.add(project);
            }
        }
        return deduplicateProjects(expanded);
    }

    private static List<ResumeProjectDTO> extractFromProjectSource(ResumeProjectDTO project, int indexOffset) {
        ResumeSourceRefDTO sourceRef = project == null ? null : project.getSourceRef();
        if (sourceRef == null || !hasText(sourceRef.getText())) {
            return List.of();
        }
        Integer startLine = sourceRef.getStartLine();
        List<ProjectSegment> segments = splitSegments(linesFromSourceRef(sourceRef), project.getSourceSectionId(), startLine);
        List<ResumeProjectDTO> projects = new ArrayList<>();
        List<SourceLine> pendingPrefixLines = new ArrayList<>();
        for (ProjectSegment segment : segments) {
            if (isProjectPrefixOnly(segment.lines())) {
                pendingPrefixLines.addAll(segment.lines());
                continue;
            }
            if (!pendingPrefixLines.isEmpty()) {
                segment.prepend(pendingPrefixLines);
                pendingPrefixLines.clear();
            }
            ResumeProjectDTO extracted = buildProject(segment, indexOffset + projects.size(), sourceRef);
            if (extracted != null) {
                projects.add(extracted);
            }
        }
        return projects;
    }

    private static ResumeProjectDTO mergeProject(ResumeProjectDTO base, ResumeProjectDTO fallback) {
        return ResumeProjectDTO.builder()
                .name(firstNonBlank(cleanProjectName(base.getName()), cleanProjectName(fallback.getName())))
                .description(firstNonBlank(base.getDescription(), fallback.getDescription()))
                .role(firstNonBlank(base.getRole(), fallback.getRole()))
                .mentor(firstNonBlank(base.getMentor(), fallback.getMentor()))
                .timeRange(firstNonBlank(base.getTimeRange(), fallback.getTimeRange()))
                .environment(firstNonBlank(base.getEnvironment(), fallback.getEnvironment()))
                .techStack(unique(concat(base.getTechStack(), fallback.getTechStack())))
                .responsibilities(unique(concat(base.getResponsibilities(), fallback.getResponsibilities())))
                .startDate(firstNonBlank(base.getStartDate(), fallback.getStartDate()))
                .endDate(firstNonBlank(base.getEndDate(), fallback.getEndDate()))
                .sourceType(firstNonBlank(base.getSourceType(), fallback.getSourceType(), "INDEPENDENT"))
                .parentExperienceIndex(base.getParentExperienceIndex() == null ? fallback.getParentExperienceIndex() : base.getParentExperienceIndex())
                .sourceSectionId(firstNonBlank(base.getSourceSectionId(), fallback.getSourceSectionId()))
                .evidence(unique(concat(base.getEvidence(), fallback.getEvidence())))
                .sourceRef(base.getSourceRef() == null ? fallback.getSourceRef() : base.getSourceRef())
                .confidence(base.getConfidence() == null ? fallback.getConfidence() : Math.max(base.getConfidence(), fallback.getConfidence() == null ? 0.0 : fallback.getConfidence()))
                .build();
    }

    private static List<ResumeProjectDTO> deduplicateProjects(List<ResumeProjectDTO> projects) {
        List<ResumeProjectDTO> result = new ArrayList<>();
        for (ResumeProjectDTO project : projects == null ? List.<ResumeProjectDTO>of() : projects) {
            if (!shouldKeepProject(project)) {
                continue;
            }
            int duplicateIndex = -1;
            for (int index = 0; index < result.size(); index++) {
                if (isDuplicateProject(result.get(index), project)) {
                    duplicateIndex = index;
                    break;
                }
            }
            if (duplicateIndex < 0) {
                result.add(project);
                continue;
            }
            ResumeProjectDTO existing = result.get(duplicateIndex);
            ResumeProjectDTO preferred = projectScore(project) > projectScore(existing) ? project : existing;
            ResumeProjectDTO secondary = preferred == project ? existing : project;
            result.set(duplicateIndex, mergeProject(preferred, secondary));
        }
        return result;
    }

    private static boolean shouldKeepProject(ResumeProjectDTO project) {
        if (project == null) {
            return false;
        }
        boolean hasReliableTitle = hasText(cleanProjectName(project.getName()));
        boolean hasSummary = isHighQualitySummary(project.getDescription());
        boolean hasResponsibilities = project.getResponsibilities() != null && !project.getResponsibilities().isEmpty();
        boolean hasTechStack = project.getTechStack() != null && !project.getTechStack().isEmpty();
        if (hasReliableTitle) {
            return hasSummary || hasResponsibilities || hasTechStack;
        }
        return hasSummary && hasResponsibilities;
    }

    private static boolean isDuplicateProject(ResumeProjectDTO left, ResumeProjectDTO right) {
        if (left == null || right == null) {
            return false;
        }
        ResumeSourceRefDTO leftRef = left.getSourceRef();
        ResumeSourceRefDTO rightRef = right.getSourceRef();
        if (sameSourceRange(leftRef, rightRef) || sourceRangeOverlap(leftRef, rightRef) >= 0.6) {
            return true;
        }
        String leftName = cleanProjectName(left.getName());
        String rightName = cleanProjectName(right.getName());
        if (hasText(leftName) && hasText(rightName) && similarText(leftName, rightName)) {
            return true;
        }
        return false;
    }

    private static int projectScore(ResumeProjectDTO project) {
        int score = 0;
        if (hasText(cleanProjectName(project.getName()))) {
            score += 6;
        }
        if (isHighQualitySummary(project.getDescription())) {
            score += 3;
        }
        score += Math.min(project.getResponsibilities() == null ? 0 : project.getResponsibilities().size(), 4);
        score += Math.min(project.getTechStack() == null ? 0 : project.getTechStack().size(), 4);
        ResumeSourceRefDTO sourceRef = project.getSourceRef();
        if (sourceRef != null && hasText(sourceRef.getText())) {
            score += Math.min(sourceRef.getText().length() / 80, 3);
        }
        return score;
    }

    private static boolean sameSourceRange(ResumeSourceRefDTO left, ResumeSourceRefDTO right) {
        return left != null
                && right != null
                && left.getStartLine() != null
                && left.getEndLine() != null
                && left.getStartLine().equals(right.getStartLine())
                && left.getEndLine().equals(right.getEndLine());
    }

    private static double sourceRangeOverlap(ResumeSourceRefDTO left, ResumeSourceRefDTO right) {
        if (left == null || right == null || left.getStartLine() == null || left.getEndLine() == null || right.getStartLine() == null || right.getEndLine() == null) {
            return 0.0;
        }
        int start = Math.max(left.getStartLine(), right.getStartLine());
        int end = Math.min(left.getEndLine(), right.getEndLine());
        if (end < start) {
            return 0.0;
        }
        int overlap = end - start + 1;
        int shorter = Math.min(left.getEndLine() - left.getStartLine() + 1, right.getEndLine() - right.getStartLine() + 1);
        return shorter <= 0 ? 0.0 : (double) overlap / shorter;
    }

    private static List<SourceLine> linesFromSection(ResumeRawSectionDTO section) {
        List<SourceLine> lines = new ArrayList<>();
        List<ResumeRawSectionBlockDTO> blocks = section == null || section.getBlocks() == null ? List.of() : section.getBlocks();
        for (ResumeRawSectionBlockDTO block : blocks) {
            if (block != null && hasText(block.getText())) {
                Integer lineId = positiveOrFallback(block.getDisplayOrder(), block.getOriginalIndex(), block.getIndex(), lines.size() + 1);
                lines.add(new SourceLine(block.getText().strip(), lineId, lineId));
            }
        }
        return lines;
    }

    private static List<SourceLine> linesFromSourceRef(ResumeSourceRefDTO sourceRef) {
        if (sourceRef == null || !hasText(sourceRef.getText())) {
            return List.of();
        }
        List<String> rawLines = sourceRef.getText().lines().toList();
        List<SourceLine> lines = new ArrayList<>();
        int start = sourceRef.getStartLine() == null ? 1 : sourceRef.getStartLine();
        for (int index = 0; index < rawLines.size(); index++) {
            String line = rawLines.get(index);
            if (hasText(line)) {
                int lineId = start + index;
                lines.add(new SourceLine(line.strip(), lineId, index));
            }
        }
        return lines;
    }

    private static List<ProjectSegment> splitSegments(List<SourceLine> sourceLines, String sourceSectionId, Integer sourceStartLine) {
        List<ProjectSegment> segments = new ArrayList<>();
        ProjectSegment current = null;
        for (SourceLine sourceLine : sourceLines == null ? List.<SourceLine>of() : sourceLines) {
            String line = sourceLine.text();
            if (!hasText(line) || isProjectSectionHeading(line)) {
                continue;
            }
            Matcher indexMatcher = PROJECT_INDEX_PATTERN.matcher(line);
            LabelValue projectNameLabel = parseProjectNameLabel(line);
            boolean startsByIndex = indexMatcher.matches();
            boolean startsByDatedHeader = projectNameFromDatedHeader(line) != null;
            boolean startsByRepeatedName = projectNameLabel != null
                    && hasText(cleanProjectName(projectNameLabel.value()))
                    && current != null
                    && current.hasProjectFieldContent();
            if (startsByIndex || startsByDatedHeader || startsByRepeatedName) {
                if (current != null && current.hasMeaningfulContent()) {
                    segments.add(current);
                }
                current = new ProjectSegment(sourceSectionId, sourceStartLine);
                if (startsByIndex) {
                    String tail = indexMatcher.group("tail");
                    if (hasText(tail) && !isProjectFieldLabel(tail)) {
                        current.add(new SourceLine(tail.strip(), sourceLine.lineId(), sourceLine.order()));
                    }
                } else {
                    current.add(sourceLine);
                }
                continue;
            }
            if (current == null) {
                current = new ProjectSegment(sourceSectionId, sourceStartLine);
            }
            current.add(sourceLine);
        }
        if (current != null && current.hasMeaningfulContent()) {
            segments.add(current);
        }
        return segments;
    }

    private static ResumeProjectDTO buildProject(ProjectSegment segment, int index, ResumeSourceRefDTO parentSourceRef) {
        ProjectFields fields = parseFields(segment.lines());
        List<String> evidence = unique(segment.lines().stream().map(SourceLine::text).toList());
        if (evidence.isEmpty()) {
            return null;
        }
        String sourceText = String.join("\n", evidence);
        String name = cleanProjectName(firstNonBlank(
                fields.name(),
                evidence.stream().filter(ProjectSourceTextExtractor::looksLikeStandaloneProjectName).findFirst().orElse(null)));
        boolean nameAlreadyInEvidence = false;
        if (hasText(name)) {
            for (String line : evidence) {
                if (sameText(line, name)) {
                    nameAlreadyInEvidence = true;
                    break;
                }
            }
        }
        if (hasText(name) && !nameAlreadyInEvidence) {
            // Keep a normalized field value alongside the labeled source line so the
            // legacy candidate view remains compatible without changing the source boundary.
            evidence.add(name);
        }
        List<String> responsibilities = normalizeResponsibilities(firstNonEmpty(fields.responsibilities(), extractResponsibilityLines(evidence)));
        String summary = firstNonBlank(firstSentenceSummary(fields.description()), fallbackSummary(evidence, name, responsibilities));
        Set<String> techStack = new LinkedHashSet<>();
        addSkills(fields.techText(), techStack);
        addSkills(fields.environment(), techStack);
        addSkills(fields.description(), techStack);
        responsibilities.forEach(line -> addSkills(line, techStack));
        addSkills(sourceText, techStack);
        boolean hasReliableTitle = hasText(name);
        boolean hasUsefulSummary = isHighQualitySummary(summary);
        if (hasReliableTitle && !hasUsefulSummary && responsibilities.isEmpty() && techStack.isEmpty()) {
            return null;
        }
        if (!hasReliableTitle && (!hasUsefulSummary || responsibilities.isEmpty())) {
            return null;
        }
        if (!hasReliableTitle) {
            name = "项目经历";
        }
        ResumeSourceRefDTO sourceRef = buildSourceRef(segment, parentSourceRef, sourceText);
        return ResumeProjectDTO.builder()
                .name(name)
                .description(summary)
                .role(extractRole(sourceText))
                .mentor(extractMentor(sourceText))
                .timeRange(firstNonBlank(fields.timeRange(), extractTimeRange(sourceText)))
                .environment(blankToNull(fields.environment()))
                .techStack(List.copyOf(techStack))
                .responsibilities(responsibilities)
                .startDate(extractStartDate(sourceText))
                .endDate(extractEndDate(sourceText))
                .sourceType("INDEPENDENT")
                .sourceSectionId(segment.sourceSectionId())
                .evidence(evidence)
                .sourceRef(sourceRef)
                .confidence(0.9)
                .build();
    }

    private static ProjectFields parseFields(List<SourceLine> sourceLines) {
        String name = null;
        String timeRange = null;
        List<String> descriptions = new ArrayList<>();
        List<String> responsibilities = new ArrayList<>();
        List<String> techTexts = new ArrayList<>();
        List<String> environments = new ArrayList<>();
        ProjectField activeField = ProjectField.DESCRIPTION;
        for (SourceLine sourceLine : sourceLines == null ? List.<SourceLine>of() : sourceLines) {
            String line = sourceLine.text();
            if (!hasText(line) || PROJECT_INDEX_PATTERN.matcher(line).matches()) {
                continue;
            }
            String datedName = projectNameFromDatedHeader(line);
            if (datedName != null) {
                name = firstNonBlank(name, datedName);
                timeRange = firstNonBlank(timeRange, extractTimeRange(line));
                continue;
            }
            LabelValue labelValue = parseLabel(line);
            if (labelValue != null) {
                activeField = labelValue.field();
                String value = labelValue.value();
                if (!hasText(value)) {
                    continue;
                }
                switch (activeField) {
                    case NAME -> {
                        String candidateName = cleanProjectName(value);
                        if (hasText(candidateName)) {
                            name = firstNonBlank(name, candidateName);
                        } else {
                            descriptions.add(value);
                        }
                    }
                    case TIME -> timeRange = firstNonBlank(timeRange, value);
                    case DESCRIPTION -> descriptions.add(value);
                    case RESPONSIBILITY -> responsibilities.add(value);
                    case TECH -> techTexts.add(value);
                    case ENVIRONMENT -> environments.add(value);
                }
                continue;
            }
            ProjectField labelOnly = parseLabelOnly(line);
            if (labelOnly != null) {
                activeField = labelOnly;
                continue;
            }
            if (activeField == ProjectField.NAME) {
                String candidateName = cleanProjectName(line);
                if (hasText(candidateName)) {
                    name = firstNonBlank(name, candidateName);
                } else {
                    descriptions.add(line);
                }
                activeField = ProjectField.DESCRIPTION;
            } else if (activeField == ProjectField.TIME) {
                timeRange = firstNonBlank(timeRange, line);
            } else if (activeField == ProjectField.RESPONSIBILITY || isResponsibilityLine(line)) {
                responsibilities.add(line);
            } else if (activeField == ProjectField.TECH) {
                techTexts.add(line);
            } else if (activeField == ProjectField.ENVIRONMENT) {
                environments.add(line);
            } else if (!hasText(name) && looksLikeStandaloneProjectName(line)) {
                name = line;
            } else {
                descriptions.add(line);
            }
        }
        return new ProjectFields(name,
                timeRange,
                String.join(" ", descriptions).strip(),
                String.join("，", techTexts).strip(),
                String.join("，", environments).strip(),
                responsibilities);
    }

    private static LabelValue parseLabel(String line) {
        LabelValue projectNameLabel = parseProjectNameLabel(line);
        if (projectNameLabel != null) {
            return projectNameLabel;
        }
        for (Map.Entry<Pattern, ProjectField> entry : Map.of(
                PROJECT_TIME_LABEL_PATTERN, ProjectField.TIME,
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

    private static LabelValue parseProjectNameLabel(String line) {
        String cleaned = line == null ? "" : line.strip();
        Matcher labelMatcher = PROJECT_NAME_LABEL_PATTERN.matcher(cleaned);
        if (labelMatcher.matches()) {
            return new LabelValue(ProjectField.NAME, labelMatcher.group("value") == null ? "" : labelMatcher.group("value").strip());
        }
        Matcher inlineMatcher = PROJECT_NAME_INLINE_PATTERN.matcher(cleaned);
        if (inlineMatcher.matches()) {
            return new LabelValue(ProjectField.NAME, inlineMatcher.group("value") == null ? "" : inlineMatcher.group("value").strip());
        }
        return null;
    }

    private static ProjectField parseLabelOnly(String line) {
        String cleaned = line == null ? "" : line.strip();
        if (cleaned.matches("^(项目名称|项目名|项目|系统名称)\\s*[:：]?$")) {
            return ProjectField.NAME;
        }
        if (cleaned.matches("^(开发时间|开发周期|项目周期|时间)\\s*[:：]?$")) {
            return ProjectField.TIME;
        }
        if (cleaned.matches("^(项目描述|项目简介|项目介绍|系统简介)\\s*[:：]?$")) {
            return ProjectField.DESCRIPTION;
        }
        if (cleaned.matches("^(责任描述|主要职责|负责模块|主要工作|工作内容|主要工作和业绩)\\s*[:：]?$")) {
            return ProjectField.RESPONSIBILITY;
        }
        if (cleaned.matches("^(技术选型|技术栈|使用技术|开发框架|软件架构|软件构架|技术架构)\\s*[:：]?$")) {
            return ProjectField.TECH;
        }
        if (cleaned.matches("^(开发环境|开发工具|环境)\\s*[:：]?$")) {
            return ProjectField.ENVIRONMENT;
        }
        return null;
    }

    private static boolean isProjectFieldLabel(String line) {
        return parseLabel(line == null ? "" : line.strip()) != null || parseLabelOnly(line) != null;
    }

    private static boolean isProjectSectionHeading(String line) {
        return line != null && line.strip().matches("^(项目经历|项目经验|项目实践|项目介绍|参加项目描述|Projects|Project Experience)\\s*$");
    }

    /**
     * 带日期的项目标题是跨格式都相对可靠的条目边界：只接受日期前有短标题、且标题不像职责句的行。
     * 没有明确边界的连续文本不在这里猜测，交给后续未决内容处理。
     */
    private static String projectNameFromDatedHeader(String line) {
        if (!hasText(line)) {
            return null;
        }
        Matcher matcher = DATE_RANGE_PATTERN.matcher(line.strip());
        if (!matcher.find()) {
            return null;
        }
        String before = line.substring(0, matcher.start()).strip();
        String after = line.substring(matcher.end()).strip();
        String candidate = hasText(before) ? before : after;
        candidate = removeFieldLabel(candidate);
        if (!hasText(candidate)
                || candidate.length() > 36
                || candidate.matches(".*[。！？!?；;，,].*")
                || candidate.matches("^(负责|参与|使用|采用|通过|实现|开发|编写|维护|优化|设计|管理|完成|做|对|是一个|该系统|该项目|主要).*")) {
            return null;
        }
        return candidate;
    }

    private static boolean isProjectPrefixOnly(List<SourceLine> lines) {
        List<String> usefulLines = unique(lines.stream().map(SourceLine::text).toList());
        return !usefulLines.isEmpty()
                && usefulLines.stream().allMatch(line -> PROJECT_ENV_LABEL_PATTERN.matcher(line).matches()
                || PROJECT_TECH_LABEL_PATTERN.matcher(line).matches()
                || PROJECT_TIME_LABEL_PATTERN.matcher(line).matches()
                || parseLabelOnly(line) == ProjectField.ENVIRONMENT
                || parseLabelOnly(line) == ProjectField.TECH
                || parseLabelOnly(line) == ProjectField.TIME);
    }

    private static boolean looksLikeStandaloneProjectName(String line) {
        if (!hasText(line) || isProjectFieldLabel(line)) {
            return false;
        }
        String cleaned = cleanProjectName(line);
        if (!hasText(cleaned) || !isReliableProjectTitle(cleaned) || cleaned.length() > 36 || cleaned.matches(".*[。；;，,].*")) {
            return false;
        }
        return cleaned.matches(".*(?:系统|平台|项目|中心|网站|商城|网|SRTP|研究).*");
    }

    private static String cleanProjectName(String value) {
        if (!hasText(value)) {
            return "";
        }
        String cleaned = removeFieldLabel(value);
        cleaned = DATE_RANGE_PATTERN.matcher(cleaned).replaceAll("").strip();
        cleaned = cleaned.replaceAll("^项目\\s*(?:[一二三四五六七八九十]+|\\d+)\\s*[:：.、-]?\\s*", "").strip();
        if (!hasText(cleaned) || isProjectFieldLabel(cleaned)) {
            return "";
        }
        cleaned = cleaned.split("[，,；;。]")[0].strip();
        if (!isReliableProjectTitle(cleaned)) {
            return "";
        }
        return cleaned;
    }

    private static boolean isReliableProjectTitle(String value) {
        String cleaned = value == null ? "" : removeFieldLabel(value).strip();
        if (!hasText(cleaned) || isProjectFieldLabel(cleaned)) {
            return false;
        }
        if (cleaned.matches("^项目经历\\s*\\d*$")) {
            return false;
        }
        if (cleaned.length() > 36 || cleaned.matches(".*[。！？!?；;].*")) {
            return false;
        }
        if (cleaned.matches("^(负责|参与|使用|采用|通过|实现|开发|编写|维护|优化|设计|管理|完成|做|对|是一个|该系统|该项目|主要|为了|左右).*")) {
            return false;
        }
        if (cleaned.matches(".*(?:是一个|该系统|该项目|采用|通过|使用|负责|参与|实现|开发|编写|维护|优化|设计|左右代码|其余代码|交给系统|自动生成).*")) {
            return false;
        }
        return !isSkillOnly(cleaned);
    }

    private static String firstSentenceSummary(String value) {
        if (!hasText(value)) {
            return "";
        }
        String cleaned = removeFieldLabel(value);
        if (isSkillOnly(cleaned)) {
            return "";
        }
        String[] parts = cleaned.split("(?<=[。！？!?；;])");
        String summary = "";
        int sentenceCount = 0;
        for (String part : parts) {
            if (!hasText(part) || isProjectFieldLabel(part)) {
                continue;
            }
            summary = (summary + part.strip()).strip();
            sentenceCount++;
            if (sentenceCount >= 2 || summary.length() >= 100) {
                break;
            }
        }
        if (!hasText(summary)) {
            summary = cleaned;
        }
        return summary.length() > 180 ? summary.substring(0, 180) + "..." : summary;
    }

    private static boolean isHighQualitySummary(String value) {
        String cleaned = removeFieldLabel(value);
        return hasText(cleaned)
                && cleaned.length() >= 8
                && !isProjectFieldLabel(cleaned)
                && !isSkillOnly(cleaned);
    }

    private static String fallbackSummary(List<String> evidence, String name, List<String> responsibilities) {
        for (String line : evidence == null ? List.<String>of() : evidence) {
            String cleaned = removeFieldLabel(line);
            if (!hasText(cleaned)
                    || cleaned.equals(name)
                    || projectNameFromDatedHeader(cleaned) != null
                    || isProjectFieldLabel(cleaned)
                    || isSkillOnly(cleaned)) {
                continue;
            }
            if (responsibilities != null && responsibilities.stream().anyMatch(item -> sameText(item, cleaned))) {
                continue;
            }
            if (cleaned.matches(".*(?:系统|平台|项目|实现|支持|提供|用于|应用于|主要).*")) {
                return firstSentenceSummary(cleaned);
            }
        }
        return "";
    }

    private static List<String> normalizeResponsibilities(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            String cleaned = removeFieldLabel(value);
            for (String part : cleaned.split("[；;]\\s*|(?<=。)")) {
                String item = part.strip();
                if (!hasText(item) || isProjectFieldLabel(item) || isSkillOnly(item)) {
                    continue;
                }
                // 责任描述中的非谓语尾句（例如“；压测峰值达到 6,000 QPS”）仍是用户事实，
                // 不能因为不像“负责/实现”句就被静默丢弃。
                result.add(item);
            }
        }
        return unique(result);
    }

    private static List<String> extractResponsibilityLines(List<String> evidence) {
        return (evidence == null ? List.<String>of() : evidence).stream()
                .map(ProjectSourceTextExtractor::removeFieldLabel)
                .filter(ProjectSourceTextExtractor::isResponsibilityLine)
                .toList();
    }

    private static boolean isResponsibilityLine(String line) {
        return hasText(line)
                && !isSkillOnly(line)
                && line.matches(".*(?:负责|参与|实现|开发|编写|维护|设计|优化|管理|完成).*");
    }

    private static String removeFieldLabel(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.strip()
                .replaceFirst("^(项目名称|项目名|项目|系统名称|开发时间|开发周期|项目周期|时间|项目描述|项目简介|项目介绍|系统简介|开发环境|开发工具|环境|技术选型|技术栈|使用技术|开发框架|软件架构|软件构架|技术架构|责任描述|主要职责|负责模块|主要工作|工作内容|主要工作和业绩)\\s*[:：]\\s*", "")
                .strip();
    }

    private static boolean isSkillOnly(String value) {
        String cleaned = value == null ? "" : value.strip();
        if (!hasText(cleaned)) {
            return true;
        }
        if (cleaned.matches("^[A-Za-z0-9+#.\\s,，、/\\\\+\\-]+$") && cleaned.matches(".*[,，、/\\\\+\\s].*")) {
            return true;
        }
        return TECH_ALIASES.keySet().stream().anyMatch(skill -> sameText(skill, cleaned));
    }

    private static void addSkills(String line, Set<String> skills) {
        if (!hasText(line)) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : TECH_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                if (containsAlias(line, alias)) {
                    skills.add(entry.getKey());
                    break;
                }
            }
        }
    }

    private static boolean containsAlias(String line, String alias) {
        if (!hasText(line) || !hasText(alias)) {
            return false;
        }
        String normalized = line.toLowerCase();
        String target = alias.toLowerCase();
        if (target.matches("[a-z0-9+#. ]+")) {
            return Pattern.compile("(?<![A-Za-z0-9+#.])" + Pattern.quote(alias) + "(?![A-Za-z0-9+#.])", Pattern.CASE_INSENSITIVE)
                    .matcher(line)
                    .find();
        }
        return normalized.contains(target);
    }

    private static ResumeSourceRefDTO buildSourceRef(ProjectSegment segment, ResumeSourceRefDTO parentSourceRef, String fallbackText) {
        List<Integer> ids = segment.lines().stream()
                .map(SourceLine::lineId)
                .filter(id -> id != null && id > 0)
                .toList();
        if (ids.isEmpty()) {
            return parentSourceRef;
        }
        int start = ids.stream().min(Integer::compareTo).orElse(ids.get(0));
        int end = ids.stream().max(Integer::compareTo).orElse(ids.get(ids.size() - 1));
        return ResumeSourceRefDTO.builder()
                .startLine(start)
                .endLine(end)
                .text(segment.sourceText(fallbackText))
                .build();
    }

    private static String extractRole(String text) {
        String value = text == null ? "" : text;
        Matcher dateMatcher = DATE_RANGE_PATTERN.matcher(value);
        if (dateMatcher.find()) {
            String suffix = value.substring(dateMatcher.end()).lines()
                    .map(String::strip)
                    .findFirst()
                    .orElse("");
            if (hasText(suffix)
                    && suffix.length() <= 24
                    && !suffix.matches(".*[，,。；;].*")) {
                return suffix;
            }
        }
        Matcher matcher = ROLE_PATTERN.matcher(value);
        return matcher.find() ? matcher.group("role") : null;
    }

    private static String extractMentor(String text) {
        Matcher matcher = MENTOR_PATTERN.matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group("mentor") : null;
    }

    private static String extractTimeRange(String value) {
        Matcher matcher = DATE_RANGE_PATTERN.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group().replaceAll("\\s+", "") : null;
    }

    private static String extractStartDate(String value) {
        Matcher matcher = DATE_RANGE_PATTERN.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group("start") : null;
    }

    private static String extractEndDate(String value) {
        Matcher matcher = DATE_RANGE_PATTERN.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group("end") : null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.strip();
            }
        }
        return "";
    }

    private static <T> List<T> firstNonEmpty(List<T> first, List<T> second) {
        return first != null && !first.isEmpty() ? first : second == null ? List.of() : second;
    }

    private static Integer positiveOrFallback(Integer... values) {
        if (values == null) {
            return 1;
        }
        for (Integer value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return 1;
    }

    private static List<String> concat(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>();
        if (first != null) {
            result.addAll(first);
        }
        if (second != null) {
            result.addAll(second);
        }
        return result;
    }

    private static List<String> unique(List<String> values) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            String cleaned = value == null ? "" : value.strip();
            String key = cleaned.toLowerCase();
            if (hasText(cleaned) && seen.add(key)) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value.strip() : null;
    }

    private static boolean sameText(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static boolean similarText(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (!hasText(normalizedLeft) || !hasText(normalizedRight)) {
            return false;
        }
        if (normalizedLeft.equals(normalizedRight)) {
            return true;
        }
        String shorter = normalizedLeft.length() <= normalizedRight.length() ? normalizedLeft : normalizedRight;
        String longer = normalizedLeft.length() > normalizedRight.length() ? normalizedLeft : normalizedRight;
        return shorter.length() >= 8 && longer.contains(shorter);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }

    private static String sourceKey(ResumeSourceRefDTO sourceRef) {
        if (sourceRef == null || !hasText(sourceRef.getText())) {
            return "";
        }
        return (sourceRef.getStartLine() == null ? "" : sourceRef.getStartLine())
                + "-"
                + (sourceRef.getEndLine() == null ? "" : sourceRef.getEndLine())
                + ":"
                + normalize(sourceRef.getText());
    }

    private static void putSkill(String canonical, String... aliases) {
        TECH_ALIASES.put(canonical, List.of(aliases));
    }

    private enum ProjectField {
        NAME,
        TIME,
        DESCRIPTION,
        RESPONSIBILITY,
        TECH,
        ENVIRONMENT
    }

    private record LabelValue(ProjectField field, String value) {
    }

    private record ProjectFields(
            String name,
            String timeRange,
            String description,
            String techText,
            String environment,
            List<String> responsibilities) {
    }

    private record SourceLine(String text, Integer lineId, Integer order) {
    }

    private static final class ProjectSegment {
        private final String sourceSectionId;
        private final Integer sourceStartLine;
        private final List<SourceLine> lines = new ArrayList<>();

        private ProjectSegment(String sourceSectionId, Integer sourceStartLine) {
            this.sourceSectionId = sourceSectionId;
            this.sourceStartLine = sourceStartLine;
        }

        private void add(SourceLine line) {
            if (line != null && hasText(line.text())) {
                lines.add(line);
            }
        }

        private void prepend(List<SourceLine> prefixLines) {
            List<SourceLine> merged = new ArrayList<>();
            merged.addAll(prefixLines == null ? List.of() : prefixLines);
            merged.addAll(lines);
            lines.clear();
            lines.addAll(merged);
        }

        private boolean hasMeaningfulContent() {
            return lines.stream().anyMatch(line -> hasText(line.text()) && !PROJECT_INDEX_PATTERN.matcher(line.text()).matches());
        }

        private boolean hasProjectFieldContent() {
            return lines.stream().anyMatch(line -> {
                String text = line.text();
                if (!hasText(text)) {
                    return false;
                }
                Matcher nameMatcher = PROJECT_NAME_LABEL_PATTERN.matcher(text);
                if (nameMatcher.matches() && hasText(nameMatcher.group("value"))) {
                    return true;
                }
                return PROJECT_DESCRIPTION_LABEL_PATTERN.matcher(text).matches()
                        || PROJECT_RESPONSIBILITY_LABEL_PATTERN.matcher(text).matches()
                        || PROJECT_TECH_LABEL_PATTERN.matcher(text).matches()
                        || PROJECT_ENV_LABEL_PATTERN.matcher(text).matches();
            });
        }

        private String sourceSectionId() {
            return sourceSectionId;
        }

        private List<SourceLine> lines() {
            return lines;
        }

        private String sourceText(String fallbackText) {
            String text = String.join("\n", lines.stream().map(SourceLine::text).filter(ProjectSourceTextExtractor::hasText).toList());
            return hasText(text) ? text : fallbackText;
        }
    }
}
