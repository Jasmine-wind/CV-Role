package com.winter.airesumeoptimizer.module.evidence.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.PromptTemplateService;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchPromptDTO;
import org.junit.jupiter.api.Test;

class EvidenceMatchPromptServiceImplTest {

    private final EvidenceMatchPromptServiceImpl service =
            new EvidenceMatchPromptServiceImpl(new PromptTemplateService());

    @Test
    void buildPromptShouldRenderJobAndResumeContentIntoTemplate() {
        EvidenceMatchPromptDTO prompt = service.buildPrompt(
                "{\"requiredSkills\":[\"Java\"]}",
                "{\"skills\":[\"熟悉 Java\"]}");

        assertThat(prompt.getPromptVersion()).isEqualTo("evidence_match_v1");
        assertThat(prompt.getPrompt())
                .contains("evidence_match_v1")
                .contains("requiredSkills")
                .contains("熟悉 Java")
                .doesNotContain("{{jobStructuredContent}}")
                .doesNotContain("{{resumeStructuredContent}}");
    }

    @Test
    void buildPromptShouldRejectBlankInputs() {
        assertThatThrownBy(() -> service.buildPrompt(" ", "{}"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("目标岗位结构化解析结果不能为空");
        assertThatThrownBy(() -> service.buildPrompt("{}", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历结构化解析结果不能为空");
    }

    @Test
    void buildPromptShouldTruncateOversizedResumeContent() {
        String oversizedResume = "{\"skills\":[\"" + "长".repeat(6000) + "\"]}";

        EvidenceMatchPromptDTO prompt = service.buildPrompt("{\"requiredSkills\":[]}", oversizedResume);

        assertThat(prompt.getPrompt()).contains("[内容过长，已截断]");
    }
}
