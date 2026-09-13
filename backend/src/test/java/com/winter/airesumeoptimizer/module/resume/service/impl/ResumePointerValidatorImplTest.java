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
    void sourceRefShouldJoinContiguousLinesAcrossPages() {
        List<ResumeIndexedLineDTO> lines = List.of(
                line(1, "entry header", false, 1, "internship"),
                line(2, "first body line", false, 1, "internship"),
                line(3, "second body line", false, 2, "internship"));

        ResumeSourceRefDTO sourceRef = validator.sourceRef(1, 3, lines);

        assertThat(sourceRef).isNotNull();
        assertThat(sourceRef.getText()).isEqualTo("entry header\nfirst body line\nsecond body line");
        assertThat(sourceRef.getPage()).isNull();
    }

    @Test
    void sourceRefShouldRejectSparseOrCrossSectionRanges() {
        List<ResumeIndexedLineDTO> sparse = List.of(
                line(1, "first", false, 1, "internship"),
                line(3, "third", false, 1, "internship"));
        List<ResumeIndexedLineDTO> crossSection = List.of(
                line(1, "first", false, 1, "internship"),
                line(2, "other section", false, 1, "education"),
                line(3, "third", false, 2, "internship"));

        assertThat(validator.sourceRef(1, 3, sparse)).isNull();
        assertThat(validator.sourceRef(1, 3, crossSection)).isNull();
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
        return line(id, text, noise, 1, null);
    }

    private ResumeIndexedLineDTO line(int id, String text, boolean noise, int page, String rawSectionId) {
        return ResumeIndexedLineDTO.builder()
                .lineId(id)
                .page(page)
                .text(text)
                .normalizedText(text)
                .rawSectionId(rawSectionId)
                .isNoise(noise)
                .build();
    }
}
