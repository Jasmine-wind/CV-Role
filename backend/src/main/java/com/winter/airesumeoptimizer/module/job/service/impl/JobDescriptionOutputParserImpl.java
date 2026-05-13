package com.winter.airesumeoptimizer.module.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionParseResultDTO;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionOutputParser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JobDescriptionOutputParserImpl implements JobDescriptionOutputParser {

    private static final int MAX_LIST_SIZE = 8;
    private static final int MAX_TEXT_ITEM_LENGTH = 80;
    private static final int MAX_SUMMARY_LENGTH = 200;

    private final ObjectMapper objectMapper;

    public JobDescriptionOutputParserImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public JobDescriptionParseResultDTO parse(String aiOutput) {
        JsonNode root = readJson(aiOutput);
        if (!root.isObject()) {
            throw invalidOutput("岗位描述解析结果必须是 JSON 对象");
        }

        return JobDescriptionParseResultDTO.builder()
                .jobTitle(readText(root.get("jobTitle"), MAX_TEXT_ITEM_LENGTH))
                .requiredSkills(readTextList(root.get("requiredSkills")))
                .bonusSkills(readTextList(root.get("bonusSkills")))
                .experienceSignals(readTextList(root.get("experienceSignals")))
                .responsibilities(readTextList(root.get("responsibilities")))
                .keywords(readTextList(root.get("keywords")))
                .summary(readText(root.get("summary"), MAX_SUMMARY_LENGTH))
                .build();
    }

    private JsonNode readJson(String aiOutput) {
        if (aiOutput == null || aiOutput.isBlank()) {
            throw invalidOutput("岗位描述解析结果不能为空");
        }
        try {
            return objectMapper.readTree(stripJsonCodeFence(aiOutput.strip()));
        } catch (JsonProcessingException exception) {
            throw invalidOutput("岗位描述解析结果不是合法 JSON");
        }
    }

    private String stripJsonCodeFence(String value) {
        if (!value.startsWith("```")) {
            return value;
        }
        String stripped = value.replaceFirst("^```(?:json)?\\s*", "");
        return stripped.replaceFirst("\\s*```$", "").strip();
    }

    private String readText(JsonNode node, int maxLength) {
        if (node == null || node.isNull() || !node.isTextual()) {
            return "";
        }
        return truncate(node.asText().strip(), maxLength);
    }

    private List<String> readTextList(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                continue;
            }
            String text = item.asText().strip();
            if (!text.isBlank()) {
                values.add(truncate(text, MAX_TEXT_ITEM_LENGTH));
            }
            if (values.size() == MAX_LIST_SIZE) {
                break;
            }
        }

        return List.copyOf(values);
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private BusinessException invalidOutput(String message) {
        return new BusinessException(502, message);
    }
}
