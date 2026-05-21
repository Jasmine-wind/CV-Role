package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.PromptTemplateService;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionPromptService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AiRewriteSuggestionPromptServiceImpl implements AiRewriteSuggestionPromptService {

    private static final String TEMPLATE_PATH = "prompts/rewrite-suggestion-v1.md";
    private static final int MAX_ORIGINAL_TEXT_LENGTH = 2000;
    private static final int MAX_REWRITE_TYPE_LENGTH = 30;
    private static final int MAX_TARGET_SECTION_LENGTH = 100;
    private static final int MAX_JOB_STRUCTURED_LENGTH = 2500;
    private static final int MAX_MATCH_RESULT_LENGTH = 2500;
    private static final int MAX_AI_SUGGESTION_LENGTH = 2500;
    private static final int MAX_REWRITE_GOAL_LENGTH = 200;
    private static final int MAX_JOB_KEYWORDS_LENGTH = 600;
    private static final int MAX_TONE_LENGTH = 40;

    private final PromptTemplateService promptTemplateService;

    public AiRewriteSuggestionPromptServiceImpl() {
        this(new PromptTemplateService());
    }

    public AiRewriteSuggestionPromptServiceImpl(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @Override
    public AiRewriteSuggestionPromptDTO buildPrompt(
            String originalText,
            String rewriteType,
            String targetSection,
            String jobStructuredContent,
            String aiMatchResult,
            String aiSuggestion,
            String rewriteGoal,
            List<String> jobKeywords,
            String tone,
            Integer lengthLimit) {
        if (originalText == null || originalText.isBlank()) {
            throw new BusinessException(400, "原文片段不能为空");
        }
        if (rewriteType == null || rewriteType.isBlank()) {
            throw new BusinessException(400, "改写对象类型不能为空");
        }
        if (targetSection == null || targetSection.isBlank()) {
            throw new BusinessException(400, "目标简历部分不能为空");
        }

        return AiRewriteSuggestionPromptDTO.builder()
                .promptVersion(PROMPT_VERSION)
                .prompt(promptTemplateService.render(TEMPLATE_PATH, Map.of(
                        "originalText", normalize(originalText, MAX_ORIGINAL_TEXT_LENGTH),
                        "rewriteType", normalize(rewriteType, MAX_REWRITE_TYPE_LENGTH),
                        "targetSection", normalize(targetSection, MAX_TARGET_SECTION_LENGTH),
                        "jobStructuredContent", normalizeOptional(jobStructuredContent, MAX_JOB_STRUCTURED_LENGTH),
                        "aiMatchResult", normalizeOptional(aiMatchResult, MAX_MATCH_RESULT_LENGTH),
                        "aiSuggestion", normalizeOptional(aiSuggestion, MAX_AI_SUGGESTION_LENGTH),
                        "rewriteGoal", normalizeOptional(rewriteGoal, MAX_REWRITE_GOAL_LENGTH),
                        "jobKeywords", normalizeOptional(formatKeywords(jobKeywords), MAX_JOB_KEYWORDS_LENGTH),
                        "tone", normalizeOptional(tone, MAX_TONE_LENGTH),
                        "lengthLimit", formatLengthLimit(lengthLimit))))
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

    private String formatKeywords(List<String> jobKeywords) {
        if (jobKeywords == null || jobKeywords.isEmpty()) {
            return null;
        }
        return String.join("、", jobKeywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::strip)
                .distinct()
                .limit(20)
                .toList());
    }

    private String formatLengthLimit(Integer lengthLimit) {
        if (lengthLimit == null || lengthLimit <= 0) {
            return "未提供";
        }
        return lengthLimit + " 字以内";
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n[内容过长，已截断]";
    }
}
