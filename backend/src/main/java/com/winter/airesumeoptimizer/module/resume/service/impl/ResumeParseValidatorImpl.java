package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBasicInfoFieldDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeParseValidator;
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
public class ResumeParseValidatorImpl implements ResumeParseValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\+?86[-\\s]?)?1[3-9]\\d[-\\s]?\\d{4}[-\\s]?\\d{4}(?!\\d)");
    private static final Pattern WORK_YEARS_PATTERN = Pattern.compile("^(?:[1-9]|[1-3]\\d|40)\\s*年$");
    private static final Pattern NUMBERING_ONLY_PATTERN = Pattern.compile("^(?:(?:\\(?\\d{1,3}\\)?|[一二三四五六七八九十百]+)[、.．)）:：]?|[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳])$");
    private static final Pattern SYMBOL_ONLY_PATTERN = Pattern.compile("^[\\s\\p{Punct}，。；：、（）【】《》“”‘’·•●○◆◇■□▪◦▶►✓✔①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳]+$");
    private static final int OTHERS_MAX_ITEMS = 8;
    private static final Set<String> SECTION_HEADINGS = Set.of(
            "个人信息", "基本信息", "联系方式", "教育经历", "教育背景", "专业技能", "技术能力", "技能关键词", "技术栈",
            "工作经历", "工作经验", "职业经历", "实习经历", "项目经历", "项目经验", "校园经历", "获奖经历", "证书",
            "自我评价", "个人总结", "个人概述", "profile", "education", "skills", "experience", "projects", "summary");
    private static final Set<String> INVALID_NAME_EXACT = Set.of(
            "本人", "个人简历", "personal resume", "resume", "personal", "curriculum vitae", "cv", "参加项目描述",
            "基本情况", "基本资料", "组织", "同学们");
    private static final Set<String> RESUME_TYPES = Set.of("STUDENT", "INTERN", "EXPERIENCED", "UNKNOWN");
    private static final List<String> DEGREE_WORDS = List.of("博士", "硕士", "研究生", "本科", "大专", "专科", "高中");
    private static final Map<String, List<String>> TECH_SKILL_ALIASES = new LinkedHashMap<>();

    static {
        putSkill("Java", "Java");
        putSkill("Spring", "Spring");
        putSkill("Spring Boot", "Spring Boot", "SpringBoot");
        putSkill("Spring Data", "Spring Data", "SpringData");
        putSkill("Spring MVC", "Spring MVC", "SpringMVC");
        putSkill("Spring Cloud", "Spring Cloud");
        putSkill("Spring Security", "Spring Security");
        putSkill("MyBatis-Plus", "MyBatis-Plus", "MyBatis Plus");
        putSkill("MyBatis", "MyBatis");
        putSkill("MySQL", "MySQL");
        putSkill("PostgreSQL", "PostgreSQL");
        putSkill("Redis", "Redis");
        putSkill("Docker", "Docker");
        putSkill("Kubernetes", "Kubernetes", "K8s");
        putSkill("Vue", "Vue", "Vue.js");
        putSkill("TypeScript", "TypeScript");
        putSkill("JavaScript", "JavaScript");
        putSkill("Python", "Python");
        putSkill("SQL", "SQL");
        putSkill("Pandas", "Pandas");
        putSkill("FastAPI", "FastAPI");
        putSkill("LangChain", "LangChain");
        putSkill("RAG", "RAG", "检索增强");
        putSkill("向量检索", "向量检索");
        putSkill("Prompt Engineering", "Prompt Engineering", "Prompt");
        putSkill("Linux", "Linux");
        putSkill("Git", "Git");
        putSkill("Maven", "Maven");
        putSkill("Gradle", "Gradle");
        putSkill("RESTful", "RESTful", "REST API");
        putSkill("JWT", "JWT");
        putSkill("HTML", "HTML");
        putSkill("CSS", "CSS");
        putSkill("RabbitMQ", "RabbitMQ");
        putSkill("RocketMQ", "RocketMQ");
        putSkill("Kafka", "Kafka");
        putSkill("Dubbo", "Dubbo");
        putSkill("Zookeeper", "Zookeeper", "ZooKeeper");
        putSkill("Eureka", "Eureka");
        putSkill("Nginx", "Nginx");
        putSkill("Elasticsearch", "Elasticsearch", "ElasticSearch", "ES");
        putSkill("MongoDB", "MongoDB");
        putSkill("Lucene", "Lucene");
        putSkill("FreeMarker", "FreeMarker", "freemarker");
        putSkill("FFmpeg", "FFmpeg", "ffmpeg");
        putSkill("IDEA", "IDEA", "IntelliJ IDEA");
        putSkill("Tomcat", "Tomcat", "tomcat7", "tomcat8", "tomcat9", "tomcat10");
        putSkill("JDK", "JDK", "JDK1.8", "JDK8", "JDK 8");
        putSkill("Oracle", "Oracle");
        putSkill("Netty", "Netty");
        putSkill("JUnit", "JUnit");
        putSkill("Mockito", "Mockito");
    }

    @Override
    public ResumeStructuredContentDTO validateAndMerge(
            ResumeStructuredContentDTO aiContent,
            ResumeStructuredContentDTO ruleContent,
            List<String> qualityWarnings) {
        ResumeStructuredContentDTO ai = aiContent == null ? ResumeStructuredContentDTO.builder().build() : aiContent;
        ResumeStructuredContentDTO rule = ruleContent == null ? ResumeStructuredContentDTO.builder().build() : ruleContent;
        List<String> warnings = new ArrayList<>(qualityWarnings == null ? List.of() : qualityWarnings);

        String name = validName(rule.getName()) ? rule.getName().strip() : null;
        if (isBlank(name) && validName(valueFromBasicInfo(rule, "name"))) {
            name = valueFromBasicInfo(rule, "name").strip();
        }
        if (isBlank(name) && validName(ai.getName())) {
            name = ai.getName().strip();
        }
        if (isBlank(name) && validName(valueFromBasicInfo(ai, "name"))) {
            name = valueFromBasicInfo(ai, "name").strip();
        }
        if (!isBlank(ai.getName()) && !ai.getName().strip().equals(name)) {
            warnings.add("AI_NAME_INVALID_FALLBACK_TO_RULE");
        }

        String phone = validPhone(rule.getPhone()) ? normalizePhone(rule.getPhone()) : null;
        if (isBlank(phone) && validPhone(valueFromBasicInfo(rule, "phone"))) {
            phone = normalizePhone(valueFromBasicInfo(rule, "phone"));
        }
        if (isBlank(phone) && validPhone(ai.getPhone())) {
            phone = normalizePhone(ai.getPhone());
        }
        if (isBlank(phone) && validPhone(valueFromBasicInfo(ai, "phone"))) {
            phone = normalizePhone(valueFromBasicInfo(ai, "phone"));
        }
        if (!isBlank(ai.getPhone()) && !normalizePhone(ai.getPhone()).equals(phone)) {
            warnings.add("AI_PHONE_INVALID_FALLBACK_TO_RULE");
        }

        String email = extractEmail(rule.getEmail());
        if (isBlank(email)) {
            email = extractEmail(valueFromBasicInfo(rule, "email"));
        }
        if (isBlank(email)) {
            email = extractEmail(ai.getEmail());
        }
        if (isBlank(email)) {
            email = extractEmail(valueFromBasicInfo(ai, "email"));
        }
        if (!isBlank(ai.getEmail()) && !ai.getEmail().strip().equals(email)) {
            warnings.add("AI_EMAIL_INVALID_FALLBACK_TO_RULE");
        }

        String jobIntention = firstNotBlank(rule.getJobIntention(), valueFromBasicInfo(rule, "jobIntention"),
                ai.getJobIntention(), valueFromBasicInfo(ai, "jobIntention"));
        String highestEducation = firstDegree(rule.getHighestEducation(), valueFromBasicInfo(rule, "degree"),
                ai.getHighestEducation(), valueFromBasicInfo(ai, "degree"));
        String resumeType = validResumeType(rule.getResumeType()) ? rule.getResumeType() : ai.getResumeType();
        if (!validResumeType(resumeType)) {
            resumeType = "UNKNOWN";
        }

        List<String> education = filterEducation(preferRuleList(rule.getEducation(), ai.getEducation()), warnings);
        List<String> skills = normalizeSkills(preferRuleList(rule.getSkills(), ai.getSkills()), warnings);
        List<String> workExperiences = uniqueLines(preferRuleList(rule.getWorkExperiences(), ai.getWorkExperiences()), warnings);
        List<String> internships = uniqueLines(preferRuleList(rule.getInternships(), ai.getInternships()), warnings);
        List<String> projects = uniqueLines(preferRuleList(rule.getProjects(), ai.getProjects()), warnings);
        List<String> campusExperiences = uniqueLines(preferRuleList(rule.getCampusExperiences(), ai.getCampusExperiences()), warnings);
        List<String> awards = uniqueLines(preferRuleList(rule.getAwards(), ai.getAwards()), warnings);
        List<String> certificates = uniqueLines(preferRuleList(rule.getCertificates(), ai.getCertificates()), warnings);
        String summary = sanitizeSummary(firstNotBlank(ai.getSummary(), rule.getSummary()), warnings);
        List<String> others = limitOthers(uniqueLines(preferRuleList(rule.getOthers(), ai.getOthers()), warnings), warnings);

        removeAssignedDuplicates(
                warnings,
                education,
                workExperiences,
                internships,
                campusExperiences,
                projects,
                awards,
                certificates);
        removeOthersAlreadyAssigned(others, warnings, education, workExperiences, internships, projects,
                campusExperiences, awards, certificates);

        Map<String, String> basicInfo = mergeBasicInfo(rule.getBasicInfo(), ai.getBasicInfo(), warnings);
        putIfNotBlank(basicInfo, "name", name);
        putIfNotBlank(basicInfo, "phone", phone);
        putIfNotBlank(basicInfo, "email", email);
        putIfNotBlank(basicInfo, "gender", firstNotBlank(valueFromBasicInfo(rule, "gender"), validGender(valueFromBasicInfo(ai, "gender"))));
        putIfNotBlank(basicInfo, "age", firstNotBlank(valueFromBasicInfo(rule, "age"), validAge(valueFromBasicInfo(ai, "age"))));
        putIfNotBlank(basicInfo, "degree", highestEducation);
        putIfNotBlank(basicInfo, "school", firstNotBlank(valueFromBasicInfo(rule, "school"), safeSupplementText(valueFromBasicInfo(ai, "school"), 60)));
        putIfNotBlank(basicInfo, "location", firstNotBlank(valueFromBasicInfo(rule, "location"), safeSupplementText(valueFromBasicInfo(ai, "location"), 40)));
        putIfNotBlank(basicInfo, "jobIntention", jobIntention);
        putIfNotBlank(basicInfo, "workYears", firstNotBlank(valueFromBasicInfo(rule, "workYears"), validWorkYears(valueFromBasicInfo(ai, "workYears"))));
        putIfNotBlank(basicInfo, "resumeType", resumeType);
        Map<String, ResumeBasicInfoFieldDTO> basicInfoDebug = mergeBasicInfoDebug(rule, ai, basicInfo);

        ResumeStructuredContentDTO result = ResumeStructuredContentDTO.builder()
                .name(name)
                .phone(phone)
                .email(email)
                .basicInfo(basicInfo)
                .basicInfoDebug(basicInfoDebug)
                .jobIntention(jobIntention)
                .highestEducation(highestEducation)
                .resumeType(resumeType)
                .parseMode(firstNotBlank(rule.getParseMode(), ai.getParseMode()))
                .education(education)
                .skills(skills)
                .projects(projects)
                .workExperiences(workExperiences)
                .internships(internships)
                .campusExperiences(campusExperiences)
                .awards(awards)
                .certificates(certificates)
                .summary(summary)
                .others(others)
                .qualityWarnings(uniqueLines(warnings))
                .sections(rule.getSections())
                .rawText(rule.getRawText())
                .build();
        return ResumeStructuredResultAssembler.enrich(result);
    }

    private static void putSkill(String canonical, String... aliases) {
        TECH_SKILL_ALIASES.put(canonical, List.of(aliases));
    }

    private List<String> preferRuleList(List<String> ruleValues, List<String> aiValues) {
        return ruleValues == null || ruleValues.isEmpty() ? safeList(aiValues) : safeList(ruleValues);
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private List<String> uniqueLines(List<String> values) {
        return uniqueLines(values, null);
    }

    private List<String> uniqueLines(List<String> values, List<String> warnings) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : safeList(values)) {
            String cleaned = cleanLine(value);
            if (cleaned.isBlank() || cleaned.length() > 300) {
                continue;
            }
            if (isInvalidContentLine(cleaned)) {
                if (warnings != null) {
                    warnings.add("AI_INVALID_CONTENT_FILTERED");
                }
                continue;
            }
            if (seen.add(normalizeForDedupe(cleaned))) {
                result.add(cleaned);
            }
        }
        return result;
    }

    private List<String> filterEducation(List<String> values, List<String> warnings) {
        List<String> result = new ArrayList<>();
        for (String line : uniqueLines(values, warnings)) {
            if (skillCount(line) >= 3 && DEGREE_WORDS.stream().noneMatch(line::contains)
                    && !line.matches(".*(大学|学院|学校|专业|学历|学士|硕士|博士|Education).*")) {
                warnings.add("AI_EDUCATION_TECH_TEXT_FILTERED");
                continue;
            }
            result.add(line);
        }
        return result;
    }

    private List<String> normalizeSkills(List<String> values, List<String> warnings) {
        Set<String> skills = new LinkedHashSet<>();
        for (String line : safeList(values)) {
            if (isInvalidContentLine(cleanLine(line))) {
                warnings.add("AI_INVALID_CONTENT_FILTERED");
                continue;
            }
            for (Map.Entry<String, List<String>> entry : TECH_SKILL_ALIASES.entrySet()) {
                if (entry.getValue().stream().anyMatch(alias -> containsSkillAlias(line, alias))) {
                    skills.add(entry.getKey());
                }
            }
        }
        if (!safeList(values).isEmpty() && skills.size() < uniqueLines(values).size()) {
            warnings.add("AI_SKILLS_NON_TECH_TEXT_FILTERED");
        }
        return List.copyOf(skills);
    }

    @SafeVarargs
    private final void removeAssignedDuplicates(List<String> warnings, List<String>... lists) {
        // 只去掉同一章节列表内的重复；同一句经历合法地出现在工作/项目等章节时不能静默吞掉。
        for (List<String> list : lists) {
            Set<String> seenWithinSection = new LinkedHashSet<>();
            List<String> kept = new ArrayList<>();
            for (String value : list) {
                String key = normalizeForDedupe(value);
                if (seenWithinSection.add(key)) {
                    kept.add(value);
                } else {
                    warnings.add("AI_DUPLICATE_TEXT_REMOVED");
                }
            }
            list.clear();
            list.addAll(kept);
        }
    }

    @SafeVarargs
    private final void removeOthersAlreadyAssigned(List<String> others, List<String> warnings, List<String>... assignedLists) {
        Set<String> assigned = new LinkedHashSet<>();
        for (List<String> list : assignedLists) {
            list.stream().map(this::normalizeForDedupe).forEach(assigned::add);
        }
        int before = others.size();
        others.removeIf(line -> assigned.contains(normalizeForDedupe(line)));
        if (others.size() < before) {
            warnings.add("AI_OTHERS_ASSIGNED_TEXT_REMOVED");
        }
    }

    private String sanitizeSummary(String summary, List<String> warnings) {
        String cleaned = cleanLine(summary);
        if (cleaned.isBlank()) {
            return null;
        }
        if (isInvalidContentLine(cleaned)) {
            warnings.add("AI_INVALID_CONTENT_FILTERED");
            return null;
        }
        if (skillCount(cleaned) >= 5 && cleaned.length() < 120) {
            warnings.add("AI_SUMMARY_SKILL_LIST_FILTERED");
            return null;
        }
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    private List<String> limitOthers(List<String> others, List<String> warnings) {
        if (others.size() <= OTHERS_MAX_ITEMS) {
            return others;
        }
        warnings.add("AI_OTHERS_TOO_MANY_FILTERED");
        return new ArrayList<>(others.subList(0, OTHERS_MAX_ITEMS));
    }

    private Map<String, String> mergeBasicInfo(Map<String, String> rule, Map<String, String> ai, List<String> warnings) {
        Map<String, String> result = new LinkedHashMap<>();
        if (rule != null) {
            rule.forEach((key, value) -> putIfNotBlank(result, key, value));
        }
        if (ai != null) {
            ai.forEach((key, value) -> {
                if (result.containsKey(key)) {
                    return;
                }
                String validValue = validBasicInfoValue(key, value);
                if (isBlank(validValue)) {
                    warnings.add("AI_BASIC_INFO_INVALID_FILTERED");
                    return;
                }
                putIfNotBlank(result, key, validValue);
            });
        }
        return result;
    }

    private String valueFromBasicInfo(ResumeStructuredContentDTO content, String key) {
        if (content == null || content.getBasicInfo() == null) {
            return null;
        }
        return content.getBasicInfo().get(key);
    }

    private Map<String, ResumeBasicInfoFieldDTO> mergeBasicInfoDebug(
            ResumeStructuredContentDTO rule,
            ResumeStructuredContentDTO ai,
            Map<String, String> finalBasicInfo) {
        Map<String, ResumeBasicInfoFieldDTO> result = new LinkedHashMap<>();
        List<String> keys = List.of("name", "phone", "email", "gender", "age", "degree", "school", "location", "jobIntention", "workYears", "resumeType");
        for (String key : keys) {
            String finalValue = finalBasicInfo.get(key);
            ResumeBasicInfoFieldDTO ruleDebug = debugFrom(rule, key);
            ResumeBasicInfoFieldDTO aiDebug = debugFrom(ai, key);
            if (!isBlank(finalValue)) {
                if (isSameValue(finalValue, valueFromBasicInfo(rule, key)) || isConfirmed(ruleDebug)) {
                    result.put(key, confirmedDebug(ruleDebug, finalValue, "RULE"));
                } else if (isSameValue(finalValue, valueFromBasicInfo(ai, key)) || isConfirmed(aiDebug)) {
                    result.put(key, confirmedDebug(aiDebug, finalValue, "AI"));
                } else {
                    result.put(key, confirmedDebug(null, finalValue, "MERGED"));
                }
                continue;
            }
            if (isRejected(ruleDebug) || isLowConfidence(ruleDebug)) {
                result.put(key, ruleDebug);
            } else if (isRejected(aiDebug) || isLowConfidence(aiDebug)) {
                result.put(key, aiDebug);
            } else {
                result.put(key, emptyDebug(key));
            }
        }
        return result;
    }

    private ResumeBasicInfoFieldDTO debugFrom(ResumeStructuredContentDTO content, String key) {
        if (content == null || content.getBasicInfoDebug() == null) {
            return null;
        }
        return content.getBasicInfoDebug().get(key);
    }

    private ResumeBasicInfoFieldDTO confirmedDebug(ResumeBasicInfoFieldDTO source, String finalValue, String fallbackSource) {
        if (source == null) {
            return ResumeBasicInfoFieldDTO.builder()
                    .value(finalValue)
                    .confidence("AI".equals(fallbackSource) ? 0.68 : 0.8)
                    .source(fallbackSource)
                    .status("CONFIRMED")
                    .build();
        }
        return ResumeBasicInfoFieldDTO.builder()
                .value(finalValue)
                .confidence(source.getConfidence())
                .source(firstNotBlank(source.getSource(), fallbackSource))
                .evidence(source.getEvidence())
                .status("CONFIRMED")
                .rejectReason(null)
                .build();
    }

    private ResumeBasicInfoFieldDTO emptyDebug(String key) {
        return ResumeBasicInfoFieldDTO.builder()
                .value("")
                .confidence(0.0)
                .source("MERGED")
                .status("EMPTY")
                .build();
    }

    private boolean isConfirmed(ResumeBasicInfoFieldDTO debug) {
        return debug != null && "CONFIRMED".equals(debug.getStatus()) && !isBlank(debug.getValue());
    }

    private boolean isRejected(ResumeBasicInfoFieldDTO debug) {
        return debug != null && "REJECTED".equals(debug.getStatus());
    }

    private boolean isLowConfidence(ResumeBasicInfoFieldDTO debug) {
        return debug != null && "LOW_CONFIDENCE".equals(debug.getStatus());
    }

    private boolean isSameValue(String left, String right) {
        return !isBlank(left) && !isBlank(right) && cleanLine(left).equals(cleanLine(right));
    }

    private void putIfNotBlank(Map<String, String> target, String key, String value) {
        if (!isBlank(key) && !isBlank(value)) {
            target.put(key, value.strip());
        }
    }

    private boolean validName(String value) {
        String cleaned = cleanLine(value);
        if (cleaned.length() < 2 || cleaned.length() > 24
                || EMAIL_PATTERN.matcher(cleaned).find()
                || PHONE_PATTERN.matcher(cleaned).find()
                || cleaned.matches(".*\\d.*")
                || cleaned.matches(".*[，,。；;：:、/|]{2,}.*")
                || isInvalidContentLine(cleaned)
                || skillCount(cleaned) > 0) {
            return false;
        }
        String lower = cleaned.toLowerCase(Locale.ROOT);
        if (INVALID_NAME_EXACT.contains(lower)) {
            return false;
        }
        if (SECTION_HEADINGS.stream().anyMatch(lower::contains)) {
            return false;
        }
        return cleaned.matches("[\\u4e00-\\u9fa5]{2,6}")
                || cleaned.matches("[A-Za-z]+(?:[ .·-][A-Za-z]+){1,3}");
    }

    private boolean validPhone(String value) {
        return !isBlank(value) && PHONE_PATTERN.matcher(value).find();
    }

    /**
     * 与规则抽取层同为 find() 语义：邮箱前后带噪声字符时先抽取再整值校验，
     * 避免合法邮箱因全串匹配被误判无效而丢失。
     */
    private boolean validEmail(String value) {
        return !isBlank(value) && EMAIL_PATTERN.matcher(value).find();
    }

    private String extractEmail(String value) {
        if (isBlank(value)) {
            return null;
        }
        Matcher matcher = EMAIL_PATTERN.matcher(value);
        return matcher.find() ? matcher.group().strip() : null;
    }

    private String validBasicInfoValue(String key, String value) {
        return switch (key) {
            case "name" -> validName(value) ? cleanLine(value) : null;
            case "phone" -> validPhone(value) ? normalizePhone(value) : null;
            case "email" -> extractEmail(value);
            case "degree" -> firstDegree(value);
            case "gender" -> validGender(value);
            case "age" -> validAge(value);
            case "workYears" -> validWorkYears(value);
            case "jobIntention" -> safeSupplementText(value, 60);
            case "location" -> safeSupplementText(value, 40);
            case "resumeType" -> validResumeType(value) ? value : null;
            default -> safeSupplementText(value, 120);
        };
    }

    private String firstDegree(String... values) {
        for (String value : values) {
            if (isBlank(value)) {
                continue;
            }
            String degree = DEGREE_WORDS.stream()
                    .filter(value::contains)
                    .findFirst()
                    .orElse(null);
            if (degree != null) {
                return degree;
            }
        }
        return null;
    }

    private String validGender(String value) {
        String cleaned = cleanLine(value);
        return "男".equals(cleaned) || "女".equals(cleaned) ? cleaned : null;
    }

    private String validAge(String value) {
        String cleaned = cleanLine(value);
        if (!cleaned.matches("[1-5]?\\d")) {
            return null;
        }
        int age = Integer.parseInt(cleaned);
        return age >= 16 && age <= 60 ? cleaned : null;
    }

    private String validWorkYears(String value) {
        String cleaned = cleanLine(value);
        return WORK_YEARS_PATTERN.matcher(cleaned).matches() ? cleaned : null;
    }

    private String safeSupplementText(String value, int maxLength) {
        String cleaned = cleanLine(value);
        if (cleaned.isBlank()
                || isInvalidContentLine(cleaned)
                || cleaned.length() > maxLength
                || EMAIL_PATTERN.matcher(cleaned).find()
                || PHONE_PATTERN.matcher(cleaned).find()) {
            return null;
        }
        return cleaned;
    }

    private boolean isInvalidContentLine(String value) {
        String cleaned = cleanLine(value);
        return cleaned.isBlank()
                || NUMBERING_ONLY_PATTERN.matcher(cleaned).matches()
                || SYMBOL_ONLY_PATTERN.matcher(cleaned).matches();
    }

    private boolean validResumeType(String value) {
        return value != null && RESUME_TYPES.contains(value);
    }

    private String normalizePhone(String value) {
        if (isBlank(value)) {
            return null;
        }
        String phone = value.replaceAll("[\\s-]", "");
        if (phone.startsWith("+86")) {
            return phone.substring(3);
        }
        if (phone.startsWith("86") && phone.length() == 13) {
            return phone.substring(2);
        }
        return phone;
    }

    private int skillCount(String line) {
        int count = 0;
        for (Map.Entry<String, List<String>> entry : TECH_SKILL_ALIASES.entrySet()) {
            if (entry.getValue().stream().anyMatch(alias -> containsSkillAlias(line, alias))) {
                count++;
            }
        }
        return count;
    }

    private boolean containsSkillAlias(String line, String alias) {
        if (isBlank(line) || isBlank(alias)) {
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

    private boolean containsChinese(String value) {
        return value.chars().anyMatch(ch -> Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN);
    }

    private String cleanLine(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .replaceAll("^[\\s>*•·●▪■◆◇○◦▶►✓✔-]+", "")
                .replaceAll("[\\t\\x0B\\f\\r 　]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private String normalizeForDedupe(String line) {
        return cleanLine(line)
                .replaceAll("[\\s\\p{Punct}，。；：、（）【】《》“”‘’·•●○◆◇■□▪◦▶►✓✔①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.strip();
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
