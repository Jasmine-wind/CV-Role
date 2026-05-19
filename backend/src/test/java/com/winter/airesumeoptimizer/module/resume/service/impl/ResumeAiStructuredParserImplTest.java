package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
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
    void parseShouldReturnDisabledWhenConfigOff() {
        properties.setAiStructuredParseEnabled(false);
        ResumeStructuredContentDTO rule = ResumeStructuredContentDTO.builder()
                .name("张三")
                .skills(List.of("Java"))
                .build();

        var result = service.parse(blocks(), rule, List.of());

        assertThat(result.shouldApply()).isFalse();
        assertThat(result.getStructuredContent()).isSameAs(rule);
        verify(aiClientService, never()).complete(anyString());
    }

    @Test
    void parseShouldApplyValidAiJsonAfterValidation() {
        properties.setAiStructuredParseEnabled(true);
        when(aiClientService.complete(anyString())).thenReturn("""
                {
                  "name": "张三",
                  "phone": "13800000000",
                  "email": "zhangsan@example.com",
                  "skills": ["Java", "Spring Boot", "项目简介"],
                  "projects": ["AI 简历优化系统"],
                  "qualityWarnings": ["AI_LOW_CONFIDENCE"]
                }
                """);

        var result = service.parse(blocks(), ResumeStructuredContentDTO.builder().build(), List.of("SECTION_TOO_FEW"));

        assertThat(result.shouldApply()).isTrue();
        assertThat(result.getStructuredContent().getName()).isEqualTo("张三");
        assertThat(result.getStructuredContent().getSkills()).containsExactly("Java", "Spring Boot");
        assertThat(result.getStructuredContent().getQualityWarnings()).contains("SECTION_TOO_FEW", "AI_SKILLS_NON_TECH_TEXT_FILTERED");
    }

    @Test
    void parseShouldExtractJsonObjectFromAiText() {
        properties.setAiStructuredParseEnabled(true);
        when(aiClientService.complete(anyString())).thenReturn("""
                下面是结构化结果：
                ```JSON
                {
                  "name": "张三",
                  "phone": "13800000000",
                  "email": "zhangsan@example.com",
                  "skills": ["Java", "Spring Boot"]
                }
                ```
                请核对。
                """);

        var result = service.parse(blocks(), ResumeStructuredContentDTO.builder().build(), List.of());

        assertThat(result.shouldApply()).isTrue();
        assertThat(result.getStructuredContent().getName()).isEqualTo("张三");
        assertThat(result.getStructuredContent().getSkills()).containsExactly("Java", "Spring Boot");
    }

    @Test
    void parseShouldNormalizeWrappedObjectArrays() {
        properties.setAiStructuredParseEnabled(true);
        when(aiClientService.complete(anyString())).thenReturn("""
                {
                  "structuredResult": {
                    "basicInfo": {
                      "name": "张三",
                      "age": 23,
                      "phone": "13800000000",
                      "email": "zhangsan@example.com"
                    },
                    "skills": ["Java", {"name": "Spring Boot"}],
                    "projects": [
                      {"name": "AI 简历优化系统", "description": "负责解析模块"}
                    ],
                    "qualityWarnings": ["AI_LOW_CONFIDENCE"]
                  }
                }
                """);

        var result = service.parse(blocks(), ResumeStructuredContentDTO.builder().build(), List.of());

        assertThat(result.shouldApply()).isTrue();
        assertThat(result.getStructuredContent().getName()).isEqualTo("张三");
        assertThat(result.getStructuredContent().getBasicInfo()).containsEntry("age", "23");
        assertThat(result.getStructuredContent().getSkills()).containsExactly("Java", "Spring Boot");
        assertThat(result.getStructuredContent().getProjects()).containsExactly("{\"name\":\"AI 简历优化系统\",\"description\":\"负责解析模块\"}");
    }

    @Test
    void parseShouldFallbackWhenAiReturnsInvalidJson() {
        properties.setAiStructuredParseEnabled(true);
        ResumeStructuredContentDTO rule = ResumeStructuredContentDTO.builder()
                .name("张三")
                .build();
        when(aiClientService.complete(anyString())).thenReturn("not-json");

        var result = service.parse(blocks(), rule, List.of());

        assertThat(result.shouldApply()).isFalse();
        assertThat(result.getFallbackReason()).contains("JSON");
        assertThat(result.getStructuredContent()).isSameAs(rule);
    }

    @Test
    void parseShouldFallbackWhenAiFails() {
        properties.setAiStructuredParseEnabled(true);
        when(aiClientService.complete(anyString())).thenThrow(new RuntimeException("timeout"));

        var result = service.parse(blocks(), ResumeStructuredContentDTO.builder().build(), List.of());

        assertThat(result.shouldApply()).isFalse();
        assertThat(result.getFallbackReason()).contains("AI 结构化补全失败");
    }

    @Test
    void parseShouldSkipWhenAllBlocksAreLockedStableSections() {
        properties.setAiStructuredParseEnabled(true);
        ResumeStructuredContentDTO rule = ResumeStructuredContentDTO.builder()
                .name("张三")
                .skills(List.of("Java"))
                .build();

        var result = service.parse(List.of(ResumeBlockDTO.builder()
                .index(0)
                .text("Java Spring Boot")
                .sourceType("cleanedText")
                .sourceSection("SKILLS")
                .sectionLocked(true)
                .build()), rule, List.of());

        assertThat(result.shouldApply()).isFalse();
        assertThat(result.getAiStatus()).isEqualTo("SKIPPED");
        assertThat(result.getSkippedReason()).isEqualTo("STABLE_FIELDS_RULE_CONFIRMED");
        assertThat(result.getFallbackOccurred()).isFalse();
        assertThat(result.getDurationMs()).isNotNull();
        verify(aiClientService, never()).complete(anyString());
    }

    @Test
    void parseShouldUseCacheForSameBlocksPromptAndModel() {
        properties.setAiStructuredParseEnabled(true);
        when(aiClientService.modelName()).thenReturn("test-model");
        when(aiClientService.complete(anyString())).thenReturn("""
                {"phone":"13800000000","email":"zhangsan@example.com"}
                """);

        var first = service.parse(blocks(), ResumeStructuredContentDTO.builder().build(), List.of());
        var second = service.parse(blocks(), ResumeStructuredContentDTO.builder().build(), List.of());

        assertThat(first.shouldApply()).isTrue();
        assertThat(first.getCacheHit()).isFalse();
        assertThat(second.shouldApply()).isTrue();
        assertThat(second.getCacheHit()).isTrue();
        assertThat(second.getCacheKey()).isEqualTo(first.getCacheKey());
        assertThat(first.getCacheKey())
                .contains("cleanedTextHash=")
                .contains("promptVersion=resume-structured-parse-v2")
                .contains("modelName=test-model")
                .contains("parserVersion=" + ResumeParseVersions.PARSER_VERSION)
                .contains("parseMode=unknown")
                .contains("blockBuilderVersion=" + ResumeParseVersions.BLOCK_BUILDER_VERSION)
                .contains("sectionRuleVersion=" + ResumeParseVersions.SECTION_RULE_VERSION);
        verify(aiClientService, times(1)).complete(anyString());
    }

    @Test
    void parseShouldUseDifferentCacheKeyForDifferentParseMode() {
        properties.setAiStructuredParseEnabled(true);
        when(aiClientService.modelName()).thenReturn("test-model");
        when(aiClientService.complete(anyString())).thenReturn("""
                {"phone":"13800000000","email":"zhangsan@example.com"}
                """);

        var fast = service.parse(blocks(), ResumeStructuredContentDTO.builder()
                .parseMode("FAST")
                .build(), List.of());
        var accurate = service.parse(blocks(), ResumeStructuredContentDTO.builder()
                .parseMode("ACCURATE")
                .build(), List.of());

        assertThat(fast.getCacheKey()).isNotEqualTo(accurate.getCacheKey());
        verify(aiClientService, times(2)).complete(anyString());
    }

    private List<ResumeBlockDTO> blocks() {
        return List.of(ResumeBlockDTO.builder()
                .index(0)
                .text("张三 13800000000 Java Spring Boot")
                .sourceType("cleanedText")
                .sourceSection("GENERAL")
                .build());
    }
}
