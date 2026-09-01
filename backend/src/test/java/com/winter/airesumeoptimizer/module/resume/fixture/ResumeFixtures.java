package com.winter.airesumeoptimizer.module.resume.fixture;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Slice A Gate fixture 目录：合成但真实版式的中文简历。
 * 文件提交在 src/test/resources/resumes/ 下；缺失时按确定性内容重新生成，
 * 保证任何环境都能复现同一批测试材料。
 */
public final class ResumeFixtures {

    public static final String STANDARD_PDF = "resumes/chinese-java-standard.pdf";
    public static final String STANDARD_DOCX = "resumes/chinese-java-standard.docx";
    public static final String TWO_PAGE_PDF = "resumes/chinese-java-two-page.pdf";
    public static final String MIXED_PDF = "resumes/mixed-backend-platform.pdf";
    public static final String AMBIGUOUS_PDF = "resumes/chinese-java-ambiguous.pdf";

    private ResumeFixtures() {
    }

    /** 标准中文简历（Audit 风格）：头部混合联系行、多段经历、多项目、技能组。 */
    public static List<String> standardLines() {
        return List.of(
                "李明",
                "Java 后端工程师",
                "上海 · 138-1234-5678 · liming.dev@example.com · github.com/liming-dev",
                "个人概述",
                "4 年 Java 后端开发经验，参与电商订单与营销系统建设，熟悉 Spring Boot、MySQL、Redis 和 Kafka。重视系统稳定",
                "性、可观测性与可维护性，有高并发活动保障和线上故障排查经验。",
                "教育经历",
                "2016.09 - 2020.06 华东理工大学 软件工程 本科",
                "工作经历",
                "上海云启科技有限公司 2022.07 - 至今 Java 后端工程师",
                "负责订单中台核心服务开发，日均处理订单 120 万笔",
                "主导缓存与数据库优化，接口 P99 延迟下降 45%",
                "杭州数澜信息技术有限公司 2020.07 - 2022.06 后端开发工程师",
                "负责数据同步服务开发，支撑 30 余个下游业务系统",
                "项目经历",
                "订单中台重构 2023.01 - 2023.09 核心开发",
                "设计统一订单状态机，落地分布式事务方案",
                "技术栈：Java、Spring Boot、MySQL、Redis、Kafka",
                "实时风控平台 2021.03 - 2021.12 后端开发",
                "基于规则引擎实现毫秒级风险识别与拦截",
                "技能",
                "编程语言：Java、Python",
                "框架：Spring Boot、Spring Cloud、MyBatis",
                "数据库：MySQL、Redis",
                "证书",
                "CET-6、软件设计师",
                "AWS Certified Developer",
                "Java 后端开发认证");
    }

    /** 两页简历：内容充足，第二页承载完整章节，不属于孤立末页。 */
    public static List<String> twoPageLines() {
        List<String> lines = new ArrayList<>(standardLines());
        lines.add("实习经历");
        lines.add("上海某金融科技公司 2019.06 - 2019.12 后端开发实习生");
        for (int index = 1; index <= 26; index++) {
            lines.add("参与支付清结算模块开发，完成第 " + index + " 项对账与差错处理功能改造");
        }
        return lines;
    }

    /** 英文/中文混排简历：用于解析、字体、长英文换行与最终 PDF dogfooding。 */
    public static List<String> mixedLines() {
        return List.of(
                "李明",
                "Backend Platform Engineer / 后端工程师",
                "上海 · 138-1234-5678 · liming.dev@example.com · github.com/liming-dev",
                "Summary",
                "Backend platform engineer focused on reliable services, observability, and practical delivery.",
                "Education",
                "2016.09 - 2020.06 华东理工大学 Software Engineering 本科",
                "Experience",
                "上海云启科技有限公司 2022.07 - Present Backend Platform Engineer / 后端工程师",
                "Designed Spring Boot services for an order platform handling 1.2M requests per day.",
                "Improved PostgreSQL query plans and Redis caching, reducing p99 latency by 45%.",
                "杭州数澜信息技术有限公司 2020.07 - 2022.06 Backend Engineer / 后端开发工程师",
                "Built data synchronization services used by more than 30 downstream systems.",
                "Projects",
                "项目一：订单结算平台",
                "开发时间：2023.01 - 2023.09",
                "技术负责人：Built a recoverable settlement workflow with Kafka and PostgreSQL.",
                "项目二：实时风控平台",
                "开发时间：2021.03 - 2021.12",
                "后端开发：Implemented millisecond-level rule-based risk blocking.",
                "Skills",
                "编程语言：Java, Python",
                "框架：Spring Boot, Spring Cloud",
                "数据与消息：PostgreSQL, Redis, Kafka",
                "证书",
                "AWS Certified Developer",
                "软件设计师");
    }

    /** 歧义简历：联系方式残缺、经历缺少组织与日期边界，必须进入确认流程。 */
    public static List<String> ambiguousLines() {
        return List.of(
                "王芳",
                "电话：138-8888 邮箱：wangfang#mail 微信：wxid_unknown",
                "个人情况",
                "做过一些项目，主要负责后端相关开发工作",
                "2019 到 2021 在某家公司从事开发工作",
                "其他说明",
                "获得过若干奖励，具体内容详见原始证明材料");
    }

    /** 缺失的文件按确定性内容重新生成；已提交的文件保持字节不变。 */
    public static void ensureFiles(Path resourcesDir) throws IOException {
        ensure(resourcesDir.resolve(STANDARD_PDF), ResumeFixtureFactory.renderPdf(standardLines()));
        ensure(resourcesDir.resolve(STANDARD_DOCX), ResumeFixtureFactory.renderDocx(standardLines()));
        ensure(resourcesDir.resolve(TWO_PAGE_PDF), ResumeFixtureFactory.renderPdf(twoPageLines()));
        ensure(resourcesDir.resolve(MIXED_PDF), ResumeFixtureFactory.renderPdf(mixedLines()));
        ensure(resourcesDir.resolve(AMBIGUOUS_PDF), ResumeFixtureFactory.renderPdf(ambiguousLines()));
    }

    private static void ensure(Path path, byte[] content) throws IOException {
        if (Files.exists(path)) {
            return;
        }
        Files.createDirectories(path.getParent());
        Files.write(path, content);
    }

    public static byte[] read(String resourcePath) throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IOException("fixture 不存在：" + resourcePath);
            }
            return input.readAllBytes();
        }
    }
}
