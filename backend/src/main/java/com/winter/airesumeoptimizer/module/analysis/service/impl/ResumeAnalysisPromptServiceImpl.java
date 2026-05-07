package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.ResumeAnalysisPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.service.ResumeAnalysisPromptService;
import org.springframework.stereotype.Service;

@Service
public class ResumeAnalysisPromptServiceImpl implements ResumeAnalysisPromptService {

    private static final int MAX_EXTRACTED_TEXT_LENGTH = 6000;
    private static final int MAX_STRUCTURED_JSON_LENGTH = 3000;

    @Override
    public ResumeAnalysisPromptDTO buildPrompt(String extractedText, String structuredJson) {
        if (extractedText == null || extractedText.isBlank()) {
            throw new BusinessException(400, "简历解析文本不能为空");
        }

        return ResumeAnalysisPromptDTO.builder()
                .promptVersion(PROMPT_VERSION)
                .prompt("""
                        你是一个简历分析助手。请只根据下面提供的简历解析内容进行分析，不得编造用户不存在的学校、公司、项目、岗位、时间、奖项或技能经历。

                        输出要求：
                        1. 只能输出一个 JSON 对象，不要输出 Markdown、解释文字或代码块。
                        2. JSON 字段必须为 score、strengths、problems、suggestionsSummary。
                        3. score 必须是 0 到 100 的整数，表示简历完整度和综合表达质量。
                        4. strengths、problems、suggestionsSummary 必须是字符串数组，每个数组保留 1 到 5 条。
                        5. 如果简历内容不足，只能指出信息不足，不能补充不存在的经历。
                        6. 建议要具体、朴素，面向 Phase 1 的基础简历优化，不做岗位匹配。
                        7. 输出内容要简洁，每条不超过 40 个中文字。

                        输出 JSON 示例：
                        {
                          "score": 78,
                          "strengths": [
                            "具备 Java 基础和项目经历"
                          ],
                          "problems": [
                            "项目描述缺少个人职责和结果说明"
                          ],
                          "suggestionsSummary": [
                            "补充项目中的个人职责、技术实现和可验证结果"
                          ]
                        }

                        简历结构化解析 JSON：
                        %s

                        简历原始解析文本：
                        %s
                        """.formatted(
                        normalizeStructuredJson(structuredJson),
                        normalizeExtractedText(extractedText)))
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
