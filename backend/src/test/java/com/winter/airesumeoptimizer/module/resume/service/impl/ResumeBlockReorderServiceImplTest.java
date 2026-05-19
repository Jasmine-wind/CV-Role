package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumeBlockReorderServiceImplTest {

    private final ResumeBlockReorderServiceImpl service = new ResumeBlockReorderServiceImpl();

    @Test
    void reorderShouldUseBusinessSectionOrderAndKeepOriginalIndex() {
        List<ResumeBlockDTO> blocks = List.of(
                block(0, "项目经历", "PROJECTS"),
                block(1, "张三 13800000000", "BASIC_INFO"),
                block(2, "Java Spring Boot", "SKILLS"),
                block(3, "视觉顺序未知内容", "GENERAL"));

        List<ResumeBlockDTO> result = service.reorder(blocks);

        assertThat(result).extracting("text")
                .containsExactly("张三 13800000000", "Java Spring Boot", "项目经历", "视觉顺序未知内容");
        assertThat(result).extracting("originalIndex").containsExactly(1, 2, 0, 3);
        assertThat(result).extracting("displayOrder").containsExactly(0, 1, 2, 3);
        assertThat(result.get(3).getSourceSection()).isEqualTo("OTHERS");
        assertThat(result.get(3).getSectionLocked()).isFalse();
        assertThat(result.get(0).getRuleSection()).isEqualTo("BASIC_INFO");
        assertThat(result.get(0).getRuleConfidence()).isEqualTo(0.95);
        assertThat(result.get(0).getPrevText()).isEqualTo("prev");
        assertThat(result.get(0).getNextText()).isEqualTo("next");
        assertThat(result.get(0).getFinalSectionSource()).isEqualTo("RULE_SOURCE_SECTION");
    }

    private ResumeBlockDTO block(int index, String text, String section) {
        return ResumeBlockDTO.builder()
                .index(index)
                .originalIndex(index)
                .displayOrder(index)
                .text(text)
                .prevText("prev")
                .nextText("next")
                .sourceType("cleanedText")
                .sourceSection(section)
                .ruleSection(section)
                .ruleConfidence(!"GENERAL".equals(section) ? 0.95 : 0.35)
                .sourceSectionConfidence(!"GENERAL".equals(section) ? "HIGH" : "LOW")
                .lockedLevel(!"GENERAL".equals(section) ? "HIGH" : "LOW")
                .finalSectionSource("RULE_SOURCE_SECTION")
                .sectionLocked(!"GENERAL".equals(section))
                .build();
    }
}
