package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.infra.ai.AiSource;
import com.winter.airesumeoptimizer.module.resume.config.ResumeParseProperties;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeAiSectionClassifierImplTest {

    private final AiClientService aiClientService = mock(AiClientService.class);
    private final ResumeParseProperties properties = new ResumeParseProperties();
    private final ResumeAiSectionClassifierImpl service = new ResumeAiSectionClassifierImpl(
            properties,
            new ResumeSectionClassifyPromptServiceImpl(new ObjectMapper()),
            aiClientService,
            new ObjectMapper());

    @Test
    void classifyShouldDisableWhenConfigIsOff() {
        properties.setAiSectionClassifyEnabled(false);

        var result = service.classify(blocks());

        assertThat(result.getAiStatus()).isEqualTo("DISABLED");
        assertThat(result.getSkippedReason()).isEqualTo("AI_SECTION_CLASSIFY_DISABLED");
        assertThat(result.getAiInvoked()).isFalse();
        assertThat(result.getClassifications()).isEmpty();
        verify(aiClientService, never()).complete(anyString());
    }

    @Test
    void classifyShouldNeverDispatchBecauseRulesOwnSectionProvenance() {
        properties.setAiSectionClassifyEnabled(true);
        when(aiClientService.complete(anyString())).thenReturn("{\"items\":[]}");

        var result = service.classify(blocks(), true);

        assertThat(result.shouldApply()).isFalse();
        assertThat(result.getAiStatus()).isEqualTo("SKIPPED");
        assertThat(result.getSkippedReason()).isEqualTo("AI_SECTION_CLASSIFY_RULES_CANONICAL");
        assertThat(result.getAiEnabled()).isFalse();
        assertThat(result.getAiInvoked()).isFalse();
        verify(aiClientService, never()).complete(anyString());
    }

    @Test
    void selectionAwareClassifyShouldStillNeverDispatchEvenForByok() {
        AiSelectionSnapshot selection = new AiSelectionSnapshot(
                AiSource.USER_BYOK,
                AiSelectionSnapshot.OPENAI_COMPATIBLE,
                77L,
                5L,
                "https://provider.example.com:443/v1",
                "byok-model",
                "{}",
                null);

        var result = service.classify(1L, 100L, blocks(), true, selection);

        assertThat(result.getAiStatus()).isEqualTo("SKIPPED");
        assertThat(result.getSkippedReason()).isEqualTo("AI_SECTION_CLASSIFY_RULES_CANONICAL");
        assertThat(result.getAiInvoked()).isFalse();
        verify(aiClientService, never()).complete(anyString());
    }

    private List<ResumeBlockDTO> blocks() {
        return List.of(
                ResumeBlockDTO.builder().index(0).text("张三 13800000000").sourceSection("GENERAL").build(),
                ResumeBlockDTO.builder().index(1).text("教育背景 示例大学 本科").sourceSection("GENERAL").build());
    }
}
