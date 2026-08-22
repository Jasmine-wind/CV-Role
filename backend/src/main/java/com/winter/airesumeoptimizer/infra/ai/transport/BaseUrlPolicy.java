package com.winter.airesumeoptimizer.infra.ai.transport;

import java.net.IDN;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Server-side URL and address policy shared by all model transports.
 * Not final so loopback security tests can substitute a test-only address scope.
 */
public class BaseUrlPolicy {

    public static final int HTTPS_PORT = 443;
    private static final Duration DEFAULT_DNS_TIMEOUT = Duration.ofSeconds(5);
    private static final ExecutorService DNS_EXECUTOR = new ThreadPoolExecutor(
            0,
            8,
            30L,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            runnable -> Thread.ofPlatform().daemon().name("ai-dns-worker").unstarted(runnable),
            new ThreadPoolExecutor.AbortPolicy());
    private static final Pattern NUMERIC_HOST = Pattern.compile("^[0-9.]+$");
    private static final Pattern HEX_LITERAL_HOST = Pattern.compile("^(?i)(0x[0-9a-f]+|[0-9a-f:]+)$");
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "localhost",
            "localhost.",
            "metadata",
            "metadata.google.internal",
            "instance-data.ec2.internal");

    private final Function<String, InetAddress[]> resolver;
    private final Duration dnsTimeout;

    public BaseUrlPolicy() {
        this(BaseUrlPolicy::resolveSystemDns, DEFAULT_DNS_TIMEOUT);
    }

    public BaseUrlPolicy(Function<String, InetAddress[]> resolver) {
        this(resolver, DEFAULT_DNS_TIMEOUT);
    }

    public BaseUrlPolicy(Function<String, InetAddress[]> resolver, Duration dnsTimeout) {
        this.resolver = resolver == null ? BaseUrlPolicy::resolveSystemDns : resolver;
        this.dnsTimeout = dnsTimeout == null || dnsTimeout.isZero() || dnsTimeout.isNegative()
                ? DEFAULT_DNS_TIMEOUT
                : dnsTimeout.compareTo(DEFAULT_DNS_TIMEOUT) > 0 ? DEFAULT_DNS_TIMEOUT : dnsTimeout;
    }

    public URI validateStructure(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw unsafe("Base URL 不能为空");
        }
        String value = rawUrl.strip();
        final URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw unsafe("Base URL 格式不正确");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw unsafe("Base URL 只允许 HTTPS");
        }
        if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
            throw unsafe("Base URL 不得包含凭据、查询参数或片段");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw unsafe("Base URL 必须使用 DNS hostname");
        }
        if (uri.getPort() != -1 && uri.getPort() != HTTPS_PORT) {
            throw unsafe("Base URL 只允许 443 端口");
        }
        String host = canonicalHost(uri.getHost());
        if (isIpLiteral(uri.getHost()) || isBlockedHostname(host)) {
            throw unsafe("Base URL 主机地址不允许访问");
        }
        String rawPath = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (containsPathTraversal(rawPath) || endsWithProviderEndpoint(rawPath)) {
            throw unsafe("Base URL 路径不符合要求");
        }
        try {
            return new URI(
                    "https",
                    null,
                    host,
                    HTTPS_PORT,
                    normalizePath(uri.getPath()),
                    null,
                    null);
        } catch (URISyntaxException exception) {
            throw unsafe("Base URL 规范化失败");
        }
    }

    public InetAddress[] resolveAndValidate(String host) {
        final Future<InetAddress[]> future;
        try {
            future = DNS_EXECUTOR.submit(() -> resolver.apply(host));
        } catch (RejectedExecutionException exception) {
            throw new OutboundTransportException(
                    OutboundTransportException.Kind.TIMEOUT,
                    "Base URL DNS 解析资源繁忙");
        }
        final InetAddress[] addresses;
        try {
            addresses = future.get(dnsTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw new OutboundTransportException(
                    OutboundTransportException.Kind.TIMEOUT,
                    "Base URL DNS 解析超时");
        } catch (InterruptedException exception) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new OutboundTransportException(
                    OutboundTransportException.Kind.INTERRUPTED,
                    "Base URL DNS 解析被中断");
        } catch (ExecutionException | RuntimeException exception) {
            future.cancel(true);
            throw unsafe("Base URL DNS 解析失败");
        }
        if (addresses == null || addresses.length == 0) {
            throw unsafe("Base URL 没有可用地址");
        }
        if (Arrays.stream(addresses).anyMatch(address -> address == null || !isGlobalUnicast(address))) {
            throw unsafe("Base URL 解析到了不允许的网络地址");
        }
        return addresses.clone();
    }

    public ValidatedBaseUrl validateAndResolve(String rawUrl) {
        URI normalized = validateStructure(rawUrl);
        return new ValidatedBaseUrl(normalized, resolveAndValidate(normalized.getHost()));
    }

    public boolean isGlobalUnicast(InetAddress address) {
        if (address == null
                || address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            long value = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getInt() & 0xffffffffL;
            long first = (value >>> 24) & 0xff;
            long second = (value >>> 16) & 0xff;
            long third = (value >>> 8) & 0xff;
            if (first == 0 || first == 10 || first == 127 || first >= 224) {
                return false;
            }
            if (first == 100 && second >= 64 && second <= 127) {
                return false;
            }
            if (first == 169 && second == 254) {
                return false;
            }
            if (first == 172 && second >= 16 && second <= 31) {
                return false;
            }
            if (first == 192 && (second == 0 || second == 168)) {
                return false;
            }
            if (first == 192 && second == 31 && third == 196) {
                return false;
            }
            if (first == 192 && second == 52 && third == 193) {
                return false;
            }
            if (first == 192 && second == 88 && third == 99) {
                return false;
            }
            if (first == 192 && second == 175 && third == 48) {
                return false;
            }
            if (first == 192 && second == 0 && third == 2) {
                return false;
            }
            if (first == 198 && (second == 18 || second == 19 || second == 51)) {
                return false;
            }
            if (first == 203 && second == 0 && third == 113) {
                return false;
            }
            return value != 0xffffffffL;
        }
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        if ((first & 0xe0) != 0x20 // outside 2000::/3 global unicast
                || (first & 0xfe) == 0xfc // fc00::/7 ULA
                || (first == 0xfe && (second & 0xc0) == 0x80) // fe80::/10
                || first == 0xff // multicast
                || isIpv4Mapped(bytes)
                || isDocumentationIpv6(bytes)
                || isSpecialPurposeIpv6(bytes)) {
            return false;
        }
        return !isAllZero(bytes) && !(first == 0 && isIpv6Loopback(bytes));
    }

    private String canonicalHost(String host) {
        String normalized = host.strip().toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        try {
            return IDN.toASCII(normalized, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw unsafe("Base URL hostname 不合法");
        }
    }

    private boolean isIpLiteral(String host) {
        String normalized = host == null ? "" : host.strip();
        return normalized.contains(":")
                || NUMERIC_HOST.matcher(normalized).matches()
                || HEX_LITERAL_HOST.matcher(normalized).matches();
    }

    private boolean isBlockedHostname(String host) {
        return BLOCKED_HOSTS.contains(host)
                || host.endsWith(".localhost")
                || host.endsWith(".internal")
                || host.endsWith(".local");
    }

    private boolean containsPathTraversal(String rawPath) {
        String lower = rawPath.toLowerCase(Locale.ROOT);
        return lower.contains("%2e") || lower.contains("%2f") || lower.contains("%5c")
                || Arrays.stream(rawPath.split("/", -1)).anyMatch(".."::equals);
    }

    private boolean endsWithProviderEndpoint(String rawPath) {
        String path = rawPath == null ? "" : rawPath.replaceAll("/+", "/").toLowerCase(Locale.ROOT);
        return path.endsWith("/chat/completions") || path.endsWith("/embeddings");
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "";
        }
        String normalized = path.replaceAll("/{2,}", "/");
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int index = 0; index < 10; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private boolean isDocumentationIpv6(byte[] bytes) {
        return bytes.length == 16
                && (bytes[0] & 0xff) == 0x20
                && (bytes[1] & 0xff) == 0x01
                && (bytes[2] & 0xff) == 0x0d
                && (bytes[3] & 0xff) == 0xb8;
    }

    private boolean isSpecialPurposeIpv6(byte[] bytes) {
        // Reject the IETF special-purpose blocks conservatively. A public
        // Provider does not need transition, benchmarking or documentation space.
        return startsWith(bytes, 0x20, 0x01) && (bytes[2] & 0xff) <= 0x01 // 2001::/23
                || startsWith(bytes, 0x20, 0x02) // 2002::/16 6to4
                || startsWith(bytes, 0x3f, 0xff); // 3fff::/20 documentation
    }

    private static InetAddress[] resolveSystemDns(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalArgumentException("DNS 解析失败", exception);
        }
    }

    private boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((bytes[index] & 0xff) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean isAllZero(byte[] bytes) {
        for (byte value : bytes) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isIpv6Loopback(byte[] bytes) {
        for (int index = 0; index < 15; index++) {
            if (bytes[index] != 0) {
                return false;
            }
        }
        return bytes[15] == 1;
    }

    private OutboundTransportException unsafe(String message) {
        return new OutboundTransportException(OutboundTransportException.Kind.UNSAFE_URL, message);
    }

    public record ValidatedBaseUrl(URI uri, InetAddress[] addresses) {
        public ValidatedBaseUrl {
            addresses = addresses == null ? new InetAddress[0] : addresses.clone();
        }
    }
}
