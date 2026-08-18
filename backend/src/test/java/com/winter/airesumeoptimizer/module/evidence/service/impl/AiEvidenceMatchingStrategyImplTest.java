package com.winter.airesumeoptimizer.module.evidence.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.AiClientException;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.infra.ai.PromptTemplateService;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchOutcomeDTO;
import com.winter.airesumeoptimizer.module.evidence.enums.EvidenceMatchLevel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiEvidenceMatchingStrategyImplTest {

    private final AiClientService aiClientService = mock(AiClientService.class);
    private final EvidenceMatchPromptServiceImpl promptService =
            new EvidenceMatchPromptServiceImpl(new PromptTemplateService());
    private final EvidenceMatchOutputParserImpl parser =
            new EvidenceMatchOutputParserImpl(new ObjectMapper());
    private final AiEvidenceMatchingStrategyImpl strategy =
            new AiEvidenceMatchingStrategyImpl(promptService, parser, aiClientService);

    @Test
    void matchShouldBuildPromptFromFrozenInputsAndValidateQuotesAgainstFullSnapshot() {
        String resumeSnapshot = "{\"skills\":[\"熟悉 Java\"]}";
        String jobStructured = "{\"requiredSkills\":[\"Java\"]}";
        when(aiClientService.complete(anyString())).thenReturn("""
                {"requirements":[{"requirement":"熟悉 Java","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"已有证据","suggestion":"",
                "evidences":[{"section":"技能","quote":"熟悉 Java","expression":"ADEQUATE"}]}]}
                """);

        EvidenceMatchOutcomeDTO outcome = strategy.match(jobStructured, resumeSnapshot);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiClientService).complete(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("evidence_match_v1")
                .contains("熟悉 Java")
                .contains("requiredSkills");
        assertThat(outcome.getRequirements().get(0).getMatchLevel()).isEqualTo(EvidenceMatchLevel.MATCHED);
    }

    @Test
    void matchShouldExposePromptVersionForTaskSnapshot() {
        assertThat(strategy.promptVersion()).isEqualTo("evidence_match_v1");
    }

    @Test
    void matchShouldRejectBlankInputsBeforeCallingAi() {
        assertThatThrownBy(() -> strategy.match(" ", "{\"skills\":[]}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标岗位结构化解析结果");
        assertThatThrownBy(() -> strategy.match("{\"requiredSkills\":[]}", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历结构化解析结果");
    }

    @Test
    void matchShouldMapAiClientFailureToBusinessException() {
        when(aiClientService.complete(anyString())).thenThrow(new AiClientException("connection timeout"));

        assertThatThrownBy(() -> strategy.match("{\"requiredSkills\":[\"Java\"]}", "{\"skills\":[\"Java\"]}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("AI 服务暂时不可用");
    }
}
