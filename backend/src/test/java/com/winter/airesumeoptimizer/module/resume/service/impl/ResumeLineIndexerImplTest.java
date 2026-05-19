package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeRawSectionDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeLineIndexerImplTest {

    private final ResumeLineIndexerImpl indexer = new ResumeLineIndexerImpl();

    @Test
    void indexShouldGenerateContinuousLineIdsAndKeepRawSectionContext() {
        ResumeRawSectionDTO workSection = ResumeRawSectionDTO.builder()
                .id("section-work")
                .normalizedSection("WORK_EXPERIENCES")
                .confidence(0.92)
                .displayOrder(2)
                .blocks(List.of(
                        ResumeRawSectionBlockDTO.builder()
                                .index(2)
                                .originalIndex(2)
                                .displayOrder(2)
                                .text("JavaEE 软件工程师")
                                .build(),
                        ResumeRawSectionBlockDTO.builder()
                                .index(1)
                                .originalIndex(1)
                                .displayOrder(1)
                                .text("北京华来知识科技有限公司")
                                .build()))
                .build();
        ResumeRawSectionDTO educationSection = ResumeRawSectionDTO.builder()
                .id("section-edu")
                .normalizedSection("EDUCATION")
                .confidence(0.95)
                .displayOrder(1)
                .blocks(List.of(ResumeRawSectionBlockDTO.builder()
                        .index(3)
                        .displayOrder(1)
                        .text("郑州轻工业学院 本科")
                        .iconType("EDUCATION_ICON")
                        .build()))
                .build();

        List<ResumeIndexedLineDTO> lines = indexer.index(List.of(workSection, educationSection));

        assertThat(lines).extracting(ResumeIndexedLineDTO::getLineId).containsExactly(1, 2, 3);
        assertThat(lines).extracting(ResumeIndexedLineDTO::getText)
                .containsExactly("郑州轻工业学院 本科", "北京华来知识科技有限公司", "JavaEE 软件工程师");
        assertThat(lines.get(0).getRawSectionId()).isEqualTo("section-edu");
        assertThat(lines.get(0).getSectionHint()).isEqualTo("EDUCATION");
        assertThat(lines.get(0).getSourceType()).isEqualTo("icon-line");
        assertThat(lines.get(1).getRawSectionId()).isEqualTo("section-work");
        assertThat(lines.get(1).getSectionConfidence()).isEqualTo(0.92);
    }

    @Test
    void indexShouldKeepNoiseLinesButMarkThem() {
        ResumeRawSectionDTO section = ResumeRawSectionDTO.builder()
                .id("section-1")
                .displayOrder(1)
                .blocks(List.of(
                        ResumeRawSectionBlockDTO.builder().displayOrder(1).text("1").build(),
                        ResumeRawSectionBlockDTO.builder().displayOrder(2).text("项目描述").build()))
                .build();

        List<ResumeIndexedLineDTO> lines = indexer.index(List.of(section));

        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).getIsNoise()).isTrue();
        assertThat(lines.get(1).getIsNoise()).isFalse();
    }
}
