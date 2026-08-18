package com.winter.airesumeoptimizer.module.evidence.service.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchOutcomeDTO;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceQuoteDTO;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceRequirementEvaluationDTO;
import com.winter.airesumeoptimizer.module.evidence.enums.EvidenceExpressionStatus;
import com.winter.airesumeoptimizer.module.evidence.enums.EvidenceMatchLevel;
import com.winter.airesumeoptimizer.module.evidence.enums.RequirementImportance;
import com.winter.airesumeoptimizer.module.evidence.service.EvidenceMatchOutputParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class EvidenceMatchOutputParserImpl implements EvidenceMatchOutputParser {

    private static final int MAX_REQUIREMENT_COUNT = 10;
    private static final int MAX_EVIDENCE_PER_REQUIREMENT = 3;
    private static final int MAX_REQUIREMENT_TEXT_LENGTH = 500;
    private static final int MAX_SECTION_LABEL_LENGTH = 100;
    private static final int MAX_EVIDENCE_TEXT_LENGTH = 1000;
    private static final int MAX_CONCLUSION_LENGTH = 300;
    private static final int MAX_SUGGESTION_LENGTH = 300;
    private static final int MIN_QUOTE_LENGTH = 2;
    private static final String NO_EVIDENCE_CONCLUSION = "当前简历中没有找到与这条要求对应的内容。";
    private static final String NO_EVIDENCE_SUGGESTION = "请确认自己是否确有相关经历；如有，可自行在简历中补充真实内容。";

    private final ObjectMapper objectMapper;
    private final ObjectReader lenientJsonReader;

    public EvidenceMatchOutputParserImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.lenientJsonReader = objectMapper.reader()
                .with(JsonParser.Feature.ALLOW_SINGLE_QUOTES)
                .with(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
                .with(JsonParser.Feature.ALLOW_COMMENTS)
                .with(JsonParser.Feature.ALLOW_TRAILING_COMMA);
    }

    @Override
    public EvidenceMatchOutcomeDTO parse(String aiOutput, String resumeCorpus) {
        JsonNode root = readJson(aiOutput);
        if (!root.isObject()) {
            throw invalidOutput("岗位证据分析结果必须是 JSON 对象");
        }
        String normalizedCorpus = normalizeForContainment(resumeCorpus);

        JsonNode requirementsNode = root.get("requirements");
        if (requirementsNode == null || !requirementsNode.isArray()) {
            throw invalidOutput("岗位证据分析结果缺少 requirements");
        }

        Set<String> seenRequirementTexts = new HashSet<>();
        List<EvidenceRequirementEvaluationDTO> requirements = new ArrayList<>();
        for (JsonNode item : requirementsNode) {
            if (!item.isObject()) {
                continue;
            }
            EvidenceRequirementEvaluationDTO evaluation = readRequirement(item, normalizedCorpus);
            if (evaluation == null) {
                continue;
            }
            if (!seenRequirementTexts.add(normalizeForContainment(evaluation.getRequirementText()))) {
                continue;
            }
            requirements.add(evaluation);
            if (requirements.size() == MAX_REQUIREMENT_COUNT) {
                break;
            }
        }

        if (requirements.isEmpty()) {
            throw invalidOutput("岗位证据分析结果没有可用的岗位要求");
        }
        return EvidenceMatchOutcomeDTO.builder()
                .requirements(List.copyOf(requirements))
                .build();
    }

    private EvidenceRequirementEvaluationDTO readRequirement(JsonNode item, String normalizedCorpus) {
        String requirementText = readText(item.get("requirement"), MAX_REQUIREMENT_TEXT_LENGTH);
        EvidenceMatchLevel matchLevel = readMatchLevel(item.get("matchLevel"));
        if (requirementText.isBlank() || matchLevel == null) {
            return null;
        }

        List<EvidenceQuoteDTO> evidences = readEvidences(item.get("evidences"), normalizedCorpus);
        if (matchLevel != EvidenceMatchLevel.NO_EVIDENCE && evidences.isEmpty()) {
            // 证据未通过简历原文校核：不得保留“有证据”的结论，降级为无证据。
            return EvidenceRequirementEvaluationDTO.builder()
                    .requirementText(requirementText)
                    .importance(readImportance(item.get("importance")))
                    .matchLevel(EvidenceMatchLevel.NO_EVIDENCE)
                    .conclusion(NO_EVIDENCE_CONCLUSION)
                    .suggestion(NO_EVIDENCE_SUGGESTION)
                    .evidences(List.of())
                    .build();
        }

        EvidenceMatchLevel effectiveLevel = matchLevel == EvidenceMatchLevel.NO_EVIDENCE
                ? EvidenceMatchLevel.NO_EVIDENCE
                : matchLevel;
        List<EvidenceQuoteDTO> effectiveEvidences = matchLevel == EvidenceMatchLevel.NO_EVIDENCE
                ? List.of()
                : evidences;
        return EvidenceRequirementEvaluationDTO.builder()
                .requirementText(requirementText)
                .importance(readImportance(item.get("importance")))
                .matchLevel(effectiveLevel)
                .conclusion(readText(item.get("conclusion"), MAX_CONCLUSION_LENGTH))
                .suggestion(effectiveLevel == EvidenceMatchLevel.MATCHED
                        ? ""
                        : readText(item.get("suggestion"), MAX_SUGGESTION_LENGTH))
                .evidences(List.copyOf(effectiveEvidences))
                .build();
    }

    private List<EvidenceQuoteDTO> readEvidences(JsonNode node, String normalizedCorpus) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<EvidenceQuoteDTO> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            String quote = readText(item.get("quote"), MAX_EVIDENCE_TEXT_LENGTH);
            if (quote.length() < MIN_QUOTE_LENGTH || !containsVerbatim(normalizedCorpus, quote)) {
                continue;
            }
            values.add(EvidenceQuoteDTO.builder()
                    .sectionLabel(readText(item.get("section"), MAX_SECTION_LABEL_LENGTH))
                    .quote(quote)
                    .expressionStatus(readExpressionStatus(item.get("expression")))
                    .build());
            if (values.size() == MAX_EVIDENCE_PER_REQUIREMENT) {
                break;
            }
        }
        return List.copyOf(values);
    }

    private EvidenceMatchLevel readMatchLevel(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        try {
            return EvidenceMatchLevel.valueOf(node.asText().strip().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private RequirementImportance readImportance(JsonNode node) {
        if (node != null && node.isTextual()) {
            try {
                return RequirementImportance.valueOf(node.asText().strip().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through to default
            }
        }
        return RequirementImportance.REQUIRED;
    }

    private EvidenceExpressionStatus readExpressionStatus(JsonNode node) {
        if (node != null && node.isTextual()) {
            try {
                return EvidenceExpressionStatus.valueOf(node.asText().strip().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through to default
            }
        }
        return EvidenceExpressionStatus.WEAK;
    }

    private boolean containsVerbatim(String normalizedCorpus, String quote) {
        if (normalizedCorpus.isEmpty()) {
            return false;
        }
        return normalizedCorpus.contains(normalizeForContainment(quote));
    }

    private String normalizeForContainment(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!Character.isWhitespace(current)) {
                builder.append(Character.toLowerCase(current));
            }
        }
        return builder.toString();
    }

    private JsonNode readJson(String aiOutput) {
        if (aiOutput == null || aiOutput.isBlank()) {
            throw invalidOutput("岗位证据分析结果不能为空");
        }
        JsonNode firstObject = null;
        for (String candidate : extractJsonCandidates(aiOutput.strip())) {
            JsonNode root = tryReadJson(candidate);
            if (root == null || !root.isObject()) {
                continue;
            }
            if (root.has("requirements")) {
                return root;
            }
            if (firstObject == null) {
                firstObject = root;
            }
        }
        if (firstObject != null) {
            return firstObject;
        }
        throw invalidOutput("岗位证据分析结果不是合法 JSON");
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
