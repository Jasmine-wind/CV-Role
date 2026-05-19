package com.winter.airesumeoptimizer.module.analysis.rewrite.service.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionResultDTO;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionOutputParser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiRewriteSuggestionOutputParserImpl implements AiRewriteSuggestionOutputParser {

    private static final int MAX_TEXT_LENGTH = 3000;
    private static final int MAX_QUESTION_LENGTH = 300;
    private static final int MAX_QUESTION_SIZE = 5;

    private final ObjectReader lenientJsonReader;

    public AiRewriteSuggestionOutputParserImpl(ObjectMapper objectMapper) {
        this.lenientJsonReader = objectMapper.reader()
                .with(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
                .with(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
                .with(JsonParser.Feature.ALLOW_COMMENTS)
                .with(JsonParser.Feature.ALLOW_TRAILING_COMMA);
    }

    @Override
    public AiRewriteSuggestionResultDTO parse(String aiOutput) {
        JsonNode root = readJson(aiOutput);
        if (!root.isObject()) {
            throw invalidOutput("AI 局部改写结果必须是 JSON 对象");
        }

        String rewrittenText = readRequiredText(root.get("rewrittenText"), "rewrittenText");
        String rewriteReason = readRequiredText(root.get("rewriteReason"), "rewriteReason");
        String caution = readRequiredText(root.get("caution"), "caution");
        Boolean needUserSupplement = readRequiredBoolean(root.get("needUserSupplement"), "needUserSupplement");
        List<String> supplementQuestions = readTextList(root.get("supplementQuestions"));
        if (Boolean.TRUE.equals(needUserSupplement) && supplementQuestions.isEmpty()) {
            throw invalidOutput("AI 局部改写结果需要补充信息时必须提供 supplementQuestions");
        }

        return AiRewriteSuggestionResultDTO.builder()
                .rewrittenText(rewrittenText)
                .rewriteReason(rewriteReason)
                .caution(caution)
                .needUserSupplement(needUserSupplement)
                .supplementQuestions(supplementQuestions)
                .build();
    }

    private JsonNode readJson(String aiOutput) {
        if (aiOutput == null || aiOutput.isBlank()) {
            throw invalidOutput("AI 局部改写结果不能为空");
        }
        for (String candidate : extractJsonCandidates(aiOutput.strip())) {
            JsonNode root = tryReadJson(candidate);
            if (root != null && root.isObject() && root.has("rewrittenText")) {
                return root;
            }
        }
        throw invalidOutput("AI 局部改写结果不是合法 JSON");
    }

    private List<String> extractJsonCandidates(String value) {
        String stripped = stripJsonCodeFence(value);
        List<String> candidates = new ArrayList<>();
        if (stripped.startsWith("{") && stripped.endsWith("}")) {
            candidates.add(stripped);
        }
        candidates.addAll(findBalancedJsonObjects(stripped));
        if (candidates.isEmpty()) {
            candidates.add(stripped);
        }
        return candidates;
    }

    private String stripJsonCodeFence(String value) {
        String stripped = value.strip();
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceFirst("^```(?:json|JSON)?\\s*", "");
            stripped = stripped.replaceFirst("\\s*```$", "").strip();
        }
        return stripped;
    }

    private List<String> findBalancedJsonObjects(String value) {
        List<String> candidates = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        int start = -1;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = inString;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                if (depth == 0) {
                    start = index;
                }
                depth++;
            } else if (current == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        candidates.add(value.substring(start, index + 1));
                        start = -1;
                    }
                }
            }
        }
        return candidates;
    }

    private JsonNode tryReadJson(String value) {
        try {
            return lenientJsonReader.readTree(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private String readRequiredText(JsonNode node, String fieldName) {
        String text = readText(node, MAX_TEXT_LENGTH);
        if (text.isBlank()) {
            throw invalidOutput("AI 局部改写结果缺少 " + fieldName);
        }
        return text;
    }

    private Boolean readRequiredBoolean(JsonNode node, String fieldName) {
        if (node == null || node.isNull() || !node.isBoolean()) {
            throw invalidOutput("AI 局部改写结果缺少 " + fieldName);
        }
        return node.asBoolean();
    }

    private List<String> readTextList(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String text = readText(item, MAX_QUESTION_LENGTH);
            if (!text.isBlank()) {
                values.add(text);
            }
            if (values.size() == MAX_QUESTION_SIZE) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private String readText(JsonNode node, int maxLength) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return "";
        }
        String text = node.asText().strip();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private BusinessException invalidOutput(String message) {
        return new BusinessException(502, message);
    }
}
