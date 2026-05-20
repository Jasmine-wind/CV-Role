package com.winter.airesumeoptimizer.common.logging;

import java.util.regex.Pattern;

public final class LogSanitizer {

    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile("Bearer\\s+[A-Za-z0-9._\\-]+");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(api[_-]?key|token|secret|password)\\s*[:=]\\s*[^\\s,;]+");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "([A-Za-z0-9._%+\\-])([A-Za-z0-9._%+\\-]*)([A-Za-z0-9._%+\\-])@([A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})");
    private static final Pattern UNIX_ABSOLUTE_PATH_PATTERN = Pattern.compile("(?<![\\w.-])/(?:[\\w.\\-]+/)+[\\w.\\-]+");
    private static final Pattern WINDOWS_ABSOLUTE_PATH_PATTERN = Pattern.compile("(?i)\\b[A-Z]:\\\\(?:[^\\\\\\s]+\\\\)*[^\\\\\\s]+");

    private LogSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String sanitized = BEARER_TOKEN_PATTERN.matcher(value).replaceAll("Bearer ***");
        sanitized = API_KEY_PATTERN.matcher(sanitized).replaceAll("$1=***");
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll(match -> maskPhone(match.group(1)));
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll(match -> maskEmail(match.group()));
        sanitized = UNIX_ABSOLUTE_PATH_PATTERN.matcher(sanitized).replaceAll("[path]");
        sanitized = WINDOWS_ABSOLUTE_PATH_PATTERN.matcher(sanitized).replaceAll("[path]");
        return truncate(sanitized);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0 || atIndex == email.length() - 1) {
            return email;
        }
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        String prefix = localPart.substring(0, Math.min(3, localPart.length()));
        return prefix + "***@" + domain;
    }

    public static String truncate(String value) {
        if (value == null || value.length() <= MAX_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_MESSAGE_LENGTH) + "...";
    }
}
