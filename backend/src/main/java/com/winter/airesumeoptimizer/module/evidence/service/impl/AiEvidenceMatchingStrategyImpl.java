package com.winter.airesumeoptimizer.module.evidence.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.infra.ai.AiClientException;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchOutcomeDTO;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchPromptDTO;
import com.winter.airesumeoptimizer.module.evidence.service.EvidenceMatchOutputParser;
import com.winter.airesumeoptimizer.module.evidence.service.EvidenceMatchPromptService;
import com.winter.airesumeoptimizer.module.evidence.service.EvidenceMatchingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiEvidenceMatchingStrategyImpl implements EvidenceMatchingStrategy {

    private static final Logger log = LoggerFactory.getLogger(AiEvidenceMatchingStrategyImpl.class);

    private final EvidenceMatchPromptService evidenceMatchPromptService;
    private final EvidenceMatchOutputParser evidenceMatchOutputParser;
    private final AiClientService aiClientService;

    public AiEvidenceMatchingStrategyImpl(
            EvidenceMatchPromptService evidenceMatchPromptService,
            EvidenceMatchOutputParser evidenceMatchOutputParser,
            AiClientService aiClientService) {
        this.evidenceMatchPromptService = evidenceMatchPromptService;
        this.evidenceMatchOutputParser = evidenceMatchOutputParser;
        this.aiClientService = aiClientService;
    }

    @Override
    public EvidenceMatchOutcomeDTO match(
            String frozenJobDescription,
            String jobStructuredContent,
            String resumeStructuredContent) {
        EvidenceMatchPromptDTO prompt = evidenceMatchPromptService.buildPrompt(
                jobStructuredContent,
                resumeStructuredContent);
        String aiOutput;
        try {
            aiOutput = aiClientService.complete(prompt.getPrompt());
        } catch (AiClientException exception) {
            log.warn("Evidence match AI call failed: model={}, reason={}",
                    aiClientService.modelName(),
                    LogSanitizer.sanitize(exception.getMessage()));
            throw new BusinessException(502, "AI 服务暂时不可用，请稍后重试");
        }
        // 引用校核使用完整简历快照，而不是截断后的 Prompt 输入。
        EvidenceMatchOutcomeDTO parsed = evidenceMatchOutputParser.parse(
                aiOutput,
                frozenJobDescription,
                jobStructuredContent,
                resumeStructuredContent);
        return EvidenceMatchOutcomeDTO.builder()
                .requirements(parsed.getRequirements())
                .modelName(aiClientService.modelName())
                .promptVersion(prompt.getPromptVersion())
                .build();
    }
}
