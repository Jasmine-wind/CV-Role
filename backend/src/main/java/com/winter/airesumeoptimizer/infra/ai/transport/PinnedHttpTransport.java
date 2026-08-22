package com.winter.airesumeoptimizer.infra.ai.transport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.config.TlsConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Bounded HTTPS transport which validates DNS answers and gives Apache HttpClient
 * the already validated addresses. A new connection manager is used per request,
 * preventing an old pooled connection from crossing a Credential revision boundary.
 */
@Service
public class PinnedHttpTransport {

    public static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    public static final Duration DNS_AND_CONNECT_LIMIT = Duration.ofSeconds(5);
    public static final Duration MAX_TOTAL_TIMEOUT = Duration.ofSeconds(120);

    private static final ExecutorService REQUEST_EXECUTOR = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("ai-http-", 0).factory());

    private final BaseUrlPolicy baseUrlPolicy;
    private final SSLContext testSslContext;
    private final org.apache.hc.client5.http.SchemePortResolver testSchemePortResolver;

    @Autowired
    public PinnedHttpTransport() {
        this(new BaseUrlPolicy());
    }

    public PinnedHttpTransport(BaseUrlPolicy baseUrlPolicy) {
        this(baseUrlPolicy, null, null);
    }

    /**
     * Production beans always use the JVM default trust material and the
     * canonical HTTPS port. Non-null values are test-only seams which keep the
     * loopback TLS security tests unprivileged.
     */
    PinnedHttpTransport(
            BaseUrlPolicy baseUrlPolicy,
            SSLContext testSslContext,
            org.apache.hc.client5.http.SchemePortResolver testSchemePortResolver) {
        this.baseUrlPolicy = baseUrlPolicy == null ? new BaseUrlPolicy() : baseUrlPolicy;
        this.testSslContext = testSslContext;
        this.testSchemePortResolver = testSchemePortResolver;
    }

    public OutboundResponse execute(OutboundRequest request) {
        if (request == null) {
            throw new OutboundTransportException(
                    OutboundTransportException.Kind.UNSAFE_URL,
                    "出站请求不能为空");
        }
        Duration timeout = clampTimeout(request.timeout());
        long startedAt = System.nanoTime();
        BaseUrlPolicy.ValidatedBaseUrl validated = baseUrlPolicy.validateAndResolve(request.baseUrl());
        URI validatedUri = validated.uri();
        if (testSchemePortResolver != null) {
            // Test-only seam: drop the explicit HTTPS port so the route planner
            // falls back to the injected resolver. Production URIs always keep 443.
            validatedUri = stripExplicitPort(validatedUri);
        }
        URI requestUri = buildRequestUri(validatedUri, request.endpointPath());
        Duration remaining = remainingBudget(timeout, startedAt);
        Future<OutboundResponse> execution = REQUEST_EXECUTOR.submit(() ->
                executeBound(request, requestUri, validated, remaining));
        try {
            return execution.get(Math.max(1L, remaining.toMillis()), TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException exception) {
            execution.cancel(true);
            throw new OutboundTransportException(
                    OutboundTransportException.Kind.TIMEOUT,
                    "出站请求超时");
        } catch (InterruptedException exception) {
            execution.cancel(true);
            Thread.currentThread().interrupt();
            throw new OutboundTransportException(
                    OutboundTransportException.Kind.INTERRUPTED,
                    "出站请求被中断");
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof OutboundTransportException transportException) {
                throw transportException;
            }
            if (cause instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw new OutboundTransportException(
                        OutboundTransportException.Kind.INTERRUPTED,
                        "出站请求被中断");
            }
            if (isTimeout(cause)) {
                throw new OutboundTransportException(
                        OutboundTransportException.Kind.TIMEOUT,
                        "出站请求超时");
            }
            throw new OutboundTransportException(
                    OutboundTransportException.Kind.NETWORK,
                    "出站请求失败");
        }
    }

    private OutboundResponse executeBound(
            OutboundRequest request,
            URI requestUri,
            BaseUrlPolicy.ValidatedBaseUrl validated,
            Duration timeout)
            throws IOException, InterruptedException {
        DnsResolver resolver = new PinnedDnsResolver(
                validated.uri().getHost(),
                validated.addresses(),
                baseUrlPolicy);
        var connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(resolver)
                .setMaxConnTotal(1)
                .setMaxConnPerRoute(1)
                .setConnectionTimeToLive(TimeValue.ZERO_MILLISECONDS)
                .setDefaultTlsConfig(TlsConfig.custom()
                        .setHandshakeTimeout(Timeout.ofMilliseconds(
                                Math.min(DNS_AND_CONNECT_LIMIT.toMillis(), timeout.toMillis())))
                        .build());
        if (testSslContext != null) {
            connectionManagerBuilder.setSSLSocketFactory(
                    org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder.create()
                            .setSslContext(testSslContext)
                            .build());
        }
        var connectionManager = connectionManagerBuilder.build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setRedirectsEnabled(false)
                .setCircularRedirectsAllowed(false)
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeout.toMillis()))
                .setConnectTimeout(Timeout.ofMilliseconds(
                        Math.min(DNS_AND_CONNECT_LIMIT.toMillis(), timeout.toMillis())))
                .setResponseTimeout(Timeout.ofMilliseconds(timeout.toMillis()))
                .setHardCancellationEnabled(true)
                .build();

        try (connectionManager;
             CloseableHttpClient client = HttpClients.custom()
                     .setConnectionManager(connectionManager)
                     .setDefaultRequestConfig(requestConfig)
                     .setRoutePlanner(new DefaultRoutePlanner(testSchemePortResolver))
                     .disableRedirectHandling()
                     .disableAutomaticRetries()
                     .disableCookieManagement()
                     .build()) {
            ClassicHttpRequest httpRequest = buildHttpRequest(request, requestUri);
            long startedAt = System.nanoTime();
            try (CloseableHttpResponse response = client.execute(httpRequest)) {
                String body = readBoundedBody(response.getEntity());
                Map<String, String> headers = new LinkedHashMap<>();
                for (var header : response.getHeaders()) {
                    headers.putIfAbsent(header.getName(), header.getValue());
                }
                return new OutboundResponse(response.getCode(), headers, body);
            } finally {
                if (elapsedMillis(startedAt) > timeout.toMillis()) {
                    throw new OutboundTransportException(
                            OutboundTransportException.Kind.TIMEOUT,
                            "出站请求超时");
                }
            }
        }
    }

    private ClassicHttpRequest buildHttpRequest(OutboundRequest request, URI requestUri) {
        ClassicHttpRequest httpRequest;
        if ("POST".equals(request.method())) {
            httpRequest = new org.apache.hc.client5.http.classic.methods.HttpPost(requestUri);
            httpRequest.setEntity(new StringEntity(request.body(), ContentType.APPLICATION_JSON));
        } else if ("GET".equals(request.method())) {
            httpRequest = new org.apache.hc.client5.http.classic.methods.HttpGet(requestUri);
        } else {
            throw new OutboundTransportException(
                    OutboundTransportException.Kind.UNSAFE_URL,
                    "不支持的出站 HTTP 方法");
        }
        request.headers().forEach(httpRequest::setHeader);
        return httpRequest;
    }

    String readBoundedBody(HttpEntity entity) throws IOException {
        if (entity == null) {
            return "";
        }
        if (entity.getContentLength() > MAX_RESPONSE_BYTES) {
            throw new OutboundTransportException(
                    OutboundTransportException.Kind.RESPONSE_TOO_LARGE,
                    "Provider 响应超过安全大小限制");
        }
        try (InputStream input = entity.getContent(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new OutboundTransportException(
                            OutboundTransportException.Kind.RESPONSE_TOO_LARGE,
                            "Provider 响应超过安全大小限制");
                }
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private URI buildRequestUri(URI baseUri, String endpointPath) {
        String endpoint = endpointPath == null ? "" : endpointPath.strip();
        if (!endpoint.startsWith("/")
                || endpoint.contains("?")
                || endpoint.contains("#")
                || endpoint.contains("..")
                || endpoint.toLowerCase().contains("%2f")
                || endpoint.toLowerCase().contains("%2e")) {
            throw new OutboundTransportException(
                    OutboundTransportException.Kind.UNSAFE_URL,
                    "Provider endpoint 路径不安全");
        }
        String base = baseUri.toString();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + endpoint);
    }

    private URI stripExplicitPort(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), -1, uri.getPath(), null, null);
        } catch (java.net.URISyntaxException exception) {
            throw new OutboundTransportException(
                    OutboundTransportException.Kind.UNSAFE_URL,
                    "Base URL 规范化失败");
        }
    }

    private Duration clampTimeout(Duration requested) {
        if (requested == null || requested.isZero() || requested.isNegative()) {
            return Duration.ofSeconds(30);
        }
        return requested.compareTo(MAX_TOTAL_TIMEOUT) > 0 ? MAX_TOTAL_TIMEOUT : requested;
    }

    private boolean isTimeout(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.util.concurrent.TimeoutException
                    || current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Duration remainingBudget(Duration timeout, long startedAt) {
        long elapsedNanos = System.nanoTime() - startedAt;
        long remainingNanos = timeout.toNanos() - elapsedNanos;
        if (remainingNanos <= 0) {
            throw new OutboundTransportException(
                    OutboundTransportException.Kind.TIMEOUT,
                    "出站请求超时");
        }
        return Duration.ofNanos(remainingNanos);
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private static final class PinnedDnsResolver implements DnsResolver {

        private final String expectedHost;
        private final InetAddress[] addresses;
        private final BaseUrlPolicy policy;

        private PinnedDnsResolver(String expectedHost, InetAddress[] addresses, BaseUrlPolicy policy) {
            this.expectedHost = expectedHost;
            this.addresses = addresses.clone();
            this.policy = policy;
        }

        @Override
        public InetAddress[] resolve(String host) {
            if (!expectedHost.equalsIgnoreCase(host)) {
                throw new OutboundTransportException(
                        OutboundTransportException.Kind.UNSAFE_URL,
                        "Provider hostname 在连接时发生变化");
            }
            if (policy.resolveAndValidate(host).length == 0) {
                throw new OutboundTransportException(
                        OutboundTransportException.Kind.UNSAFE_URL,
                        "Provider hostname 没有安全地址");
            }
            return addresses.clone();
        }

        @Override
        public String resolveCanonicalHostname(String host) {
            if (!expectedHost.equalsIgnoreCase(host)) {
                throw new OutboundTransportException(
                        OutboundTransportException.Kind.UNSAFE_URL,
                        "Provider hostname 在连接时发生变化");
            }
            return expectedHost;
        }
    }
}
