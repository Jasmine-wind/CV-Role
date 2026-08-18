package com.winter.airesumeoptimizer.module.evidence.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.PromptTemplateService;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchPromptDTO;
import com.winter.airesumeoptimizer.module.evidence.service.EvidenceMatchPromptService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EvidenceMatchPromptServiceImpl implements EvidenceMatchPromptService {

    private static final String TEMPLATE_PATH = "prompts/evidence-match-v1.md";
    private static final int MAX_JOB_STRUCTURED_LENGTH = 2500;
    private static final int MAX_RESUME_STRUCTURED_LENGTH = 4500;

    private final PromptTemplateService promptTemplateService;

    public EvidenceMatchPromptServiceImpl(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @Override
    public EvidenceMatchPromptDTO buildPrompt(String jobStructuredContent, String resumeStructuredContent) {
        if (jobStructuredContent == null || jobStructuredContent.isBlank()) {
            throw new BusinessException(400, "目标岗位结构化解析结果不能为空");
        }
        if (resumeStructuredContent == null || resumeStructuredContent.isBlank()) {
            throw new BusinessException(400, "简历结构化解析结果不能为空");
        }

        return EvidenceMatchPromptDTO.builder()
                .promptVersion(PROMPT_VERSION)
                .prompt(promptTemplateService.render(TEMPLATE_PATH, Map.of(
                        "jobStructuredContent", normalize(jobStructuredContent, MAX_JOB_STRUCTURED_LENGTH),
                        "resumeStructuredContent", normalize(resumeStructuredContent, MAX_RESUME_STRUCTURED_LENGTH))))
                .build();
    }

    private String normalize(String value, int maxLength) {
        return truncate(value.strip().replace("\r\n", "\n").replace('\r', '\n'), maxLength);
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n[内容过长，已截断]";
    }
}
