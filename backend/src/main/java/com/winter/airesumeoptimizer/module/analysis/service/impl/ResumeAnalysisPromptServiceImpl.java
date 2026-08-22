package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.PromptTemplateService;
import com.winter.airesumeoptimizer.module.analysis.dto.ResumeAnalysisPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.service.ResumeAnalysisPromptService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ResumeAnalysisPromptServiceImpl implements ResumeAnalysisPromptService {

    private static final String TEMPLATE_PATH = "prompts/resume-diagnosis-v1.md";
    private static final int MAX_EXTRACTED_TEXT_LENGTH = 6000;
    private static final int MAX_STRUCTURED_JSON_LENGTH = 3000;

    private final PromptTemplateService promptTemplateService;

    public ResumeAnalysisPromptServiceImpl() {
        this(new PromptTemplateService());
    }

    public ResumeAnalysisPromptServiceImpl(PromptTemplateService promptTemplateService) {
        this.promptTemplateService = promptTemplateService;
    }

    @Override
    public ResumeAnalysisPromptDTO buildPrompt(String extractedText, String structuredJson) {
        if (extractedText == null || extractedText.isBlank()) {
            throw new BusinessException(400, "简历解析文本不能为空");
        }

        String rendered = promptTemplateService.render(TEMPLATE_PATH, Map.of(
                "structuredJson", normalizeStructuredJson(structuredJson),
                "extractedText", normalizeExtractedText(extractedText)));
        String boundary = "简历结构化解析 JSON：";
        int boundaryIndex = rendered.indexOf(boundary);
        String systemPrompt = boundaryIndex < 0 ? rendered : rendered.substring(0, boundaryIndex).strip();
        String userPrompt = boundaryIndex < 0 ? "" : rendered.substring(boundaryIndex).strip();
        return ResumeAnalysisPromptDTO.builder()
                .promptVersion(PROMPT_VERSION)
                .prompt(rendered)
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .build();
    }

    private String normalizeStructuredJson(String structuredJson) {
        if (structuredJson == null || structuredJson.isBlank()) {
            return "{}";
        }
        return truncate(structuredJson.strip(), MAX_STRUCTURED_JSON_LENGTH);
    }

    private String normalizeExtractedText(String extractedText) {
        return truncate(mergeBrokenLines(extractedText.strip()), MAX_EXTRACTED_TEXT_LENGTH);
    }

    private String mergeBrokenLines(String text) {
        StringBuilder result = new StringBuilder();
        String previous = "";

        for (String rawLine : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            String line = rawLine.strip().replaceAll("\\s+", " ");
            if (line.isBlank()) {
                appendBlankLine(result);
                previous = "";
                continue;
            }

            if (isSectionHeading(line)) {
                appendLineBreak(result);
                result.append(line);
                previous = line;
                continue;
            }

            if (shouldMerge(previous, line)) {
                result.append(' ');
                result.append(line);
            } else {
                appendLineBreak(result);
                result.append(line);
            }
            previous = line;
        }

        return result.toString().strip();
    }

    private boolean shouldMerge(String previous, String current) {
        if (previous.isBlank() || isSectionHeading(previous) || isSectionHeading(current)) {
            return false;
        }
        if (previous.endsWith("。") || previous.endsWith("；") || previous.endsWith(";")
                || previous.endsWith(":") || previous.endsWith("：")) {
            return false;
        }
        if (current.startsWith("-") || current.startsWith("*") || current.startsWith("•")) {
            return false;
        }
        return previous.length() <= 28 && current.length() <= 28;
    }

    private boolean isSectionHeading(String line) {
        String normalized = line.replaceAll("[:：\\s]", "");
        return normalized.matches("(个人信息|基本信息|教育经历|教育背景|项目经历|项目经验|实习经历|工作经历|工作经验|专业技能|技能清单|技能|自我评价|荣誉奖项)");
    }

    private void appendBlankLine(StringBuilder result) {
        if (!result.isEmpty() && !result.toString().endsWith("\n\n")) {
            result.append("\n\n");
        }
    }

    private void appendLineBreak(StringBuilder result) {
        if (!result.isEmpty() && !result.toString().endsWith("\n")) {
            result.append('\n');
        }
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n[内容过长，已截断]";
    }
}
