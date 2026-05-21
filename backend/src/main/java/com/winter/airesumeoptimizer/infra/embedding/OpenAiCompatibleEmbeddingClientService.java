package com.winter.airesumeoptimizer.infra.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.infra.ai.AiClientException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OpenAiCompatibleEmbeddingClientService implements EmbeddingClientService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleEmbeddingClientService.class);
    private static final String EMBEDDINGS_PATH = "/embeddings";

    private final EmbeddingClientProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public OpenAiCompatibleEmbeddingClientService(
            EmbeddingClientProperties properties,
            ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newHttpClient());
    }

    OpenAiCompatibleEmbeddingClientService(
            EmbeddingClientProperties properties,
            ObjectMapper objectMapper,
            HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @Override
    public List<Double> embed(String text) {
        validateConfig();
        String normalizedText = normalizeInputText(text);

        HttpRequest request = buildRequest(normalizedText);
        long startedAt = System.nanoTime();
        try {
            log.info("Embedding request started: model={}, dimension={}, timeoutSeconds={}, inputLength={}",
                    properties.getModel(),
                    properties.getDimension(),
                    resolveTimeoutSeconds(),
                    normalizedText.length());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Embedding request failed: model={}, dimension={}, inputLength={}, durationMs={}, httpStatus={}",
                        properties.getModel(),
                        properties.getDimension(),
                        normalizedText.length(),
                        elapsedMillis(startedAt),
                        response.statusCode());
                throw buildHttpErrorException(response.statusCode());
            }
            List<Double> embedding = extractEmbedding(response.body());
            validateEmbeddingDimension(embedding);
            log.info("Embedding request succeeded: model={}, dimension={}, inputLength={}, durationMs={}",
                    properties.getModel(),
                    embedding.size(),
                    normalizedText.length(),
                    elapsedMillis(startedAt));
            return embedding;
        } catch (HttpTimeoutException exception) {
            log.warn("Embedding request timed out: model={}, dimension={}, inputLength={}, timeoutSeconds={}",
                    properties.getModel(),
                    properties.getDimension(),
                    normalizedText.length(),
                    resolveTimeoutSeconds());
            throw new AiClientException("Embedding 服务调用超时，请稍后重试或缩短输入文本", exception);
        } catch (IOException exception) {
            log.warn("Embedding request IO failed: model={}, dimension={}, inputLength={}, message={}",
                    properties.getModel(),
                    properties.getDimension(),
                    normalizedText.length(),
                    LogSanitizer.sanitize(exception.getMessage()));
            throw new AiClientException("Embedding 调用失败，请检查网络或 base-url 配置", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Embedding request interrupted: model={}", properties.getModel());
            throw new AiClientException("Embedding 调用被中断", exception);
        }
    }

    @Override
    public String modelName() {
        return properties.getModel();
    }

    @Override
    public Integer dimension() {
        return properties.getDimension();
    }

    List<Double> extractEmbedding(String responseBody) {
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);
            JsonNode dataNode = rootNode.path("data");
            if (!dataNode.isArray() || dataNode.isEmpty()) {
                throw new AiClientException("Embedding 响应中缺少 data 数据");
            }

            JsonNode embeddingNode = dataNode.path(0).path("embedding");
            if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                throw new AiClientException("Embedding 响应中缺少向量数据");
            }

            List<Double> embedding = new ArrayList<>(embeddingNode.size());
            for (JsonNode item : embeddingNode) {
                if (!item.isNumber()) {
                    throw new AiClientException("Embedding 响应向量包含非数字元素");
                }
                embedding.add(item.asDouble());
            }
            return embedding;
        } catch (IOException exception) {
            throw new AiClientException("Embedding 响应 JSON 解析失败", exception);
        }
    }

    String normalizeInputText(String text) {
        if (text == null || text.isBlank()) {
            throw new AiClientException("Embedding 输入不能为空");
        }

        String normalized = text.strip();
        int maxInputLength = resolveMaxInputLength();
        if (normalized.length() > maxInputLength) {
            throw new AiClientException("Embedding 输入过长，最大允许字符数：" + maxInputLength);
        }
        return normalized;
    }

    void validateEmbeddingDimension(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) {
            throw new AiClientException("Embedding 响应向量为空");
        }
        Integer configuredDimension = properties.getDimension();
        if (configuredDimension != null
                && configuredDimension > 0
                && embedding.size() != configuredDimension) {
            throw new AiClientException("Embedding 向量维度不一致，期望："
                    + configuredDimension + "，实际：" + embedding.size());
        }
    }

    private HttpRequest buildRequest(String text) {
        try {
            String requestBody = buildRequestBody(text);

            return HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(properties.getBaseUrl()) + EMBEDDINGS_PATH))
                    .version(HttpClient.Version.HTTP_1_1)
                    .timeout(Duration.ofSeconds(resolveTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
        } catch (IllegalArgumentException exception) {
            throw new AiClientException("Embedding base-url 配置不正确", exception);
        } catch (IOException exception) {
            throw new AiClientException("Embedding 请求体序列化失败", exception);
        }
    }

    String buildRequestBody(String text) throws IOException {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", properties.getModel());
        requestBody.put("input", text);
        if (properties.getDimension() != null && properties.getDimension() > 0) {
            requestBody.put("dimensions", properties.getDimension());
        }
        return objectMapper.writeValueAsString(requestBody);
    }

    void validateConfig() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new AiClientException("Embedding API Key 未配置");
        }
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()) {
            throw new AiClientException("Embedding base-url 未配置");
        }
        if (properties.getModel() == null || properties.getModel().isBlank()) {
            throw new AiClientException("Embedding 模型名称未配置");
        }
        if (properties.getDimension() != null && properties.getDimension() <= 0) {
            throw new AiClientException("Embedding 维度配置必须大于 0");
        }
    }

    private int resolveTimeoutSeconds() {
        if (properties.getTimeoutSeconds() == null || properties.getTimeoutSeconds() <= 0) {
            return 120;
        }
        return properties.getTimeoutSeconds();
    }

    private int resolveMaxInputLength() {
        if (properties.getMaxInputLength() == null || properties.getMaxInputLength() <= 0) {
            return 8000;
        }
        return properties.getMaxInputLength();
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(EMBEDDINGS_PATH)) {
            throw new IllegalArgumentException("Embedding base-url 不应以 /embeddings 结尾");
        }
        return normalized;
    }

    AiClientException buildHttpErrorException(int httpStatus) {
        if (httpStatus == 401 || httpStatus == 403) {
            return new AiClientException("Embedding API Key 无效或没有调用权限");
        }
        if (httpStatus == 429) {
            return new AiClientException("Embedding 请求过于频繁或额度不足，请稍后重试");
        }
        if (httpStatus >= 500) {
            return new AiClientException("SiliconFlow Embedding 服务暂时不可用，请稍后重试");
        }
        return new AiClientException("Embedding 调用失败，HTTP 状态码：" + httpStatus);
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
