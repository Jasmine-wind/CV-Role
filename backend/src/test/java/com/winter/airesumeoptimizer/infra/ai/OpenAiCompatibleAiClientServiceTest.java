package com.winter.airesumeoptimizer.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenAiCompatibleAiClientServiceTest {

    @Test
    void extractContentShouldReturnAssistantContent() {
        OpenAiCompatibleAiClientService service = new OpenAiCompatibleAiClientService(
                new AiClientProperties(),
                new ObjectMapper());

        String responseBody = """
                {"choices":[{"message":{"content":"基础 AI 调用成功"}}]}
                """;

        assertThat(service.extractContent(responseBody)).isEqualTo("基础 AI 调用成功");
    }

    @Test
    void extractContentShouldReadArrayContentText() {
        OpenAiCompatibleAiClientService service = new OpenAiCompatibleAiClientService(
                new AiClientProperties(),
                new ObjectMapper());

        String responseBody = """
                {"choices":[{"message":{"content":[{"type":"text","text":"数组文本内容"}]}}]}
                """;

        assertThat(service.extractContent(responseBody)).isEqualTo("数组文本内容");
    }

    @Test
    void extractContentShouldReportLengthFinishReason() {
        OpenAiCompatibleAiClientService service = new OpenAiCompatibleAiClientService(
                new AiClientProperties(),
                new ObjectMapper());

        String responseBody = """
                {"choices":[{"finish_reason":"length","message":{"content":""}}]}
                """;

        assertThatThrownBy(() -> service.extractContent(responseBody))
                .isInstanceOf(AiClientException.class)
                .hasMessage("AI 响应中缺少文本内容，可能是 max_tokens 不足，请调大 AI_MAX_TOKENS 后重试");
    }

    @Test
    void completeShouldRejectMissingApiKey() {
        AiClientProperties properties = new AiClientProperties();
        properties.setBaseUrl("http://localhost:8080/v1");
        properties.setModel("test-model");

        OpenAiCompatibleAiClientService service = new OpenAiCompatibleAiClientService(
                properties,
                new ObjectMapper());

        assertThatThrownBy(() -> service.complete("测试输入"))
                .isInstanceOf(AiClientException.class)
                .hasMessage("AI API Key 未配置");
    }

    @Test
    @SuppressWarnings("unchecked")
    void completeShouldSendRoleSeparatedMessages() throws Exception {
        AiClientProperties properties = configuredProperties();
        ObjectMapper objectMapper = new ObjectMapper();
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> httpResponse = mock(HttpResponse.class);
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(
                "{\"choices\":[{\"message\":{\"content\":\"改写结果\"}}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        OpenAiCompatibleAiClientService service = new OpenAiCompatibleAiClientService(
                properties, objectMapper, httpClient);

        String content = service.complete(List.of(
                AiChatMessage.system("平台真实性策略"),
                AiChatMessage.user("不可信简历内容")));

        assertThat(content).isEqualTo("改写结果");
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        JsonNode messages = objectMapper.readTree(bodyOf(requestCaptor.getValue())).path("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).path("role").asText()).isEqualTo("system");
        assertThat(messages.get(0).path("content").asText()).isEqualTo("平台真实性策略");
        assertThat(messages.get(1).path("role").asText()).isEqualTo("user");
        assertThat(messages.get(1).path("content").asText()).isEqualTo("不可信简历内容");
    }

    @Test
    void completeShouldRejectEmptyMessageList() {
        OpenAiCompatibleAiClientService service = new OpenAiCompatibleAiClientService(
                configuredProperties(), new ObjectMapper());

        assertThatThrownBy(() -> service.complete(List.of()))
                .isInstanceOf(AiClientException.class)
                .hasMessage("AI 输入不能为空");
    }

    @Test
    void completeShouldRejectBlankMessageContent() {
        OpenAiCompatibleAiClientService service = new OpenAiCompatibleAiClientService(
                configuredProperties(), new ObjectMapper());

        assertThatThrownBy(() -> service.complete(List.of(AiChatMessage.system("  "))))
                .isInstanceOf(AiClientException.class)
                .hasMessage("AI 输入不能为空");
    }

    private AiClientProperties configuredProperties() {
        AiClientProperties properties = new AiClientProperties();
        properties.setBaseUrl("http://localhost:8080/v1");
        properties.setModel("test-model");
        properties.setApiKey("test-key");
        return properties;
    }

    private static String bodyOf(HttpRequest request) throws Exception {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();
        List<java.nio.ByteBuffer> chunks = new java.util.ArrayList<>();
        publisher.subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            @Override
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(java.nio.ByteBuffer item) {
                chunks.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                int size = chunks.stream().mapToInt(java.nio.ByteBuffer::remaining).sum();
                byte[] bytes = new byte[size];
                int position = 0;
                for (java.nio.ByteBuffer chunk : chunks) {
                    int remaining = chunk.remaining();
                    chunk.get(bytes, position, remaining);
                    position += remaining;
                }
                future.complete(new String(bytes, StandardCharsets.UTF_8));
            }
        });
        return future.get(5, java.util.concurrent.TimeUnit.SECONDS);
    }
}
