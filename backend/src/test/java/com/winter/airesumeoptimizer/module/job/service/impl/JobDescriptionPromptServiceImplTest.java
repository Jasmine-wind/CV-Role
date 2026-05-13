package com.winter.airesumeoptimizer.module.job.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionPromptDTO;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionPromptService;
import org.junit.jupiter.api.Test;

class JobDescriptionPromptServiceImplTest {

    private final JobDescriptionPromptServiceImpl service = new JobDescriptionPromptServiceImpl();

    @Test
    void buildPromptShouldUseVersionAndConstrainOutput() {
        JobDescriptionPromptDTO result = service.buildPrompt("招聘 Java 后端开发工程师，要求熟悉 Spring Boot，Redis 经验优先。");

        assertThat(result.getPromptVersion()).isEqualTo(JobDescriptionPromptService.PROMPT_VERSION);
        assertThat(result.getPrompt()).contains("job_description_parse_v1");
        assertThat(result.getPrompt()).contains("只能输出一个 JSON 对象");
        assertThat(result.getPrompt()).contains("jobTitle、requiredSkills、bonusSkills、experienceSignals、responsibilities、keywords、summary");
        assertThat(result.getPrompt()).contains("不得编造原文中不存在");
        assertThat(result.getPrompt()).contains("requiredSkills 只放原文明确要求");
        assertThat(result.getPrompt()).contains("bonusSkills 只放原文明确描述为加分");
        assertThat(result.getPrompt()).contains("如果岗位描述过短或信息不完整");
        assertThat(result.getPrompt()).contains("招聘 Java 后端开发工程师");
    }

    @Test
    void buildPromptShouldRejectBlankRawText() {
        assertThatThrownBy(() -> service.buildPrompt(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位描述原文不能为空");
    }

    @Test
    void buildPromptShouldTruncateLongRawText() {
        JobDescriptionPromptDTO result = service.buildPrompt("岗位职责".repeat(2100));

        assertThat(result.getPrompt()).contains("[内容过长，已截断]");
    }
}
