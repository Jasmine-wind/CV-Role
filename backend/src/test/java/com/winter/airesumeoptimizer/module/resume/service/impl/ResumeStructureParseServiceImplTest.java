package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeStructureParseServiceImplTest {

    private final ResumeStructureParseServiceImpl service = new ResumeStructureParseServiceImpl();

    @Test
    void parseShouldExtractBasicFields() {
        String rawText = """
                张三
                手机：+86 138-1234-5678
                邮箱：zhangsan@example.com
                教育经历
                西南交通大学 软件工程 本科
                专业技能
                Java Spring Boot MyBatis Redis Docker Git
                项目经历
                AI 简历优化系统，负责后端接口开发
                实习经历
                某科技公司 Java 后端实习生
                荣誉奖项
                校级奖学金
                自我评价
                具备后端工程实践经验
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getName()).isEqualTo("张三");
        assertThat(result.getPhone()).isEqualTo("13812345678");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(result.getSkills()).contains("Java", "Spring Boot", "MyBatis", "Redis", "Docker", "Git");
        assertThat(result.getSkills()).doesNotContain("具备后端工程实践经验");
        assertThat(result.getEducation()).contains("西南交通大学 软件工程 本科");
        assertThat(result.getProjects()).contains("AI 简历优化系统，负责后端接口开发");
        assertThat(result.getInternships()).contains("某科技公司 Java 后端实习生");
        assertThat(result.getBasicInfo()).containsEntry("name", "张三");
        assertThat(result.getBasicInfoDebug().get("name").getStatus()).isEqualTo("CONFIRMED");
        assertThat(result.getBasicInfoDebug().get("phone").getSource()).isEqualTo("REGEX");
        assertThat(result.getBasicInfoDebug().get("phone").getEvidence()).isEqualTo("手机：+86 138-1234-5678");
        assertThat(result.getHighestEducation()).isEqualTo("本科");
        assertThat(result.getAwards()).contains("校级奖学金");
        assertThat(result.getSummary()).isEqualTo("具备后端工程实践经验");
        assertThat(result.getSections()).extracting("sectionType")
                .contains("EDUCATION", "SKILLS", "PROJECTS", "INTERNSHIPS", "AWARDS", "SUMMARY");
        assertThat(result.getRawText()).contains("张三");
    }

    @Test
    void parseShouldExposeRawSectionsAndStructuredData() {
        String rawText = """
                四叶草
                在校经历 Experience
                2020.05参加网络利弊辩论赛，获得最佳辩论手
                专业技能
                Java SpringBoot MySQL Redis
                项目经历
                项目名称：校园招聘系统
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getRawSections()).isNotEmpty();
        assertThat(result.getRawSections()).anySatisfy(section -> {
            assertThat(section.getOriginalTitle()).isEqualTo("在校经历 Experience");
            assertThat(section.getNormalizedSection()).isEqualTo("CAMPUS");
        });
        assertThat(result.getStructuredData()).isNotNull();
        assertThat(result.getStructuredData().getSkills().getKeywords())
                .contains("Java", "Spring Boot", "MySQL", "Redis");
        assertThat(result.getStructuredData().getExperiences())
                .anySatisfy(experience -> {
                    assertThat(experience.getType()).isEqualTo("CAMPUS");
                    assertThat(experience.getDescription()).contains("获得最佳辩论手");
                });
        assertThat(result.getStructuredData().getAchievements())
                .anySatisfy(achievement -> assertThat(achievement.getTitle()).contains("获得最佳辩论手"));
        assertThat(result.getStructuredData().getProjects())
                .anySatisfy(project -> assertThat(project.getName()).contains("校园招聘系统"));
        assertThat(result.getSkills()).containsExactlyElementsOf(result.getStructuredData().getSkills().getKeywords());
    }

    @Test
    void parseShouldKeepSummaryTextAndExtractSkillTagsFromEvidence() {
        String rawText = """
                张三
                自我评价
                熟悉SpringBoot、Spring、MyBatis，有较强的学习能力和沟通能力
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getSummary()).contains("学习能力");
        assertThat(result.getStructuredData().getSummary()).contains("学习能力");
        assertThat(result.getStructuredData().getSkills().getKeywords())
                .contains("Spring Boot", "Spring", "MyBatis");
        assertThat(result.getSkills()).doesNotContain("熟悉SpringBoot、Spring、MyBatis，有较强的学习能力和沟通能力");
    }

    @Test
    void parseShouldAllowMissingOptionalFields() {
        ResumeStructuredContentDTO result = service.parse("专业技能\nJava");

        assertThat(result.getName()).isNull();
        assertThat(result.getPhone()).isNull();
        assertThat(result.getEmail()).isNull();
        assertThat(result.getSkills()).contains("Java");
    }

    @Test
    void parseShouldPreferSectionBoundaries() {
        String rawText = """
                张三
                专业技能：Java，Spring Boot，Redis
                项目经历
                AI 简历优化系统
                - 使用 Spring Boot 开发后端接口
                工作经历
                某科技公司 Java 后端实习生
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getSkills()).contains("Java", "Spring Boot", "Redis");
        assertThat(result.getProjects()).contains("AI 简历优化系统", "使用 Spring Boot 开发后端接口");
        assertThat(result.getWorkExperiences()).contains("某科技公司 Java 后端实习生");
        assertThat(result.getProjects()).doesNotContain("某科技公司 Java 后端实习生");
        assertThat(result.getOthers()).doesNotContain("AI 简历优化系统", "某科技公司 Java 后端实习生");
    }

    @Test
    void parseShouldSplitProjectSectionIntoProjectEntities() {
        String rawText = """
                张三
                项目经历
                项目一：
                开发环境：tomcat7、Maven、JDK1.8
                项目名称：蓝天健康中心管理系统
                项目描述：该管理系统支持线上预约、套餐项目管理和手机号快速登录。
                负责模块：
                负责编写后台的套餐管理模块；
                后台的预约管理模块；
                实现手机号快速登录模块；
                编写对应的 API 文档，整理其他的开发文档
                技术选型：Spring、SpringMVC、MyBatis、Spring Security、MySQL
                项目二：
                项目名称：公司后台管理系统
                项目描述：该管理系统可以对员工职务、产品库存和售后进行管理。
                负责开发员工管理模块，员工的职务管理模块
                技术选型：Spring Boot、MyBatis、MySQL
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getStructuredData().getProjects()).hasSize(2);
        assertThat(result.getStructuredData().getProjects().get(0).getName()).isEqualTo("蓝天健康中心管理系统");
        assertThat(result.getStructuredData().getProjects().get(0).getDescription())
                .contains("线上预约")
                .doesNotContain("项目描述");
        assertThat(result.getStructuredData().getProjects().get(0).getTechStack())
                .contains("Tomcat", "Maven", "JDK", "Spring", "Spring MVC", "MyBatis", "Spring Security", "MySQL");
        assertThat(result.getStructuredData().getProjects().get(0).getResponsibilities())
                .contains("负责编写后台的套餐管理模块", "实现手机号快速登录模块");
        assertThat(result.getStructuredData().getProjects().get(0).getEvidence())
                .contains("蓝天健康中心管理系统")
                .doesNotContain("项目一：");
        assertThat(result.getStructuredData().getProjects().get(1).getName()).isEqualTo("公司后台管理系统");
        assertThat(result.getStructuredData().getProjects().get(1).getTechStack())
                .contains("Spring Boot", "MyBatis", "MySQL");
    }

    @Test
    void projectSourceTextExtractorShouldOnlySplitByStrongProjectSeparators() {
        List<String> lines = List.of(
                "项目一：",
                "开发环境：tomcat7、Maven、JDK1.8",
                "项目名称：蓝天健康中心管理系统",
                "项目描述：该管理系统支持线上预约、套餐项目管理和手机号快速登录。",
                "负责模块：",
                "负责编写后台的套餐管理模块；",
                "实现手机号快速登录模块；",
                "技术选型：Spring、SpringMVC、MyBatis、Spring Security、MySQL",
                "项目二：",
                "项目名称：公司后台管理系统",
                "项目描述：该管理系统可以对员工职务、产品库存和售后进行管理。",
                "负责开发员工管理模块，员工的职务管理模块",
                "技术选型：Spring Boot、MyBatis、MySQL");
        List<ResumeRawSectionBlockDTO> blocks = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            blocks.add(ResumeRawSectionBlockDTO.builder()
                    .index(index)
                    .originalIndex(index)
                    .displayOrder(index)
                    .text(lines.get(index))
                    .build());
        }
        ResumeRawSectionDTO rawSection = ResumeRawSectionDTO.builder()
                .id("section-projects")
                .normalizedSection("PROJECTS")
                .blocks(blocks)
                .build();

        var projects = ProjectSourceTextExtractor.extractFromRawSections(List.of(rawSection));

        assertThat(projects).hasSize(2);
        assertThat(projects).extracting("name")
                .containsExactly("蓝天健康中心管理系统", "公司后台管理系统");
    }

    @Test
    void projectSourceTextExtractorShouldNotSplitDescriptionsOrResponsibilitiesAsProjects() {
        List<String> lines = List.of(
                "比丘商城后台管理系统",
                "开发环境",
                "IDEA+maven+Tomcat+mysql",
                "软件构架",
                "freemarker+spring+zookeeper+dubbo+spring boot+Mybatis+druid+",
                "项目描述",
                "比丘商城后台管理系统是一个综合性的商城系统",
                "做系统缓存，提高了系统性能。系统中还包括一些模块：",
                "责任描述：",
                "参与项目分析和项目构建，项目构建使用Maven，分布式服务框架使用Dubbo。",
                "参与后台商品信息，CMS内容管理模块的开发。",
                "火萤商城",
                "火萤商城是一个能够在浏览器和手机微信小程序都能进行访问的商城系统。",
                "参与了后台管理系统的权限开发；",
                "参与了小商城功能的地址保存、收藏、足迹的开发；");

        var projects = ProjectSourceTextExtractor.extractFromLines(lines, "section-projects");

        assertThat(projects).hasSize(1);
        assertThat(projects.get(0).getName()).isEqualTo("比丘商城后台管理系统");
        assertThat(projects).extracting("name")
                .doesNotContain("做系统缓存", "是一个提供在线学习的平台", "对系统的简单的代码进行封装", "项目经历 5", "项目经历 9");
    }

    @Test
    void parseShouldSupportExperiencedResumeSections() {
        String rawText = """
                李四
                13900001111
                lisi@example.com
                求职意向：高级 Java 后端工程师
                核心能力
                Java，Spring Boot，Redis，RabbitMQ，Kubernetes
                职业经历
                某科技公司 Java 后端工程师
                负责订单系统和支付系统重构
                项目实践
                交易中台建设
                证书
                软件设计师
                About me
                具备 5 年后端系统设计和交付经验
                其他说明
                可接受远程协作
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getName()).isEqualTo("李四");
        assertThat(result.getPhone()).isEqualTo("13900001111");
        assertThat(result.getEmail()).isEqualTo("lisi@example.com");
        assertThat(result.getJobIntention()).isEqualTo("高级 Java 后端工程师");
        assertThat(result.getResumeType()).isEqualTo("EXPERIENCED");
        assertThat(result.getSkills()).contains("Java", "Spring Boot", "Redis", "RabbitMQ", "Kubernetes");
        assertThat(result.getSkills()).doesNotContain("负责订单系统和支付系统重构");
        assertThat(result.getWorkExperiences()).contains("某科技公司 Java 后端工程师", "负责订单系统和支付系统重构");
        assertThat(result.getProjects()).contains("交易中台建设");
        assertThat(result.getCertificates()).contains("软件设计师");
        assertThat(result.getSummary()).isEqualTo("具备 5 年后端系统设计和交付经验");
        assertThat(result.getOthers()).contains("可接受远程协作");
    }

    @Test
    void parseShouldExtractGlobalBasicInfoAndStudentType() {
        String rawText = """
                王小明
                男 23岁 上海
                电话：13800000001
                邮箱：wang@example.com
                目标岗位：Java 后端开发工程师
                教育背景
                示例大学 软件工程 本科
                技术栈
                Java / Spring Boot / MySQL / Redis
                校园经历
                负责学院技术社团活动组织
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getName()).isEqualTo("王小明");
        assertThat(result.getBasicInfo())
                .containsEntry("gender", "男")
                .containsEntry("age", "23")
                .containsEntry("degree", "本科")
                .containsEntry("jobIntention", "Java 后端开发工程师")
                .containsEntry("resumeType", "STUDENT");
        assertThat(result.getResumeType()).isEqualTo("STUDENT");
        assertThat(result.getCampusExperiences()).contains("负责学院技术社团活动组织");
    }

    @Test
    void parseShouldExtractBasicInfoFromTail() {
        String rawText = """
                Personal Resume
                专业技能
                Java Spring Boot MySQL Redis
                项目经历
                校园招聘系统，负责后端接口开发
                姓名：四叶草
                电话：15000032333
                邮箱：Docer@qq.com
                学历：大学本科
                求职意向：JAVA实习生
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getName()).isEqualTo("四叶草");
        assertThat(result.getPhone()).isEqualTo("15000032333");
        assertThat(result.getEmail()).isEqualTo("Docer@qq.com");
        assertThat(result.getHighestEducation()).isEqualTo("本科");
        assertThat(result.getJobIntention()).isEqualTo("JAVA实习生");
        assertThat(result.getBasicInfo())
                .containsEntry("name", "四叶草")
                .containsEntry("phone", "15000032333")
                .containsEntry("email", "Docer@qq.com")
                .containsEntry("degree", "本科")
                .containsEntry("jobIntention", "JAVA实习生");
        assertThat(result.getOthers()).doesNotContain("Personal Resume", "姓名：四叶草", "电话：15000032333");
    }

    @Test
    void parseShouldNotUseTemplateTitleAsName() {
        String rawText = """
                Personal Resume
                教育背景 Education
                示例大学 软件工程 本科
                专业技能
                Java Spring Boot
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getName()).isNull();
        assertThat(result.getQualityWarnings()).contains("NAME_MISSING");
    }

    @Test
    void parseShouldRejectLowConfidenceNameCandidates() {
        String rawText = """
                个人简历
                本人
                参加项目描述
                手机：+86 138-1234-5678
                邮箱：zhangsan@example.com
                学历：大学本科
                期望岗位：Java 后端开发工程师
                专业技能
                Java Spring Boot
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getName()).isNull();
        assertThat(result.getPhone()).isEqualTo("13812345678");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(result.getHighestEducation()).isEqualTo("本科");
        assertThat(result.getJobIntention()).isEqualTo("Java 后端开发工程师");
        assertThat(result.getQualityWarnings()).contains("NAME_MISSING");
        assertThat(result.getBasicInfoDebug().get("name").getStatus()).isEqualTo("REJECTED");
        assertThat(result.getBasicInfoDebug().get("name").getEvidence()).isEqualTo("个人简历");
        assertThat(result.getBasicInfoDebug().get("name").getRejectReason()).isNotBlank();
        assertThat(result.getBasicInfoDebug().get("phone").getEvidence()).isEqualTo("手机：+86 138-1234-5678");
        assertThat(result.getBasicInfoDebug().get("email").getEvidence()).isEqualTo("邮箱：zhangsan@example.com");
        assertThat(result.getBasicInfoDebug().get("degree").getEvidence()).isEqualTo("学历：大学本科");
        assertThat(result.getBasicInfoDebug().get("jobIntention").getEvidence()).isEqualTo("期望岗位：Java 后端开发工程师");
    }

    @Test
    void parseShouldKeepOnlyTechnicalTermsInSkills() {
        String rawText = """
                赵六
                专业技能
                Java、Spring Boot、MySQL、Redis
                学习能力强，工作认真，负责客户需求分析
                项目经历
                订单系统
                主要工作和业绩：完成报表模块、页面管理和接口开发
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getSkills()).contains("Java", "Spring Boot", "MySQL", "Redis");
        assertThat(result.getSkills()).doesNotContain("学习能力强", "主要工作和业绩", "完成报表模块");
    }

    @Test
    void parseShouldNormalizeCompatibilityHanAndExtractCompactBasicInfo() {
        String rawText = """
                个⼈信息
                王路/男/26
                学历：本科
                期望职位：JavaEE开发⼯程师
                Tel（微信）：16638800828
                Email：luke56@aliyun.com
                个⼈技能
                熟练掌握JavaSE及⾼级知识
                项⽬名称：郑州⾦融财讯综合管理系统
                技术栈：SpringMVC，MyBatis，Apache POI，Bootstrap，jQuery，Ajax等
                ①完成对个⼈⼯作管理模块开发
                教育经历
                2013年9⽉-2017年7⽉ ⻩淮学院 电⼦信息⼯程
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getName()).isEqualTo("王路");
        assertThat(result.getBasicInfo())
                .containsEntry("gender", "男")
                .containsEntry("age", "26")
                .containsEntry("degree", "本科")
                .containsEntry("jobIntention", "JavaEE开发工程师");
        assertThat(result.getPhone()).isEqualTo("16638800828");
        assertThat(result.getEmail()).isEqualTo("luke56@aliyun.com");
        assertThat(result.getProjects())
                .contains(
                        "郑州金融财讯综合管理系统",
                        "技术栈：SpringMVC，MyBatis，Apache POI，Bootstrap，jQuery，Ajax等",
                        "完成对个人工作管理模块开发");
        assertThat(result.getEducation()).contains("2013年9月-2017年7月 黄淮学院 电子信息工程");
        assertThat(result.getSections()).extracting("sectionType")
                .contains("BASIC_INFO", "SKILLS", "PROJECTS", "EDUCATION");
    }

    @Test
    void parseShouldRejectSectionLikeNameAndPreferRealNameFromTail() {
        String rawText = """
                个人技能
                具备扎实的Java语言基础
                在校经历
                2020.04参加团学，担任新闻部编辑组组长；
                组织
                同学们
                Personal Resume
                邮箱：Docer@qq.com
                求职意向：JAVA实习生
                四叶草
                学历：大学本科
                电话：15000032333
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getName()).isEqualTo("四叶草");
        assertThat(result.getResumeType()).isEqualTo("INTERN");
        assertThat(result.getBasicInfo())
                .containsEntry("name", "四叶草")
                .containsEntry("resumeType", "INTERN");
    }

    @Test
    void parseShouldHandleMisorderedStudentResumeWithoutFailing() {
        String rawText = """
                具备扎实的Java语言基础，熟悉nio、多线程、集合等基础框架；
                精通web前端开发技术，包括HTML、CSS、JavaScript、VUE、Ajax、Jquery、React等内容，熟悉HTML5；
                个人技能 Technique
                自我评价 About me
                本人
                熟悉JAVA主流开发平台，熟练框架：SpringBoot,Spring,Mybatis；了解应用Vue前端框架；熟悉sql、Mysql数据库；
                在校经历 Experience
                20xx/09--20xx/07
                重庆理工大学
                专业：JAVA开发
                主修课程：高级计算机系统结构、计算机网络、数据库、操作系统、程序设计、JAVA语言、JAVA内存模型、C++语言、软件工程。
                教育背景 Education
                2020.05参加网络利弊辩论赛，获得最佳辩论手；
                偶尔协助学校开发一些小程序；
                Personal Resume
                邮箱：
                Docer@qq.com
                求职意向：JAVA实习生
                四叶草
                学历：大学本科
                电话：15000032333
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getName()).isEqualTo("四叶草");
        assertThat(result.getPhone()).isEqualTo("15000032333");
        assertThat(result.getEmail()).isEqualTo("Docer@qq.com");
        assertThat(result.getSkills()).contains("Java", "Spring Boot", "MySQL", "Vue");
        assertThat(result.getEducation()).isNotEmpty();
        assertThat(result.getStructuredData()).isNotNull();
    }

    @Test
    void parseShouldExtractSchoolAndLabeledWorkYearsWithoutUsingBirthOrEducationYear() {
        String rawText = """
                个人简历
                基本情况
                姓 名 段焯峰 性 别 男 出生年月 1995-3-1
                民 族 汉 工作年限 2 担任职务 Java 后台开发
                教育经历
                2014-2017.6 河南经贸职业学院 计算机网络技术 大专
                工作经历
                2017 年 3 月至 2019 年 12 月 郑州易德鑫电子技术有限公司 Java 开发工程师
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getName()).isEqualTo("段焯峰");
        assertThat(result.getBasicInfo())
                .containsEntry("name", "段焯峰")
                .containsEntry("gender", "男")
                .containsEntry("school", "河南经贸职业学院")
                .containsEntry("workYears", "2年");
        assertThat(result.getBasicInfo().get("workYears")).isNotEqualTo("17年");
        assertThat(result.getBasicInfo()).doesNotContainEntry("age", "1");
    }

    @Test
    void parseShouldExtractInlineNameAgeAndTargetFunction() {
        String rawText = """
                基本资料
                姓名：西施 性别：女
                邮箱：xianyulx@126.com 年龄：24
                院校：郑州轻工业学院 学历：本科
                求职意向
                工作性质：全职 目标地点：郑州
                专业技能
                目标职能：JAVA软件工程师 目标薪资：面议
                能够熟练使用 SpringBoot，SpringData，Eureka，Nginx
                教育经历
                2014.09 – 2018.06 郑州轻工业学院 信息工程 本科
                工作经历
                公司名称：北京华来知识科技有限公司
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getName()).isEqualTo("西施");
        assertThat(result.getBasicInfo())
                .containsEntry("gender", "女")
                .containsEntry("age", "24")
                .containsEntry("school", "郑州轻工业学院")
                .containsEntry("jobIntention", "JAVA软件工程师");
        assertThat(result.getSkills()).contains("Spring Boot", "Spring Data", "Eureka", "Nginx");
    }

    @Test
    void parseShouldExtractIconFontTwoColumnHeaderSample() {
        String rawText = """
                徐亦轲 Python
                 690153729@qq.com C++
                 (+86) 19705440329 C
                 - Verilog
                 github.com/DoontRain Linux
                教育背景
                西南交通大学,成都 2023 –至今
                在读本科人工智能,预计 2027年 6月毕业
                实习/项目经历
                SRTP(大学生创新训练项目) 2025年 4月 – 2026年 3月
                项目组组长 导师: 朱宗海
                基于 yolo算法改进无人机视觉的电网故障检测
                使用 YOLO算法对无人机拍摄的图像进行目标检测，并识别出图像中的故障点
                使用 OpenCV对图像进行预处理，包括图像增强、图像去噪、图像分割等
                借鉴DETR算法的思想对YOLO算法进行改进，使用 Transformer对YOLO算法进行改进
                使用MATLAB对数据进行分析，并使用 Python对数据进行可视化
                 IT技能
                编程语言: C++ > C > Python
                开发框架: Opencv, Yolo, Transformer
                工具平台: Linux, Git, Docker
                机器学习: Pytorch, Tensorflow, Scikit-learn, Pandas
                获奖情况
                省二等奖,蓝桥杯全国软件和信息技术专业人才大赛 A组 2025年 4月
                省三等奖,挑战者杯全国大学生创业大赛 2024年 7月-2024年 9月
                前 20%, CCF计算机软件能力认证 2023年 9月
                其他信息
                GitHub: https://github.com/DoontRain
                语言能力: 英语 -良好 (CET-6: 509分，能查阅英文文献及进行技术交流)
                学分绩点: 3.72/4.0大一学年智育成绩排名专业第 7
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getBasicInfo())
                .containsEntry("name", "徐亦轲")
                .containsEntry("email", "690153729@qq.com")
                .containsEntry("phone", "(+86) 19705440329")
                .containsEntry("github", "github.com/DoontRain")
                .containsEntry("university", "西南交通大学")
                .containsEntry("degree", "本科")
                .containsEntry("major", "人工智能")
                .containsEntry("graduationDate", "2027年6月")
                .containsEntry("gpa", "3.72/4.0");
        assertThat(result.getStructuredData().getSkills().getKeywords())
                .contains("Python", "C++", "C", "Verilog", "Linux", "OpenCV", "YOLO", "DETR",
                        "Transformer", "MATLAB", "Git", "Docker", "PyTorch", "TensorFlow", "Scikit-learn", "Pandas");
        assertThat(result.getStructuredData().getSkills().getGroups().get("cv"))
                .contains("OpenCV", "YOLO", "DETR");
        assertThat(result.getStructuredData().getProjects())
                .anySatisfy(project -> {
                    assertThat(project.getName()).contains("SRTP");
                    assertThat(project.getRole()).isEqualTo("项目组组长");
                    assertThat(project.getMentor()).isEqualTo("朱宗海");
                    assertThat(project.getTechStack()).contains("YOLO", "OpenCV", "DETR", "Transformer", "MATLAB", "Python");
                });
        assertThat(result.getStructuredData().getAchievements())
                .anySatisfy(achievement -> assertThat(achievement.getTitle()).contains("省二等奖", "蓝桥杯"))
                .anySatisfy(achievement -> assertThat(achievement.getTitle()).contains("前 20%", "CCF"));
        assertThat(result.getOthers()).doesNotContain("GitHub: https://github.com/DoontRain");
    }

    @Test
    void parseShouldRejectBlankText() {
        assertThatThrownBy(() -> service.parse(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历原始文本不能为空");
    }
}
