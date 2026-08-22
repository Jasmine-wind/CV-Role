package com.winter.airesumeoptimizer.infra.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.infra.ai.AiClientException;
import com.winter.airesumeoptimizer.infra.ai.transport.OutboundRequest;
import com.winter.airesumeoptimizer.infra.ai.transport.OutboundResponse;
import com.winter.airesumeoptimizer.infra.ai.transport.OutboundTransportException;
import com.winter.airesumeoptimizer.infra.ai.transport.PinnedHttpTransport;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Platform-only embedding adapter; it never reads user BYOK Credentials. */
@Service
public class OpenAiCompatibleEmbeddingClientService implements EmbeddingClientService {

    private static final String EMBEDDINGS_PATH = "/embeddings";

    private final EmbeddingClientProperties properties;
    private final ObjectMapper objectMapper;
    private final PinnedHttpTransport transport;

    @Autowired
    public OpenAiCompatibleEmbeddingClientService(
            EmbeddingClientProperties properties,
            ObjectMapper objectMapper,
            PinnedHttpTransport transport) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.transport = transport;
    }

    /** Retained for JSON-focused unit tests. */
    OpenAiCompatibleEmbeddingClientService(
            EmbeddingClientProperties properties,
            ObjectMapper objectMapper) {
        this(properties, objectMapper, new PinnedHttpTransport());
    }

    @Override
    public List<Double> embed(String text) {
        validateConfig();
        String normalizedText = normalizeInputText(text);
        long startedAt = System.nanoTime();
        try {
            OutboundResponse response = transport.execute(new OutboundRequest(
                    "POST",
                    properties.getBaseUrl(),
                    EMBEDDINGS_PATH,
                    Map.of(
                            "Authorization", "Bearer " + properties.getApiKey().strip(),
                            "Content-Type", "application/json",
                            "Accept", "application/json"),
                    buildRequestBody(normalizedText),
                    Duration.ofSeconds(resolveTimeoutSeconds())));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw buildHttpErrorException(response.statusCode());
            }
            List<Double> embedding = extractEmbedding(response.body());
            validateEmbeddingDimension(embedding);
            return embedding;
        } catch (OutboundTransportException exception) {
            throw mapTransportFailure(exception);
        } catch (IOException exception) {
            throw new AiClientException("Embedding 请求体序列化失败");
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
                if (!item.isNumber() || !Double.isFinite(item.asDouble())) {
                    throw new AiClientException("Embedding 响应向量包含非数字元素");
                }
                embedding.add(item.asDouble());
            }
            return embedding;
        } catch (AiClientException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new AiClientException("Embedding 响应 JSON 解析失败");
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
        return Math.min(120, properties.getTimeoutSeconds());
    }

    private int resolveMaxInputLength() {
        if (properties.getMaxInputLength() == null || properties.getMaxInputLength() <= 0) {
            return 8000;
        }
        return properties.getMaxInputLength();
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
        return new AiClientException("Embedding 调用失败，Provider 拒绝了请求");
    }

    private AiClientException mapTransportFailure(OutboundTransportException exception) {
        return switch (exception.getKind()) {
            case UNSAFE_URL -> new AiClientException("Embedding base-url 不安全");
            case TIMEOUT -> new AiClientException("Embedding 服务调用超时，请稍后重试或缩短输入文本");
            case RESPONSE_TOO_LARGE -> new AiClientException("Embedding Provider 响应过大");
            case NETWORK -> new AiClientException("Embedding 调用失败，请检查网络或 base-url 配置");
            case INTERRUPTED -> new AiClientException("Embedding 调用被中断");
        };
    }
}
