package com.winter.airesumeoptimizer.module.analysis.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.ResumeAnalysisPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.service.ResumeAnalysisPromptService;
import org.junit.jupiter.api.Test;

class ResumeAnalysisPromptServiceImplTest {

    private final ResumeAnalysisPromptServiceImpl service = new ResumeAnalysisPromptServiceImpl();

    @Test
    void buildPromptShouldUseVersionAndConstrainOutput() {
        ResumeAnalysisPromptDTO result = service.buildPrompt(
                "张三\nJava Spring Boot 项目经历",
                "{\"skills\":[\"Java\",\"Spring Boot\"]}");

        assertThat(result.getPromptVersion()).isEqualTo(ResumeAnalysisPromptService.PROMPT_VERSION);
        assertThat(result.getPrompt()).contains("不得编造用户不存在");
        assertThat(result.getPrompt()).contains("只根据下面提供的简历解析内容");
        assertThat(result.getPrompt()).contains("只能输出一个 JSON 对象");
        assertThat(result.getPrompt()).contains("score、strengths、problems、suggestionsSummary");
        assertThat(result.getPrompt()).contains("张三");
    }

    @Test
    void buildPromptShouldRejectBlankExtractedText() {
        assertThatThrownBy(() -> service.buildPrompt(" ", "{}"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历解析文本不能为空");
    }
}
