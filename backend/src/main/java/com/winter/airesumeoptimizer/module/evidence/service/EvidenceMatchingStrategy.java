package com.winter.airesumeoptimizer.module.evidence.service;

import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchOutcomeDTO;

/**
 * 岗位证据匹配策略。正式分析只依赖该接口，具体匹配实现（当前为单次 AI 结构化输出）
 * 可以在不改动上层编排的情况下替换。
 */
public interface EvidenceMatchingStrategy {

    /**
     * 基于冻结的任务输入（岗位结构化解析结果与简历结构化快照）产出逐条要求的证据评估。
     * 实现必须保证证据引用来自简历原文；无法找到证据的要求只能判定为无证据。
     */
    EvidenceMatchOutcomeDTO match(
            String frozenJobDescription,
            String jobStructuredContent,
            String resumeStructuredContent);

    default EvidenceMatchOutcomeDTO match(
            Long userId,
            String frozenJobDescription,
            String jobStructuredContent,
            String resumeStructuredContent,
            AiSelectionSnapshot selection) {
        return match(frozenJobDescription, jobStructuredContent, resumeStructuredContent);
    }

    /**
     * Formal analysis calls carry their task identity so the Provider-attempt
     * ledger can be traced without changing Evidence facts.
     */
    default EvidenceMatchOutcomeDTO match(
            Long userId,
            Long optimizationTaskId,
            String frozenJobDescription,
            String jobStructuredContent,
            String resumeStructuredContent,
            AiSelectionSnapshot selection) {
        return match(userId, frozenJobDescription, jobStructuredContent, resumeStructuredContent, selection);
    }
}
