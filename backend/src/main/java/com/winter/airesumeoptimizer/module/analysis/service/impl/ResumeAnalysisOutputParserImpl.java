package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.ResumeAnalysisResultDTO;
import com.winter.airesumeoptimizer.module.analysis.service.ResumeAnalysisOutputParser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ResumeAnalysisOutputParserImpl implements ResumeAnalysisOutputParser {

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;
    private static final int MAX_LIST_SIZE = 5;
    private static final int MAX_TEXT_ITEM_LENGTH = 80;

    private final ObjectMapper objectMapper;

    public ResumeAnalysisOutputParserImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ResumeAnalysisResultDTO parse(String aiOutput) {
        JsonNode root = readJson(aiOutput);
        if (!root.isObject()) {
            throw invalidOutput("AI 分析结果必须是 JSON 对象");
        }

        return ResumeAnalysisResultDTO.builder()
                .score(normalizeScore(root.get("score")))
                .strengths(readTextList(root.get("strengths"), "简历优势信息不足"))
                .problems(readTextList(root.get("problems"), "简历问题信息不足"))
                .suggestionsSummary(readTextList(
                        root.get("suggestionsSummary"),
                        "建议补充更完整的简历内容后重新分析"))
                .build();
    }

    private JsonNode readJson(String aiOutput) {
        if (aiOutput == null || aiOutput.isBlank()) {
            throw invalidOutput("AI 分析结果不能为空");
        }
        try {
            return objectMapper.readTree(stripJsonCodeFence(aiOutput.strip()));
        } catch (JsonProcessingException exception) {
            throw invalidOutput("AI 分析结果不是合法 JSON");
        }
    }

    private String stripJsonCodeFence(String value) {
        if (!value.startsWith("```")) {
            return value;
        }
        String stripped = value.replaceFirst("^```(?:json)?\\s*", "");
        return stripped.replaceFirst("\\s*```$", "").strip();
    }

    private Integer normalizeScore(JsonNode scoreNode) {
        if (scoreNode == null || scoreNode.isNull()) {
            return 0;
        }
        int score;
        if (scoreNode.isInt()) {
            score = scoreNode.asInt();
        } else if (scoreNode.isTextual() && scoreNode.asText().matches("-?\\d+")) {
            score = Integer.parseInt(scoreNode.asText());
        } else {
            return 0;
        }
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
    }

    private List<String> readTextList(JsonNode node, String fallback) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of(fallback);
        }

        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                continue;
            }
            String text = item.asText().strip();
            if (!text.isBlank()) {
                values.add(truncateTextItem(text));
            }
            if (values.size() == MAX_LIST_SIZE) {
                break;
            }
        }

        if (values.isEmpty()) {
            return List.of(fallback);
        }
        return List.copyOf(values);
    }

    private BusinessException invalidOutput(String message) {
        return new BusinessException(502, message);
    }

    private String truncateTextItem(String text) {
        if (text.length() <= MAX_TEXT_ITEM_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_ITEM_LENGTH);
    }
}
