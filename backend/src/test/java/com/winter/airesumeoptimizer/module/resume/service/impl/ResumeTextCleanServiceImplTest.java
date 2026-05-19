package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResumeTextCleanServiceImplTest {

    private final ResumeTextCleanServiceImpl service = new ResumeTextCleanServiceImpl();

    @Test
    void cleanAndSplitSectionsShouldNormalizeSpacesAndBullets() {
        String text = """
                张三

                专业技能：   Java    Spring Boot
                •   Redis
                项目经历
                ● AI 简历优化系统
                第 1 页
                """;

        var result = service.cleanAndSplitSections(text);

        assertThat(result.getCleanedText()).contains("专业技能： Java Spring Boot");
        assertThat(result.getCleanedText()).contains("Redis");
        assertThat(result.getCleanedText()).contains("AI 简历优化系统");
        assertThat(result.getCleanedText()).doesNotContain("第 1 页");
    }

    @Test
    void cleanAndSplitSectionsShouldDeduplicateNormalizedLines() {
        String text = """
                专业技能
                Java Spring Boot Redis
                • Java   Spring Boot Redis
                """;

        var result = service.cleanAndSplitSections(text);

        assertThat(result.getDuplicateLineCount()).isEqualTo(1);
        assertThat(result.getSections().get(0).getLines()).containsExactly("Java Spring Boot Redis");
    }

    @Test
    void cleanAndSplitSectionsShouldRecognizeCommonSections() {
        String text = """
                Profile
                Java 后端开发
                Education
                西南交通大学 软件工程 本科
                Work Experience
                某科技公司 Java 后端实习生
                Awards
                校级奖学金
                """;

        var result = service.cleanAndSplitSections(text);

        assertThat(result.getSections()).extracting("sectionType")
                .contains("BASIC_INFO", "EDUCATION", "WORK_EXPERIENCES", "AWARDS");
        assertThat(result.getSections()).extracting("sourceSectionConfidence")
                .containsOnly("HIGH");
    }

    @Test
    void cleanAndSplitSectionsShouldRecognizeExperiencedResumeAliases() {
        String text = """
                核心能力
                Java Spring Boot Redis
                职业经历
                某科技公司 Java 后端工程师
                证书
                软件设计师
                About me
                五年后端开发经验
                """;

        var result = service.cleanAndSplitSections(text);

        assertThat(result.getSections()).extracting("sectionType")
                .contains("SKILLS", "WORK_EXPERIENCES", "CERTIFICATES", "SUMMARY");
    }

    @Test
    void cleanAndSplitSectionsShouldNotTreatSkillSentenceAsHeading() {
        String text = """
                专业技能
                熟悉 Spring Boot、SpringMVC、MyBatis 的设计思想及实现过程
                工作经历
                某科技公司 Java 后端工程师
                """;

        var result = service.cleanAndSplitSections(text);

        assertThat(result.getSections()).filteredOn(section -> "SKILLS".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .contains("熟悉 Spring Boot、SpringMVC、MyBatis 的设计思想及实现过程"));
    }

    @Test
    void cleanAndSplitSectionsShouldUseGeneralSectionWhenNoHeading() {
        var result = service.cleanAndSplitSections("张三\nJava Spring Boot Redis");

        assertThat(result.getSections()).hasSize(1);
        assertThat(result.getSections().get(0).getSectionType()).isEqualTo("GENERAL");
        assertThat(result.getSections().get(0).getSourceSectionConfidence()).isEqualTo("LOW");
        assertThat(result.getSections().get(0).getLines()).contains("张三", "Java Spring Boot Redis");
    }

    @Test
    void cleanAndSplitSectionsShouldAttachSkillLinesBeforeHeading() {
        String text = """
                四叶草
                15000032333
                具备扎实的 Java 语言基础，熟悉 nio、多线程、集合等基础框架
                精通 Spring、Struts2、mybatis 等框架
                个人技能 Technique
                """;

        var result = service.cleanAndSplitSections(text);

        assertThat(result.getSections()).filteredOn(section -> "GENERAL".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines()).containsExactly("四叶草", "15000032333"));
        assertThat(result.getSections()).filteredOn(section -> "SKILLS".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .containsExactly(
                                "具备扎实的 Java 语言基础，熟悉 nio、多线程、集合等基础框架",
                                "精通 Spring、Struts2、mybatis 等框架"));
    }

    @Test
    void cleanAndSplitSectionsShouldAttachSummaryCampusAndEducationLinesBeforeHeading() {
        String text = """
                本人学习能力强，责任心强，具备良好的团队沟通能力
                自我评价 About me
                组织校园技术分享活动，担任社团干事
                在校经历 Experience
                西南交通大学 软件工程 本科
                教育背景 Education
                """;

        var result = service.cleanAndSplitSections(text);

        assertThat(result.getSections()).filteredOn(section -> "SUMMARY".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .containsExactly("本人学习能力强，责任心强，具备良好的团队沟通能力"));
        assertThat(result.getSections()).filteredOn(section -> "SUMMARY".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getSourceSectionConfidence()).isEqualTo("MEDIUM"));
        assertThat(result.getSections()).filteredOn(section -> "CAMPUS_EXPERIENCES".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .containsExactly("组织校园技术分享活动，担任社团干事"));
        assertThat(result.getSections()).filteredOn(section -> "EDUCATION".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .containsExactly("西南交通大学 软件工程 本科"));
    }

    @Test
    void cleanAndSplitSectionsShouldFilterInvalidNumberingLinesAndKeepContent() {
        String text = """
                专业技能
                1、
                2.
                ①
                一、
                、
                1、具备扎实的 Java 语言基础
                ② 熟悉 Spring Boot 和 MySQL
                项目经历
                1）AI 简历优化系统
                - 2、负责解析模块开发
                校园经历
                三、组织校园技术分享活动
                """;

        var result = service.cleanAndSplitSections(text);

        assertThat(result.getCleanedText())
                .doesNotContain("1、")
                .doesNotContain("2.")
                .doesNotContain("①")
                .doesNotContain("一、");
        assertThat(result.getCleanedText())
                .contains("具备扎实的 Java 语言基础")
                .contains("熟悉 Spring Boot 和 MySQL")
                .contains("AI 简历优化系统")
                .contains("负责解析模块开发")
                .contains("组织校园技术分享活动");
        assertThat(result.getInvalidLineCount()).isEqualTo(5);
        assertThat(result.getSections()).filteredOn(section -> "SKILLS".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .contains("具备扎实的 Java 语言基础", "熟悉 Spring Boot 和 MySQL"));
        assertThat(result.getSections()).filteredOn(section -> "PROJECTS".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .contains("AI 简历优化系统", "负责解析模块开发"));
        assertThat(result.getSections()).filteredOn(section -> "CAMPUS_EXPERIENCES".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .contains("组织校园技术分享活动"));
    }

    @Test
    void cleanAndSplitSectionsShouldNormalizeCompatibilityHanAndKeepProjectTechStack() {
        String text = """
                个⼈信息
                王路/男/26
                个⼈技能
                熟练掌握 JavaSE
                项⽬名称：郑州⾦融财讯综合管理系统
                技术栈：SpringMVC，MyBatis，Bootstrap
                ①完成个⼈⼯作管理模块开发
                教育经历
                2013年9⽉-2017年7⽉ ⻩淮学院 电⼦信息⼯程
                """;

        var result = service.cleanAndSplitSections(text);

        assertThat(result.getCleanedText())
                .contains("个人信息")
                .contains("个人技能")
                .contains("项目名称：郑州金融财讯综合管理系统")
                .contains("2013年9月-2017年7月 黄淮学院 电子信息工程");
        assertThat(result.getSections()).extracting("sectionType")
                .contains("BASIC_INFO", "SKILLS", "PROJECTS", "EDUCATION");
        assertThat(result.getSections()).filteredOn(section -> "PROJECTS".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .containsExactly(
                                "郑州金融财讯综合管理系统",
                                "技术栈：SpringMVC，MyBatis，Bootstrap",
                                "完成个人工作管理模块开发"));
    }

    @Test
    void cleanAndSplitSectionsShouldRepairReversedTemplateForJavaInternResume() {
        String text = """
                具备扎实的Java语言基础，熟悉nio、多线程、集合等基础框架；
                精通web前端开发技术，包括HTML、CSS、JavaScript、VUE、Ajax、Jquery、React等内容，熟悉HTML5；
                精通Spring、Struts2、mybatis等框架；
                精通MySql数据库平台；
                具有良好的编程技巧和文档编写能力
                个人技能 Technique
                自我评价 About me
                本人
                熟悉JAVA主流开发平台，熟练框架：SpringBoot,Spring,Mybatis；了解应用Vue前端框架；熟悉sql、Mysql数据库；熟悉主流Web应用服务器，如Tomcat、Nginx
                熟练使用IDEA、Eclipse、 SVN、Tomcat等开发工具
                有很强的抗压能力，熟练操作电脑高效率完成自己的工作进度
                在校经历 Experience
                20xx/09--20xx/07
                重庆理工大学
                专业：JAVA开发
                主修课程：高级计算机系统结构、计算机网络、数据库、操作系统、程序设计、JAVA语言、JAVA内存模型、C++语言、软件工程、漏洞分析与发现技术。
                教育背景 Education
                2020.05参加网络利弊辩论赛，获得最佳辩论手；
                2020.04参加团学，担任新闻部编辑组组长；
                组织
                同学们
                Personal Resume
                邮箱：
                Docer@qq.com
                求职意向：JAVA实习生
                四叶草
                学历：大学本科
                电话：15000032333
                """;

        var result = service.cleanAndSplitSections(text);

        assertThat(result.getSections()).extracting("sectionType")
                .contains("SKILLS", "EDUCATION", "CAMPUS_EXPERIENCES", "BASIC_INFO");
        assertThat(result.getSections()).filteredOn(section -> "SKILLS".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .contains(
                                "具备扎实的Java语言基础，熟悉nio、多线程、集合等基础框架；",
                                "精通web前端开发技术，包括HTML、CSS、JavaScript、VUE、Ajax、Jquery、React等内容，熟悉HTML5；",
                                "熟悉JAVA主流开发平台，熟练框架：SpringBoot,Spring,Mybatis；了解应用Vue前端框架；熟悉sql、Mysql数据库；熟悉主流Web应用服务器，如Tomcat、Nginx")
                        .doesNotContain("本人"));
        assertThat(result.getSections()).filteredOn(section -> "EDUCATION".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .contains("重庆理工大学", "专业：JAVA开发"));
        assertThat(result.getSections()).filteredOn(section -> "BASIC_INFO".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .contains("Docer@qq.com", "求职意向：JAVA实习生", "学历：大学本科", "电话：15000032333")
                        .doesNotContain("Personal Resume", "邮箱："));
    }

    @Test
    void cleanAndSplitSectionsShouldSplitSkillsAndProjectsFromExperienceTemplate() {
        String text = """
                个人简历
                姓 名 段焯峰 性 别 男 出生年月 1995-3-1
                民 族 汉 工作年限 2 担任职务 Java 后台开发
                联系电话 13083665971 邮箱 duanzhuofeng123@163.com
                教育经历
                时 间 学 校 专 业 学 历
                2014-2017.6 河南经贸职业学院 计算机网络技术 大专
                技术能力描述
                熟悉 Java 语言开发，具有良好的编码习惯；
                掌握并实践过 Spring、Spring MVC、Mybatis、SpringBoot 等主流框架工具；
                熟悉 MySQL 数据库，对 sql 的优化有一定的了解；
                工作经验
                2017 年 3 月至 2019 年 12 月 郑州易德鑫电子技术有限公司 Java 开发工程师
                参加项目描述
                项目一：
                开发环境：tomcat7、Maven、JDK1.8
                开发工具：IntelliJ IDEA
                项目名称：蓝天健康中心管理系统
                项目描述：后台可以设置每天的预约人数
                """;

        var result = service.cleanAndSplitSections(text);

        assertThat(result.getSections()).filteredOn(section -> "EDUCATION".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .contains("2014-2017.6 河南经贸职业学院 计算机网络技术 大专")
                        .doesNotContain("技术能力描述", "熟悉 Java 语言开发，具有良好的编码习惯；"));
        assertThat(result.getSections()).filteredOn(section -> "SKILLS".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .contains(
                                "熟悉 Java 语言开发，具有良好的编码习惯；",
                                "掌握并实践过 Spring、Spring MVC、Mybatis、SpringBoot 等主流框架工具；",
                                "熟悉 MySQL 数据库，对 sql 的优化有一定的了解；"));
        assertThat(result.getSections()).filteredOn(section -> "WORK_EXPERIENCES".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .containsExactly("2017 年 3 月至 2019 年 12 月 郑州易德鑫电子技术有限公司 Java 开发工程师"));
        assertThat(result.getSections()).filteredOn(section -> "PROJECTS".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .contains("项目一：", "开发环境：tomcat7、Maven、JDK1.8", "蓝天健康中心管理系统"));
    }

    @Test
    void cleanAndSplitSectionsShouldNormalizeIconHeadingsAndSplitHeaderSkills() {
        String text = """
                徐亦轲 Python
                 690153729@qq.com C++
                 (+86) 19705440329 C
                 - Verilog
                 github.com/DoontRain Linux
                教育背景
                西南交通大学,成都 2023 –至今
                 IT技能
                编程语言: C++ > C > Python
                获奖情况
                省二等奖,蓝桥杯全国软件和信息技术专业人才大赛 A组 2025年 4月
                其他信息
                GitHub: https://github.com/DoontRain
                """;

        var result = service.cleanAndSplitSections(text);

        assertThat(result.getCleanedText()).doesNotContain("", "", "", "", "", "");
        assertThat(result.getSections()).filteredOn(section -> "GENERAL".equals(section.getSectionType()))
                .singleElement()
                .satisfies(section -> assertThat(section.getLines())
                        .contains("徐亦轲", "Python", "690153729@qq.com", "C++", "(+86) 19705440329", "C",
                                "Verilog", "GitHub: github.com/DoontRain", "Linux"));
        assertThat(result.getSections()).extracting("sectionType")
                .contains("EDUCATION", "SKILLS", "AWARDS", "OTHERS");
    }
}
