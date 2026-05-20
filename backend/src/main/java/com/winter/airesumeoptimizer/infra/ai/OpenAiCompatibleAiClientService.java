package com.winter.airesumeoptimizer.infra.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OpenAiCompatibleAiClientService implements AiClientService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiClientService.class);
    private static final int MAX_ERROR_BODY_LENGTH = 500;

    private final AiClientProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public OpenAiCompatibleAiClientService(
            AiClientProperties properties,
            ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newHttpClient());
    }

    OpenAiCompatibleAiClientService(
            AiClientProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public String complete(String prompt) {
        validateConfig();
        if (prompt == null || prompt.isBlank()) {
            throw new AiClientException("AI 输入不能为空");
        }

        HttpRequest request = buildRequest(prompt);
        try {
            log.info("AI completion request started: model={}, timeoutSeconds={}",
                    properties.getModel(),
                    resolveTimeoutSeconds());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI completion request failed: model={}, httpStatus={}",
                        properties.getModel(),
                        response.statusCode());
                throw new AiClientException("AI 调用失败，HTTP 状态码：" + response.statusCode()
                        + "，响应：" + truncate(response.body()));
            }
            log.info("AI completion request succeeded: model={}, httpStatus={}",
                    properties.getModel(),
                    response.statusCode());
            return extractContent(response.body());
        } catch (HttpTimeoutException exception) {
            log.warn("AI completion request timed out: model={}, timeoutSeconds={}",
                    properties.getModel(),
                    resolveTimeoutSeconds());
            throw new AiClientException("AI 调用超时，请稍后重试或缩短简历内容", exception);
        } catch (IOException exception) {
            log.warn("AI completion request IO failed: model={}, message={}",
                    properties.getModel(),
                    LogSanitizer.sanitize(exception.getMessage()));
            throw new AiClientException("AI 调用失败，请检查网络或 base-url 配置", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("AI completion request interrupted: model={}", properties.getModel());
            throw new AiClientException("AI 调用被中断", exception);
        }
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    private HttpRequest buildRequest(String prompt) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", properties.getModel(),
                    "temperature", properties.getTemperature(),
                    "max_tokens", resolveMaxTokens(),
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", prompt))));

            return HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(properties.getBaseUrl()) + "/chat/completions"))
                    .timeout(Duration.ofSeconds(resolveTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
        } catch (IllegalArgumentException exception) {
            throw new AiClientException("AI base-url 配置不正确", exception);
        } catch (IOException exception) {
            throw new AiClientException("AI 请求体序列化失败", exception);
        }
    }

    String extractContent(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choiceNode = root.path("choices").path(0);
            JsonNode messageNode = choiceNode.path("message");
            String content = readMessageContent(messageNode);
            if (content.isBlank()) {
                String finishReason = choiceNode.path("finish_reason").asText("");
                if ("length".equals(finishReason)) {
                    throw new AiClientException("AI 响应中缺少文本内容，可能是 max_tokens 不足，请调大 OPENAI_MAX_TOKENS 后重试");
                }
                String refusal = messageNode.path("refusal").asText("");
                if (!refusal.isBlank()) {
                    throw new AiClientException("AI 拒绝返回文本内容：" + truncate(refusal));
                }
                throw new AiClientException("AI 响应中缺少文本内容，finish_reason=" + emptyToUnknown(finishReason));
            }
            return content;
        } catch (IOException exception) {
            throw new AiClientException("AI 响应 JSON 解析失败", exception);
        }
    }

    private String readMessageContent(JsonNode messageNode) {
        JsonNode contentNode = messageNode.path("content");
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            StringBuilder result = new StringBuilder();
            for (JsonNode item : contentNode) {
                JsonNode textNode = item.path("text");
                if (textNode.isTextual() && !textNode.asText().isBlank()) {
                    if (!result.isEmpty()) {
                        result.append('\n');
                    }
                    result.append(textNode.asText());
                }
            }
            return result.toString();
        }

        JsonNode legacyTextNode = messageNode.path("text");
        if (legacyTextNode.isTextual()) {
            return legacyTextNode.asText();
        }
        return "";
    }

    private void validateConfig() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new AiClientException("AI API Key 未配置");
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new AiClientException("AI base-url 未配置");
        }
        if (properties.getModel() == null || properties.getModel().isBlank()) {
            throw new AiClientException("AI 模型名称未配置");
        }
    }

    private int resolveTimeoutSeconds() {
        if (properties.getTimeoutSeconds() == null || properties.getTimeoutSeconds() <= 0) {
            return 30;
        }
        return properties.getTimeoutSeconds();
    }

    private int resolveMaxTokens() {
        if (properties.getMaxTokens() == null || properties.getMaxTokens() <= 0) {
            return 4000;
        }
        return properties.getMaxTokens();
    }

    private String emptyToUnknown(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_BODY_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_BODY_LENGTH);
    }
}
