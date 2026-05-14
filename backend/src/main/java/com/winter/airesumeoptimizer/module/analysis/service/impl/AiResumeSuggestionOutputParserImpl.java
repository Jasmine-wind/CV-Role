package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionItemDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionResultDTO;
import com.winter.airesumeoptimizer.module.analysis.service.AiResumeSuggestionOutputParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AiResumeSuggestionOutputParserImpl implements AiResumeSuggestionOutputParser {

    private static final int MAX_SUGGESTION_SIZE = 8;
    private static final int MAX_LIST_SIZE = 8;
    private static final int MAX_TEXT_ITEM_LENGTH = 300;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "SKILL_GAP",
            "EXPERIENCE_WEAKNESS",
            "PROJECT_DESCRIPTION",
            "HIGHLIGHT_STRENGTH",
            "STRUCTURE",
            "GENERAL");
    private static final Set<String> ALLOWED_PRIORITIES = Set.of("HIGH", "MEDIUM", "LOW");

    private final ObjectReader lenientJsonReader;

    public AiResumeSuggestionOutputParserImpl(ObjectMapper objectMapper) {
        this.lenientJsonReader = objectMapper.reader()
                .with(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
                .with(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
                .with(JsonParser.Feature.ALLOW_COMMENTS)
                .with(JsonParser.Feature.ALLOW_TRAILING_COMMA);
    }

    @Override
    public AiResumeSuggestionResultDTO parse(String aiOutput) {
        JsonNode root = readJson(aiOutput);
        if (!root.isObject()) {
            throw invalidOutput("AI 优化建议结果必须是 JSON 对象");
        }
        JsonNode suggestionsNode = root.get("suggestions");
        if (suggestionsNode == null || suggestionsNode.isNull() || !suggestionsNode.isArray()) {
            throw invalidOutput("AI 优化建议结果缺少 suggestions 数组");
        }
        return AiResumeSuggestionResultDTO.builder()
                .suggestions(readSuggestions(suggestionsNode))
                .build();
    }

    private JsonNode readJson(String aiOutput) {
        if (aiOutput == null || aiOutput.isBlank()) {
            throw invalidOutput("AI 优化建议结果不能为空");
        }
        for (String candidate : extractJsonCandidates(aiOutput.strip())) {
            JsonNode root = tryReadJson(candidate);
            if (root != null && root.isObject() && root.has("suggestions")) {
                return root;
            }
        }
        throw invalidOutput("AI 优化建议结果不是合法 JSON");
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

    private List<AiResumeSuggestionItemDTO> readSuggestions(JsonNode node) {
        List<AiResumeSuggestionItemDTO> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                throw invalidOutput("单条优化建议必须是 JSON 对象");
            }
            values.add(readSuggestion(item));
            if (values.size() == MAX_SUGGESTION_SIZE) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private AiResumeSuggestionItemDTO readSuggestion(JsonNode item) {
        String type = readRequiredText(item.get("type"), "type");
        if (!ALLOWED_TYPES.contains(type)) {
            throw invalidOutput("优化建议 type 不合法");
        }
        String priority = readRequiredText(item.get("priority"), "priority");
        if (!ALLOWED_PRIORITIES.contains(priority)) {
            throw invalidOutput("优化建议 priority 不合法");
        }
        String issue = readRequiredText(item.get("issue"), "issue");
        String suggestion = readRequiredText(item.get("suggestion"), "suggestion");
        List<String> evidence = readRequiredTextList(item.get("evidence"), "evidence");
        return AiResumeSuggestionItemDTO.builder()
                .type(type)
                .priority(priority)
                .targetSection(readText(item.get("targetSection")))
                .issue(issue)
                .suggestion(suggestion)
                .evidence(evidence)
                .caution(readText(item.get("caution")))
                .relatedItems(readTextList(item.get("relatedItems")))
                .build();
    }

    private String readRequiredText(JsonNode node, String fieldName) {
        String text = readText(node);
        if (text.isBlank()) {
            throw invalidOutput("优化建议缺少 " + fieldName);
        }
        return text;
    }

    private List<String> readRequiredTextList(JsonNode node, String fieldName) {
        List<String> values = readTextList(node);
        if (values.isEmpty()) {
            throw invalidOutput("优化建议缺少 " + fieldName);
        }
        return values;
    }

    private List<String> readTextList(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            String text = readText(item);
            if (!text.isBlank()) {
                values.add(text);
            }
            if (values.size() == MAX_LIST_SIZE) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private String readText(JsonNode node) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return "";
        }
        String text = node.asText().strip();
        if (text.length() <= MAX_TEXT_ITEM_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_ITEM_LENGTH);
    }

    private BusinessException invalidOutput(String message) {
        return new BusinessException(502, message);
    }
}
