package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.PromptTemplateService;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.service.AiResumeSuggestionPromptService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiResumeSuggestionPromptServiceImpl implements AiResumeSuggestionPromptService {

    private static final String TEMPLATE_PATH = "prompts/job-suggestion-v1.md";
    private static final int MAX_RESUME_STRUCTURED_LENGTH = 3500;
    private static final int MAX_JOB_STRUCTURED_LENGTH = 2500;
    private static final int MAX_MATCH_RESULT_LENGTH = 3000;
    private static final int MAX_RAG_CONTEXT_LENGTH = 1800;

    private final PromptTemplateService promptTemplateService;

    public AiResumeSuggestionPromptServiceImpl() {
        this(new PromptTemplateService());
    }

    public AiResumeSuggestionPromptServiceImpl(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @Override
    public AiResumeSuggestionPromptDTO buildPrompt(
            String resumeStructuredContent,
            String jobStructuredContent,
            String aiMatchResult,
            String ragContext) {
        if (resumeStructuredContent == null || resumeStructuredContent.isBlank()) {
            throw new BusinessException(400, "简历结构化解析结果不能为空");
        }
        if (jobStructuredContent == null || jobStructuredContent.isBlank()) {
            throw new BusinessException(400, "目标岗位结构化解析结果不能为空");
        }
        if (aiMatchResult == null || aiMatchResult.isBlank()) {
            throw new BusinessException(400, "AI 匹配结果不能为空");
        }

        return AiResumeSuggestionPromptDTO.builder()
                .promptVersion(PROMPT_VERSION)
                .prompt(promptTemplateService.render(TEMPLATE_PATH, Map.of(
                        "resumeStructuredContent", normalize(resumeStructuredContent, MAX_RESUME_STRUCTURED_LENGTH),
                        "jobStructuredContent", normalize(jobStructuredContent, MAX_JOB_STRUCTURED_LENGTH),
                        "aiMatchResult", normalize(aiMatchResult, MAX_MATCH_RESULT_LENGTH),
                        "ragContext", normalizeOptional(ragContext, MAX_RAG_CONTEXT_LENGTH))))
                .build();
    }

    private String normalize(String value, int maxLength) {
        return truncate(value.strip().replace("\r\n", "\n").replace('\r', '\n'), maxLength);
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "未使用";
        }
        return normalize(value, maxLength);
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n[内容过长，已截断]";
    }
}
