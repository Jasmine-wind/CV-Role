package com.winter.airesumeoptimizer.module.export.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.winter.airesumeoptimizer.module.export.service.PreviewReceiptClaims;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PreviewReceiptServiceTest {

    private static final String SECRET = "phase6-preview-receipt-test-secret-at-least-32-chars";
    private static final Instant NOW = Instant.parse("2026-08-21T00:00:00Z");

    @Test
    void roundTripsEveryFrozenPreviewBinding() {
        PreviewReceiptService service = serviceAt(NOW);
        PreviewReceiptClaims claims = claims();

        String receipt = service.issue(claims);

        assertThat(service.verify(receipt)).isEqualTo(claims);
    }

    @Test
    void rejectsTamperedAndExpiredReceipt() {
        PreviewReceiptService issuer = serviceAt(NOW);
        String receipt = issuer.issue(claims());
        String tampered = receipt.substring(0, receipt.length() - 1)
                + (receipt.endsWith("a") ? "b" : "a");

        assertThatThrownBy(() -> issuer.verify(tampered))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serviceAt(NOW.plus(Duration.ofMinutes(11))).verify(receipt))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private PreviewReceiptService serviceAt(Instant instant) {
        return new PreviewReceiptService(
                SECRET,
                Duration.ofMinutes(10),
                Clock.fixed(instant, ZoneOffset.UTC));
    }

    private PreviewReceiptClaims claims() {
        return new PreviewReceiptClaims(
                7L, 42L, 99L, 3L,
                "classic", "1", "typst-resume-renderer/1", "a".repeat(64));
    }
}
