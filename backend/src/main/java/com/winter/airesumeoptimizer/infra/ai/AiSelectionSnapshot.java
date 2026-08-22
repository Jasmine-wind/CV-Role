package com.winter.airesumeoptimizer.infra.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Immutable, non-secret provider selection captured for a task.
 * The API key and its ciphertext are deliberately absent.
 */
public record AiSelectionSnapshot(
        AiSource source,
        String providerType,
        Long credentialId,
        Long credentialRevision,
        String baseUrl,
        String model,
        String configJson,
        String configFingerprint) {

    public static final String OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";

    public AiSelectionSnapshot {
        source = source == null ? AiSource.SYSTEM_DEFAULT : source;
        providerType = normalize(providerType, OPENAI_COMPATIBLE);
        baseUrl = normalize(baseUrl, "");
        model = normalize(model, "");
        configJson = normalize(configJson, "{}");
        configFingerprint = normalize(configFingerprint, fingerprint(configJson));
    }

    public String cacheIdentity(Long userId) {
        String raw = String.join("|",
                String.valueOf(userId == null ? "unknown" : userId),
                source.name(),
                String.valueOf(credentialId == null ? "none" : credentialId),
                String.valueOf(credentialRevision == null ? "none" : credentialRevision),
                providerType,
                model,
                configFingerprint,
                baseUrl);
        return fingerprint(raw);
    }

    public boolean isUserByok() {
        return source == AiSource.USER_BYOK;
    }

    private static String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    public static String fingerprint(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    (value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用");
        }
    }
}
