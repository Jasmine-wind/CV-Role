package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.infra.ai.AiGateway;
import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.infra.ai.AiSource;
import com.winter.airesumeoptimizer.module.resume.config.ResumeParseProperties;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeAiStructuredParserImplTest {

    private final AiClientService aiClientService = mock(AiClientService.class);
    private final ResumeParseProperties properties = new ResumeParseProperties();
    private final ResumeAiStructuredParserImpl service = new ResumeAiStructuredParserImpl(
            properties,
            new ResumeStructuredParsePromptServiceImpl(new ObjectMapper()),
            new ResumeParseValidatorImpl(),
            aiClientService,
            new ObjectMapper());

    @Test
    void normalParseNeverDispatchesAiEvenWhenEnabled() {
        properties.setAiStructuredParseEnabled(true);
        when(aiClientService.complete(anyString())).thenReturn("{\"name\":\"编造姓名\"}");
        ResumeStructuredContentDTO rule = ResumeStructuredContentDTO.builder()
                .name("规则姓名")
                .build();

        ResumeStructuredContentDTO returned = rule;
        var result = service.parse(blocks(), returned, List.of(), true);

        assertThat(result.getAiStatus()).isEqualTo("SKIPPED");
        assertThat(result.getSkippedReason()).isEqualTo("AI_STRUCTURED_PARSE_RULES_CANONICAL");
        assertThat(result.getAiInvoked()).isFalse();
        assertThat(result.getApplied()).isFalse();
        assertThat(result.getStructuredContent()).isSameAs(rule);
        verify(aiClientService, never()).complete(anyString());
    }

    @Test
    void selectionAwareNormalParseStillNeverDispatchesAi() {
        AiSelectionSnapshot selection = byokSelection();

        var result = service.parse(
                1L, 99L, blocks(), ResumeStructuredContentDTO.builder().build(), List.of(), true, selection);

        assertThat(result.getAiStatus()).isEqualTo("SKIPPED");
        assertThat(result.getSkippedReason()).isEqualTo("AI_STRUCTURED_PARSE_RULES_CANONICAL");
        assertThat(result.getAiInvoked()).isFalse();
        verify(aiClientService, never()).complete(anyString());
    }

    @Test
    void referenceRepairRequiresAResolvedByokSelection() {
        properties.setAiStructuredParseEnabled(true);

        var result = service.parseReferenceOnly(
                1L, blocks(), ResumeStructuredContentDTO.builder().build(), List.of(), true, null);

        assertThat(result.getAiStatus()).isEqualTo("SKIPPED");
        assertThat(result.getSkippedReason()).isEqualTo("AI_CONFIGURATION_REQUIRED");
        assertThat(result.getReferenceOnly()).isTrue();
        assertThat(result.getAiInvoked()).isFalse();
        verify(aiClientService, never()).complete(anyString());
    }

    @Test
    void referenceRepairRejectsSystemSelectionWithoutDispatch() {
        AiSelectionSnapshot systemSelection = new AiSelectionSnapshot(
                AiSource.SYSTEM_DEFAULT,
                AiSelectionSnapshot.OPENAI_COMPATIBLE,
                null,
                null,
                "https://provider.example.com:443/v1",
                "system-model",
                "{}",
                null);

        var result = service.parseReferenceOnly(
                1L, blocks(), ResumeStructuredContentDTO.builder().build(), List.of(), true, systemSelection);

        assertThat(result.getAiStatus()).isEqualTo("SKIPPED");
        assertThat(result.getSkippedReason()).isEqualTo("AI_BYOK_REQUIRED");
        assertThat(result.getAiInvoked()).isFalse();
        verify(aiClientService, never()).complete(anyString());
    }

    @Test
    void referenceRepairIsSourceBackedAndCachedAtMostOnce() {
        properties.setAiStructuredParseEnabled(true);
        when(aiClientService.complete(anyString())).thenReturn("""
                {"name":"张三","skills":["Java","Kotlin"],"projects":["Invented Project"]}
                """);
        AiSelectionSnapshot selection = byokSelection();
        ResumeStructuredContentDTO rule = ResumeStructuredContentDTO.builder()
                .parseMode("BALANCED")
                .build();

        var first = service.parseReferenceOnly(1L, 77L, blocks(), rule, List.of(), true, selection);
        var second = service.parseReferenceOnly(1L, 77L, blocks(), rule, List.of(), true, selection);

        assertThat(first.getReferenceOnly()).isTrue();
        assertThat(first.getApplied()).isFalse();
        assertThat(first.getAiStatus()).isEqualTo("REFERENCE_ONLY");
        assertThat(first.getReferenceConfidence()).isLessThan(0.5d);
        assertThat(first.getStructuredContent()).isSameAs(rule);
        assertThat(first.getReferenceContent()).isNotNull();
        assertThat(first.getReferenceContent().getName()).isEqualTo("张三");
        assertThat(first.getReferenceContent().getSkills()).containsExactly("Java");
        assertThat(first.getReferenceContent().getProjects()).isEmpty();
        assertThat(second.getCacheHit()).isTrue();
        assertThat(second.getAiInvoked()).isFalse();
        verify(aiClientService, times(1)).complete(anyString());
    }

    @Test
    void referenceRepairFallsBackWithoutApplyingMalformedProviderOutput() {
        properties.setAiStructuredParseEnabled(true);
        when(aiClientService.complete(anyString())).thenReturn("not-json");

        var result = service.parseReferenceOnly(
                1L,
                blocks(),
                ResumeStructuredContentDTO.builder().rawText("张三").build(),
                List.of(),
                true,
                byokSelection());

        assertThat(result.getReferenceOnly()).isTrue();
        assertThat(result.getApplied()).isFalse();
        assertThat(result.getAiStatus()).isEqualTo("FALLBACK");
        assertThat(result.getFallbackOccurred()).isTrue();
        assertThat(result.getAiInvoked()).isTrue();
        assertThat(result.getStructuredContent().getRawText()).isEqualTo("张三");
    }

    @Test
    void referenceRepairDoesNotAcceptResolvedSystemSelection() {
        AiGateway gateway = mock(AiGateway.class);
        when(gateway.selectionForNewTask(1L)).thenReturn(new AiSelectionSnapshot(
                AiSource.SYSTEM_DEFAULT,
                AiSelectionSnapshot.OPENAI_COMPATIBLE,
                null,
                null,
                "https://provider.example.com:443/v1",
                "system-model",
                "{}",
                null));
        ResumeAiStructuredParserImpl gatewayService = new ResumeAiStructuredParserImpl(
                properties,
                new ResumeStructuredParsePromptServiceImpl(new ObjectMapper()),
                new ResumeParseValidatorImpl(),
                gateway,
                new ObjectMapper());

        var result = gatewayService.parseReferenceOnly(
                1L, blocks(), ResumeStructuredContentDTO.builder().build(), List.of(), true, null);

        assertThat(result.getAiStatus()).isEqualTo("SKIPPED");
        assertThat(result.getSkippedReason()).isEqualTo("AI_BYOK_REQUIRED");
        verify(gateway, never()).complete(any(), any());
    }

    @Test
    void normalParseKeepsStableRuleSectionsWithoutCallingProvider() {
        properties.setAiStructuredParseEnabled(true);
        ResumeStructuredContentDTO rule = ResumeStructuredContentDTO.builder()
                .skills(List.of("Java"))
                .build();
        List<ResumeBlockDTO> lockedBlocks = List.of(ResumeBlockDTO.builder()
                .index(0)
                .text("Java")
                .sourceSection("SKILLS")
                .sectionLocked(true)
                .build());

        var result = service.parse(lockedBlocks, rule, List.of());

        assertThat(result.getAiStatus()).isEqualTo("SKIPPED");
        assertThat(result.getSkippedReason()).isEqualTo("STABLE_FIELDS_RULE_CONFIRMED");
        assertThat(result.getStructuredContent()).isSameAs(rule);
        verify(aiClientService, never()).complete(anyString());
    }

    private AiSelectionSnapshot byokSelection() {
        return new AiSelectionSnapshot(
                AiSource.USER_BYOK,
                AiSelectionSnapshot.OPENAI_COMPATIBLE,
                77L,
                5L,
                "https://provider.example.com:443/v1",
                "byok-model",
                "{}",
                null);
    }

    private List<ResumeBlockDTO> blocks() {
        return List.of(ResumeBlockDTO.builder()
                .id("source-1")
                .sourceBlockIds(List.of("source-1"))
                .sourceOccurrenceIds(List.of("source-occurrence-1"))
                .index(0)
                .text("张三 13800000000 Java Spring Boot")
                .sourceType("cleanedText")
                .sourceSection("SKILLS")
                .build());
    }
}
