package com.winter.airesumeoptimizer.module.evidence.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.infra.ai.AiClientProperties;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.infra.ai.OpenAiCompatibleAiClientService;
import com.winter.airesumeoptimizer.infra.ai.PromptTemplateService;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchOutcomeDTO;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceQuoteDTO;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceRequirementEvaluationDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * 真实 AI 效果 smoke：使用代表性案例跑完整证据匹配链路（Prompt → 真实模型 → 事实校核解析）。
 * 默认跳过；运行方式与 RealAiClientSmokeTest 一致，需要 -DrealAi=true 与真实密钥。
 */
class RealEvidenceMatchSmokeTest {

    private static final String JOB_DESCRIPTION = """
            Java 后端开发工程师。熟练掌握 Java，熟悉 Spring Boot；熟悉 Redis，具备缓存设计经验；
            熟练使用 Git 进行团队协作。有后端项目开发经验，具备高并发系统优化经验。
            负责业务接口开发和维护。具备 Kafka 消息队列使用经验者优先。
            """;

    private static final String JOB_STRUCTURED = """
            {"jobTitle":"Java 后端开发工程师",
            "requiredSkills":["熟练掌握 Java，熟悉 Spring Boot","熟悉 Redis，具备缓存设计经验","熟练使用 Git 进行团队协作"],
            "bonusSkills":["具备 Kafka 消息队列使用经验"],
            "experienceSignals":["有后端项目开发经验","具备高并发系统优化经验"],
            "responsibilities":["负责业务接口开发和维护"],
            "keywords":["Java","Spring Boot","Redis","Git","Kafka"],
            "summary":"岗位侧重 Java 后端开发"}
            """;

    private static final String RESUME_STRUCTURED = """
            {"name":"测试用户",
            "skills":["熟悉 Java","熟悉 Spring Boot","熟悉 Redis","了解 MySQL","使用 Git 进行版本控制"],
            "projects":["电商订单系统：基于 Spring Boot 完成订单创建、支付回调等核心接口开发，负责接口性能优化",
            "校园二手交易平台：负责后端接口开发与部署"],
            "workExperiences":[],
            "education":["某某大学 计算机科学与技术 本科"],
            "summary":""}
            """;

    @Test
    @EnabledIfSystemProperty(named = "realAi", matches = "true")
    void evidenceMatchShouldJudgeRepresentativeCasesWithoutFabrication() {
        AiClientProperties properties = new AiClientProperties();
        properties.setApiKey(requiredProperty("realAi.apiKey"));
        properties.setBaseUrl(propertyOrDefault("realAi.baseUrl", "https://api.deepseek.com/v1"));
        properties.setModel(propertyOrDefault("realAi.model", "deepseek-chat"));
        properties.setTimeoutSeconds(Integer.parseInt(propertyOrDefault("realAi.timeoutSeconds", "60")));
        properties.setTemperature(Double.parseDouble(propertyOrDefault("realAi.temperature", "0.2")));
        properties.setMaxTokens(Integer.parseInt(propertyOrDefault("realAi.maxTokens", "8000")));

        OpenAiCompatibleAiClientService provider = new OpenAiCompatibleAiClientService(
                new ObjectMapper(),
                new com.winter.airesumeoptimizer.infra.ai.transport.PinnedHttpTransport());
        com.winter.airesumeoptimizer.infra.ai.AiGateway gateway = new com.winter.airesumeoptimizer.infra.ai.AiGateway() {
            @Override
            public com.winter.airesumeoptimizer.infra.ai.AiCompletionResult complete(
                    com.winter.airesumeoptimizer.infra.ai.AiInvocationContext context,
                    com.winter.airesumeoptimizer.infra.ai.AiGatewayRequest request) {
                var response = provider.complete(new com.winter.airesumeoptimizer.infra.ai.AiProviderRequest(
                        properties.getApiKey(),
                        properties.getBaseUrl(),
                        properties.getModel(),
                        properties.getTemperature(),
                        properties.getMaxTokens(),
                        java.time.Duration.ofSeconds(properties.getTimeoutSeconds()),
                        java.util.List.of(
                                com.winter.airesumeoptimizer.infra.ai.AiChatMessage.system(request.trustedPolicy()),
                                com.winter.airesumeoptimizer.infra.ai.AiChatMessage.user(request.untrustedData()))));
                return new com.winter.airesumeoptimizer.infra.ai.AiCompletionResult(
                        response.text(),
                        com.winter.airesumeoptimizer.infra.ai.AiSource.SYSTEM_DEFAULT,
                        com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot.OPENAI_COMPATIBLE,
                        properties.getModel(),
                        null,
                        null,
                        com.winter.airesumeoptimizer.infra.ai.AiUsageMetrics.empty(0, 1));
            }

            @Override
            public String modelName(com.winter.airesumeoptimizer.infra.ai.AiInvocationContext context) {
                return properties.getModel();
            }
        };
        AiEvidenceMatchingStrategyImpl strategy = new AiEvidenceMatchingStrategyImpl(
                new EvidenceMatchPromptServiceImpl(new PromptTemplateService(), new ObjectMapper()),
                new EvidenceMatchOutputParserImpl(new ObjectMapper()),
                gateway);

        EvidenceMatchOutcomeDTO outcome = strategy.match(JOB_DESCRIPTION, JOB_STRUCTURED, RESUME_STRUCTURED);

        assertThat(outcome.getRequirements()).isNotEmpty();
        for (EvidenceRequirementEvaluationDTO requirement : outcome.getRequirements()) {
            // 硬不变量：任何保留下来的证据引用必须真实出现在简历快照中。
            for (EvidenceQuoteDTO quote : requirement.getEvidences()) {
                assertThat(RESUME_STRUCTURED).contains(quote.getQuote());
            }
            if (requirement.getMatchLevel().name().equals("MATCHED")
                    || requirement.getMatchLevel().name().equals("PARTIAL_EVIDENCE")) {
                assertThat(requirement.getEvidences()).isNotEmpty();
            }
            System.out.printf("[evidence-smoke] %s | %s | %s | 证据 %d 条 | %s%n",
                    requirement.getMatchLevel(),
                    requirement.getImportance(),
                    requirement.getRequirementText(),
                    requirement.getEvidences().size(),
                    requirement.getConclusion());
        }

        // 简历中完全没有 Kafka：如果模型输出了该要求，只能是无证据。
        outcome.getRequirements().stream()
                .filter(requirement -> requirement.getRequirementText().toUpperCase().contains("KAFKA"))
                .forEach(requirement -> assertThat(requirement.getMatchLevel().name())
                        .as("Kafka 在简历中不存在，不允许判定为有证据")
                        .isEqualTo("NO_EVIDENCE"));
    }

    private String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少 JVM 参数：" + name);
        }
        return value;
    }

    private String propertyOrDefault(String name, String defaultValue) {
        String value = System.getProperty(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
