package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.module.resume.config.ResumeParseProperties;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAiStructuredParseResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredParsePromptDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAiStructuredParser;
import com.winter.airesumeoptimizer.module.resume.service.ResumeParseValidator;
import com.winter.airesumeoptimizer.module.resume.service.ResumeStructuredParsePromptService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResumeAiStructuredParserImpl implements ResumeAiStructuredParser {

    private static final Logger log = LoggerFactory.getLogger(ResumeAiStructuredParserImpl.class);
    private static final String AI_STATUS_USED = "USED";
    private static final String AI_STATUS_SKIPPED = "SKIPPED";
    private static final String AI_STATUS_FALLBACK = "FALLBACK";
    private static final String AI_STATUS_DISABLED = "DISABLED";

    private final ResumeParseProperties properties;
    private final ResumeStructuredParsePromptService promptService;
    private final ResumeParseValidator resumeParseValidator;
    private final AiClientService aiClientService;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, ResumeStructuredContentDTO> cache = new ConcurrentHashMap<>();

    public ResumeAiStructuredParserImpl(
            ResumeParseProperties properties,
            ResumeStructuredParsePromptService promptService,
            ResumeParseValidator resumeParseValidator,
            AiClientService aiClientService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.promptService = promptService;
        this.resumeParseValidator = resumeParseValidator;
        this.aiClientService = aiClientService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResumeAiStructuredParseResultDTO parse(
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings) {
        return parse(blocks, ruleStructuredContent, qualityWarnings, null);
    }

    @Override
    public ResumeAiStructuredParseResultDTO parse(
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride) {
        long startedAt = System.nanoTime();
        boolean enabled = enabledOverride == null ? properties.aiStructuredParseEnabled() : Boolean.TRUE.equals(enabledOverride);
        if (!enabled) {
            return disabled("AI_STRUCTURED_PARSE_DISABLED", ruleStructuredContent, qualityWarnings, startedAt);
        }
        if (blocks == null || blocks.isEmpty()) {
            return skipped(true, "NO_STRUCTURED_PARSE_BLOCKS", ruleStructuredContent, qualityWarnings, startedAt);
        }
        List<ResumeBlockDTO> aiBlocks = blocks.stream()
                .filter(this::needsAiStructuredParse)
                .toList();
        if (aiBlocks.isEmpty()) {
            return skipped(true, "STABLE_FIELDS_RULE_CONFIRMED", ruleStructuredContent, qualityWarnings, startedAt);
        }
        if (aiBlocks.size() > properties.aiMaxBlocks()) {
            return skipped(true, "AI_BLOCK_LIMIT_EXCEEDED", ruleStructuredContent, qualityWarnings, startedAt);
        }

        List<String> warnings = new ArrayList<>(qualityWarnings == null ? List.of() : qualityWarnings);
        try {
            ResumeStructuredParsePromptDTO prompt = promptService.buildPrompt(aiBlocks, ruleStructuredContent, warnings);
            String cacheKey = buildCacheKey(aiBlocks, prompt, ruleStructuredContent);
            ResumeStructuredContentDTO cached = cache.get(cacheKey);
            if (cached != null) {
                return ResumeAiStructuredParseResultDTO.builder()
                        .aiEnabled(true)
                        .applied(true)
                        .aiInvoked(false)
                        .aiStatus(AI_STATUS_USED)
                        .fallbackOccurred(false)
                        .durationMs(elapsedMs(startedAt))
                        .cacheHit(true)
                        .cacheKey(cacheKey)
                        .structuredContent(cached)
                        .qualityWarnings(cached.getQualityWarnings())
                        .build();
            }
            String aiOutput = aiClientService.complete(prompt.getPrompt());
            ResumeStructuredContentDTO aiContent = readAiStructuredContent(aiOutput);
            if (aiContent.getQualityWarnings() != null) {
                warnings.addAll(aiContent.getQualityWarnings());
            }
            ResumeStructuredContentDTO validated = resumeParseValidator.validateAndMerge(aiContent, ruleStructuredContent, warnings);
            if (validated == null) {
                return aiFallback("AI 结构化补全校验结果为空", ruleStructuredContent, qualityWarnings, startedAt);
            }
            cache.put(cacheKey, validated);
            return ResumeAiStructuredParseResultDTO.builder()
                    .aiEnabled(true)
                    .applied(true)
                    .aiInvoked(true)
                    .aiStatus(AI_STATUS_USED)
                    .fallbackOccurred(false)
                    .durationMs(elapsedMs(startedAt))
                    .cacheHit(false)
                    .cacheKey(cacheKey)
                    .structuredContent(validated)
                    .qualityWarnings(validated.getQualityWarnings())
                    .build();
        } catch (JsonProcessingException exception) {
            log.warn("Resume AI structured parse fallback: reason={}", LogSanitizer.sanitize(exception.getMessage()));
            return aiFallback("AI 结构化补全 JSON 解析失败：" + sanitizeErrorMessage(exception.getOriginalMessage()),
                    ruleStructuredContent, qualityWarnings, startedAt);
        } catch (RuntimeException exception) {
            log.warn("Resume AI structured parse fallback: reason={}", LogSanitizer.sanitize(exception.getMessage()));
            return aiFallback("AI 结构化补全失败：" + sanitizeErrorMessage(exception.getMessage()),
                    ruleStructuredContent, qualityWarnings, startedAt);
        }
    }

    private boolean needsAiStructuredParse(ResumeBlockDTO block) {
        if (block == null || block.getText() == null || block.getText().isBlank()) {
            return false;
        }
        String section = block.getSourceSection();
        return !Boolean.TRUE.equals(block.getSectionLocked())
                || section == null
                || section.isBlank()
                || "GENERAL".equals(section)
                || "OTHERS".equals(section);
    }

    private ResumeAiStructuredParseResultDTO disabled(
            String reasonCode,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            long startedAt) {
        if (ruleStructuredContent != null && ruleStructuredContent.getQualityWarnings() == null && qualityWarnings != null) {
            ruleStructuredContent.setQualityWarnings(qualityWarnings);
        }
        return ResumeAiStructuredParseResultDTO.builder()
                .aiEnabled(false)
                .applied(false)
                .aiInvoked(false)
                .aiStatus(AI_STATUS_DISABLED)
                .skippedReason(reasonCode)
                .fallbackOccurred(false)
                .durationMs(elapsedMs(startedAt))
                .cacheHit(false)
                .structuredContent(ruleStructuredContent)
                .qualityWarnings(qualityWarnings == null ? List.of() : qualityWarnings)
                .build();
    }

    private ResumeAiStructuredParseResultDTO skipped(
            boolean enabled,
            String reasonCode,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            long startedAt) {
        if (ruleStructuredContent != null && ruleStructuredContent.getQualityWarnings() == null && qualityWarnings != null) {
            ruleStructuredContent.setQualityWarnings(qualityWarnings);
        }
        return ResumeAiStructuredParseResultDTO.builder()
                .aiEnabled(enabled)
                .applied(false)
                .aiInvoked(false)
                .aiStatus(AI_STATUS_SKIPPED)
                .skippedReason(reasonCode)
                .fallbackOccurred(false)
                .durationMs(elapsedMs(startedAt))
                .cacheHit(false)
                .structuredContent(ruleStructuredContent)
                .qualityWarnings(qualityWarnings == null ? List.of() : qualityWarnings)
                .build();
    }

    private ResumeAiStructuredParseResultDTO aiFallback(
            String reason,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            long startedAt) {
        if (ruleStructuredContent != null && ruleStructuredContent.getQualityWarnings() == null && qualityWarnings != null) {
            ruleStructuredContent.setQualityWarnings(qualityWarnings);
        }
        return ResumeAiStructuredParseResultDTO.builder()
                .aiEnabled(true)
                .applied(false)
                .aiInvoked(true)
                .aiStatus(AI_STATUS_FALLBACK)
                .fallbackOccurred(true)
                .fallbackReason(reason)
                .durationMs(elapsedMs(startedAt))
                .cacheHit(false)
                .structuredContent(ruleStructuredContent)
                .qualityWarnings(qualityWarnings == null ? List.of() : qualityWarnings)
                .build();
    }

    private String buildCacheKey(
            List<ResumeBlockDTO> blocks,
            ResumeStructuredParsePromptDTO prompt,
            ResumeStructuredContentDTO ruleStructuredContent) {
        String modelName = aiClientService.modelName();
        return "structured"
                + ":cleanedTextHash=" + hashCleanedText(blocks)
                + ":promptVersion=" + nullToUnknown(prompt == null ? null : prompt.getPromptVersion())
                + ":modelName=" + nullToUnknown(modelName)
                + ":parserVersion=" + ResumeParseVersions.PARSER_VERSION
                + ":parseMode=" + nullToUnknown(ruleStructuredContent == null ? null : ruleStructuredContent.getParseMode())
                + ":blockBuilderVersion=" + ResumeParseVersions.BLOCK_BUILDER_VERSION
                + ":sectionRuleVersion=" + ResumeParseVersions.SECTION_RULE_VERSION
                + ":blockContextHash=" + hashBlocks(blocks);
    }

    private String hashCleanedText(List<ResumeBlockDTO> blocks) {
        StringBuilder builder = new StringBuilder();
        for (ResumeBlockDTO block : blocks == null ? List.<ResumeBlockDTO>of() : blocks) {
            builder.append(nullToUnknown(block == null ? null : block.getText())).append('\n');
        }
        return sha256(builder.toString());
    }

    private String hashBlocks(List<ResumeBlockDTO> blocks) {
        StringBuilder builder = new StringBuilder();
        for (ResumeBlockDTO block : blocks == null ? List.<ResumeBlockDTO>of() : blocks) {
            builder.append(nullToUnknown(block.getIndex())).append('\t')
                    .append(nullToUnknown(block.getSourceSection())).append('\t')
                    .append(nullToUnknown(block.getSectionLocked())).append('\t')
                    .append(nullToUnknown(block.getText())).append('\n');
        }
        return sha256(builder.toString());
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private String nullToUnknown(Object value) {
        if (value == null) {
            return "unknown";
        }
        String text = String.valueOf(value);
        return text.isBlank() ? "unknown" : text;
    }

    private long elapsedMs(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private ResumeStructuredContentDTO readAiStructuredContent(String aiOutput) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(extractJsonObject(aiOutput));
        JsonNode contentNode = unwrapStructuredContent(root);
        JsonNode normalized = normalizeStructuredContent(contentNode);
        return objectMapper.treeToValue(normalized, ResumeStructuredContentDTO.class);
    }

    private JsonNode unwrapStructuredContent(JsonNode root) {
        for (String fieldName : List.of("structuredResult", "structuredContent", "result", "data")) {
            JsonNode node = root.path(fieldName);
            if (node.isObject()) {
                return node;
            }
        }
        return root;
    }

    private JsonNode normalizeStructuredContent(JsonNode contentNode) throws JsonProcessingException {
        ObjectNode normalized = objectMapper.createObjectNode();
        if (contentNode == null || !contentNode.isObject()) {
            return normalized;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = contentNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode value = field.getValue();
            if ("basicInfo".equals(fieldName) && value.isObject()) {
                normalized.set(fieldName, normalizeStringMap(value));
            } else if (isStringArrayField(fieldName)) {
                normalized.set(fieldName, normalizeStringArray(value));
            } else if (isStringField(fieldName)) {
                putStringValue(normalized, fieldName, value);
            } else {
                normalized.set(fieldName, value);
            }
        }
        return normalized;
    }

    private ObjectNode normalizeStringMap(JsonNode value) throws JsonProcessingException {
        ObjectNode result = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String text = toText(field.getValue());
            if (!text.isBlank()) {
                result.put(field.getKey(), text);
            }
        }
        return result;
    }

    private ArrayNode normalizeStringArray(JsonNode value) throws JsonProcessingException {
        ArrayNode result = objectMapper.createArrayNode();
        if (value == null || value.isNull()) {
            return result;
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                String text = toText(item);
                if (!text.isBlank()) {
                    result.add(text);
                }
            }
            return result;
        }
        String text = toText(value);
        if (!text.isBlank()) {
            result.add(text);
        }
        return result;
    }

    private void putStringValue(ObjectNode target, String fieldName, JsonNode value) throws JsonProcessingException {
        String text = toText(value);
        if (text.isBlank()) {
            target.putNull(fieldName);
        } else {
            target.put(fieldName, text);
        }
    }

    private String toText(JsonNode value) throws JsonProcessingException {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isTextual()) {
            return value.asText().strip();
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return objectMapper.writeValueAsString(value);
    }

    private boolean isStringArrayField(String fieldName) {
        return List.of(
                "education",
                "skills",
                "workExperiences",
                "internships",
                "projects",
                "campusExperiences",
                "awards",
                "certificates",
                "others",
                "qualityWarnings")
                .contains(fieldName);
    }

    private boolean isStringField(String fieldName) {
        return List.of(
                "name",
                "phone",
                "email",
                "jobIntention",
                "highestEducation",
                "resumeType",
                "summary",
                "aiSectionClassifyFallbackReason",
                "aiStructuredParseFallbackReason")
                .contains(fieldName);
    }

    private String sanitizeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "unknown";
        }
        String singleLine = LogSanitizer.sanitize(message).replaceAll("\\s+", " ").strip();
        if (singleLine.length() <= 120) {
            return singleLine;
        }
        return singleLine.substring(0, 120);
    }

    private String extractJsonObject(String value) throws JsonProcessingException {
        if (value == null) {
            return "";
        }
        String stripped = value.strip();
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceFirst("(?i)^```(?:json)?\\s*", "");
            stripped = stripped.replaceFirst("\\s*```$", "");
        }
        stripped = stripped.strip();
        if (stripped.startsWith("{") && stripped.endsWith("}")) {
            return stripped;
        }

        int start = stripped.indexOf('{');
        if (start < 0) {
            throw new JsonProcessingException("AI 输出中未找到 JSON 对象") {
            };
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = start; index < stripped.length(); index++) {
            char current = stripped.charAt(index);
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
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return stripped.substring(start, index + 1);
                }
            }
        }

        throw new JsonProcessingException("AI 输出中的 JSON 对象不完整") {
        };
    }
}
