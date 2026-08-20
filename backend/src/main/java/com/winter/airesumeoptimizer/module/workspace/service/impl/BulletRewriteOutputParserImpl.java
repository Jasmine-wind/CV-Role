package com.winter.airesumeoptimizer.module.workspace.service.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.workspace.dto.BulletRewriteOutputDTO;
import com.winter.airesumeoptimizer.module.workspace.service.BulletRewriteOutputParser;
import com.winter.airesumeoptimizer.module.workspace.service.BulletRewriteRefusedException;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Strict parser for the Phase 5 AI rewrite envelope. No prose or malformed JSON recovery. */
@Service
public class BulletRewriteOutputParserImpl implements BulletRewriteOutputParser {

    private static final int MAX_AI_OUTPUT_LENGTH = 50_000;
    private static final int MAX_SUGGESTED_TEXT_LENGTH = 4000;
    private static final int MAX_REASON_LENGTH = 200;
    private static final Set<String> REQUIRED_FIELDS = Set.of("suggestedText", "reason");

    /** Non-empty refusal prose is never exposed as a READY candidate. */
    private static final Pattern REFUSAL_PREFIX = Pattern.compile(
            "^(?:说明[:：]?\\s*)?(抱歉|很抱歉|非常抱歉|对不起|我无法|我不能|无法|不能完成|拒绝|"
                    + "作为\\s*(一个|一名)?\\s*(AI|人工智能|语言模型|助手))");

    private final ObjectMapper strictObjectMapper;

    public BulletRewriteOutputParserImpl(ObjectMapper objectMapper) {
        this.strictObjectMapper = objectMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    @Override
    public BulletRewriteOutputDTO parse(String aiOutput) {
        if (aiOutput == null || aiOutput.isBlank()) {
            throw malformed();
        }
        if (aiOutput.length() > MAX_AI_OUTPUT_LENGTH) {
            throw malformed();
        }
        String trimmed = aiOutput.strip();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw malformed();
        }

        JsonNode root;
        try {
            root = strictObjectMapper.readTree(trimmed);
        } catch (Exception exception) {
            throw malformed();
        }
        if (root == null || !root.isObject() || !hasExactSchema(root)) {
            throw malformed();
        }

        JsonNode suggestedNode = root.get("suggestedText");
        JsonNode reasonNode = root.get("reason");
        if (!suggestedNode.isTextual() || !reasonNode.isTextual()) {
            throw malformed();
        }

        String suggestedText = suggestedNode.asText().strip();
        String reason = reasonNode.asText().strip();
        if (reason.isEmpty() || reason.length() > MAX_REASON_LENGTH) {
            throw malformed();
        }
        if (suggestedText.isEmpty()) {
            throw new BulletRewriteRefusedException(reason);
        }
        if (suggestedText.length() > MAX_SUGGESTED_TEXT_LENGTH) {
            throw malformed();
        }
        if (REFUSAL_PREFIX.matcher(suggestedText).find()) {
            throw new BulletRewriteRefusedException("AI 拒绝给出改写建议");
        }
        return new BulletRewriteOutputDTO(suggestedText, reason);
    }

    private boolean hasExactSchema(JsonNode root) {
        Iterator<String> fieldNames = root.fieldNames();
        int count = 0;
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (!REQUIRED_FIELDS.contains(fieldName)) {
                return false;
            }
            count++;
        }
        return count == REQUIRED_FIELDS.size()
                && root.has("suggestedText")
                && root.has("reason");
    }

    private BusinessException malformed() {
        return new BusinessException(502, "AI 输出格式不正确，本次建议已放弃，请重新生成");
    }
}
