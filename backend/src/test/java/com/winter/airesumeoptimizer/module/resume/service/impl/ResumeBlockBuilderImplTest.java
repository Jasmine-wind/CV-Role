package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeBlockBuilderImplTest {

    private final ResumeBlockBuilderImpl service = new ResumeBlockBuilderImpl();

    @Test
    void buildShouldCreateIndexedBlocksFromSections() {
        ResumeTextCleanResultDTO cleanResult = ResumeTextCleanResultDTO.builder()
                .sections(List.of(
                        ResumeTextSectionDTO.builder()
                                .sectionType("BASIC_INFO")
                                .heading("个人信息")
                                .sourceSectionConfidence("HIGH")
                                .lines(List.of("张三", " "))
                                .build(),
                        ResumeTextSectionDTO.builder()
                                .sectionType("SKILLS")
                                .heading("专业技能")
                                .sourceSectionConfidence("MEDIUM")
                                .lines(List.of("• Java Spring Boot"))
                                .build()))
                .build();

        var blocks = service.build(cleanResult);

        assertThat(blocks).hasSize(2);
        assertThat(blocks).extracting("index").containsExactly(0, 1);
        assertThat(blocks).extracting("originalIndex").containsExactly(0, 1);
        assertThat(blocks).extracting("displayOrder").containsExactly(0, 1);
        assertThat(blocks).extracting("sourceType").containsOnly("cleanedText");
        assertThat(blocks).extracting("sourceSection").containsExactly("BASIC_INFO", "SKILLS");
        assertThat(blocks).extracting("ruleSection").containsExactly("BASIC_INFO", "SKILLS");
        assertThat(blocks).extracting("ruleConfidence").containsExactly(0.95, 0.72);
        assertThat(blocks).extracting("sourceSectionConfidence").containsExactly("HIGH", "MEDIUM");
        assertThat(blocks).extracting("lockedLevel").containsExactly("HIGH", "MEDIUM");
        assertThat(blocks).extracting("finalSectionSource").containsOnly("RULE_SOURCE_SECTION");
        assertThat(blocks).extracting("sectionLocked").containsExactly(true, false);
        assertThat(blocks).extracting("text").containsExactly("张三", "Java Spring Boot");
        assertThat(blocks.get(0).getPrevText()).isNull();
        assertThat(blocks.get(0).getNextText()).isEqualTo("Java Spring Boot");
        assertThat(blocks.get(1).getPrevText()).isEqualTo("张三");
        assertThat(blocks.get(1).getNextText()).isNull();
    }
}
