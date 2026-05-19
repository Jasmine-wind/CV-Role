package com.winter.airesumeoptimizer.module.analysis.match.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchResultDTO;
import org.junit.jupiter.api.Test;

class AiJobMatchOutputParserImplTest {

    private final AiJobMatchOutputParserImpl parser = new AiJobMatchOutputParserImpl(new ObjectMapper());

    @Test
    void parseShouldReadValidJson() {
        AiJobMatchResultDTO result = parser.parse("""
                {
                  "overallScore": 82,
                  "strongMatches": [{"item": "Java", "reason": "双方都出现 Java"}],
                  "weakMatches": [{"item": "Redis", "reason": "简历缺少项目支撑"}],
                  "missingSkills": [{"item": "Docker", "reason": "岗位要求但简历未出现"}],
                  "weakExperienceDescriptions": [{"section": "项目经历", "issue": "缺少结果说明"}],
                  "evidence": [{"source": "job", "content": "岗位要求 Java"}],
                  "riskNotes": ["部分技能缺少项目支撑"]
                }
                """);

        assertThat(result.getOverallScore()).isEqualTo(82);
        assertThat(result.getStrongMatches().getFirst().getItem()).isEqualTo("Java");
        assertThat(result.getWeakMatches().getFirst().getReason()).isEqualTo("简历缺少项目支撑");
        assertThat(result.getMissingSkills().getFirst().getItem()).isEqualTo("Docker");
        assertThat(result.getWeakExperienceDescriptions().getFirst().getSection()).isEqualTo("项目经历");
        assertThat(result.getEvidence().getFirst().getSource()).isEqualTo("job");
        assertThat(result.getRiskNotes()).containsExactly("部分技能缺少项目支撑");
    }

    @Test
    void parseShouldStripJsonCodeFenceAndLimitListSize() {
        AiJobMatchResultDTO result = parser.parse("""
                ```json
                {
                  "overallScore": "90",
                  "strongMatches": [
                    {"item":"A","reason":"A"},
                    {"item":"B","reason":"B"},
                    {"item":"C","reason":"C"},
                    {"item":"D","reason":"D"},
                    {"item":"E","reason":"E"},
                    {"item":"F","reason":"F"},
                    {"item":"G","reason":"G"},
                    {"item":"H","reason":"H"},
                    {"item":"I","reason":"I"}
                  ],
                  "weakMatches": [],
                  "missingSkills": [],
                  "weakExperienceDescriptions": [],
                  "evidence": [],
                  "riskNotes": []
                }
                ```
                """);

        assertThat(result.getOverallScore()).isEqualTo(90);
        assertThat(result.getStrongMatches()).hasSize(8);
    }

    @Test
    void parseShouldExtractJsonObjectFromSurroundingText() {
        AiJobMatchResultDTO result = parser.parse("""
                下面是匹配结果：
                {
                  "overallScore": 76,
                  "strongMatches": [{"item": "Spring Boot", "reason": "双方都出现 Spring Boot"}],
                  "weakMatches": [],
                  "missingSkills": [],
                  "weakExperienceDescriptions": [],
                  "evidence": [],
                  "riskNotes": []
                }
                如需继续优化，可查看建议。
                """);

        assertThat(result.getOverallScore()).isEqualTo(76);
        assertThat(result.getStrongMatches().getFirst().getItem()).isEqualTo("Spring Boot");
    }

    @Test
    void parseShouldChooseJsonObjectWithOverallScoreWhenEarlierObjectExists() {
        AiJobMatchResultDTO result = parser.parse("""
                说明：前置示例 {"note":"这不是结果"}。
                ```JSON
                {
                  overallScore: 68,
                  strongMatches: [{'item':'Java','reason':'双方都出现 Java',}],
                  weakMatches: [],
                  missingSkills: [],
                  weakExperienceDescriptions: [],
                  evidence: [],
                  riskNotes: [],
                }
                ```
                """);

        assertThat(result.getOverallScore()).isEqualTo(68);
        assertThat(result.getStrongMatches().getFirst().getReason()).isEqualTo("双方都出现 Java");
    }

    @Test
    void parseShouldRejectInvalidScoreAndNonJson() {
        assertThatThrownBy(() -> parser.parse("{\"overallScore\": 120}"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("overallScore 必须在 0 到 100 之间");

        assertThatThrownBy(() -> parser.parse("普通文本"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("AI 匹配结果不是合法 JSON");
    }
}
