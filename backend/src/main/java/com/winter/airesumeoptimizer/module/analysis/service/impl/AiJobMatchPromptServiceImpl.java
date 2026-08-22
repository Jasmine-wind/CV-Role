package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.PromptTemplateService;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchPromptService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiJobMatchPromptServiceImpl implements AiJobMatchPromptService {

    private static final String TEMPLATE_PATH = "prompts/match-analysis-v1.md";
    private static final int MAX_RESUME_STRUCTURED_LENGTH = 3500;
    private static final int MAX_JOB_STRUCTURED_LENGTH = 2500;
    private static final int MAX_RESUME_SUMMARY_LENGTH = 800;
    private static final int MAX_RAG_CONTEXT_LENGTH = 1800;

    private final PromptTemplateService promptTemplateService;

    public AiJobMatchPromptServiceImpl() {
        this(new PromptTemplateService());
    }

    public AiJobMatchPromptServiceImpl(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @Override
    public AiJobMatchPromptDTO buildPrompt(
            String resumeStructuredContent,
            String jobStructuredContent,
            String resumeRawTextSummary,
            String ragContext) {
        if (resumeStructuredContent == null || resumeStructuredContent.isBlank()) {
            throw new BusinessException(400, "简历结构化解析结果不能为空");
        }
        if (jobStructuredContent == null || jobStructuredContent.isBlank()) {
            throw new BusinessException(400, "目标岗位结构化解析结果不能为空");
        }

        String rendered = promptTemplateService.render(TEMPLATE_PATH, Map.of(
                "resumeStructuredContent", normalize(resumeStructuredContent, MAX_RESUME_STRUCTURED_LENGTH),
                "jobStructuredContent", normalize(jobStructuredContent, MAX_JOB_STRUCTURED_LENGTH),
                "resumeRawTextSummary", normalizeOptional(resumeRawTextSummary, MAX_RESUME_SUMMARY_LENGTH),
                "ragContext", normalizeOptional(ragContext, MAX_RAG_CONTEXT_LENGTH)));
        String boundary = "简历结构化解析结果：";
        int boundaryIndex = rendered.indexOf(boundary);
        String systemPrompt = boundaryIndex < 0 ? rendered : rendered.substring(0, boundaryIndex).strip();
        String userPrompt = boundaryIndex < 0 ? "" : rendered.substring(boundaryIndex).strip();
        return AiJobMatchPromptDTO.builder()
                .promptVersion(PROMPT_VERSION)
                .prompt(rendered)
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .build();
    }

    private String normalize(String value, int maxLength) {
        return truncate(value.strip().replace("\r\n", "\n").replace('\r', '\n'), maxLength);
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "未提供";
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
