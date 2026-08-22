package com.winter.airesumeoptimizer.infra.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/** Allowlisted, provider-neutral generation settings. */
public record AiGenerationConfig(Double temperature, Integer maxOutputTokens) {

    private static final Set<String> ALLOWED_KEYS = Set.of("temperature", "maxOutputTokens");

    public AiGenerationConfig {
        temperature = temperature == null ? 0.2d : temperature;
        maxOutputTokens = maxOutputTokens == null ? 16000 : maxOutputTokens;
        if (temperature < 0d || temperature > 1d || maxOutputTokens < 1 || maxOutputTokens > 16000) {
            throw new AiGatewayException(AiFailureCode.CONFIGURATION_INVALID, "AI 生成配置不正确");
        }
    }

    public static AiGenerationConfig fromJson(
            ObjectMapper objectMapper,
            String configJson,
            Double defaultTemperature,
            Integer defaultMaxTokens) {
        double temperature = defaultTemperature == null ? 0.2d : defaultTemperature;
        int maxTokens = defaultMaxTokens == null ? 16000 : Math.min(16000, Math.max(1, defaultMaxTokens));
        if (configJson == null || configJson.isBlank() || "{}".equals(configJson.strip())) {
            return new AiGenerationConfig(temperature, maxTokens);
        }
        try {
            JsonNode root = objectMapper.readTree(configJson);
            if (!root.isObject()) {
                throw invalid();
            }
            Iterator<String> fields = root.fieldNames();
            while (fields.hasNext()) {
                if (!ALLOWED_KEYS.contains(fields.next())) {
                    throw invalid();
                }
            }
            if (root.has("temperature")) {
                if (!root.get("temperature").isNumber()
                        || !Double.isFinite(root.get("temperature").asDouble())) {
                    throw invalid();
                }
                temperature = root.get("temperature").asDouble();
            }
            if (root.has("maxOutputTokens")) {
                if (!root.get("maxOutputTokens").isIntegralNumber()
                        || !root.get("maxOutputTokens").canConvertToInt()) {
                    throw invalid();
                }
                maxTokens = root.get("maxOutputTokens").asInt();
            }
            return new AiGenerationConfig(temperature, maxTokens);
        } catch (AiGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    public static String normalize(
            ObjectMapper objectMapper,
            Map<String, Object> requested,
            Double defaultTemperature,
            Integer defaultMaxTokens) {
        try {
            String raw = requested == null || requested.isEmpty()
                    ? "{}"
                    : objectMapper.writeValueAsString(requested);
            AiGenerationConfig parsed = fromJson(objectMapper, raw, defaultTemperature, defaultMaxTokens);
            return objectMapper.writeValueAsString(Map.of(
                    "temperature", parsed.temperature(),
                    "maxOutputTokens", parsed.maxOutputTokens()));
        } catch (AiGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private static AiGatewayException invalid() {
        return new AiGatewayException(AiFailureCode.CONFIGURATION_INVALID, "AI 生成配置不正确");
    }
}
