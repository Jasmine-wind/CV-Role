package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeDisplayModelDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillSetDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredDataDTO;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResumeDisplayModelServiceImplTest {

    @Test
    void buildAiDisplayModelShouldUseAiAndCacheResult() {
        CountingAiClient aiClient = new CountingAiClient("""
                {
                  "overview": {"name":"张三","targetRole":"Java 后端","resumeType":"EXPERIENCED","highestDegree":"本科","workYears":"2年","coreSkills":["Java","Spring Boot"]},
                  "skillSummary": {"topSkills":["Java","Spring Boot"],"groups":[{"name":"后端","skills":["Java","Spring Boot"]}]},
                  "educationCards": [{"school":"北京大学","degree":"本科","major":"软件工程","timeRange":"2019-2023","summary":"北京大学 软件工程 本科"}],
                  "workExperienceCards": [],
                  "internshipCards": [],
                  "campusExperienceCards": [],
                  "projectCards": [],
                  "achievementCards": [],
                  "certificateTags": [],
                  "summaryCard": {"content":"熟悉 Java 后端开发。","collapsed":true},
                  "pendingItems": [],
                  "displayMeta": {"generatedBy":"AI","aiDisplayUsed":true,"aiDisplayFallback":false,"aiDisplayErrorMessage":"","aiDisplayDurationMs":0,"cacheHit":false}
                }
                """);
        ResumeDisplayModelServiceImpl service = new ResumeDisplayModelServiceImpl(aiClient, new ObjectMapper());

        ResumeDisplayModelDTO first = service.buildAiDisplayModel(1L, sampleContent());
        ResumeDisplayModelDTO second = service.buildAiDisplayModel(1L, sampleContent());

        assertThat(first.getDisplayMeta().getAiDisplayUsed()).isTrue();
        assertThat(first.getDisplayMeta().getCacheHit()).isFalse();
        assertThat(second.getDisplayMeta().getAiDisplayUsed()).isTrue();
        assertThat(second.getDisplayMeta().getCacheHit()).isTrue();
        assertThat(aiClient.callCount).isEqualTo(1);
    }

    @Test
    void buildAiDisplayModelShouldFallbackToRuleWhenAiJsonInvalid() {
        ResumeDisplayModelServiceImpl service = new ResumeDisplayModelServiceImpl(
                new CountingAiClient("not-json"),
                new ObjectMapper());

        ResumeDisplayModelDTO model = service.buildAiDisplayModel(1L, sampleContent());

        assertThat(model.getDisplayMeta().getGeneratedBy()).isEqualTo("RULE");
        assertThat(model.getDisplayMeta().getAiDisplayUsed()).isFalse();
        assertThat(model.getDisplayMeta().getAiDisplayFallback()).isTrue();
        assertThat(model.getOverview().getName()).isEqualTo("张三");
    }

    @Test
    void buildRuleDisplayModelShouldKeepMultipleProjectsInSameSectionSeparated() {
        ResumeDisplayModelServiceImpl service = new ResumeDisplayModelServiceImpl(
                new CountingAiClient(""),
                new ObjectMapper());
        ResumeStructuredContentDTO content = sampleContent();
        content.getStructuredData().setProjects(List.of(
                ResumeProjectDTO.builder()
                        .name("蓝天健康中心管理系统")
                        .description("支持线上预约、套餐项目管理和手机号快速登录。")
                        .techStack(List.of("Spring", "MyBatis", "MySQL"))
                        .responsibilities(List.of("负责编写后台的套餐管理模块", "实现手机号快速登录模块"))
                        .sourceSectionId("section-projects")
                        .sourceRef(ResumeSourceRefDTO.builder().startLine(10).endLine(20).text("项目一原文").build())
                        .build(),
                ResumeProjectDTO.builder()
                        .name("公司后台管理系统")
                        .description("支持员工职务、产品库存和售后管理。")
                        .techStack(List.of("Spring Boot", "MyBatis", "MySQL"))
                        .responsibilities(List.of("负责开发员工管理模块"))
                        .sourceSectionId("section-projects")
                        .sourceRef(ResumeSourceRefDTO.builder().startLine(21).endLine(30).text("项目二原文").build())
                        .build()));

        ResumeDisplayModelDTO model = service.buildRuleDisplayModel(1L, content);

        assertThat(model.getProjectCards()).hasSize(2);
        assertThat(model.getProjectCards()).extracting("name")
                .containsExactly("蓝天健康中心管理系统", "公司后台管理系统");
        assertThat(model.getProjectCards().get(0).getResponsibilities())
                .contains("负责编写后台的套餐管理模块");
        assertThat(model.getProjectCards().get(0).getSourceRef().getText()).isEqualTo("项目一原文");
        assertThat(model.getProjectCards().get(1).getSourceRef().getText()).isEqualTo("项目二原文");
    }

    @Test
    void buildRuleDisplayModelShouldSplitAndFillProjectCardsFromSourceText() {
        ResumeDisplayModelServiceImpl service = new ResumeDisplayModelServiceImpl(
                new CountingAiClient(""),
                new ObjectMapper());
        ResumeStructuredContentDTO content = sampleContent();
        content.getStructuredData().setProjects(List.of(ResumeProjectDTO.builder()
                .name("项目经历 1")
                .sourceSectionId("section-projects")
                .sourceRef(ResumeSourceRefDTO.builder()
                        .startLine(10)
                        .endLine(29)
                        .text("""
                                项目一：
                                开发环境：tomcat7、Maven、JDK1.8
                                项目名称：蓝天健康中心管理系统
                                项目描述：该管理系统支持线上预约、套餐项目管理和手机号快速登录。
                                负责模块：
                                负责编写后台的套餐管理模块；
                                实现手机号快速登录模块；
                                技术选型：Spring、SpringMVC、MyBatis、Spring Security、MySQL
                                项目二：
                                项目名称：公司后台管理系统
                                项目描述：该管理系统可以对员工职务、产品库存和售后进行管理。
                                负责开发员工管理模块，员工的职务管理模块
                                技术选型：Spring Boot、MyBatis、MySQL
                                """)
                        .build())
                .build()));

        ResumeDisplayModelDTO model = service.buildRuleDisplayModel(1L, content);

        assertThat(model.getProjectCards()).hasSize(2);
        assertThat(model.getProjectCards()).extracting("name")
                .containsExactly("蓝天健康中心管理系统", "公司后台管理系统");
        assertThat(model.getProjectCards().get(0).getSummary()).contains("线上预约");
        assertThat(model.getProjectCards().get(0).getTechStack())
                .contains("Tomcat", "Maven", "JDK", "Spring", "Spring MVC", "MyBatis", "Spring Security", "MySQL");
        assertThat(model.getProjectCards().get(0).getResponsibilities())
                .contains("负责编写后台的套餐管理模块", "实现手机号快速登录模块");
        assertThat(model.getProjectCards().get(0).getSourceRef().getText())
                .contains("项目名称：蓝天健康中心管理系统")
                .doesNotContain("项目二：");
        assertThat(model.getProjectCards().get(1).getSourceRef().getText())
                .contains("项目名称：公司后台管理系统")
                .doesNotContain("项目一：");
    }

    private ResumeStructuredContentDTO sampleContent() {
        return ResumeStructuredContentDTO.builder()
                .name("张三")
                .jobIntention("Java 后端")
                .highestEducation("本科")
                .resumeType("EXPERIENCED")
                .basicInfo(Map.of(
                        "name", "张三",
                        "workYears", "2年",
                        "school", "北京大学",
                        "degree", "本科",
                        "major", "软件工程"))
                .structuredData(ResumeStructuredDataDTO.builder()
                        .education(List.of("北京大学 软件工程 本科 2019-2023"))
                        .skills(ResumeSkillSetDTO.builder()
                                .keywords(List.of("Java", "Spring Boot"))
                                .groups(Map.of("framework", List.of("Spring Boot"), "language", List.of("Java")))
                                .build())
                        .summary("熟悉 Java 后端开发。")
                        .build())
                .build();
    }

    private static final class CountingAiClient implements AiClientService {

        private final String output;
        private int callCount;

        private CountingAiClient(String output) {
            this.output = output;
        }

        @Override
        public String complete(String prompt) {
            callCount++;
            return output;
        }

        @Override
        public String modelName() {
            return "test-model";
        }
    }
}
