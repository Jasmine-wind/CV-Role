package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeExperienceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredDataDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumePointerPostProcessorImplTest {

    private final ResumePointerPostProcessorImpl postProcessor =
            new ResumePointerPostProcessorImpl(new ResumePointerValidatorImpl());

    @Test
    void attachSourceRefsShouldUseOriginalIndexedLineText() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .structuredData(ResumeStructuredDataDTO.builder()
                        .experiences(List.of(ResumeExperienceDTO.builder()
                                .type("WORK")
                                .organization("北京华来知识科技有限公司")
                                .role("JavaEE 软件工程师")
                                .sourceSectionId("work")
                                .bullets(List.of("参与软件需求分析", "进行模块详细设计"))
                                .build()))
                        .projects(List.of(ResumeProjectDTO.builder()
                                .name("比丘商城后台管理系统")
                                .sourceSectionId("project")
                                .description("后台管理系统项目")
                                .techStack(List.of("Spring Boot", "MySQL", "Redis"))
                                .build()))
                        .summary("熟悉 Java 后端开发。")
                        .build())
                .build();

        postProcessor.attachSourceRefs(content, lines());

        ResumeExperienceDTO experience = content.getStructuredData().getExperiences().get(0);
        ResumeProjectDTO project = content.getStructuredData().getProjects().get(0);
        assertThat(experience.getSourceRef()).isNotNull();
        assertThat(experience.getSourceRef().getStartLine()).isEqualTo(1);
        assertThat(experience.getSourceRef().getEndLine()).isEqualTo(5);
        assertThat(experience.getSourceRef().getText()).contains("北京华来知识科技有限公司", "参与软件需求分析");
        assertThat(project.getSourceRef()).isNotNull();
        assertThat(project.getSourceRef().getText()).contains("比丘商城后台管理系统", "Spring Boot、MySQL、Redis");
        assertThat(content.getStructuredData().getSummarySourceRef().getText()).isEqualTo("熟悉 Java 后端开发。");
    }

    @Test
    void attachSourceRefsShouldIgnoreNullSummaryAndBlankEducation() {
        ResumeStructuredContentDTO content = ResumeStructuredContentDTO.builder()
                .structuredData(ResumeStructuredDataDTO.builder()
                        .education(List.of("", "重庆理工大学 JAVA开发 大学本科"))
                        .summary(null)
                        .build())
                .build();

        assertThatNoException().isThrownBy(() -> postProcessor.attachSourceRefs(content, List.of(
                line(1, "重庆理工大学", "education"),
                line(2, "专业：JAVA开发", "education"),
                line(3, "学历：大学本科", "education"))));

        assertThat(content.getStructuredData().getSummarySourceRef()).isNull();
        assertThat(content.getStructuredData().getEducationSourceRefs()).hasSize(1);
        assertThat(content.getStructuredData().getEducationSourceRefs().get(0).getText())
                .contains("重庆理工大学");
    }

    private List<ResumeIndexedLineDTO> lines() {
        return List.of(
                line(1, "北京华来知识科技有限公司", "work"),
                line(2, "JavaEE 软件工程师", "work"),
                line(3, "2017.10 - 2019.09", "work"),
                line(4, "参与软件需求分析", "work"),
                line(5, "进行模块详细设计", "work"),
                line(6, "比丘商城后台管理系统", "project"),
                line(7, "后台管理系统项目，技术栈 Spring Boot、MySQL、Redis", "project"),
                line(8, "熟悉 Java 后端开发。", null));
    }

    private ResumeIndexedLineDTO line(int id, String text, String rawSectionId) {
        return ResumeIndexedLineDTO.builder()
                .lineId(id)
                .page(1)
                .text(text)
                .normalizedText(text)
                .rawSectionId(rawSectionId)
                .isNoise(false)
                .build();
    }
}
