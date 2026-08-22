package com.winter.airesumeoptimizer.infra.ai.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaseUrlPolicyTest {

    @Test
    void shouldNormalizeSafeHttpsDnsBaseUrl() {
        BaseUrlPolicy policy = new BaseUrlPolicy(host -> addresses("8.8.8.8"));

        BaseUrlPolicy.ValidatedBaseUrl result = policy.validateAndResolve("HTTPS://Api.Example.com/v1/");

        assertThat(result.uri().toString()).isEqualTo("https://api.example.com:443/v1");
        assertThat(result.addresses()).hasSize(1);
    }

    @Test
    void shouldRejectDangerousUrlStructure() {
        BaseUrlPolicy policy = new BaseUrlPolicy(host -> addresses("8.8.8.8"));

        for (String value : List.of(
                "http://api.example.com",
                "https://api.example.com:8443",
                "https://user:pass@api.example.com",
                "https://api.example.com?next=http://127.0.0.1",
                "https://api.example.com/#fragment",
                "https://127.0.0.1",
                "https://localhost",
                "https://api.example.com/v1/../internal",
                "https://api.example.com/v1/%2e%2e/internal")) {
            assertThatThrownBy(() -> policy.validateStructure(value))
                    .isInstanceOf(OutboundTransportException.class)
                    .extracting(exception -> ((OutboundTransportException) exception).getKind())
                    .isEqualTo(OutboundTransportException.Kind.UNSAFE_URL);
        }
    }

    @Test
    void shouldRejectAnyUnsafeDnsAnswerIncludingMixedARecords() {
        BaseUrlPolicy privatePolicy = new BaseUrlPolicy(host -> addresses("10.0.0.8"));
        assertThatThrownBy(() -> privatePolicy.validateAndResolve("https://provider.example.com"))
                .isInstanceOf(OutboundTransportException.class);

        BaseUrlPolicy mixedPolicy = new BaseUrlPolicy(host -> new InetAddress[]{
                address("8.8.8.8"),
                address("169.254.169.254")});
        assertThatThrownBy(() -> mixedPolicy.validateAndResolve("https://provider.example.com"))
                .isInstanceOf(OutboundTransportException.class);

        BaseUrlPolicy ulaPolicy = new BaseUrlPolicy(host -> addresses("fc00::1"));
        assertThatThrownBy(() -> ulaPolicy.validateAndResolve("https://provider.example.com"))
                .isInstanceOf(OutboundTransportException.class);
    }

    @Test
    void shouldRejectReservedAndMappedAddresses() throws Exception {
        for (String ip : List.of(
                "0.0.0.0",
                "100.64.0.1",
                "192.0.2.1",
                "198.51.100.1",
                "203.0.113.1",
                "::1",
                "::8.8.8.8",
                "64:ff9b::808:808",
                "100::1",
                "2001:20::1",
                "2001:db8::1",
                "2002::1")) {
            BaseUrlPolicy policy = new BaseUrlPolicy(host -> addresses(ip));
            assertThatThrownBy(() -> policy.validateAndResolve("https://provider.example.com"))
                    .as("address %s", ip)
                    .isInstanceOf(OutboundTransportException.class);
        }
        byte[] mapped = new byte[16];
        mapped[10] = (byte) 0xff;
        mapped[11] = (byte) 0xff;
        mapped[12] = 8;
        mapped[13] = 8;
        mapped[14] = 8;
        mapped[15] = 8;
        BaseUrlPolicy mappedPolicy = new BaseUrlPolicy(host -> new InetAddress[]{address6(mapped)});
        assertThatThrownBy(() -> mappedPolicy.validateAndResolve("https://provider.example.com"))
                .isInstanceOf(OutboundTransportException.class);
    }

    @Test
    void shouldBoundDnsResolutionTime() {
        BaseUrlPolicy policy = new BaseUrlPolicy(host -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return addresses("8.8.8.8");
        }, Duration.ofMillis(100));
        long startedAt = System.nanoTime();

        assertThatThrownBy(() -> policy.validateAndResolve("https://provider.example.com"))
                .isInstanceOf(OutboundTransportException.class)
                .extracting(exception -> ((OutboundTransportException) exception).getKind())
                .isEqualTo(OutboundTransportException.Kind.TIMEOUT);
        assertThat(Duration.ofNanos(System.nanoTime() - startedAt)).isLessThan(Duration.ofSeconds(1));
    }

    private InetAddress[] addresses(String... values) {
        InetAddress[] result = new InetAddress[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = address(values[index]);
        }
        return result;
    }

    private InetAddress address(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private InetAddress address(byte[] value) {
        try {
            return InetAddress.getByAddress(value);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private Inet6Address address6(byte[] value) {
        try {
            return Inet6Address.getByAddress(null, value, -1);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
