package com.winter.airesumeoptimizer.module.resume.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResumePointerValidatorImplTest {

    private final ResumePointerValidatorImpl validator = new ResumePointerValidatorImpl();

    @Test
    void sourceRefShouldJoinOriginalTextByLegalLineRange() {
        List<ResumeIndexedLineDTO> lines = lines();

        ResumeSourceRefDTO sourceRef = validator.sourceRef(2, 4, lines);

        assertThat(sourceRef).isNotNull();
        assertThat(sourceRef.getStartLine()).isEqualTo(2);
        assertThat(sourceRef.getEndLine()).isEqualTo(4);
        assertThat(sourceRef.getText()).isEqualTo("""
                北京华来知识科技有限公司
                JavaEE 软件工程师
                2017.10 - 2019.09""".strip());
    }

    @Test
    void validatorShouldRejectIllegalLineIdsAndRanges() {
        List<ResumeIndexedLineDTO> lines = lines();

        assertThat(validator.validLineId(99, lines)).isFalse();
        assertThat(validator.validLineRange(4, 2, lines)).isFalse();
        assertThat(validator.sourceRef(4, 99, lines)).isNull();
    }

    @Test
    void validEntityLineShouldRejectFieldLabelsAndNoise() {
        List<ResumeIndexedLineDTO> lines = lines();

        assertThat(validator.validEntityLine(1, lines)).isFalse();
        assertThat(validator.validEntityLine(5, lines)).isFalse();
        assertThat(validator.validEntityLine(2, lines)).isTrue();
    }

    private List<ResumeIndexedLineDTO> lines() {
        return List.of(
                line(1, "公司名称", false),
                line(2, "北京华来知识科技有限公司", false),
                line(3, "JavaEE 软件工程师", false),
                line(4, "2017.10 - 2019.09", false),
                line(5, "1", true));
    }

    private ResumeIndexedLineDTO line(int id, String text, boolean noise) {
        return ResumeIndexedLineDTO.builder()
                .lineId(id)
                .page(1)
                .text(text)
                .normalizedText(text)
                .isNoise(noise)
                .build();
    }
}
