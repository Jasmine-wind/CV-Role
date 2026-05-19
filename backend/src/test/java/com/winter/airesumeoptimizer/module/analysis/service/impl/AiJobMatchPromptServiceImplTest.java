package com.winter.airesumeoptimizer.module.analysis.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchPromptService;
import org.junit.jupiter.api.Test;

class AiJobMatchPromptServiceImplTest {

    private final AiJobMatchPromptServiceImpl service = new AiJobMatchPromptServiceImpl();

    @Test
    void buildPromptShouldUseVersionAndConstrainOutput() {
        AiJobMatchPromptDTO result = service.buildPrompt(
                "{\"skills\":[\"Java\",\"Spring Boot\"]}",
                "{\"requiredSkills\":[\"Java\"],\"bonusSkills\":[\"Redis\"]}",
                "Java 后端项目经历",
                "RAG 片段");

        assertThat(result.getPromptVersion()).isEqualTo(AiJobMatchPromptService.PROMPT_VERSION);
        assertThat(result.getPrompt()).contains("ai_job_match_v1");
        assertThat(result.getPrompt()).contains("只输出一个 JSON 对象");
        assertThat(result.getPrompt()).contains("overallScore、strongMatches、weakMatches、missingSkills、weakExperienceDescriptions、evidence、riskNotes");
        assertThat(result.getPrompt()).contains("不得编造简历中不存在");
        assertThat(result.getPrompt()).contains("不得把岗位要求直接写成用户已具备能力");
        assertThat(result.getPrompt()).contains("每个数组最多 3 条");
        assertThat(result.getPrompt()).contains("\"skills\":[\"Java\",\"Spring Boot\"]");
        assertThat(result.getPrompt()).contains("\"requiredSkills\":[\"Java\"]");
        assertThat(result.getPrompt()).contains("语义检索辅助上下文");
        assertThat(result.getPrompt()).contains("RAG 片段");
    }

    @Test
    void buildPromptShouldRejectBlankStructuredContent() {
        assertThatThrownBy(() -> service.buildPrompt(" ", "{\"requiredSkills\":[\"Java\"]}", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历结构化解析结果不能为空");

        assertThatThrownBy(() -> service.buildPrompt("{\"skills\":[\"Java\"]}", " ", null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("目标岗位结构化解析结果不能为空");
    }

    @Test
    void buildPromptShouldTruncateLongInputs() {
        AiJobMatchPromptDTO result = service.buildPrompt(
                "简历内容".repeat(1000),
                "岗位内容".repeat(1000),
                "摘要".repeat(1500),
                "RAG".repeat(1000));

        assertThat(result.getPrompt()).contains("[内容过长，已截断]");
    }
}
