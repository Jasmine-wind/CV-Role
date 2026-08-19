package com.winter.airesumeoptimizer.module.evidence.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchOutcomeDTO;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceRequirementEvaluationDTO;
import com.winter.airesumeoptimizer.module.evidence.enums.EvidenceMatchLevel;
import com.winter.airesumeoptimizer.module.evidence.enums.EvidenceSupportLevel;
import org.junit.jupiter.api.Test;

class EvidenceMatchOutputParserImplTest {

    private static final String RESUME_CORPUS = """
            {"skills":["熟悉 Java","熟悉 Redis","使用 Git 进行版本控制"],
            "projects":["基于 Spring Boot 完成电商订单系统，负责订单接口开发与性能优化"],
            "workExperiences":["在某公司使用 Spring Cloud 拆分微服务并独立部署"]}
            """;
    private static final String JOB_DESCRIPTION = """
            熟悉 Java；具备 Redis 缓存设计经验；熟练使用 Git 协作；具备 Spring Boot 项目经验；
            具备高并发系统设计经验；熟悉微服务架构；具备 Kafka 使用经验。
            要求 0 要求 1 要求 2 要求 3 要求 4 要求 5 要求 6 要求 7 要求 8 要求 9
            要求 10 要求 11 要求 12 要求 13 要求 14
            """;
    private static final String JOB_STRUCTURED = """
            {"requiredSkills":["熟悉 Java","具备 Redis 缓存设计经验","熟练使用 Git 协作",
            "具备 Spring Boot 项目经验","具备高并发系统设计经验","熟悉微服务架构",
            "要求 0","要求 1","要求 2","要求 3","要求 4","要求 5","要求 6","要求 7",
            "要求 8","要求 9","要求 10","要求 11","要求 12","要求 13","要求 14"],
            "bonusSkills":["具备 Kafka 使用经验"],"experienceSignals":[],"responsibilities":[]}
            """;

    private final EvidenceMatchOutputParserImpl parser =
            new EvidenceMatchOutputParserImpl(new ObjectMapper());

    @Test
    void parseShouldKeepMatchedRequirementWithVerbatimEvidence() {
        String aiOutput = """
                {"requirements":[{"requirement":"熟悉 Java 开发","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"简历清楚描述了 Java 开发经历","suggestion":"",
                "evidences":[{"section":"技能","quote":"熟悉 Java","supportLevel":"SUFFICIENT"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parse(aiOutput);

        EvidenceRequirementEvaluationDTO requirement = outcome.getRequirements().get(0);
        assertThat(outcome.getRequirements()).hasSize(1);
        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.MATCHED);
        assertThat(requirement.getEvidences()).hasSize(1);
        assertThat(requirement.getEvidences().get(0).getQuote()).isEqualTo("熟悉 Java");
        assertThat(requirement.getEvidences().get(0).getSupportLevel())
                .isEqualTo(EvidenceSupportLevel.SUFFICIENT);
    }

    @Test
    void parseShouldKeepPartialEvidenceWithPartialSupport() {
        String aiOutput = """
                {"requirements":[{"requirement":"具备 Redis 缓存设计经验","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"模型错误地判为完整匹配","suggestion":"补充真实使用场景",
                "evidences":[{"section":"技能","quote":"熟悉 Redis","supportLevel":"PARTIAL"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parse(aiOutput);

        EvidenceRequirementEvaluationDTO requirement = outcome.getRequirements().get(0);
        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.PARTIAL_EVIDENCE);
        assertThat(requirement.getConclusion()).contains("当前材料中有相关证据");
        assertThat(requirement.getSuggestion()).contains("用户确认真实事实");
        assertThat(requirement.getEvidences().get(0).getSupportLevel())
                .isEqualTo(EvidenceSupportLevel.PARTIAL);
    }

    @Test
    void parseShouldKeepNoEvidenceRequirementWithoutEvidenceRows() {
        String aiOutput = """
                {"requirements":[{"requirement":"具备 Kafka 使用经验","importance":"BONUS",
                "matchLevel":"NO_EVIDENCE","conclusion":"当前简历中没有 Kafka 相关内容",
                "suggestion":"请确认是否确有相关经历","evidences":[]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parse(aiOutput);

        EvidenceRequirementEvaluationDTO requirement = outcome.getRequirements().get(0);
        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.NO_EVIDENCE);
        assertThat(requirement.getEvidences()).isEmpty();
    }

    @Test
    void parseShouldAcceptSynonymQuoteWithDifferentWhitespace() {
        String aiOutput = """
                {"requirements":[{"requirement":"熟练使用 Git 协作","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"简历已说明 Git 使用方式","suggestion":"",
                "evidences":[{"section":"技能","quote":"使用 Git 进行\\u7248本控制","supportLevel":"SUFFICIENT"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parse(aiOutput);

        assertThat(outcome.getRequirements().get(0).getEvidences()).hasSize(1);
    }

    @Test
    void parseShouldDropUnrelatedEvidenceForSingleRequirement() {
        String aiOutput = """
                {"requirements":[{"requirement":"具备 Spring Boot 项目经验","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"多处经历可以证明","suggestion":"",
                "evidences":[
                {"section":"项目经历","quote":"基于 Spring Boot 完成电商订单系统，负责订单接口开发与性能优化","supportLevel":"SUFFICIENT"},
                {"section":"技能","quote":"熟悉 Java","supportLevel":"SUFFICIENT"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parse(aiOutput);

        assertThat(outcome.getRequirements().get(0).getEvidences()).hasSize(1);
    }

    @Test
    void parseShouldDowngradeRequirementWhenQuoteIsFabricated() {
        String aiOutput = """
                {"requirements":[{"requirement":"具备高并发系统设计经验","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"简历描述了高并发经验","suggestion":"",
                "evidences":[{"section":"项目经历","quote":"主导千万级并发网关建设","supportLevel":"SUFFICIENT"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parse(aiOutput);

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
                "matchLevel":"PARTIAL_EVIDENCE","conclusion":"当前材料证据不完整","suggestion":"建议完善",
                "evidences":[
                {"section":"工作经历","quote":"使用 Spring Cloud 拆分微服务并独立部署","supportLevel":"PARTIAL"},
                {"section":"工作经历","quote":"主导中台微服务改造，QPS 提升十倍","supportLevel":"PARTIAL"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parse(aiOutput);

        EvidenceRequirementEvaluationDTO requirement = outcome.getRequirements().get(0);
        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.PARTIAL_EVIDENCE);
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

        EvidenceMatchOutcomeDTO outcome = parse(output.toString());

        assertThat(outcome.getRequirements()).hasSize(10);
    }

    @Test
    void parseShouldDropDuplicateRequirementTexts() {
        String aiOutput = """
                {"requirements":[
                {"requirement":"熟悉 Java","importance":"REQUIRED","matchLevel":"NO_EVIDENCE","conclusion":"无","suggestion":"确认","evidences":[]},
                {"requirement":"熟悉 Java","importance":"REQUIRED","matchLevel":"NO_EVIDENCE","conclusion":"无","suggestion":"确认","evidences":[]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parse(aiOutput);

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

        EvidenceMatchOutcomeDTO outcome = parse(aiOutput);

        assertThat(outcome.getRequirements()).hasSize(1);
    }

    @Test
    void parseShouldRejectOutputWithoutRequirements() {
        assertThatThrownBy(() -> parse("{\"summary\":\"没有要求\"}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("requirements");
    }

    @Test
    void parseShouldRejectOutputWhenAllRequirementsAreInvalid() {
        assertThatThrownBy(() -> parse(
                "{\"requirements\":[{\"requirement\":\"\",\"matchLevel\":\"UNKNOWN\"}]}"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可用的岗位要求");
    }

    @Test
    void parseShouldRejectBlankOutput() {
        assertThatThrownBy(() -> parse(" "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void parseShouldDefaultInvalidImportanceToRequired() {
        String aiOutput = """
                {"requirements":[{"requirement":"熟悉 Java","importance":"CRITICAL",
                "matchLevel":"NO_EVIDENCE","conclusion":"无","suggestion":"确认","evidences":[]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parse(aiOutput);

        assertThat(outcome.getRequirements().get(0).getImportance().name()).isEqualTo("REQUIRED");
    }

    @Test
    void parseShouldClearSuggestionForMatchedRequirement() {
        String aiOutput = """
                {"requirements":[{"requirement":"熟悉 Java","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"已有证据","suggestion":"不应该出现的建议",
                "evidences":[{"section":"技能","quote":"熟悉 Java","supportLevel":"SUFFICIENT"}]}]}
                """;

        EvidenceMatchOutcomeDTO outcome = parse(aiOutput);

        assertThat(outcome.getRequirements().get(0).getSuggestion()).isEmpty();
    }

    @Test
    void parseShouldRejectRequirementNotPresentInFrozenJob() {
        String aiOutput = """
                {"requirements":[{"requirement":"精通 Kubernetes 集群治理","importance":"REQUIRED",
                "matchLevel":"NO_EVIDENCE","conclusion":"无","suggestion":"确认","evidences":[]}]}
                """;

        assertThatThrownBy(() -> parse(aiOutput))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有可用的岗位要求");
    }

    @Test
    void parseShouldRejectStructuredRequirementThatStrengthensFrozenJobWithSharedKeyword() {
        String aiOutput = """
                {"requirements":[{"requirement":"精通 Redis 集群治理","importance":"REQUIRED",
                "matchLevel":"NO_EVIDENCE","conclusion":"无","suggestion":"确认","evidences":[]}]}
                """;
        String structured = """
                {"requiredSkills":["精通 Redis 集群治理"],"bonusSkills":[],
                "experienceSignals":[],"responsibilities":[]}
                """;

        assertThatThrownBy(() -> parser.parse(aiOutput, JOB_DESCRIPTION, structured, RESUME_CORPUS))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可追溯");
    }

    @Test
    void parseShouldRejectWhitespaceNormalizedOrUnrelatedPartialQuote() {
        String aiOutput = """
                {"requirements":[{"requirement":"熟悉 Java","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"已有证据","suggestion":"",
                "evidences":[
                {"section":"技能","quote":"熟悉  Java","supportLevel":"SUFFICIENT"},
                {"section":"技能","quote":"熟悉","supportLevel":"SUFFICIENT"}]}]}
                """;

        EvidenceRequirementEvaluationDTO requirement = parse(aiOutput).getRequirements().get(0);

        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.NO_EVIDENCE);
        assertThat(requirement.getEvidences()).isEmpty();
    }

    @Test
    void parseShouldDeduplicateEvidenceAndDeriveLevelFromValidatedSupport() {
        String aiOutput = """
                {"requirements":[{"requirement":"熟悉 Java","importance":"REQUIRED",
                "matchLevel":"PARTIAL_EVIDENCE","conclusion":"模型编造了千万级成果","suggestion":"任意内容",
                "evidences":[
                {"section":"技能","quote":"熟悉 Java","supportLevel":"SUFFICIENT"},
                {"section":"技能","quote":"熟悉 Java","supportLevel":"PARTIAL"}]}]}
                """;

        EvidenceRequirementEvaluationDTO requirement = parse(aiOutput).getRequirements().get(0);

        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.MATCHED);
        assertThat(requirement.getEvidences()).hasSize(1);
        assertThat(requirement.getConclusion()).doesNotContain("千万级");
    }

    @Test
    void parseShouldNotGrantMatchedWhenSupportLevelIsInvalid() {
        String aiOutput = """
                {"requirements":[{"requirement":"熟悉 Java","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"已有证据","suggestion":"",
                "evidences":[{"section":"技能","quote":"熟悉 Java","supportLevel":"CERTAIN"}]}]}
                """;

        EvidenceRequirementEvaluationDTO requirement = parse(aiOutput).getRequirements().get(0);

        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.PARTIAL_EVIDENCE);
        assertThat(requirement.getEvidences()).hasSize(1);
        assertThat(requirement.getEvidences().get(0).getSupportLevel())
                .isEqualTo(EvidenceSupportLevel.PARTIAL);
    }

    @Test
    void parseShouldKeepNoEvidenceNeutralAndWithoutRewriteAuthority() {
        String aiOutput = """
                {"requirements":[{"requirement":"具备 Kafka 使用经验","importance":"BONUS",
                "matchLevel":"NO_EVIDENCE","conclusion":"用户不会 Kafka",
                "suggestion":"直接添加 Kafka 项目经验","evidences":[]}]}
                """;

        EvidenceRequirementEvaluationDTO requirement = parse(aiOutput).getRequirements().get(0);

        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.NO_EVIDENCE);
        assertThat(requirement.getEvidences()).isEmpty();
        assertThat(requirement.getConclusion()).isEqualTo("当前材料中没有找到支持这条要求的证据。");
        assertThat(requirement.getSuggestion())
                .contains("用户补充或确认")
                .contains("不得写入简历")
                .doesNotContain("添加 Kafka");
    }

    @Test
    void parseShouldRejectEvidenceThatOnlySharesGenericTwoCharacterPhrase() {
        String aiOutput = """
                {"requirements":[{"requirement":"项目管理","importance":"REQUIRED",
                "matchLevel":"MATCHED","conclusion":"已有证据","suggestion":"",
                "evidences":[{"section":"经历","quote":"风险管理","supportLevel":"SUFFICIENT"}]}]}
                """;
        String job = "岗位要求：项目管理。";
        String structured = """
                {"requiredSkills":["项目管理"],"bonusSkills":[],
                "experienceSignals":[],"responsibilities":[]}
                """;
        String resume = "{\"workExperiences\":[\"风险管理\"]}";

        EvidenceRequirementEvaluationDTO requirement = parser.parse(aiOutput, job, structured, resume)
                .getRequirements().get(0);

        assertThat(requirement.getMatchLevel()).isEqualTo(EvidenceMatchLevel.NO_EVIDENCE);
        assertThat(requirement.getEvidences()).isEmpty();
    }

    private EvidenceMatchOutcomeDTO parse(String aiOutput) {
        return parser.parse(aiOutput, JOB_DESCRIPTION, JOB_STRUCTURED, RESUME_CORPUS);
    }
}
