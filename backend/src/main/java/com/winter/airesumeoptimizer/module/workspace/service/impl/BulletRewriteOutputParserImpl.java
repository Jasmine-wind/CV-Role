package com.winter.airesumeoptimizer.module.workspace.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.workspace.dto.BulletRewriteOutputDTO;
import com.winter.airesumeoptimizer.module.workspace.service.BulletRewriteOutputParser;
import com.winter.airesumeoptimizer.module.workspace.service.BulletRewriteRefusedException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class BulletRewriteOutputParserImpl implements BulletRewriteOutputParser {

    private static final int MAX_AI_OUTPUT_LENGTH = 50_000;
    private static final int MAX_SUGGESTED_TEXT_LENGTH = 4000;
    private static final int MAX_REASON_LENGTH = 200;

    /** 以拒绝话术开头的输出视为 AI 拒绝，按 REJECTED 处理而不是展示为建议。 */
    private static final Pattern REFUSAL_PREFIX = Pattern.compile(
            "^(抱歉|很抱歉|非常抱歉|对不起|我无法|我不能|无法|拒绝|作为\\s*(一个|一名)?\\s*(AI|人工智能|语言模型|助手))");

    private final ObjectMapper objectMapper;

    public BulletRewriteOutputParserImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public BulletRewriteOutputDTO parse(String aiOutput) {
        if (aiOutput == null || aiOutput.isBlank()) {
            throw malformed("AI 没有返回任何内容");
        }
        if (aiOutput.length() > MAX_AI_OUTPUT_LENGTH) {
            throw malformed("AI 输出超过长度上限");
        }
        String trimmed = aiOutput.strip();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw malformed("AI 输出不是纯 JSON 对象");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(trimmed);
        } catch (Exception exception) {
            throw malformed("AI 输出 JSON 解析失败");
        }
        if (root == null || !root.isObject()) {
            throw malformed("AI 输出不是 JSON 对象");
        }

        JsonNode suggestedNode = root.get("suggestedText");
        if (suggestedNode == null || !suggestedNode.isTextual()) {
            throw malformed("AI 输出缺少 suggestedText 字符串字段");
        }
        String suggestedText = suggestedNode.asText().strip();
        if (suggestedText.isEmpty()) {
            throw new BulletRewriteRefusedException("AI 无法在不新增事实的情况下改写");
        }
        if (suggestedText.length() > MAX_SUGGESTED_TEXT_LENGTH) {
            throw malformed("AI 改写文本超过要点长度上限");
        }
        if (REFUSAL_PREFIX.matcher(suggestedText).find()) {
            throw new BulletRewriteRefusedException("AI 拒绝给出改写建议");
        }

        String reason = "";
        JsonNode reasonNode = root.get("reason");
        if (reasonNode != null && reasonNode.isTextual()) {
            reason = reasonNode.asText().strip();
            if (reason.length() > MAX_REASON_LENGTH) {
                reason = reason.substring(0, MAX_REASON_LENGTH);
            }
        }
        return new BulletRewriteOutputDTO(suggestedText, reason);
    }

    private BusinessException malformed(String message) {
        return new BusinessException(502, "AI 输出格式不正确，本次建议已放弃，请重新生成");
    }
}
