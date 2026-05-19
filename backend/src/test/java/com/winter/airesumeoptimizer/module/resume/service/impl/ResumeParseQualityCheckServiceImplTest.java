package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextQualityResultDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeParseQualityCheckServiceImplTest {

    private final ResumeParseQualityCheckServiceImpl service = new ResumeParseQualityCheckServiceImpl();

    @Test
    void checkShouldReturnGoodWhenCoreFieldsAreAvailable() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("张三")
                .phone("13800000000")
                .email("zhangsan@example.com")
                .resumeType("EXPERIENCED")
                .skills(List.of("Java", "Spring Boot"))
                .workExperiences(List.of("某科技公司 Java 后端开发工程师"))
                .projects(List.of("AI 简历优化系统"))
                .education(List.of("西南交通大学 软件工程 本科"))
                .build();

        var result = service.check(content, cleanResult("专业技能\nJava\n项目经历\nAI 简历优化系统", 2), textQuality("GOOD"));

        assertThat(result.getStatus()).isEqualTo("GOOD");
        assertThat(result.getWarnings()).isEmpty();
        assertThat(result.getScore()).isEqualTo(100);
    }

    @Test
    void checkShouldWarnWhenEducationIsMissing() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("张三")
                .phone("13800000000")
                .email("zhangsan@example.com")
                .resumeType("INTERN")
                .skills(List.of("Java", "Spring Boot"))
                .internships(List.of("某公司 Java 后端实习生"))
                .build();

        var result = service.check(content, cleanResult("教育经历\n西南交通大学\n专业技能\nJava", 2), textQuality("GOOD"));

        assertThat(result.getStatus()).isEqualTo("WARNING");
        assertThat(result.getWarnings()).contains("EDUCATION_MISSING");
        assertThat(result.getMessage()).contains("未识别到教育经历");
    }

    @Test
    void checkShouldWarnWhenContactIsIncomplete() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("张三")
                .resumeType("EXPERIENCED")
                .skills(List.of("Java", "Spring Boot"))
                .projects(List.of("AI 简历优化系统"))
                .workExperiences(List.of("某科技公司 Java 后端开发工程师"))
                .education(List.of("西南交通大学 软件工程 本科"))
                .build();

        var result = service.check(content, cleanResult("专业技能\nJava\n工作经历\n某科技公司\n项目经历\nAI 简历优化系统", 3), textQuality("GOOD"));

        assertThat(result.getStatus()).isEqualTo("WARNING");
        assertThat(result.getWarnings()).contains("CONTACT_MISSING");
        assertThat(result.getMessage()).contains("联系方式");
    }

    @Test
    void checkShouldWarnWhenResumeTypeIsUnknown() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("李四")
                .phone("13900000000")
                .email("lisi@example.com")
                .resumeType("UNKNOWN")
                .skills(List.of("Java", "MySQL"))
                .projects(List.of("订单系统"))
                .education(List.of("计算机科学与技术 本科"))
                .build();
        String text = "李四 13900000000 lisi@example.com\n"
                + "教育背景\n计算机科学与技术 本科\n"
                + "专业技能\nJava MySQL\n"
                + "项目经历\n订单系统，负责接口开发和性能优化\n"
                + "项目成果\n支持订单创建、库存扣减、支付回调和异常补偿，优化接口响应时间并完善监控告警。\n"
                + "其他说明\n开源社区贡献：维护内部工具脚本，长期参与代码评审和文档维护。";

        var result = service.check(content, cleanResult(text, 4), textQuality("GOOD"));

        assertThat(result.getStatus()).isEqualTo("WARNING");
        assertThat(result.getWarnings()).contains("RESUME_TYPE_UNKNOWN");
        assertThat(result.getMessage()).contains("未能明确识别简历类型");
    }

    @Test
    void checkShouldWarnWhenExpectedExperienceSectionIsMissing() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("王五")
                .phone("13700000000")
                .email("wangwu@example.com")
                .resumeType("EXPERIENCED")
                .skills(List.of("Java", "Redis"))
                .projects(List.of("支付系统"))
                .education(List.of("软件工程 本科"))
                .build();

        var result = service.check(content, cleanResult("专业技能\nJava\n项目经历\n支付系统\n教育经历\n软件工程 本科", 3), textQuality("GOOD"));

        assertThat(result.getStatus()).isEqualTo("WARNING");
        assertThat(result.getWarnings()).contains("WORK_EXPERIENCE_MISSING");
        assertThat(result.getMessage()).contains("有工作经验");
    }

    @Test
    void checkShouldWarnWhenOthersAndDuplicatesAreTooMany() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("赵六")
                .phone("13600000000")
                .email("zhaoliu@example.com")
                .resumeType("STUDENT")
                .skills(List.of("Java"))
                .education(List.of("软件工程 本科"))
                .projects(List.of("课程管理系统"))
                .campusExperiences(List.of("学生会技术部"))
                .others(List.of("其他 1", "其他 2", "其他 3", "其他 4", "其他 5", "其他 6", "其他 7", "其他 8", "其他 9"))
                .build();

        var result = service.check(content, cleanResult("教育经历\n软件工程\n项目经历\n课程管理系统", 3, 4), textQuality("GOOD"));

        assertThat(result.getStatus()).isEqualTo("WARNING");
        assertThat(result.getWarnings()).contains("OTHERS_TOO_MANY", "DUPLICATE_CONTENT_TOO_MANY");
    }

    @Test
    void checkShouldWarnWhenInvalidContentWasFiltered() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("赵六")
                .phone("13600000000")
                .email("zhaoliu@example.com")
                .resumeType("STUDENT")
                .skills(List.of("Java"))
                .education(List.of("软件工程 本科"))
                .projects(List.of("课程管理系统"))
                .campusExperiences(List.of("学生会技术部"))
                .build();

        var result = service.check(content, cleanResult("教育经历\n软件工程\n项目经历\n课程管理系统", 3, 0, 4), textQuality("GOOD"));

        assertThat(result.getStatus()).isEqualTo("WARNING");
        assertThat(result.getWarnings()).contains("INVALID_CONTENT_FILTERED");
        assertThat(result.getMessage()).contains("无效序号");
    }

    @Test
    void checkShouldWarnWhenAiSectionConflictExists() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .name("赵六")
                .phone("13600000000")
                .email("zhaoliu@example.com")
                .resumeType("STUDENT")
                .skills(List.of("Java"))
                .education(List.of("软件工程 本科"))
                .projects(List.of("课程管理系统"))
                .campusExperiences(List.of("学生会技术部"))
                .build();
        ResumeTextCleanResultDTO cleanResult = cleanResult("教育经历\n软件工程\n在校经历\n学生会技术部", 3, 0, 0);
        cleanResult.setSectionConflictWarnings(List.of("AI_SECTION_CONFLICT:RULE_SOURCE_SECTION:0:CAMPUS_EXPERIENCES>AWARDS"));

        var result = service.check(content, cleanResult, textQuality("GOOD"));

        assertThat(result.getStatus()).isEqualTo("WARNING");
        assertThat(result.getWarnings()).contains("AI_SECTION_CONFLICT");
        assertThat(result.getMessage()).contains("置信度策略");
    }


    @Test
    void checkShouldFailWhenCoreFieldsAreMissing() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder().build();

        var result = service.check(content, cleanResult("无法识别的简历文本", 0), textQuality("GOOD"));

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getWarnings()).contains("CORE_FIELDS_MISSING");
        assertThat(result.failed()).isTrue();
    }

    private ResumeTextCleanResultDTO cleanResult(String cleanedText, int sectionCount) {
        return cleanResult(cleanedText, sectionCount, 0);
    }

    private ResumeTextCleanResultDTO cleanResult(String cleanedText, int sectionCount, int duplicateLineCount) {
        return cleanResult(cleanedText, sectionCount, duplicateLineCount, 0);
    }

    private ResumeTextCleanResultDTO cleanResult(String cleanedText, int sectionCount, int duplicateLineCount, int invalidLineCount) {
        return ResumeTextCleanResultDTO.builder()
                .cleanedText(cleanedText)
                .duplicateLineCount(duplicateLineCount)
                .invalidLineCount(invalidLineCount)
                .sections(java.util.stream.IntStream.range(0, sectionCount)
                        .mapToObj(index -> com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO.builder()
                                .sectionType("SECTION_" + index)
                                .heading("章节" + index)
                                .lines(List.of("内容" + index))
                                .build())
                        .toList())
                .build();
    }

    private ResumeTextQualityResultDTO textQuality(String status) {
        return ResumeTextQualityResultDTO.builder()
                .status(status)
                .issues(List.of())
                .message("文本质量正常")
                .build();
    }
}
