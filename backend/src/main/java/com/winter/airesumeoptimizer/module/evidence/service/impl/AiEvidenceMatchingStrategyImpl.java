package com.winter.airesumeoptimizer.module.evidence.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.AiGatewayRequest;
import com.winter.airesumeoptimizer.infra.ai.AiClientException;
import com.winter.airesumeoptimizer.infra.ai.AiCompletionResult;
import com.winter.airesumeoptimizer.infra.ai.AiGateway;
import com.winter.airesumeoptimizer.infra.ai.AiGatewayException;
import com.winter.airesumeoptimizer.infra.ai.AiGatewaySupport;
import com.winter.airesumeoptimizer.infra.ai.AiInvocationContext;
import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
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
    private final AiGateway aiGateway;

    public AiEvidenceMatchingStrategyImpl(
            EvidenceMatchPromptService evidenceMatchPromptService,
            EvidenceMatchOutputParser evidenceMatchOutputParser,
            AiGateway aiGateway) {
        this.evidenceMatchPromptService = evidenceMatchPromptService;
        this.evidenceMatchOutputParser = evidenceMatchOutputParser;
        this.aiGateway = aiGateway;
    }

    @Override
    public EvidenceMatchOutcomeDTO match(
            String frozenJobDescription,
            String jobStructuredContent,
            String resumeStructuredContent) {
        return match(null, frozenJobDescription, jobStructuredContent, resumeStructuredContent, null);
    }

    @Override
    public EvidenceMatchOutcomeDTO match(
            Long userId,
            String frozenJobDescription,
            String jobStructuredContent,
            String resumeStructuredContent,
            AiSelectionSnapshot selection) {
        return match(userId, null, frozenJobDescription, jobStructuredContent, resumeStructuredContent, selection);
    }

    @Override
    public EvidenceMatchOutcomeDTO match(
            Long userId,
            Long optimizationTaskId,
            String frozenJobDescription,
            String jobStructuredContent,
            String resumeStructuredContent,
            AiSelectionSnapshot selection) {
        EvidenceMatchPromptDTO prompt = evidenceMatchPromptService.buildPrompt(
                jobStructuredContent,
                resumeStructuredContent);
        String trustedPolicy = prompt.getSystemPrompt() == null || prompt.getSystemPrompt().isBlank()
                ? "只遵循服务端输出契约，不得编造简历事实。"
                : prompt.getSystemPrompt();
        String untrustedData = prompt.getUserPrompt() == null || prompt.getUserPrompt().isBlank()
                ? prompt.getPrompt()
                : prompt.getUserPrompt();
        AiCompletionResult completion;
        try {
            completion = AiGatewaySupport.complete(
                    aiGateway,
                    new AiInvocationContext(userId, optimizationTaskId, "EVIDENCE_MATCH", selection),
                    new AiGatewayRequest("EVIDENCE_MATCH", trustedPolicy, untrustedData));
        } catch (AiGatewayException exception) {
            log.warn("Evidence match Gateway call failed: code={}", exception.getFailureCode());
            if (selection != null && selection.isUserByok()) {
                throw exception;
            }
            throw new BusinessException(502, "AI 服务暂时不可用，请稍后重试");
        } catch (AiClientException exception) {
            log.warn("Evidence match AI call failed: reason={}", safeReason(exception));
            throw new BusinessException(502, "AI 服务暂时不可用，请稍后重试");
        } catch (RuntimeException exception) {
            log.warn("Evidence match AI call failed");
            throw new BusinessException(502, "AI 服务暂时不可用，请稍后重试");
        }
        EvidenceMatchOutcomeDTO parsed = evidenceMatchOutputParser.parse(
                completion.text(),
                frozenJobDescription,
                jobStructuredContent,
                resumeStructuredContent);
        return EvidenceMatchOutcomeDTO.builder()
                .requirements(parsed.getRequirements())
                .modelName(completion.model())
                .promptVersion(prompt.getPromptVersion())
                .build();
    }

    private String safeReason(RuntimeException exception) {
        return exception instanceof AiGatewayException
                ? exception.getMessage()
                : "provider call failed";
    }
}
