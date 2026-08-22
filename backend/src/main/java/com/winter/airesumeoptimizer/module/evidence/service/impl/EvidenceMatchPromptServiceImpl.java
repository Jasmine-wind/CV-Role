package com.winter.airesumeoptimizer.module.evidence.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.PromptTemplateService;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchPromptDTO;
import com.winter.airesumeoptimizer.module.evidence.service.EvidenceMatchPromptService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EvidenceMatchPromptServiceImpl implements EvidenceMatchPromptService {

    private static final String TEMPLATE_PATH = "prompts/evidence-match-v3.md";
    private static final int MAX_JOB_STRUCTURED_LENGTH = 2500;
    private static final int MAX_RESUME_STRUCTURED_LENGTH = 24_000;
    private static final List<String> RESUME_EVIDENCE_FIELDS = List.of(
            "rawText", "summary", "education", "skills", "projects", "workExperiences", "internships",
            "campusExperiences", "awards", "certificates", "others", "structuredData", "displayModel");

    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    public EvidenceMatchPromptServiceImpl(
            PromptTemplateService promptTemplateService,
            ObjectMapper objectMapper) {
        this.promptTemplateService = promptTemplateService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EvidenceMatchPromptDTO buildPrompt(String jobStructuredContent, String resumeStructuredContent) {
        if (jobStructuredContent == null || jobStructuredContent.isBlank()) {
            throw new BusinessException(400, "目标岗位结构化解析结果不能为空");
        }
        if (resumeStructuredContent == null || resumeStructuredContent.isBlank()) {
            throw new BusinessException(400, "简历结构化解析结果不能为空");
        }

        String rendered = promptTemplateService.render(TEMPLATE_PATH, Map.of(
                "jobStructuredContent", normalize(jobStructuredContent, MAX_JOB_STRUCTURED_LENGTH),
                "resumeStructuredContent", resumeEvidenceContent(resumeStructuredContent)));
        String boundary = "目标岗位结构化解析结果：";
        int boundaryIndex = rendered.indexOf(boundary);
        String systemPrompt = boundaryIndex < 0 ? rendered : rendered.substring(0, boundaryIndex).strip();
        String userPrompt = boundaryIndex < 0 ? "" : rendered.substring(boundaryIndex).strip();
        return EvidenceMatchPromptDTO.builder()
                .promptVersion(PROMPT_VERSION)
                .prompt(rendered)
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .build();
    }

    private String resumeEvidenceContent(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            if (root == null || !root.isObject()) {
                return normalizeResumeEvidence(value);
            }
            ObjectNode selected = objectMapper.createObjectNode();
            for (String field : RESUME_EVIDENCE_FIELDS) {
                JsonNode content = root.get(field);
                if (content != null && !content.isNull()) {
                    selected.set(field, content);
                }
            }
            String evidenceContent = selected.isEmpty() ? value : selected.toString();
            return normalizeResumeEvidence(evidenceContent);
        } catch (Exception exception) {
            return normalizeResumeEvidence(value);
        }
    }

    private String normalizeResumeEvidence(String value) {
        String normalized = value.strip().replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.length() <= MAX_RESUME_STRUCTURED_LENGTH) {
            return normalized;
        }
        int headLength = MAX_RESUME_STRUCTURED_LENGTH / 2;
        int tailLength = MAX_RESUME_STRUCTURED_LENGTH - headLength;
        return normalized.substring(0, headLength)
                + "\n[内容过长，中间部分已截断]\n"
                + normalized.substring(normalized.length() - tailLength);
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
