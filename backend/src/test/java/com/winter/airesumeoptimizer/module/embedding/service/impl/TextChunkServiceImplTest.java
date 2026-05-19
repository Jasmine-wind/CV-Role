package com.winter.airesumeoptimizer.module.embedding.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TextChunkServiceImplTest {

    private final TextChunkServiceImpl service = new TextChunkServiceImpl();

    @Test
    void splitResumeTextShouldUseStructuredJsonAndParagraphs() {
        var chunks = service.splitResumeText(
                "{\"skills\":[\"Java\"]}",
                "项目经历\n负责接口开发。\n\n技能\nJava、Spring Boot。");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).contains("简历结构化解析").contains("Java");
        assertThat(chunks.get(1)).contains("项目经历").contains("技能");
    }

    @Test
    void splitResumeTextShouldIgnoreBlankInputs() {
        var chunks = service.splitResumeText(" ", "\n\n");

        assertThat(chunks).isEmpty();
    }

    @Test
    void splitJobDescriptionTextShouldUseStructuredContentAndParagraphs() {
        var chunks = service.splitJobDescriptionText(
                "{\"requiredSkills\":[\"Java\"]}",
                "岗位职责\n负责后端接口开发。\n\n任职要求\n熟悉 Spring Boot。");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0)).contains("目标岗位结构化解析").contains("Java");
        assertThat(chunks.get(1)).contains("岗位职责").contains("任职要求");
    }
}
