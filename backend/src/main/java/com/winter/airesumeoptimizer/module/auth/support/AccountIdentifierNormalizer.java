package com.winter.airesumeoptimizer.module.auth.support;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Canonicalization shared by registration and login so account routing cannot
 * disagree with the values persisted in the users table.
 */
public final class AccountIdentifierNormalizer {

    private static final Pattern EMAIL_LIKE = Pattern.compile("^[^\\s@]+@[^\\s@]+$");

    private AccountIdentifierNormalizer() {
    }

    public static String normalizeUsername(String username) {
        return username == null ? null : username.strip();
    }

    public static String normalizeEmail(String email) {
        String normalized = email == null ? null : email.strip();
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    public static String normalizeLoginAccount(String account) {
        String normalized = account == null ? null : account.strip();
        return isEmailLike(normalized) ? normalized.toLowerCase(Locale.ROOT) : normalized;
    }

    public static boolean isEmailLike(String value) {
        return value != null && EMAIL_LIKE.matcher(value).matches();
    }
}
