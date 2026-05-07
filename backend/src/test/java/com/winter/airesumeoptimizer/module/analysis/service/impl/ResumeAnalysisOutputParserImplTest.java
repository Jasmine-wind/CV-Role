package com.winter.airesumeoptimizer.module.analysis.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.ResumeAnalysisResultDTO;
import org.junit.jupiter.api.Test;

class ResumeAnalysisOutputParserImplTest {

    private final ResumeAnalysisOutputParserImpl parser = new ResumeAnalysisOutputParserImpl(new ObjectMapper());

    @Test
    void parseShouldReadValidJson() {
        ResumeAnalysisResultDTO result = parser.parse("""
                {
                  "score": 78,
                  "strengths": ["具备 Java 基础"],
                  "problems": ["项目描述缺少结果"],
                  "suggestionsSummary": ["补充个人职责"]
                }
                """);

        assertThat(result.getScore()).isEqualTo(78);
        assertThat(result.getStrengths()).containsExactly("具备 Java 基础");
        assertThat(result.getProblems()).containsExactly("项目描述缺少结果");
        assertThat(result.getSuggestionsSummary()).containsExactly("补充个人职责");
    }

    @Test
    void parseShouldStripJsonCodeFence() {
        ResumeAnalysisResultDTO result = parser.parse("""
                ```json
                {"score": "88", "strengths": ["表达清晰"], "problems": [], "suggestionsSummary": ["补充量化结果"]}
                ```
                """);

        assertThat(result.getScore()).isEqualTo(88);
        assertThat(result.getProblems()).containsExactly("简历问题信息不足");
    }

    @Test
    void parseShouldFallbackMissingFieldsAndClampScore() {
        ResumeAnalysisResultDTO result = parser.parse("""
                {
                  "score": 130,
                  "strengths": ["A", "B", "C", "D", "E", "F"]
                }
                """);

        assertThat(result.getScore()).isEqualTo(100);
        assertThat(result.getStrengths()).containsExactly("A", "B", "C", "D", "E");
        assertThat(result.getProblems()).containsExactly("简历问题信息不足");
        assertThat(result.getSuggestionsSummary()).containsExactly("建议补充更完整的简历内容后重新分析");
    }

    @Test
    void parseShouldRejectNonJsonOutput() {
        assertThatThrownBy(() -> parser.parse("这是一段普通文本"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 分析结果不是合法 JSON");
    }
}
