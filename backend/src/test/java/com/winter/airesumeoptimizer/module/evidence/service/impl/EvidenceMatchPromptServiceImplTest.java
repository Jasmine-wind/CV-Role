package com.winter.airesumeoptimizer.module.evidence.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.PromptTemplateService;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchPromptDTO;
import org.junit.jupiter.api.Test;

class EvidenceMatchPromptServiceImplTest {

    private final EvidenceMatchPromptServiceImpl service =
            new EvidenceMatchPromptServiceImpl(new PromptTemplateService(), new ObjectMapper());

    @Test
    void buildPromptShouldRenderJobAndResumeContentIntoTemplate() {
        EvidenceMatchPromptDTO prompt = service.buildPrompt(
                "{\"requiredSkills\":[\"Java\"]}",
                "{\"skills\":[\"熟悉 Java\"]}");

        assertThat(prompt.getPromptVersion()).isEqualTo("evidence_match_v3");
        assertThat(prompt.getPrompt())
                .contains("evidence_match_v3")
                .contains("PARTIAL_EVIDENCE")
                .contains("不代表用户现实世界中的全部能力")
                .contains("不得授权 AI 增加证据中没有的能力")
                .contains("requiredSkills")
                .contains("熟悉 Java")
                .doesNotContain("EXPRESSION_GAP")
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
        String oversizedResume = "{\"skills\":[\"" + "长".repeat(30_000) + "\"]}";

        EvidenceMatchPromptDTO prompt = service.buildPrompt("{\"requiredSkills\":[]}", oversizedResume);

        assertThat(prompt.getPrompt()).contains("[内容过长，中间部分已截断]");
    }

    @Test
    void buildPromptShouldKeepEvidenceFieldsAndExcludeParserDebugData() {
        EvidenceMatchPromptDTO prompt = service.buildPrompt(
                "{\"requiredSkills\":[\"Java\"]}",
                "{\"debug\":{\"providerSecret\":\"internal\"},\"rawText\":\"项目末尾使用 Java\",\"skills\":[\"Java\"]}");

        assertThat(prompt.getPrompt())
                .contains("项目末尾使用 Java")
                .contains("skills")
                .doesNotContain("providerSecret")
                .doesNotContain("internal");
    }
}
