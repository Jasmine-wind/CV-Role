package com.winter.airesumeoptimizer.module.job.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.PromptTemplateService;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionPromptDTO;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionPromptService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class JobDescriptionPromptServiceImpl implements JobDescriptionPromptService {

    private static final String TEMPLATE_PATH = "prompts/target-job-parse-v1.md";
    private static final int MAX_RAW_TEXT_LENGTH = 8000;

    private final PromptTemplateService promptTemplateService;

    public JobDescriptionPromptServiceImpl() {
        this(new PromptTemplateService());
    }

    public JobDescriptionPromptServiceImpl(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @Override
    public JobDescriptionPromptDTO buildPrompt(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new BusinessException(400, "目标岗位 JD 原文不能为空");
        }

        String rendered = promptTemplateService.render(TEMPLATE_PATH, Map.of(
                "rawText", normalizeRawText(rawText)));
        String boundary = "目标岗位 JD 原文：";
        int boundaryIndex = rendered.indexOf(boundary);
        String systemPrompt = boundaryIndex < 0 ? rendered : rendered.substring(0, boundaryIndex).strip();
        String userPrompt = boundaryIndex < 0 ? "" : rendered.substring(boundaryIndex).strip();
        return JobDescriptionPromptDTO.builder()
                .promptVersion(PROMPT_VERSION)
                .prompt(rendered)
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .build();
    }

    private String normalizeRawText(String rawText) {
        return truncate(rawText.strip().replace("\r\n", "\n").replace('\r', '\n'), MAX_RAW_TEXT_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n[内容过长，已截断]";
    }
}
