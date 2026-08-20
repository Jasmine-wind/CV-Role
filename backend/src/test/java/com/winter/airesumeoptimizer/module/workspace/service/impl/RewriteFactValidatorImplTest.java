package com.winter.airesumeoptimizer.module.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.winter.airesumeoptimizer.module.workspace.dto.RewriteFactValidationResult;
import com.winter.airesumeoptimizer.module.workspace.enums.RewriteFactViolationCode;
import org.junit.jupiter.api.Test;

/**
 * 事实闭包校验：允许同义改写与语言重组，拒绝任何事实扩张；无法确定时 fail closed。
 */
class RewriteFactValidatorImplTest {

    private final RewriteFactValidatorImpl validator = new RewriteFactValidatorImpl();

    private RewriteFactValidationResult validate(String original, String suggested) {
        return validator.validate(original, suggested);
    }

    @Test
    void shouldAllowSynonymRewordingWithoutFactExpansion() {
        RewriteFactValidationResult result = validate(
                "负责订单服务后端接口开发",
                "承担订单服务后端接口的开发与日常维护工作");

        assertThat(result.passed()).isTrue();
    }

    @Test
    void shouldAllowReorderingAndGrammarAdjustment() {
        RewriteFactValidationResult result = validate(
                "使用 Redis 实现缓存功能，参与订单模块开发",
                "参与订单模块开发，并使用 Redis 实现缓存功能");

        assertThat(result.passed()).isTrue();
    }

    @Test
    void shouldAllowRemovingUnrelatedContent() {
        RewriteFactValidationResult result = validate(
                "负责订单服务开发，平时喜欢打篮球",
                "负责订单服务的开发工作");

        assertThat(result.passed()).isTrue();
    }

    @Test
    void shouldRejectFabricatedTechnology() {
        RewriteFactValidationResult result = validate(
                "负责订单服务开发",
                "负责订单服务开发，使用 Kafka 完成异步解耦");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_TECHNOLOGY);
    }

    @Test
    void shouldRejectFabricatedChineseTechnologyCapability() {
        RewriteFactValidationResult result = validate(
                "负责订单服务开发",
                "负责订单服务的微服务拆分与开发");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_TECHNOLOGY);
    }

    @Test
    void shouldRejectFabricatedMetric() {
        RewriteFactValidationResult result = validate(
                "负责订单服务性能优化",
                "负责订单服务性能优化，接口响应时间降低 40%");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_QUANTITATIVE_CLAIM);
    }

    @Test
    void shouldRejectMultiplierClaimWithoutNumber() {
        RewriteFactValidationResult result = validate(
                "负责活动接口开发",
                "负责活动接口开发，活动期间吞吐量翻倍");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_QUANTITATIVE_CLAIM);
    }

    @Test
    void shouldKeepExistingMetricInSynonymRewording() {
        RewriteFactValidationResult result = validate(
                "优化订单查询接口，耗时降低 40%",
                "对订单查询接口进行优化，使耗时降低 40%");

        assertThat(result.passed()).isTrue();
    }

    @Test
    void shouldRejectResponsibilityEscalationFromParticipantToOwner() {
        RewriteFactValidationResult result = validate(
                "参与订单服务开发",
                "主导订单服务的整体开发与架构设计");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.RESPONSIBILITY_ESCALATION);
    }

    @Test
    void shouldRejectResponsibilityEscalationFromDeveloperToLead() {
        RewriteFactValidationResult result = validate(
                "负责订单服务开发",
                "作为项目负责人主导订单服务的整体开发");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.RESPONSIBILITY_ESCALATION);
    }

    @Test
    void shouldRejectFabricatedAchievement() {
        RewriteFactValidationResult result = validate(
                "参与订单服务开发",
                "参与订单服务开发，项目获得公司技术创新奖");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_ACHIEVEMENT);
    }

    @Test
    void shouldRejectQualitativeAchievementInflation() {
        RewriteFactValidationResult result = validate(
                "负责订单服务性能优化",
                "负责订单服务性能优化，系统稳定性显著提升");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_ACHIEVEMENT);
    }

    @Test
    void shouldRejectFabricatedScope() {
        RewriteFactValidationResult result = validate(
                "参与订单服务开发",
                "参与订单服务开发，方案推广至全公司");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_SCOPE_OR_TIME);
    }

    @Test
    void shouldRejectFabricatedTimeFact() {
        RewriteFactValidationResult result = validate(
                "负责订单服务开发",
                "自 2021 年起负责订单服务开发");

        // 年份由数字规则或时间规则兜底拒绝，二者都满足 fail closed。
        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isIn(
                RewriteFactViolationCode.NEW_QUANTITATIVE_CLAIM,
                RewriteFactViolationCode.NEW_SCOPE_OR_TIME);
    }

    @Test
    void shouldRejectFabricatedEntity() {
        RewriteFactValidationResult result = validate(
                "参与订单服务开发",
                "参与订单服务开发，项目服务于 Amazon 平台");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_ENTITY);
    }

    @Test
    void shouldNotCompletePartialEvidenceIntoFullCapability() {
        // 原文只有“使用过 Redis”，不得被补成缓存设计经验等完整能力。
        RewriteFactValidationResult result = validate(
                "在项目中使用过 Redis",
                "具备 Redis 缓存架构设计经验，主导缓存方案落地");

        assertThat(result.passed()).isFalse();
    }

    @Test
    void shouldRejectBlankSuggestion() {
        RewriteFactValidationResult result = validate("负责订单服务开发", "   ");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.EMPTY_OR_BLANK);
    }

    @Test
    void shouldRejectOversizedSuggestion() {
        RewriteFactValidationResult result = validate(
                "负责订单服务开发", "负".repeat(RewriteFactValidatorImpl.MAX_SUGGESTED_LENGTH + 1));

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.OVERSIZED);
    }

    @Test
    void shouldRejectElementIdentityLeak() {
        RewriteFactValidationResult result = validate(
                "负责订单服务开发",
                "负责订单服务开发 b-12");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.ELEMENT_IDENTITY_LEAK);
    }

    @Test
    void shouldRejectUuidLeak() {
        RewriteFactValidationResult result = validate(
                "负责订单服务开发",
                "负责订单服务开发 8627ff1c-95a7-4f3c-8341-af914e57d267");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.ELEMENT_IDENTITY_LEAK);
    }

    @Test
    void shouldRejectFullWidthMetricBypassAttempt() {
        // 全角数字经 NFKC 归一化后仍必须被识别为新增量化事实。
        RewriteFactValidationResult result = validate(
                "负责订单服务性能优化",
                "负责订单服务性能优化，接口响应时间降低 ４０％");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_QUANTITATIVE_CLAIM);
    }

    @Test
    void shouldRejectTechTokenWithDigitsBypass() {
        RewriteFactValidationResult result = validate(
                "负责前端页面开发",
                "负责前端页面开发，熟练使用 Vue3 与 TypeScript");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_TECHNOLOGY);
    }

    @Test
    void shouldNotMoveFactsFromOtherBullets() {
        // 事实基线只有当前 Bullet 原文：即便“10 万 QPS”在别处出现过，也不得搬入本 Bullet。
        RewriteFactValidationResult result = validate(
                "使用 Redis 实现缓存",
                "使用 Redis 实现缓存，支撑 10 万 QPS");

        assertThat(result.passed()).isFalse();
    }

    @Test
    void shouldRejectNewPercentageWhenOriginalOnlyContainsYearNumber() {
        // 数字按 token 精确匹配：原文“2023”不得被解释为允许“20%”。
        RewriteFactValidationResult result = validate(
                "2023 年参与订单项目开发",
                "2023 年参与订单项目开发，接口性能提升 20%");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_QUANTITATIVE_CLAIM);
    }

    @Test
    void shouldRejectChineseNumeralQuantification() {
        RewriteFactValidationResult result = validate(
                "负责活动接口开发",
                "负责活动接口开发，吞吐量提升三倍");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_QUANTITATIVE_CLAIM);
    }

    @Test
    void shouldRejectChinesePercentExpression() {
        RewriteFactValidationResult result = validate(
                "负责订单服务性能优化",
                "负责订单服务性能优化，接口耗时下降百分之三十");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_QUANTITATIVE_CLAIM);
    }

    @Test
    void shouldRejectLowercaseTechnologyNotInOriginal() {
        RewriteFactValidationResult result = validate(
                "负责配置管理模块开发",
                "负责配置管理模块开发，使用 etcd 存储配置");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_TECHNOLOGY);
    }

    @Test
    void shouldRejectEscalationByLeadingTeam() {
        RewriteFactValidationResult result = validate(
                "参与平台开发",
                "主持平台开发并带队完成交付");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.RESPONSIBILITY_ESCALATION);
    }

    @Test
    void shouldRejectNovelKnowledgeCapabilityTerms() {
        RewriteFactValidationResult result = validate(
                "参与问答功能开发",
                "参与问答功能开发，建设知识库与向量能力");

        assertThat(result.passed()).isFalse();
        assertThat(result.code()).isEqualTo(RewriteFactViolationCode.NEW_TECHNOLOGY);
    }
}
