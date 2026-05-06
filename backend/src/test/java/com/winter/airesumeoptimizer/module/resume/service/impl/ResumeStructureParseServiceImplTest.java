package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
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
                """;

        ResumeStructuredContentDTO result = service.parse(rawText);

        assertThat(result.getName()).isEqualTo("张三");
        assertThat(result.getPhone()).isEqualTo("13812345678");
        assertThat(result.getEmail()).isEqualTo("zhangsan@example.com");
        assertThat(result.getSkills()).contains("Java", "Spring Boot", "MyBatis", "Redis", "Docker", "Git");
        assertThat(result.getEducation()).contains("西南交通大学 软件工程 本科");
        assertThat(result.getProjects()).contains("AI 简历优化系统，负责后端接口开发");
        assertThat(result.getInternships()).contains("某科技公司 Java 后端实习生");
        assertThat(result.getRawText()).contains("张三");
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
    void parseShouldRejectBlankText() {
        assertThatThrownBy(() -> service.parse(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历原始文本不能为空");
    }
}
