package com.winter.airesumeoptimizer.infra.ai.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsServer;
import com.winter.airesumeoptimizer.infra.ai.AiChatMessage;
import com.winter.airesumeoptimizer.infra.ai.AiProviderRequest;
import com.winter.airesumeoptimizer.infra.ai.OpenAiCompatibleAiClientService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Live loopback security proofs for the pinned transport: the HTTP client only
 * ever connects to the already validated addresses, TLS hostname verification
 * stays bound to the original hostname, redirects are never followed and JVM
 * proxy settings are ignored. Certificates are generated with the JDK keytool.
 */
class PinnedHttpTransportSecurityTest {

    private static final String HOST = "provider.example";
    private static final String STORE_PASSWORD = "changeit";

    @TempDir
    Path tempDir;

    private HttpsServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        System.clearProperty("https.proxyHost");
        System.clearProperty("https.proxyPort");
        System.clearProperty("http.proxyHost");
        System.clearProperty("http.proxyPort");
    }

    @Test
    void shouldCompleteOpenAiAdapterThroughPinnedRealSocket() throws Exception {
        Path keystore = generateKeystore(HOST);
        AtomicReference<String> authorization = new AtomicReference<>();
        startServer(keystore, exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, """
                    {"choices":[{"message":{"content":"adapter-ok"}}],
                     "usage":{"prompt_tokens":3,"completion_tokens":2}}
                    """);
        });
        PinnedHttpTransport transport = transport(loopbackPolicy(HOST), clientSslContext(keystore));
        OpenAiCompatibleAiClientService adapter = new OpenAiCompatibleAiClientService(
                new ObjectMapper(), transport);

        var response = adapter.complete(new AiProviderRequest(
                "synthetic-byok-key",
                "https://" + HOST + "/v1",
                "synthetic-model",
                0.2d,
                100,
                Duration.ofSeconds(10),
                List.of(AiChatMessage.system("policy"), AiChatMessage.user("data"))));

        assertThat(response.text()).isEqualTo("adapter-ok");
        assertThat(response.inputTokens()).isEqualTo(3L);
        assertThat(response.outputTokens()).isEqualTo(2L);
        assertThat(authorization.get()).isEqualTo("Bearer synthetic-byok-key");
    }

    @Test
    void shouldConnectToPinnedValidatedAddressWithHostnameBoundTls() throws Exception {
        Path keystore = generateKeystore(HOST);
        AtomicInteger hits = new AtomicInteger();
        startServer(keystore, exchange -> {
            hits.incrementAndGet();
            respond(exchange, 200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");
        });

        PinnedHttpTransport transport = transport(loopbackPolicy(HOST), clientSslContext(keystore));

        OutboundResponse response = transport.execute(request(Duration.ofSeconds(10)));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("ok");
        assertThat(hits.get()).isEqualTo(1);
    }

    @Test
    void shouldFailClosedWhenTlsCertificateHostnameDoesNotMatch() throws Exception {
        Path evilKeystore = generateKeystore("evil.example");
        startServer(evilKeystore, exchange -> respond(exchange, 200, "{\"ok\":true}"));

        PinnedHttpTransport transport = transport(loopbackPolicy(HOST), clientSslContext(evilKeystore));

        assertThatThrownBy(() -> transport.execute(request(Duration.ofSeconds(10))))
                .isInstanceOf(OutboundTransportException.class)
                .extracting(exception -> ((OutboundTransportException) exception).getKind())
                .isEqualTo(OutboundTransportException.Kind.NETWORK);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 303, 307, 308})
    void shouldNeverFollowRedirects(int redirectStatus) throws Exception {
        Path keystore = generateKeystore(HOST);
        AtomicInteger redirectedHits = new AtomicInteger();
        startServer(keystore, exchange -> {
            if ("/v1/evil".equals(exchange.getRequestURI().getPath())) {
                redirectedHits.incrementAndGet();
                respond(exchange, 200, "{\"evil\":true}");
                return;
            }
            exchange.getResponseHeaders().set("Location", "https://" + HOST + "/v1/evil");
            exchange.sendResponseHeaders(redirectStatus, -1);
            exchange.close();
        });

        PinnedHttpTransport transport = transport(loopbackPolicy(HOST), clientSslContext(keystore));

        OutboundResponse response = transport.execute(request(Duration.ofSeconds(10)));

        assertThat(response.statusCode()).isEqualTo(redirectStatus);
        assertThat(redirectedHits.get()).isZero();
    }

    @Test
    void shouldIgnoreJvmProxySettings() throws Exception {
        Path keystore = generateKeystore(HOST);
        startServer(keystore, exchange -> respond(exchange, 200, "{\"ok\":true}"));
        // A dead proxy would break the request if the transport honored it.
        System.setProperty("https.proxyHost", "127.0.0.1");
        System.setProperty("https.proxyPort", "9");
        System.setProperty("http.proxyHost", "127.0.0.1");
        System.setProperty("http.proxyPort", "9");

        PinnedHttpTransport transport = transport(loopbackPolicy(HOST), clientSslContext(keystore));

        OutboundResponse response = transport.execute(request(Duration.ofSeconds(10)));

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void shouldRejectDnsRebindingAtConnectTime() throws Exception {
        // First resolution is safe; a rebinding at connection time must be
        // caught by the pinned resolver's re-validation and never connected to.
        AtomicInteger resolutions = new AtomicInteger();
        InetAddress loopback = InetAddress.getLoopbackAddress();
        InetAddress privateAddress = InetAddress.getByName("10.0.0.5");
        LoopbackOnlyPolicy policy = new LoopbackOnlyPolicy(host -> {
            assertThat(host).isEqualTo(HOST);
            return resolutions.incrementAndGet() == 1
                    ? new InetAddress[]{loopback}
                    : new InetAddress[]{privateAddress};
        });
        PinnedHttpTransport transport = new PinnedHttpTransport(policy, null, null);

        assertThatThrownBy(() -> transport.execute(request(Duration.ofSeconds(10))))
                .isInstanceOf(OutboundTransportException.class)
                .extracting(exception -> ((OutboundTransportException) exception).getKind())
                .isEqualTo(OutboundTransportException.Kind.UNSAFE_URL);
        assertThat(resolutions.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void shouldSendOriginalHostnameAsTlsSni() throws Exception {
        Path keystore = generateKeystore(HOST);
        AtomicReference<List<String>> requestedNames = new AtomicReference<>(List.of());
        startServer(keystore, exchange -> {
            ExtendedSSLSession session = (ExtendedSSLSession) ((HttpsExchange) exchange).getSSLSession();
            requestedNames.set(session.getRequestedServerNames().stream().map(Object::toString).toList());
            respond(exchange, 200, "{\"ok\":true}");
        });

        PinnedHttpTransport transport = transport(loopbackPolicy(HOST), clientSslContext(keystore));
        transport.execute(request(Duration.ofSeconds(10)));

        assertThat(requestedNames.get()).anyMatch(name -> name.contains(HOST));
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 500})
    void shouldRejectGzipResponseAboveDecodedLimitForSuccessAndError(int status) throws Exception {
        Path keystore = generateKeystore(HOST);
        byte[] decoded = new byte[PinnedHttpTransport.MAX_RESPONSE_BYTES + 1];
        startServer(keystore, exchange -> respondGzipChunked(exchange, status, decoded));
        PinnedHttpTransport transport = transport(loopbackPolicy(HOST), clientSslContext(keystore));

        assertThatThrownBy(() -> transport.execute(request(Duration.ofSeconds(10))))
                .isInstanceOf(OutboundTransportException.class)
                .extracting(exception -> ((OutboundTransportException) exception).getKind())
                .isEqualTo(OutboundTransportException.Kind.RESPONSE_TOO_LARGE);
    }

    @Test
    void shouldCancelTricklingResponseAtAbsoluteDeadline() throws Exception {
        Path keystore = generateKeystore(HOST);
        startServer(keystore, exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream output = exchange.getResponseBody()) {
                for (int index = 0; index < 20; index++) {
                    output.write('x');
                    output.flush();
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (IOException ignored) {
                // Expected once the client enforces the absolute deadline.
            }
        });
        PinnedHttpTransport transport = transport(loopbackPolicy(HOST), clientSslContext(keystore));
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> transport.execute(request(Duration.ofMillis(600))))
                .isInstanceOf(OutboundTransportException.class)
                .extracting(exception -> ((OutboundTransportException) exception).getKind())
                .isEqualTo(OutboundTransportException.Kind.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void shouldMapStalledTlsHandshakeToBoundedTimeout() throws Exception {
        try (ServerSocket stalledTls = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread accepter = Thread.ofVirtual().start(() -> {
                try (var ignored = stalledTls.accept()) {
                    Thread.sleep(10_000);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (IOException ignored) {
                    // The test closes the listener after the timeout assertion.
                }
            });
            PinnedHttpTransport transport = new PinnedHttpTransport(
                    loopbackPolicy(HOST),
                    SSLContext.getDefault(),
                    host -> stalledTls.getLocalPort());
            long startedAt = System.nanoTime();

            try {
                assertThatThrownBy(() -> transport.execute(request(Duration.ofSeconds(10))))
                        .isInstanceOf(OutboundTransportException.class)
                        .extracting(exception -> ((OutboundTransportException) exception).getKind())
                        .isEqualTo(OutboundTransportException.Kind.TIMEOUT);
                assertThat(Duration.ofNanos(System.nanoTime() - startedAt))
                        .isLessThan(Duration.ofSeconds(7));
            } finally {
                accepter.interrupt();
            }
        }
    }

    @Test
    void shouldEnforceResponseTimeoutAgainstSlowServer() throws Exception {
        Path keystore = generateKeystore(HOST);
        startServer(keystore, exchange -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{\"ok\":true}");
        });

        PinnedHttpTransport transport = transport(loopbackPolicy(HOST), clientSslContext(keystore));
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> transport.execute(request(Duration.ofMillis(600))))
                .isInstanceOf(OutboundTransportException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(PinnedHttpTransport.DNS_AND_CONNECT_LIMIT);
    }

    /**
     * Routes the canonical HTTPS port to the ephemeral loopback server port.
     * Production transports always connect to 443.
     */
    private PinnedHttpTransport transport(BaseUrlPolicy policy, SSLContext sslContext) {
        return new PinnedHttpTransport(
                policy,
                sslContext,
                host -> server.getAddress().getPort());
    }

    private OutboundRequest request(Duration timeout) {
        return new OutboundRequest(
                "POST",
                "https://" + HOST + "/v1",
                "/chat/completions",
                Map.of("Authorization", "Bearer synthetic"),
                "{\"model\":\"m\"}",
                timeout);
    }

    private LoopbackOnlyPolicy loopbackPolicy(String host) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        return new LoopbackOnlyPolicy(name -> {
            assertThat(name).isEqualTo(host);
            return new InetAddress[]{loopback};
        });
    }

    private void startServer(Path keystore, HttpHandler handler) throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        KeyStore store = loadKeystore(keystore);
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(store, STORE_PASSWORD.toCharArray());
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        server.createContext("/", handler);
        server.start();
    }

    private SSLContext clientSslContext(Path keystore) throws Exception {
        KeyStore store = loadKeystore(keystore);
        TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(store);
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        return sslContext;
    }

    private KeyStore loadKeystore(Path keystore) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(keystore)) {
            store.load(input, STORE_PASSWORD.toCharArray());
        }
        return store;
    }

    private Path generateKeystore(String hostname) throws Exception {
        Path keystore = tempDir.resolve(hostname + ".p12");
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        ProcessBuilder builder = new ProcessBuilder(List.of(
                keytool.toString(),
                "-genkeypair",
                "-alias", "server",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "2",
                "-dname", "CN=" + hostname,
                "-ext", "SAN=dns:" + hostname,
                "-storetype", "PKCS12",
                "-keystore", keystore.toString(),
                "-storepass", STORE_PASSWORD,
                "-keypass", STORE_PASSWORD));
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("keytool failed: " + output);
        }
        return keystore;
    }

    private void respondGzipChunked(HttpExchange exchange, int status, byte[] decoded) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(decoded);
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Content-Encoding", "gzip");
        exchange.sendResponseHeaders(status, 0);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(compressed.toByteArray());
        }
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    /** Test policy which accepts loopback addresses only. */
    private static final class LoopbackOnlyPolicy extends BaseUrlPolicy {

        private LoopbackOnlyPolicy(java.util.function.Function<String, InetAddress[]> resolver) {
            super(resolver);
        }

        @Override
        public boolean isGlobalUnicast(InetAddress address) {
            return address != null && address.isLoopbackAddress();
        }
    }
}
