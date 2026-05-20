package com.winter.airesumeoptimizer.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

    @Test
    void sanitizeShouldMaskCommonSensitiveValues() {
        String sanitized = LogSanitizer.sanitize(
                "Authorization=Bearer abc.def-123 token=raw-token password=raw-password "
                        + "phone=18312349015 email=alice@example.com "
                        + "path=/home/dawn/Project/uploads/resume.pdf win=C:\\Users\\dawn\\secret.txt");

        assertThat(sanitized)
                .contains("Bearer ***")
                .contains("token=***")
                .contains("password=***")
                .contains("183****9015")
                .contains("ali***@example.com")
                .contains("[path]");
        assertThat(sanitized)
                .doesNotContain("abc.def-123")
                .doesNotContain("raw-token")
                .doesNotContain("raw-password")
                .doesNotContain("alice@example.com")
                .doesNotContain("/home/dawn/Project/uploads/resume.pdf")
                .doesNotContain("C:\\Users\\dawn\\secret.txt");
    }

    @Test
    void maskHelpersShouldKeepInvalidInputUnchanged() {
        assertThat(LogSanitizer.maskPhone("123")).isEqualTo("123");
        assertThat(LogSanitizer.maskEmail("invalid")).isEqualTo("invalid");
    }
}
