package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBasicInfoFieldDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeStructureParseService;
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
public class ResumeStructureParseServiceImpl implements ResumeStructureParseService {

    private static final String GENERAL_SECTION = "GENERAL";
    private static final int OTHERS_MAX_SIZE = 20;
    private static final String ICON_CHARS = "\uf0e0\uf095\uf0e1\uf09b\uf19d\uf0c0\uf085\uf08a\uf129";

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(?:\\(\\+?86\\)|\\+?86|86)?[-\\s]*1[3-9]\\d[-\\s]?\\d{4}[-\\s]?\\d{4}(?!\\d)");
    private static final Pattern GITHUB_PATTERN = Pattern.compile("(?i)(?:https?://)?github\\.com/[A-Za-z0-9_.-]+");
    private static final Pattern LINKEDIN_PATTERN = Pattern.compile("(?i)(?:https?://)?(?:www\\.)?linkedin\\.com/[^\\s，,；;]+");
    private static final Pattern AGE_PATTERN = Pattern.compile("(?:年龄|年\\s*龄)[:：\\s]*(?<age>[1-5]?\\d)\\s*(?:岁)?");
    private static final Pattern STANDALONE_AGE_PATTERN = Pattern.compile("(?<age>[1-5]?\\d)\\s*岁");
    private static final Pattern GENDER_PATTERN = Pattern.compile("(?:性别|性\\s*别)[:：\\s]*(?<gender>男|女)(?:\\s|$)");
    private static final Pattern STANDALONE_GENDER_PATTERN = Pattern.compile("^(?<gender>男|女)(?:\\s|$)");
    private static final Pattern COMPACT_BASIC_INFO_PATTERN = Pattern.compile("^(?<name>[\\u4e00-\\u9fa5]{2,6})\\s*[/|,，、 ]\\s*(?<gender>男|女)(?:\\s*[/|,，、 ]\\s*(?<age>[1-5]?\\d)\\s*(?:岁)?)?.*$");
    private static final Pattern NAME_LABEL_PATTERN = Pattern.compile("(?:姓名|姓\\s*名|名字|名\\s*字)[:：\\s]+(?<name>[\\u4e00-\\u9fa5A-Za-z][\\u4e00-\\u9fa5A-Za-z .·-]{1,18}?)(?=\\s*(?:性别|性\\s*别|年龄|年\\s*龄|出生年月|邮箱|电话|手机|学历|院校|$))");
    private static final Pattern DEGREE_LABEL_PATTERN = Pattern.compile("^(?:学历|最高学历|教育程度)[:：\\s]+(?<degree>.{2,30})$");
    private static final Pattern JOB_INTENTION_PATTERN = Pattern.compile("^(?:求职意向|求职目标|应聘岗位|目标岗位|期望职位|期望岗位|目标职能|目标职位|职业方向)[:：\\s]+(?<job>.{2,60})$");
    private static final Pattern LOCATION_PATTERN = Pattern.compile("^(?:所在地|现居地|现居|城市|地址)[:：\\s]+(?<location>.{2,40})$");
    private static final Pattern SCHOOL_PATTERN = Pattern.compile("(?<school>[\\u4e00-\\u9fa5]{2,}(?:大学|学院|学校|职业学院|工学院))");
    private static final Pattern SCHOOL_LOCATION_PATTERN = Pattern.compile("(?<school>[\\u4e00-\\u9fa5]{2,}(?:大学|学院|学校|职业学院|工学院))[,，、\\s]+(?<location>[\\u4e00-\\u9fa5]{2,8})\\s+(?:19|20)\\d{2}");
    private static final Pattern MAJOR_PATTERN = Pattern.compile("(?:专业[:：\\s]*)?(?:在读)?(?:本科|大专|专科|硕士|博士)?(?<major>[\\u4e00-\\u9fa5A-Za-z0-9+ #.-]{2,24})(?:专业|[,，]|\\s*预计|$)");
    private static final Pattern GRADUATION_DATE_PATTERN = Pattern.compile("(?:预计\\s*)?(?<date>(?:19|20)\\d{2}\\s*年\\s*\\d{1,2}\\s*月)\\s*毕业");
    private static final Pattern GPA_PATTERN = Pattern.compile("(?:GPA|绩点|学分绩点)[:：\\s]*(?<gpa>\\d(?:\\.\\d+)?\\s*/\\s*\\d(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("(?:语言能力|英语)[:：\\s]*(?<language>.*?(?:CET[-\\s]*\\d\\s*[:：]?\\s*\\d+分?).*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RANKING_PATTERN = Pattern.compile("(?<ranking>(?:排名|第)\\s*[\\u4e00-\\u9fa5A-Za-z0-9 /.-]*\\s*第?\\s*\\d+|专业第\\s*\\d+)");
    private static final Pattern WORK_YEARS_LABEL_PATTERN = Pattern.compile("(?:工作年限|工作经验|工作经历|从业年限|从业经验)[:：\\s]*(?<years>\\d{1,2})\\s*(?:年)?");
    private static final Pattern WORK_YEARS_PATTERN = Pattern.compile("(?<years>\\d{1,2})\\s*年(?:以上)?(?:工作|开发|从业|项目|后端|Java)?(?:经验|经历)?");
    private static final Pattern DATE_RANGE_PATTERN = Pattern.compile(".*(?:\\d{4}[./年-]\\d{1,2}|\\d{4}\\s*[-~—至]\\s*\\d{4}|至今|Present).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE_YEAR_PATTERN = Pattern.compile("(?<!\\d)(?:19|20)\\d{2}(?!\\d)");
    private static final Pattern HORIZONTAL_SPACE_PATTERN = Pattern.compile("[\\t\\x0B\\f\\r 　]+");
    private static final Pattern BULLET_PATTERN = Pattern.compile("^[\\s>*•·●▪■◆◇○◦▶►✓✔-]+");
    private static final Pattern NUMBERING_PREFIX_PATTERN = Pattern.compile("^(?:(?:\\(?\\d{1,3}\\)?|[一二三四五六七八九十百]+)[、.．)）:：]|[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳])\\s*(?<body>.*)$");
    private static final Pattern SENTENCE_LIKE_PATTERN = Pattern.compile(".*(?:熟悉|掌握|了解|负责|参与|完成|实现|开发|维护|优化|具备|能够|主要|项目|系统|模块|需求|客户|评价).*");
    private static final Pattern PUNCTUATION_DENSE_PATTERN = Pattern.compile(".*[，,。；;：:、/|]{2,}.*");

    private static final Map<String, List<String>> SECTION_ALIASES = new LinkedHashMap<>();
    private static final Map<String, List<String>> TECH_SKILL_ALIASES = new LinkedHashMap<>();
    private static final List<String> DEGREE_PRIORITY = List.of("博士", "硕士", "研究生", "本科", "大专", "专科", "高中");
    private static final List<String> HEADER_SIDE_SKILLS = List.of(
            "Python", "C++", "C", "Verilog", "Linux", "Java", "JavaScript", "TypeScript",
            "Git", "Docker", "MATLAB", "OpenCV", "YOLO", "DETR", "Transformer",
            "PyTorch", "TensorFlow", "Scikit-learn", "Pandas");
    private static final List<String> NAME_EXCLUDE_KEYWORDS = List.of(
            "时间", "学校", "专业", "学历", "个人信息", "基本信息", "教育经历", "教育背景", "技术能力", "专业技能",
            "项目经历", "工作经历", "实习经历", "校园经历", "求职意向", "目标岗位", "应聘岗位", "手机号", "电话",
            "邮箱", "证书", "荣誉", "自我评价", "个人总结", "简历", "Profile", "Education", "Skills", "Experience",
            "基本情况", "基本资料");
    private static final List<String> NAME_EXCLUDE_EXACT = List.of(
            "本人", "个人简历", "Personal Resume", "Resume", "Personal", "Curriculum Vitae", "CV", "参加项目描述",
            "基本情况", "基本资料", "组织", "同学们");

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
        putSkill("SQL", "SQL");
        putSkill("Pandas", "Pandas");
        putSkill("MATLAB", "MATLAB", "Matlab");
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
    public ResumeStructuredContentDTO parse(String rawText) {
        String normalizedText = normalizeRawText(rawText);
        List<String> lines = splitLines(normalizedText);
        List<ResumeTextSectionDTO> sections = splitSections(lines);
        return parse(normalizedText, sections);
    }

    @Override
    public ResumeStructuredContentDTO parse(String rawText, List<ResumeTextSectionDTO> sections) {
        String normalizedText = normalizeRawText(rawText);
        List<String> lines = splitLines(normalizedText);
        List<ResumeTextSectionDTO> safeSections = sections == null || sections.isEmpty() ? splitSections(lines) : sections;
        Set<String> assignedLines = new LinkedHashSet<>();
        String name = extractName(lines);
        String phone = extractPhone(normalizedText);
        String email = extractEmail(normalizedText);
        String github = extractGithub(normalizedText);
        String linkedin = extractLinkedin(normalizedText);
        String gender = extractGender(lines);
        String age = extractAge(lines);
        String degree = extractHighestEducation(lines, normalizedText);
        String school = extractSchool(lines);
        String location = extractLocation(lines);
        String university = school;
        String major = extractMajor(lines);
        String graduationDate = extractGraduationDate(lines);
        String gpa = extractGpa(lines);
        String languageAbility = extractLanguageAbility(lines);
        String ranking = extractRanking(lines);
        String jobIntention = extractJobIntention(lines);
        String workYears = extractWorkYears(normalizedText);
        String resumeType = resolveResumeType(normalizedText, safeSections, jobIntention, workYears);

        markSectionLines(safeSections, "BASIC_INFO", assignedLines);
        List<String> education = extractSectionLines(safeSections, "EDUCATION", assignedLines);
        markSectionLines(safeSections, "SKILLS", assignedLines);
        List<String> workExperiences = extractSectionLines(safeSections, "WORK_EXPERIENCES", assignedLines);
        List<String> internships = extractSectionLines(safeSections, "INTERNSHIPS", assignedLines);
        List<String> projects = extractSectionLines(safeSections, "PROJECTS", assignedLines);
        List<String> campusExperiences = extractSectionLines(safeSections, "CAMPUS_EXPERIENCES", assignedLines);
        List<String> awards = extractSectionLines(safeSections, "AWARDS", assignedLines);
        List<String> certificates = extractSectionLines(safeSections, "CERTIFICATES", assignedLines);
        String summary = extractSummary(safeSections, assignedLines);
        markPersonalInfoLines(lines, assignedLines);

        List<String> skills = extractSkills(safeSections, workExperiences, projects);
        List<String> others = extractOthers(safeSections, assignedLines);
        Map<String, String> basicInfo = buildBasicInfo(name, phone, email, github, linkedin, gender, age, degree, school, university,
                location, major, graduationDate, gpa, languageAbility, ranking, jobIntention, workYears, resumeType);
        Map<String, ResumeBasicInfoFieldDTO> basicInfoDebug = buildBasicInfoDebug(
                lines,
                name,
                phone,
                email,
                github,
                linkedin,
                gender,
                age,
                degree,
                school,
                university,
                location,
                major,
                graduationDate,
                gpa,
                languageAbility,
                ranking,
                jobIntention,
                workYears,
                resumeType);
        List<String> qualityWarnings = new ArrayList<>();
        if (name == null) {
            qualityWarnings.add("NAME_MISSING");
        }

        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name(name)
                .phone(phone)
                .email(email)
                .basicInfo(basicInfo)
                .basicInfoDebug(basicInfoDebug)
                .jobIntention(jobIntention)
                .highestEducation(degree)
                .resumeType(resumeType)
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
                .qualityWarnings(qualityWarnings)
                .sections(safeSections)
                .rawText(normalizedText)
                .build();
        return ResumeStructuredResultAssembler.enrich(content);
    }

    private static void putSkill(String canonical, String... aliases) {
        TECH_SKILL_ALIASES.put(canonical, List.of(aliases));
    }

    private String normalizeRawText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new BusinessException(400, "简历原始文本不能为空");
        }
        return normalizeUnicode(rawText).strip();
    }

    private List<String> splitLines(String rawText) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> lines = new ArrayList<>();
        boolean beforeFirstHeading = true;
        for (String rawLine : rawText.lines().toList()) {
            String line = normalizeLine(rawLine);
            if (line.isBlank()) {
                continue;
            }
            List<String> expandedLines = expandTopMixedHeaderLine(line, beforeFirstHeading);
            for (String expandedLine : expandedLines) {
                if (seen.add(normalizeForDedupe(expandedLine))) {
                    lines.add(expandedLine);
                }
            }
            if (matchHeading(line) != null) {
                beforeFirstHeading = false;
            }
        }
        return lines;
    }

    private List<String> expandTopMixedHeaderLine(String line, boolean beforeFirstHeading) {
        if (!beforeFirstHeading || matchHeading(line) != null) {
            return List.of(line);
        }
        if (line.matches(".*(?:邮箱|电话|手机|年龄|姓名|性别|学历|院校|学校|求职|目标).*")) {
            return List.of(line);
        }
        List<String> result = new ArrayList<>();
        String remaining = line.strip();
        Matcher emailMatcher = EMAIL_PATTERN.matcher(remaining);
        Matcher phoneMatcher = PHONE_PATTERN.matcher(remaining);
        Matcher githubMatcher = GITHUB_PATTERN.matcher(remaining);
        if (emailMatcher.find()) {
            result.add(emailMatcher.group());
            remaining = removeSpan(remaining, emailMatcher.start(), emailMatcher.end());
            addHeaderTrailingSkill(result, remaining);
            return result;
        }
        if (phoneMatcher.find()) {
            result.add(phoneMatcher.group().strip());
            remaining = removeSpan(remaining, phoneMatcher.start(), phoneMatcher.end());
            addHeaderTrailingSkill(result, remaining);
            return result;
        }
        if (githubMatcher.find()) {
            result.add("GitHub: " + githubMatcher.group().strip());
            remaining = removeSpan(remaining, githubMatcher.start(), githubMatcher.end());
            addHeaderTrailingSkill(result, remaining);
            return result;
        }
        if (remaining.matches("^-+\\s+.*")) {
            addHeaderTrailingSkill(result, remaining);
            return result.isEmpty() ? List.of(line) : result;
        }
        String skill = trailingHeaderSkill(remaining);
        if (skill != null) {
            String left = remaining.substring(0, remaining.length() - skill.length()).strip();
            if (isValidNameCandidate(left)) {
                return List.of(left, skill);
            }
        }
        return List.of(line);
    }

    private void addHeaderTrailingSkill(List<String> result, String value) {
        String skill = trailingHeaderSkill(value == null ? "" : value.replaceFirst("^[-:：\\s]+", "").strip());
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
            if (cleaned.equalsIgnoreCase(skill) || cleaned.matches("(?i).*\\s+" + Pattern.quote(skill) + "$")) {
                return switch (skill.toLowerCase(Locale.ROOT)) {
                    case "opencv" -> "OpenCV";
                    case "yolo" -> "YOLO";
                    case "pytorch" -> "PyTorch";
                    case "tensorflow" -> "TensorFlow";
                    default -> skill;
                };
            }
        }
        return null;
    }

    private String removeSpan(String value, int start, int end) {
        return (value.substring(0, start) + " " + value.substring(end)).replaceAll("\\s+", " ").strip();
    }

    private String normalizeLine(String rawLine) {
        String line = HORIZONTAL_SPACE_PATTERN.matcher(normalizeUnicode(rawLine)).replaceAll(" ").strip();
        line = line.replaceAll("^[" + ICON_CHARS + "]+\\s*", "");
        line = BULLET_PATTERN.matcher(line).replaceFirst("").strip();
        Matcher numberingMatcher = NUMBERING_PREFIX_PATTERN.matcher(line);
        if (numberingMatcher.matches() && !numberingMatcher.group("body").isBlank()) {
            line = numberingMatcher.group("body").strip();
        }
        return line.replaceAll("\\s+([,，、；;:：])", "$1")
                .replaceAll("([,，、；;:：])\\s+", "$1 ")
                .replaceAll("\\s+", " ")
                .strip();
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

        String matched = matcher.group().strip();
        if (matched.startsWith("(+86)") || matched.startsWith("(86)")) {
            return matched.replaceAll("\\s+", " ");
        }
        String phone = matched.replaceAll("[\\s-]", "");
        if (phone.startsWith("+86")) {
            return phone.substring(3);
        }
        if (phone.startsWith("86") && phone.length() == 13) {
            return phone.substring(2);
        }
        return phone;
    }

    private String extractGithub(String rawText) {
        Matcher matcher = GITHUB_PATTERN.matcher(rawText == null ? "" : rawText);
        return matcher.find() ? matcher.group().strip() : null;
    }

    private String extractLinkedin(String rawText) {
        Matcher matcher = LINKEDIN_PATTERN.matcher(rawText == null ? "" : rawText);
        return matcher.find() ? matcher.group().strip() : null;
    }

    private String extractAge(List<String> lines) {
        String labeledAge = findFirstGroup(lines, AGE_PATTERN, "age");
        if (labeledAge != null) {
            return labeledAge;
        }
        String standaloneAge = findFirstGroup(lines, STANDALONE_AGE_PATTERN, "age");
        if (standaloneAge != null) {
            return standaloneAge;
        }
        return lines.stream()
                .map(COMPACT_BASIC_INFO_PATTERN::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group("age"))
                .filter(age -> age != null && !age.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String extractGender(List<String> lines) {
        String labeledGender = lines.stream()
                .map(GENDER_PATTERN::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group("gender"))
                .findFirst()
                .orElse(null);
        if (labeledGender != null) {
            return labeledGender;
        }
        String standaloneGender = lines.stream()
                .filter(line -> line.length() <= 20)
                .map(STANDALONE_GENDER_PATTERN::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group("gender"))
                .findFirst()
                .orElse(null);
        if (standaloneGender != null) {
            return standaloneGender;
        }
        return lines.stream()
                .map(COMPACT_BASIC_INFO_PATTERN::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group("gender"))
                .findFirst()
                .orElse(null);
    }

    private String extractLocation(List<String> lines) {
        String labeled = findFirstGroup(lines, LOCATION_PATTERN, "location");
        if (labeled != null) {
            return labeled;
        }
        return findFirstGroup(lines, SCHOOL_LOCATION_PATTERN, "location");
    }

    private String extractJobIntention(List<String> lines) {
        String job = findFirstGroup(lines, JOB_INTENTION_PATTERN, "job");
        if (job == null) {
            return null;
        }
        return job.replaceFirst("\\s*(?:目标薪资|薪资)[:：].*$", "").strip();
    }

    private String extractHighestEducation(List<String> lines, String rawText) {
        for (String line : lines) {
            Matcher matcher = DEGREE_LABEL_PATTERN.matcher(line);
            if (matcher.find()) {
                String degree = resolveDegree(matcher.group("degree"));
                if (degree != null) {
                    return degree;
                }
            }
        }
        return resolveDegree(rawText);
    }

    private String resolveDegree(String text) {
        return DEGREE_PRIORITY.stream()
                .filter(text::contains)
                .findFirst()
                .orElse(null);
    }

    private String extractSchool(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = SCHOOL_PATTERN.matcher(line);
            if (matcher.find()) {
                return matcher.group("school");
            }
        }
        return null;
    }

    private String extractMajor(List<String> lines) {
        for (String line : lines) {
            if (!line.matches(".*(?:专业|本科|硕士|博士|大专|专科|预计).*")) {
                continue;
            }
            Matcher matcher = MAJOR_PATTERN.matcher(line);
            if (matcher.find()) {
                String major = matcher.group("major").replaceAll("^(在读|本科|硕士|博士|大专|专科)+", "").strip();
                if (major.matches(".*(?:大学|学院|学校|毕业|预计).*") || hasAnySkill(major)) {
                    continue;
                }
                return major;
            }
        }
        return null;
    }

    private String extractGraduationDate(List<String> lines) {
        String date = findFirstGroup(lines, GRADUATION_DATE_PATTERN, "date");
        return date == null ? null : date.replaceAll("\\s+", "");
    }

    private String extractGpa(List<String> lines) {
        String gpa = findFirstGroup(lines, GPA_PATTERN, "gpa");
        return gpa == null ? null : gpa.replaceAll("\\s+", "");
    }

    private String extractLanguageAbility(List<String> lines) {
        return findFirstGroup(lines, LANGUAGE_PATTERN, "language");
    }

    private String extractRanking(List<String> lines) {
        return findFirstGroup(lines, RANKING_PATTERN, "ranking");
    }

    private String extractWorkYears(String rawText) {
        Matcher labelMatcher = WORK_YEARS_LABEL_PATTERN.matcher(rawText);
        if (labelMatcher.find()) {
            return labelMatcher.group("years") + "年";
        }
        Matcher matcher = WORK_YEARS_PATTERN.matcher(rawText);
        if (matcher.find()) {
            return matcher.group("years") + "年";
        }
        return null;
    }

    private String findFirstGroup(List<String> lines, Pattern pattern, String group) {
        return lines.stream()
                .map(pattern::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(group).strip())
                .findFirst()
                .orElse(null);
    }

    private Map<String, String> buildBasicInfo(
            String name,
            String phone,
            String email,
            String github,
            String linkedin,
            String gender,
            String age,
            String degree,
            String school,
            String university,
            String location,
            String major,
            String graduationDate,
            String gpa,
            String languageAbility,
            String ranking,
            String jobIntention,
            String workYears,
            String resumeType) {
        Map<String, String> basicInfo = new LinkedHashMap<>();
        putIfNotBlank(basicInfo, "name", name);
        putIfNotBlank(basicInfo, "phone", phone);
        putIfNotBlank(basicInfo, "email", email);
        putIfNotBlank(basicInfo, "github", github);
        putIfNotBlank(basicInfo, "linkedin", linkedin);
        putIfNotBlank(basicInfo, "gender", gender);
        putIfNotBlank(basicInfo, "age", age);
        putIfNotBlank(basicInfo, "degree", degree);
        putIfNotBlank(basicInfo, "school", school);
        putIfNotBlank(basicInfo, "university", university);
        putIfNotBlank(basicInfo, "location", location);
        putIfNotBlank(basicInfo, "major", major);
        putIfNotBlank(basicInfo, "graduationDate", graduationDate);
        putIfNotBlank(basicInfo, "gpa", gpa);
        putIfNotBlank(basicInfo, "languageAbility", languageAbility);
        putIfNotBlank(basicInfo, "ranking", ranking);
        putIfNotBlank(basicInfo, "jobIntention", jobIntention);
        putIfNotBlank(basicInfo, "workYears", workYears);
        putIfNotBlank(basicInfo, "resumeType", resumeType);
        return basicInfo;
    }

    private void putIfNotBlank(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private Map<String, ResumeBasicInfoFieldDTO> buildBasicInfoDebug(
            List<String> lines,
            String name,
            String phone,
            String email,
            String github,
            String linkedin,
            String gender,
            String age,
            String degree,
            String school,
            String university,
            String location,
            String major,
            String graduationDate,
            String gpa,
            String languageAbility,
            String ranking,
            String jobIntention,
            String workYears,
            String resumeType) {
        Map<String, ResumeBasicInfoFieldDTO> debug = new LinkedHashMap<>();
        debug.put("name", buildNameDebug(lines, name));
        debug.put("phone", debugField(phone, 0.98, "REGEX", findEvidenceLine(lines, PHONE_PATTERN), "EMPTY", null));
        debug.put("email", debugField(email, 0.98, "REGEX", findEvidenceLine(lines, EMAIL_PATTERN), "EMPTY", null));
        debug.put("github", debugField(github, 0.95, "REGEX", findEvidenceLine(lines, GITHUB_PATTERN), "EMPTY", null));
        debug.put("linkedin", debugField(linkedin, 0.9, "REGEX", findEvidenceLine(lines, LINKEDIN_PATTERN), "EMPTY", null));
        debug.put("gender", debugField(gender, 0.84, "RULE", findEvidenceLine(lines, GENDER_PATTERN), "EMPTY", null));
        debug.put("age", debugField(age, 0.84, "RULE", findEvidenceLine(lines, AGE_PATTERN), "EMPTY", null));
        debug.put("degree", debugField(degree, 0.88, "RULE", findDegreeEvidence(lines, degree), "EMPTY", null));
        debug.put("school", debugField(school, 0.86, "RULE", findSchoolEvidence(lines, school), "EMPTY", null));
        debug.put("university", debugField(university, 0.86, "RULE", findSchoolEvidence(lines, university), "EMPTY", null));
        debug.put("location", debugField(location, 0.78, "RULE", findEvidenceLine(lines, LOCATION_PATTERN), "EMPTY", null));
        debug.put("major", debugField(major, 0.82, "RULE", findMajorEvidence(lines, major), "EMPTY", null));
        debug.put("graduationDate", debugField(graduationDate, 0.88, "RULE", findEvidenceLine(lines, GRADUATION_DATE_PATTERN), "EMPTY", null));
        debug.put("gpa", debugField(gpa, 0.9, "REGEX", findEvidenceLine(lines, GPA_PATTERN), "EMPTY", null));
        debug.put("languageAbility", debugField(languageAbility, 0.84, "RULE", findEvidenceLine(lines, LANGUAGE_PATTERN), "EMPTY", null));
        debug.put("ranking", debugField(ranking, 0.78, "RULE", findEvidenceLine(lines, RANKING_PATTERN), "EMPTY", null));
        debug.put("jobIntention", debugField(jobIntention, 0.86, "RULE", findEvidenceLine(lines, JOB_INTENTION_PATTERN), "EMPTY", null));
        debug.put("workYears", debugField(workYears, 0.82, "RULE", findWorkYearsEvidence(lines), "EMPTY", null));
        debug.put("resumeType", debugField(resumeType, "UNKNOWN".equals(resumeType) ? 0.3 : 0.72, "RULE", findResumeTypeEvidence(lines), "LOW_CONFIDENCE", null));
        return debug;
    }

    private ResumeBasicInfoFieldDTO buildNameDebug(List<String> lines, String name) {
        if (name != null && !name.isBlank()) {
            return debugField(name, scoreNameCandidate(name) / 100.0, "RULE", findNameEvidence(lines, name), "EMPTY", null);
        }

        for (String line : lines) {
            String candidate = cleanName(line);
            String rejectReason = rejectNameReason(candidate);
            if (rejectReason != null) {
                return debugField("", 0.2, "RULE", line, "REJECTED", rejectReason);
            }
        }
        return debugField(null, 0.0, "RULE", null, "EMPTY", null);
    }

    private ResumeBasicInfoFieldDTO debugField(
            String value,
            double confidence,
            String source,
            String evidence,
            String emptyStatus,
            String rejectReason) {
        boolean hasValue = value != null && !value.isBlank();
        return ResumeBasicInfoFieldDTO.builder()
                .value(hasValue ? value : "")
                .confidence(roundConfidence(confidence))
                .source(source)
                .evidence(evidence)
                .status(hasValue ? "CONFIRMED" : emptyStatus)
                .rejectReason(rejectReason)
                .build();
    }

    private double roundConfidence(double confidence) {
        return Math.round(Math.max(0, Math.min(1, confidence)) * 100.0) / 100.0;
    }

    private String findEvidenceLine(List<String> lines, Pattern pattern) {
        return lines.stream()
                .filter(line -> pattern.matcher(line).find())
                .findFirst()
                .orElse(null);
    }

    private String findDegreeEvidence(List<String> lines, String degree) {
        if (degree == null || degree.isBlank()) {
            return null;
        }
        return lines.stream()
                .filter(line -> line.contains(degree) || DEGREE_LABEL_PATTERN.matcher(line).find())
                .findFirst()
                .orElse(null);
    }

    private String findSchoolEvidence(List<String> lines, String school) {
        if (school == null || school.isBlank()) {
            return null;
        }
        return lines.stream()
                .filter(line -> line.contains(school))
                .findFirst()
                .orElse(null);
    }

    private String findMajorEvidence(List<String> lines, String major) {
        if (major == null || major.isBlank()) {
            return null;
        }
        return lines.stream()
                .filter(line -> line.contains(major))
                .findFirst()
                .orElse(null);
    }

    private String findWorkYearsEvidence(List<String> lines) {
        return lines.stream()
                .filter(line -> WORK_YEARS_LABEL_PATTERN.matcher(line).find()
                        || WORK_YEARS_PATTERN.matcher(line).find()
                        || line.matches(".*(工作经历|工作经验|职业经历|任职经历|从业经历|工作时间|入职时间).*"))
                .findFirst()
                .orElse(null);
    }

    private String findResumeTypeEvidence(List<String> lines) {
        return lines.stream()
                .filter(line -> line.matches(".*(工作经历|工作经验|职业经历|实习经历|校园经历|教育经历|教育背景).*"))
                .findFirst()
                .orElse(null);
    }

    private String findNameEvidence(List<String> lines, String name) {
        return lines.stream()
                .filter(line -> line.contains(name) || name.equals(cleanName(line)))
                .findFirst()
                .orElse(name);
    }

    private String rejectNameReason(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        if (candidate.length() < 2 || candidate.length() > 24) {
            return "长度不符合姓名候选范围";
        }
        if (EMAIL_PATTERN.matcher(candidate).find()) {
            return "包含邮箱";
        }
        if (PHONE_PATTERN.matcher(candidate).find()) {
            return "包含手机号";
        }
        if (DATE_RANGE_PATTERN.matcher(candidate).matches() || candidate.matches(".*\\d.*")) {
            return "包含数字或日期";
        }
        if (PUNCTUATION_DENSE_PATTERN.matcher(candidate).matches()) {
            return "标点过密";
        }
        if (SENTENCE_LIKE_PATTERN.matcher(candidate).matches()) {
            return "像经历描述句";
        }
        if (isSectionHeading(candidate)) {
            return "是章节标题";
        }
        if (containsIgnoreCase(candidate, NAME_EXCLUDE_EXACT)) {
            return "命中姓名黑名单";
        }
        if (containsIgnoreCase(candidate, NAME_EXCLUDE_KEYWORDS)) {
            return "包含非姓名关键词";
        }
        if (hasAnySkill(candidate)) {
            return "包含技能关键词";
        }
        if (!candidate.matches("[\\u4e00-\\u9fa5]{2,6}")
                && !candidate.matches("[A-Za-z]+(?:[ .·-][A-Za-z]+){1,3}")) {
            return "不符合姓名格式";
        }
        int score = scoreNameCandidate(candidate);
        return score < 60 ? "姓名候选置信度过低" : null;
    }

    private String resolveResumeType(String rawText, List<ResumeTextSectionDTO> sections, String jobIntention, String workYears) {
        boolean jobLooksIntern = jobIntention != null && jobIntention.matches("(?i).*(实习|intern).*");
        if (jobLooksIntern) {
            return "INTERN";
        }

        boolean hasWorkExperience = sections.stream()
                .anyMatch(section -> "WORK_EXPERIENCES".equals(section.getSectionType()));
        if (hasWorkExperience
                || workYears != null
                || rawText.matches("(?s).*(工作经历|工作经验|职业经历|任职经历|任职公司|从业经历|\\d{1,2}\\s*年.*经验).*")) {
            return "EXPERIENCED";
        }

        boolean hasInternship = sections.stream()
                .anyMatch(section -> "INTERNSHIPS".equals(section.getSectionType()));
        if (hasInternship) {
            return "INTERN";
        }

        boolean hasStudentSignals = sections.stream()
                .anyMatch(section -> "CAMPUS_EXPERIENCES".equals(section.getSectionType())
                        || "EDUCATION".equals(section.getSectionType())
                        || "AWARDS".equals(section.getSectionType()));
        return hasStudentSignals ? "STUDENT" : "UNKNOWN";
    }

    private String extractName(List<String> lines) {
        for (String line : lines) {
            Matcher matcher = NAME_LABEL_PATTERN.matcher(line);
            if (matcher.find()) {
                String labeledName = cleanName(matcher.group("name"));
                if (isValidNameCandidate(labeledName)) {
                    return labeledName;
                }
            }
        }
        for (String line : lines) {
            Matcher matcher = COMPACT_BASIC_INFO_PATTERN.matcher(line);
            if (matcher.find()) {
                String compactName = cleanName(matcher.group("name"));
                if (isValidNameCandidate(compactName)) {
                    return compactName;
                }
            }
        }

        return lines.stream()
                .map(this::cleanName)
                .filter(this::isValidNameCandidate)
                .map(name -> new NameCandidate(name, scoreNameCandidate(name)))
                .filter(candidate -> candidate.score() >= 60)
                .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                .map(NameCandidate::name)
                .findFirst()
                .orElse(null);
    }

    private boolean isValidNameCandidate(String line) {
        String normalized = cleanName(line);
        if (normalized == null) {
            return false;
        }
        if (normalized.length() < 2 || normalized.length() > 24) {
            return false;
        }
        if (EMAIL_PATTERN.matcher(normalized).find()
                || PHONE_PATTERN.matcher(normalized).find()
                || DATE_RANGE_PATTERN.matcher(normalized).matches()
                || normalized.matches(".*\\d.*")
                || PUNCTUATION_DENSE_PATTERN.matcher(normalized).matches()
                || SENTENCE_LIKE_PATTERN.matcher(normalized).matches()
                || isSectionHeading(normalized)
                || containsIgnoreCase(normalized, NAME_EXCLUDE_EXACT)
                || containsIgnoreCase(normalized, NAME_EXCLUDE_KEYWORDS)
                || hasAnySkill(normalized)) {
            return false;
        }
        return normalized.matches("[\\u4e00-\\u9fa5]{2,6}")
                || normalized.matches("[A-Za-z]+(?:[ .·-][A-Za-z]+){1,3}");
    }

    private int scoreNameCandidate(String name) {
        int score = 0;
        if (name.matches("[\\u4e00-\\u9fa5]{2,4}")) {
            score += 90;
        } else if (name.matches("[\\u4e00-\\u9fa5]{5,6}")) {
            score += 70;
        } else if (name.matches("[A-Za-z]+(?:[ .·-][A-Za-z]+){1,3}")) {
            score += 55;
        }
        if (name.length() <= 6) {
            score += 10;
        }
        return score;
    }

    private String cleanName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = normalizeLine(name);
        Matcher matcher = COMPACT_BASIC_INFO_PATTERN.matcher(normalized);
        String cleaned = matcher.find() ? matcher.group("name") : normalized;
        cleaned = cleaned.strip().replaceAll("\\s+", "");
        return cleaned.length() >= 2 && cleaned.length() <= 24 ? cleaned : null;
    }

    private List<ResumeTextSectionDTO> splitSections(List<String> lines) {
        List<ResumeTextSectionDTO> sections = new ArrayList<>();
        String currentType = GENERAL_SECTION;
        String currentHeading = "未识别章节";
        List<String> currentLines = new ArrayList<>();

        for (String line : lines) {
            HeadingMatch headingMatch = matchHeading(line);
            if (headingMatch != null) {
                if (shouldKeepAsProjectContent(currentType, headingMatch, line)) {
                    currentLines.add(cleanSectionLine(line));
                    continue;
                }
                addSection(sections, currentType, currentHeading, currentLines);
                currentType = headingMatch.sectionType();
                currentHeading = headingMatch.heading();
                currentLines = new ArrayList<>();
                String inlineContent = removeHeadingPrefix(line, headingMatch.heading());
                if (!inlineContent.isBlank()) {
                    currentLines.add(inlineContent);
                }
                continue;
            }
            currentLines.add(cleanSectionLine(line));
        }

        addSection(sections, currentType, currentHeading, currentLines);
        return sections;
    }

    private void addSection(List<ResumeTextSectionDTO> sections, String sectionType, String heading, List<String> lines) {
        List<String> nonBlankLines = lines.stream()
                .map(this::cleanSectionLine)
                .filter(line -> !line.isBlank())
                .distinct()
                .toList();
        if (GENERAL_SECTION.equals(sectionType) && nonBlankLines.isEmpty() && !sections.isEmpty()) {
            return;
        }
        if (!GENERAL_SECTION.equals(sectionType) || !nonBlankLines.isEmpty()) {
            sections.add(ResumeTextSectionDTO.builder()
                    .sectionType(sectionType)
                    .heading(heading)
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
                        || normalizedLine.startsWith(normalizedHeading + "：")
                        || allowInlineHeading(normalizedLine, normalizedHeading)) {
                    return new HeadingMatch(entry.getKey(),
                            allowInlineHeading(normalizedLine, normalizedHeading) ? line.strip() : heading);
                }
            }
        }
        return null;
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
        if (skillCount(stripped) > 3) {
            return false;
        }
        return stripped.length() <= 18 || !SENTENCE_LIKE_PATTERN.matcher(stripped).matches();
    }

    private boolean isSectionHeading(String line) {
        return matchHeading(line) != null;
    }

    private boolean shouldKeepAsProjectContent(String currentType, HeadingMatch headingMatch, String line) {
        return "PROJECTS".equals(currentType)
                && "SKILLS".equals(headingMatch.sectionType())
                && normalizeHeading(line).startsWith("技术栈");
    }

    private String removeHeadingPrefix(String line, String heading) {
        String stripped = line.strip();
        String pattern = "^" + Pattern.quote(heading) + "\\s*[:：]?\\s*";
        return cleanSectionLine(stripped.replaceFirst("(?i)" + pattern, ""));
    }

    private List<String> extractSectionLines(List<ResumeTextSectionDTO> sections, String sectionType, Set<String> assignedLines) {
        List<String> result = new ArrayList<>();
        for (ResumeTextSectionDTO section : sections) {
            if (!sectionType.equals(section.getSectionType())) {
                continue;
            }
            for (String line : section.getLines()) {
                String cleaned = cleanSectionLine(line);
                if (cleaned.isBlank()) {
                    continue;
                }
                String key = normalizeForDedupe(cleaned);
                assignedLines.add(key);
                if (result.stream().noneMatch(existing -> normalizeForDedupe(existing).equals(key))) {
                    result.add(cleaned);
                }
            }
        }
        return result;
    }

    private void markSectionLines(List<ResumeTextSectionDTO> sections, String sectionType, Set<String> assignedLines) {
        sections.stream()
                .filter(section -> sectionType.equals(section.getSectionType()))
                .flatMap(section -> section.getLines().stream())
                .map(this::cleanSectionLine)
                .map(this::normalizeForDedupe)
                .forEach(assignedLines::add);
    }

    private void markPersonalInfoLines(List<String> lines, Set<String> assignedLines) {
        lines.stream()
                .filter(this::isPersonalInfoLine)
                .map(this::normalizeForDedupe)
                .forEach(assignedLines::add);
    }

    private boolean isPersonalInfoLine(String line) {
        return EMAIL_PATTERN.matcher(line).find()
                || PHONE_PATTERN.matcher(line).find()
                || GITHUB_PATTERN.matcher(line).find()
                || LINKEDIN_PATTERN.matcher(line).find()
                || NAME_LABEL_PATTERN.matcher(line).find()
                || JOB_INTENTION_PATTERN.matcher(line).find()
                || GPA_PATTERN.matcher(line).find()
                || LANGUAGE_PATTERN.matcher(line).find()
                || AGE_PATTERN.matcher(line).find()
                || LOCATION_PATTERN.matcher(line).find()
                || isValidNameCandidate(line);
    }

    private List<String> extractSkills(List<ResumeTextSectionDTO> sections, List<String> workExperiences, List<String> projects) {
        Set<String> skills = new LinkedHashSet<>();
        sections.stream()
                .filter(section -> "SKILLS".equals(section.getSectionType()))
                .flatMap(section -> section.getLines().stream())
                .forEach(line -> addSkillsFromLine(line, skills));
        workExperiences.forEach(line -> addSkillsFromLine(line, skills));
        projects.forEach(line -> addSkillsFromLine(line, skills));
        return List.copyOf(skills);
    }

    private void addSkillsFromLine(String line, Set<String> skills) {
        for (Map.Entry<String, List<String>> entry : TECH_SKILL_ALIASES.entrySet()) {
            for (String alias : entry.getValue()) {
                if (containsSkillAlias(line, alias)) {
                    skills.add(entry.getKey());
                    break;
                }
            }
        }
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

    private boolean hasAnySkill(String line) {
        return skillCount(line) > 0;
    }

    private boolean containsSkillAlias(String line, String alias) {
        if (line == null || line.isBlank()) {
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

    private String extractSummary(List<ResumeTextSectionDTO> sections, Set<String> assignedLines) {
        List<String> summaryLines = extractSectionLines(sections, "SUMMARY", assignedLines);
        if (summaryLines.isEmpty()) {
            return null;
        }
        return String.join("；", summaryLines);
    }

    private List<String> extractOthers(List<ResumeTextSectionDTO> sections, Set<String> assignedLines) {
        List<String> others = new ArrayList<>();
        for (ResumeTextSectionDTO section : sections) {
            if (!GENERAL_SECTION.equals(section.getSectionType()) && !"OTHERS".equals(section.getSectionType())) {
                continue;
            }
            for (String line : section.getLines()) {
                String cleaned = cleanSectionLine(line);
                String key = normalizeForDedupe(cleaned);
                if (cleaned.isBlank()
                        || assignedLines.contains(key)
                        || isPersonalInfoLine(cleaned)
                        || isSectionHeading(cleaned)
                        || isLowValueOtherLine(cleaned)
                        || others.stream().anyMatch(existing -> normalizeForDedupe(existing).equals(key))) {
                    continue;
                }
                others.add(cleaned);
                if (others.size() >= OTHERS_MAX_SIZE) {
                    return others;
                }
            }
        }
        return others;
    }

    private boolean isLowValueOtherLine(String line) {
        return line.length() < 2
                || line.length() > 180
                || containsIgnoreCase(line, NAME_EXCLUDE_EXACT)
                || EMAIL_PATTERN.matcher(line).find()
                || PHONE_PATTERN.matcher(line).find()
                || GITHUB_PATTERN.matcher(line).find()
                || GPA_PATTERN.matcher(line).find()
                || LANGUAGE_PATTERN.matcher(line).find();
    }

    private boolean containsIgnoreCase(String value, List<String> keywords) {
        String lower = value.toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(keyword -> lower.contains(keyword.toLowerCase(Locale.ROOT)));
    }

    private String normalizeHeading(String text) {
        return normalizeUnicode(text)
                .replaceAll("^[" + ICON_CHARS + "]+", "")
                .replaceAll("[\\s]+", " ")
                .replace('：', ':')
                .strip()
                .toLowerCase(Locale.ROOT);
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

    private String cleanSectionLine(String line) {
        return normalizeLine(line).replaceFirst("^[:：]+", "").strip();
    }

    private String normalizeForDedupe(String line) {
        return normalizeLine(line)
                .replaceAll("[\\s,，、；;:：.。]+", "")
                .toLowerCase(Locale.ROOT);
    }

    private record HeadingMatch(String sectionType, String heading) {
    }

    private record NameCandidate(String name, int score) {
    }
}
