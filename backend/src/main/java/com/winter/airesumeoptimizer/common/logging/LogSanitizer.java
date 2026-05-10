package com.winter.airesumeoptimizer.common.logging;

import java.util.regex.Pattern;

public final class LogSanitizer {

    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("Bearer\\s+[A-Za-z0-9._\\-]+");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(api[_-]?key|token|secret|password)\\s*[:=]\\s*[^\\s,;]+");

    private LogSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = BEARER_TOKEN_PATTERN.matcher(value).replaceAll("Bearer ***");
        sanitized = API_KEY_PATTERN.matcher(sanitized).replaceAll("$1=***");
        return truncate(sanitized);
    }

    public static String truncate(String value) {
        if (value == null || value.length() <= MAX_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_MESSAGE_LENGTH) + "...";
    }
}
