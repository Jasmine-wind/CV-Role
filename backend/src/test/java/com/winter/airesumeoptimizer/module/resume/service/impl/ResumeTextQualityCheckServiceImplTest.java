package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResumeTextQualityCheckServiceImplTest {

    private final ResumeTextQualityCheckServiceImpl service = new ResumeTextQualityCheckServiceImpl();

    @Test
    void checkShouldMarkBlankPdfAsScannedPdfFailure() {
        var result = service.check(" ", "PDF");

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getIssues()).contains("EMPTY_TEXT", "SCANNED_PDF");
        assertThat(result.getMessage()).contains("扫描版图片 PDF");
    }

    @Test
    void checkShouldWarnWhenTextTooShort() {
        var result = service.check("Java SQL 项目", "DOCX");

        assertThat(result.getStatus()).isEqualTo("WARNING");
        assertThat(result.getIssues()).contains("TOO_SHORT_TEXT");
        assertThat(result.getMessage()).contains("提取文本过短");
    }

    @Test
    void checkShouldMarkNormalTextAsGood() {
        var result = service.check("""
                教育经历
                示例理工大学 软件工程 2023.09 - 2027.06
                专业技能
                Java Spring Boot MyBatis-Plus PostgreSQL Redis Docker
                项目经历
                校园二手交易平台，负责用户登录、商品发布、搜索和收藏接口开发。
                使用 JWT 做登录态校验，使用 Redis 缓存首页热门商品列表。
                """, "PDF");

        assertThat(result.getStatus()).isEqualTo("GOOD");
        assertThat(result.getIssues()).isEmpty();
        assertThat(result.getMessage()).isEqualTo("文本质量正常");
    }
}
