package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBasicInfoFieldDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResumeParseValidatorImplTest {

    private final ResumeParseValidatorImpl validator = new ResumeParseValidatorImpl();

    @Test
    void validateAndMergeShouldFallbackInvalidBasicInfoToRule() {
        ResumeStructuredContentDTO ai = ResumeStructuredContentDTO.builder()
                .name("教育经历")
                .phone("123")
                .email("bad-email")
                .basicInfo(Map.of("gender", "男"))
                .build();
        ResumeStructuredContentDTO rule = ResumeStructuredContentDTO.builder()
                .name("张三")
                .phone("13800000000")
                .email("zhangsan@example.com")
                .basicInfo(Map.of("resumeType", "EXPERIENCED"))
                .resumeType("EXPERIENCED")
                .build();

        ResumeStructuredContentDTO result = validator.validateAndMerge(ai, rule, List.of());

        assertThat(result.getName()).isEqualTo("张三");
        assertThat(result.getPhone()).isEqualTo("13800000000");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(result.getBasicInfo()).containsEntry("gender", "男");
        assertThat(result.getQualityWarnings()).contains("AI_NAME_INVALID_FALLBACK_TO_RULE");
    }

    @Test
    void validateAndMergeShouldNotLetAiOverrideRuleBasicInfo() {
        ResumeStructuredContentDTO ai = ResumeStructuredContentDTO.builder()
                .name("李四")
                .phone("13900000000")
                .email("lisi@example.com")
                .jobIntention("产品经理")
                .highestEducation("硕士")
                .basicInfo(Map.of(
                        "name", "本人",
                        "phone", "13900000000",
                        "email", "lisi@example.com",
                        "degree", "硕士",
                        "jobIntention", "产品经理",
                        "workYears", "三年"))
                .build();
        ResumeStructuredContentDTO rule = ResumeStructuredContentDTO.builder()
                .name("张三")
                .phone("13800000000")
                .email("zhangsan@example.com")
                .jobIntention("Java 后端开发工程师")
                .highestEducation("本科")
                .basicInfo(Map.of(
                        "name", "张三",
                        "phone", "13800000000",
                        "email", "zhangsan@example.com",
                        "degree", "本科",
                        "jobIntention", "Java 后端开发工程师",
                        "workYears", "5年"))
                .build();

        ResumeStructuredContentDTO result = validator.validateAndMerge(ai, rule, List.of());

        assertThat(result.getName()).isEqualTo("张三");
        assertThat(result.getPhone()).isEqualTo("13800000000");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(result.getJobIntention()).isEqualTo("Java 后端开发工程师");
        assertThat(result.getHighestEducation()).isEqualTo("本科");
        assertThat(result.getBasicInfo())
                .containsEntry("name", "张三")
                .containsEntry("phone", "13800000000")
                .containsEntry("email", "zhangsan@example.com")
                .containsEntry("degree", "本科")
                .containsEntry("jobIntention", "Java 后端开发工程师")
                .containsEntry("workYears", "5年");
    }

    @Test
    void validateAndMergeShouldKeepRuleConfirmedBasicInfoDebugWhenAiDiffers() {
        ResumeStructuredContentDTO ai = ResumeStructuredContentDTO.builder()
                .name("李四")
                .basicInfo(Map.of("name", "李四"))
                .basicInfoDebug(Map.of("name", ResumeBasicInfoFieldDTO.builder()
                        .value("李四")
                        .confidence(0.7)
                        .source("AI")
                        .evidence("AI 输出")
                        .status("CONFIRMED")
                        .build()))
                .build();
        ResumeStructuredContentDTO rule = ResumeStructuredContentDTO.builder()
                .name("张三")
                .basicInfo(Map.of("name", "张三"))
                .basicInfoDebug(Map.of("name", ResumeBasicInfoFieldDTO.builder()
                        .value("张三")
                        .confidence(0.92)
                        .source("RULE")
                        .evidence("姓名：张三")
                        .status("CONFIRMED")
                        .build()))
                .build();

        ResumeStructuredContentDTO result = validator.validateAndMerge(ai, rule, List.of());

        assertThat(result.getName()).isEqualTo("张三");
        assertThat(result.getBasicInfoDebug().get("name").getValue()).isEqualTo("张三");
        assertThat(result.getBasicInfoDebug().get("name").getSource()).isEqualTo("RULE");
        assertThat(result.getBasicInfoDebug().get("name").getEvidence()).isEqualTo("姓名：张三");
        assertThat(result.getQualityWarnings()).contains("AI_NAME_INVALID_FALLBACK_TO_RULE");
    }

    @Test
    void validateAndMergeShouldAllowValidAiBasicInfoOnlyWhenRuleIsEmpty() {
        ResumeStructuredContentDTO ai = ResumeStructuredContentDTO.builder()
                .name("本人")
                .phone("139-0000-0000")
                .email("lisi@example.com")
                .highestEducation("大学本科")
                .basicInfo(Map.of(
                        "name", "参加项目描述",
                        "jobIntention", "Java 实习生",
                        "workYears", "2年",
                        "age", "23"))
                .build();

        ResumeStructuredContentDTO result = validator.validateAndMerge(ai, ResumeStructuredContentDTO.builder().build(), List.of());

        assertThat(result.getName()).isNull();
        assertThat(result.getPhone()).isEqualTo("13900000000");
        assertThat(result.getEmail()).isEqualTo("lisi@example.com");
        assertThat(result.getHighestEducation()).isEqualTo("本科");
        assertThat(result.getBasicInfo())
                .doesNotContainKey("name")
                .containsEntry("phone", "13900000000")
                .containsEntry("email", "lisi@example.com")
                .containsEntry("degree", "本科")
                .containsEntry("jobIntention", "Java 实习生")
                .containsEntry("workYears", "2年")
                .containsEntry("age", "23");
    }

    @Test
    void validateAndMergeShouldRejectTemplateNameAndKeepRuleSchool() {
        ResumeStructuredContentDTO ai = ResumeStructuredContentDTO.builder()
                .name("基本资料")
                .basicInfo(Map.of(
                        "name", "基本资料",
                        "school", "郑州轻工业学院",
                        "jobIntention", "全职，目标地点：郑州"))
                .build();
        ResumeStructuredContentDTO rule = ResumeStructuredContentDTO.builder()
                .name("西施")
                .jobIntention("JAVA软件工程师")
                .basicInfo(Map.of(
                        "name", "西施",
                        "school", "郑州轻工业学院",
                        "jobIntention", "JAVA软件工程师"))
                .build();

        ResumeStructuredContentDTO result = validator.validateAndMerge(ai, rule, List.of());

        assertThat(result.getName()).isEqualTo("西施");
        assertThat(result.getJobIntention()).isEqualTo("JAVA软件工程师");
        assertThat(result.getBasicInfo())
                .containsEntry("name", "西施")
                .containsEntry("school", "郑州轻工业学院")
                .containsEntry("jobIntention", "JAVA软件工程师");
        assertThat(result.getQualityWarnings()).contains("AI_NAME_INVALID_FALLBACK_TO_RULE");
    }

    @Test
    void validateAndMergeShouldKeepExpandedTechnicalSkills() {
        ResumeStructuredContentDTO rule = ResumeStructuredContentDTO.builder()
                .skills(List.of("SpringData", "FreeMarker", "FFmpeg", "Eureka", "Lucene", "IDEA", "Tomcat", "Nginx"))
                .build();

        ResumeStructuredContentDTO result = validator.validateAndMerge(null, rule, List.of());

        assertThat(result.getSkills())
                .contains("Spring Data", "FreeMarker", "FFmpeg", "Eureka", "Lucene", "IDEA", "Tomcat", "Nginx");
    }

    @Test
    void validateAndMergeShouldKeepOnlyTechnicalSkills() {
        ResumeStructuredContentDTO ai = ResumeStructuredContentDTO.builder()
                .skills(List.of("Java", "完成报表模块", "SpringMVC", "学习能力强", "Redis"))
                .build();

        ResumeStructuredContentDTO result = validator.validateAndMerge(ai, ResumeStructuredContentDTO.builder().build(), List.of());

        assertThat(result.getSkills()).containsExactly("Java", "Spring MVC", "Redis");
        assertThat(result.getQualityWarnings()).contains("AI_SKILLS_NON_TECH_TEXT_FILTERED");
    }

    @Test
    void validateAndMergeShouldNotLetAiOverrideRuleSkills() {
        ResumeStructuredContentDTO ai = ResumeStructuredContentDTO.builder()
                .skills(List.of("Python", "Vue", "完成报表模块"))
                .build();
        ResumeStructuredContentDTO rule = ResumeStructuredContentDTO.builder()
                .skills(List.of("Java", "Spring Boot", "MySQL"))
                .build();

        ResumeStructuredContentDTO result = validator.validateAndMerge(ai, rule, List.of());

        assertThat(result.getSkills()).containsExactly("Java", "Spring Boot", "MySQL");
    }

    @Test
    void validateAndMergeShouldRemoveDuplicateAssignedTextFromOthers() {
        String project = "AI 简历优化系统：负责解析模块";
        ResumeStructuredContentDTO ai = ResumeStructuredContentDTO.builder()
                .projects(List.of(project))
                .workExperiences(List.of(project))
                .others(List.of(project, "开源贡献"))
                .build();

        ResumeStructuredContentDTO result = validator.validateAndMerge(ai, ResumeStructuredContentDTO.builder().build(), List.of());

        assertThat(result.getWorkExperiences()).containsExactly(project);
        assertThat(result.getProjects()).isEmpty();
        assertThat(result.getOthers()).containsExactly("开源贡献");
        assertThat(result.getQualityWarnings()).contains("AI_DUPLICATE_TEXT_REMOVED", "AI_OTHERS_ASSIGNED_TEXT_REMOVED");
    }

    @Test
    void validateAndMergeShouldFilterInvalidNumberingFromAllFieldsAndLimitOthers() {
        ResumeStructuredContentDTO ai = ResumeStructuredContentDTO.builder()
                .name("本人")
                .basicInfo(Map.of("name", "1、", "location", "、"))
                .education(List.of("1、", "示例大学 软件工程 本科"))
                .projects(List.of("2.", "课程管理系统"))
                .summary("①")
                .others(List.of("一、", "其他1", "其他2", "其他3", "其他4", "其他5", "其他6", "其他7", "其他8", "其他9"))
                .build();

        ResumeStructuredContentDTO result = validator.validateAndMerge(ai, ResumeStructuredContentDTO.builder().build(), List.of());

        assertThat(result.getName()).isNull();
        assertThat(result.getBasicInfo()).doesNotContainKeys("name", "location");
        assertThat(result.getEducation()).containsExactly("示例大学 软件工程 本科");
        assertThat(result.getProjects()).containsExactly("课程管理系统");
        assertThat(result.getSummary()).isNull();
        assertThat(result.getOthers()).hasSize(8).doesNotContain("一、", "其他9");
        assertThat(result.getQualityWarnings()).contains("AI_INVALID_CONTENT_FILTERED", "AI_OTHERS_TOO_MANY_FILTERED");
    }

    @Test
    void validateAndMergeShouldKeepRuleCampusExperienceWhenAiDuplicatesItInProjectsAndAwards() {
        String campusLine = "组织校园技术分享活动，获得校级奖项";
        ResumeStructuredContentDTO ai = ResumeStructuredContentDTO.builder()
                .projects(List.of(campusLine))
                .awards(List.of(campusLine))
                .build();
        ResumeStructuredContentDTO rule = ResumeStructuredContentDTO.builder()
                .campusExperiences(List.of(campusLine))
                .build();

        ResumeStructuredContentDTO result = validator.validateAndMerge(ai, rule, List.of());

        assertThat(result.getCampusExperiences()).containsExactly(campusLine);
        assertThat(result.getProjects()).isEmpty();
        assertThat(result.getAwards()).isEmpty();
        assertThat(result.getQualityWarnings()).contains("AI_DUPLICATE_TEXT_REMOVED");
    }
}
