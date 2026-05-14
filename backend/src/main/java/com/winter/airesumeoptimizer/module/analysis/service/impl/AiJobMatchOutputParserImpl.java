package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchEvidenceDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchItemDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchResultDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchWeakExperienceDTO;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchOutputParser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiJobMatchOutputParserImpl implements AiJobMatchOutputParser {

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;
    private static final int MAX_LIST_SIZE = 8;
    private static final int MAX_TEXT_ITEM_LENGTH = 120;

    private final ObjectMapper objectMapper;
    private final ObjectReader lenientJsonReader;

    public AiJobMatchOutputParserImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.lenientJsonReader = objectMapper.reader()
                .with(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
                .with(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
                .with(JsonParser.Feature.ALLOW_COMMENTS)
                .with(JsonParser.Feature.ALLOW_TRAILING_COMMA);
    }

    @Override
    public AiJobMatchResultDTO parse(String aiOutput) {
        JsonNode root = readJson(aiOutput);
        if (!root.isObject()) {
            throw invalidOutput("AI 匹配结果必须是 JSON 对象");
        }

        return AiJobMatchResultDTO.builder()
                .overallScore(readScore(root.get("overallScore")))
                .strongMatches(readMatchItems(root.get("strongMatches")))
                .weakMatches(readMatchItems(root.get("weakMatches")))
                .missingSkills(readMatchItems(root.get("missingSkills")))
                .weakExperienceDescriptions(readWeakExperiences(root.get("weakExperienceDescriptions")))
                .evidence(readEvidence(root.get("evidence")))
                .riskNotes(readTextList(root.get("riskNotes")))
                .build();
    }

    private JsonNode readJson(String aiOutput) {
        if (aiOutput == null || aiOutput.isBlank()) {
            throw invalidOutput("AI 匹配结果不能为空");
        }
        for (String candidate : extractJsonCandidates(aiOutput.strip())) {
            JsonNode root = tryReadJson(candidate);
            if (root != null && root.isObject() && root.has("overallScore")) {
                return root;
            }
        }
        throw invalidOutput("AI 匹配结果不是合法 JSON");
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

    private Integer readScore(JsonNode node) {
        if (node == null || node.isNull()) {
            throw invalidOutput("AI 匹配结果缺少 overallScore");
        }
        int score;
        if (node.isInt()) {
            score = node.asInt();
        } else if (node.isTextual() && node.asText().matches("-?\\d+")) {
            score = Integer.parseInt(node.asText());
        } else {
            throw invalidOutput("overallScore 必须是整数");
        }
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw invalidOutput("overallScore 必须在 0 到 100 之间");
        }
        return score;
    }

    private List<AiJobMatchItemDTO> readMatchItems(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<AiJobMatchItemDTO> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            String name = readText(item.get("item"));
            String reason = readText(item.get("reason"));
            if (!name.isBlank() || !reason.isBlank()) {
                values.add(AiJobMatchItemDTO.builder()
                        .item(name)
                        .reason(reason)
                        .build());
            }
            if (values.size() == MAX_LIST_SIZE) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private List<AiJobMatchWeakExperienceDTO> readWeakExperiences(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<AiJobMatchWeakExperienceDTO> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            String section = readText(item.get("section"));
            String issue = readText(item.get("issue"));
            if (!section.isBlank() || !issue.isBlank()) {
                values.add(AiJobMatchWeakExperienceDTO.builder()
                        .section(section)
                        .issue(issue)
                        .build());
            }
            if (values.size() == MAX_LIST_SIZE) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private List<AiJobMatchEvidenceDTO> readEvidence(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<AiJobMatchEvidenceDTO> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            String source = normalizeEvidenceSource(readText(item.get("source")));
            String content = readText(item.get("content"));
            if (!content.isBlank()) {
                values.add(AiJobMatchEvidenceDTO.builder()
                        .source(source)
                        .content(content)
                        .build());
            }
            if (values.size() == MAX_LIST_SIZE) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private String normalizeEvidenceSource(String source) {
        if ("job".equals(source)) {
            return "job";
        }
        return "resume";
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
