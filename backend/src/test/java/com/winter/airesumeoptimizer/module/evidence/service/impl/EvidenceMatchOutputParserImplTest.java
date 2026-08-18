package com.winter.airesumeoptimizer.module.evidence.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchOutcomeDTO;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceRequirementEvaluationDTO;
import com.winter.airesumeoptimizer.module.evidence.enums.EvidenceExpressionStatus;
import com.winter.airesumeoptimizer.module.evidence.enums.EvidenceMatchLevel;
import org.junit.jupiter.api.Test;

class EvidenceMatchOutputParserImplTest {

    private static final String RESUME_CORPUS = """
            {"skills":["熟悉 Java","熟悉 Redis","使用 Git 进行版本控制"],
            "projects":["基于 Spring Boot 完成电商订单系统，负责订单接口开发与性能优化"],
            "workExperiences":["在某公司使用 Spring Cloud 拆分服务并独立部署"]}
            """;

    private final EvidenceMatchOutputParserImpl parser =
            new EvidenceMatchOutputParserImpl(new ObjectMapper());

    @Test
    void parseShouldKeepMatchedRequirementWithVerbatimEvidence() {
        String aiOutput = """
                {"requirements":[{"requirement":"熟悉 Java 开发","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"简历清楚描述了 Java 开发经历","suggestion":"",
                "evidences":[{"section":"技能","quote":"熟悉 Java","expression":"ADEQUATE"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parser.parse(aiOutput, RESUME_CORPUS);

        EvidenceRequirementEvaluationDTO requirement = outcome.getRequirements().get(0);
        assertThat(outcome.getRequirements()).hasSize(1);
        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.MATCHED);
        assertThat(requirement.getEvidences()).hasSize(1);
        assertThat(requirement.getEvidences().get(0).getQuote()).isEqualTo("熟悉 Java");
        assertThat(requirement.getEvidences().get(0).getExpressionStatus())
                .isEqualTo(EvidenceExpressionStatus.ADEQUATE);
    }

    @Test
    void parseShouldKeepExpressionGapWithWeakEvidence() {
        String aiOutput = """
                {"requirements":[{"requirement":"具备 Redis 缓存设计经验","importance":"REQUIRED",
                "matchLevel":"EXPRESSION_GAP","conclusion":"简历只出现 Redis 名称","suggestion":"补充真实使用场景",
                "evidences":[{"section":"技能","quote":"熟悉 Redis","expression":"WEAK"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parser.parse(aiOutput, RESUME_CORPUS);

        EvidenceRequirementEvaluationDTO requirement = outcome.getRequirements().get(0);
        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.EXPRESSION_GAP);
        assertThat(requirement.getSuggestion()).isEqualTo("补充真实使用场景");
        assertThat(requirement.getEvidences().get(0).getExpressionStatus())
                .isEqualTo(EvidenceExpressionStatus.WEAK);
    }

    @Test
    void parseShouldKeepNoEvidenceRequirementWithoutEvidenceRows() {
        String aiOutput = """
                {"requirements":[{"requirement":"具备 Kafka 使用经验","importance":"BONUS",
                "matchLevel":"NO_EVIDENCE","conclusion":"当前简历中没有 Kafka 相关内容",
                "suggestion":"请确认是否确有相关经历","evidences":[]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parser.parse(aiOutput, RESUME_CORPUS);

        EvidenceRequirementEvaluationDTO requirement = outcome.getRequirements().get(0);
        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.NO_EVIDENCE);
        assertThat(requirement.getEvidences()).isEmpty();
    }

    @Test
    void parseShouldAcceptSynonymQuoteWithDifferentWhitespace() {
        String aiOutput = """
                {"requirements":[{"requirement":"熟练使用 Git 协作","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"简历已说明 Git 使用方式","suggestion":"",
                "evidences":[{"section":"技能","quote":"使用 Git 进行\\u7248本控制","expression":"ADEQUATE"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parser.parse(aiOutput, RESUME_CORPUS);

        assertThat(outcome.getRequirements().get(0).getEvidences()).hasSize(1);
    }

    @Test
    void parseShouldKeepMultipleEvidencesForSingleRequirement() {
        String aiOutput = """
                {"requirements":[{"requirement":"具备 Spring Boot 项目经验","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"多处经历可以证明","suggestion":"",
                "evidences":[
                {"section":"项目经历","quote":"基于 Spring Boot 完成电商订单系统，负责订单接口开发与性能优化","expression":"ADEQUATE"},
                {"section":"技能","quote":"熟悉 Java","expression":"ADEQUATE"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parser.parse(aiOutput, RESUME_CORPUS);

        assertThat(outcome.getRequirements().get(0).getEvidences()).hasSize(2);
    }

    @Test
    void parseShouldDowngradeRequirementWhenQuoteIsFabricated() {
        String aiOutput = """
                {"requirements":[{"requirement":"具备高并发系统设计经验","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"简历描述了高并发经验","suggestion":"",
                "evidences":[{"section":"项目经历","quote":"主导千万级并发网关建设","expression":"ADEQUATE"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parser.parse(aiOutput, RESUME_CORPUS);

        EvidenceRequirementEvaluationDTO requirement = outcome.getRequirements().get(0);
        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.NO_EVIDENCE);
        assertThat(requirement.getEvidences()).isEmpty();
        assertThat(requirement.getConclusion()).contains("没有找到");
        assertThat(requirement.getSuggestion()).contains("确认");
    }

    @Test
    void parseShouldKeepOnlyVerbatimEvidenceWhenMixedWithFabricatedQuote() {
        String aiOutput = """
                {"requirements":[{"requirement":"熟悉微服务架构","importance":"REQUIRED",
                "matchLevel":"EXPRESSION_GAP","conclusion":"有相关经历但表达不足","suggestion":"补充细节",
                "evidences":[
                {"section":"工作经历","quote":"使用 Spring Cloud 拆分服务并独立部署","expression":"WEAK"},
                {"section":"工作经历","quote":"主导中台微服务改造，QPS 提升十倍","expression":"WEAK"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parser.parse(aiOutput, RESUME_CORPUS);

        EvidenceRequirementEvaluationDTO requirement = outcome.getRequirements().get(0);
        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.EXPRESSION_GAP);
        assertThat(requirement.getEvidences()).hasSize(1);
        assertThat(requirement.getEvidences().get(0).getQuote()).contains("Spring Cloud");
    }

    @Test
    void parseShouldDeduplicateRequirementsAndCapCounts() {
        StringBuilder output = new StringBuilder("{\"requirements\":[");
        for (int index = 0; index < 15; index++) {
            if (index > 0) {
                output.append(',');
            }
            output.append("{\"requirement\":\"要求 ").append(index).append("\",\"importance\":\"REQUIRED\",")
                    .append("\"matchLevel\":\"NO_EVIDENCE\",\"conclusion\":\"无证据\",\"suggestion\":\"确认经历\",")
                    .append("\"evidences\":[]}");
        }
        output.append("]}");

        EvidenceMatchOutcomeDTO outcome = parser.parse(output.toString(), RESUME_CORPUS);

        assertThat(outcome.getRequirements()).hasSize(10);
    }

    @Test
    void parseShouldDropDuplicateRequirementTexts() {
        String aiOutput = """
                {"requirements":[
                {"requirement":"熟悉 Java","importance":"REQUIRED","matchLevel":"NO_EVIDENCE","conclusion":"无","suggestion":"确认","evidences":[]},
                {"requirement":"熟悉 Java","importance":"REQUIRED","matchLevel":"NO_EVIDENCE","conclusion":"无","suggestion":"确认","evidences":[]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parser.parse(aiOutput, RESUME_CORPUS);

        assertThat(outcome.getRequirements()).hasSize(1);
    }

    @Test
    void parseShouldIgnoreMarkdownCodeFence() {
        String aiOutput = """
                ```json
                {"requirements":[{"requirement":"熟悉 Java","importance":"REQUIRED",
                "matchLevel":"NO_EVIDENCE","conclusion":"无","suggestion":"确认","evidences":[]}]}
                ```
                """;

        EvidenceMatchOutcomeDTO outcome = parser.parse(aiOutput, RESUME_CORPUS);

        assertThat(outcome.getRequirements()).hasSize(1);
    }

    @Test
    void parseShouldRejectOutputWithoutRequirements() {
        assertThatThrownBy(() -> parser.parse("{\"summary\":\"没有要求\"}", RESUME_CORPUS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("requirements");
    }

    @Test
    void parseShouldRejectOutputWhenAllRequirementsAreInvalid() {
        assertThatThrownBy(() -> parser.parse(
                "{\"requirements\":[{\"requirement\":\"\",\"matchLevel\":\"UNKNOWN\"}]}",
                RESUME_CORPUS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可用的岗位要求");
    }

    @Test
    void parseShouldRejectBlankOutput() {
        assertThatThrownBy(() -> parser.parse(" ", RESUME_CORPUS))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void parseShouldDefaultInvalidImportanceToRequired() {
        String aiOutput = """
                {"requirements":[{"requirement":"熟悉 Java","importance":"CRITICAL",
                "matchLevel":"NO_EVIDENCE","conclusion":"无","suggestion":"确认","evidences":[]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parser.parse(aiOutput, RESUME_CORPUS);

        assertThat(outcome.getRequirements().get(0).getImportance().name()).isEqualTo("REQUIRED");
    }

    @Test
    void parseShouldClearSuggestionForMatchedRequirement() {
        String aiOutput = """
                {"requirements":[{"requirement":"熟悉 Java","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"已有证据","suggestion":"不应该出现的建议",
                "evidences":[{"section":"技能","quote":"熟悉 Java","expression":"ADEQUATE"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parser.parse(aiOutput, RESUME_CORPUS);

        assertThat(outcome.getRequirements().get(0).getSuggestion()).isEmpty();
    }
}
