package com.winter.airesumeoptimizer.module.ai.credential.service;

import com.winter.airesumeoptimizer.infra.ai.AiFailureCode;
import com.winter.airesumeoptimizer.infra.ai.AiGatewayException;
import com.winter.airesumeoptimizer.module.ai.credential.config.AiCredentialProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class AesGcmCredentialCipher implements CredentialCipher {

    private static final String ENVELOPE_PREFIX = "enc:v1:";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AiCredentialProperties properties;

    public AesGcmCredentialCipher(AiCredentialProperties properties) {
        this.properties = properties;
        if (properties != null && properties.isEnabled()) {
            validateConfiguration();
        }
    }

    @Override
    public EncryptedValue encrypt(String plaintext, Long userId, Long credentialId) {
        requireEnabled();
        if (plaintext == null || plaintext.isBlank() || userId == null || credentialId == null) {
            throw unavailable();
        }
        Map<String, byte[]> keys = readKeyRing();
        String keyId = normalize(properties.getActiveKeyId());
        byte[] key = keys.get(keyId);
        if (key == null) {
            throw unavailable();
        }
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(userId, credentialId));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return new EncryptedValue(
                    ENVELOPE_PREFIX + keyId + ":" + encoder.encodeToString(nonce) + ":" + encoder.encodeToString(ciphertext),
                    keyId);
        } catch (GeneralSecurityException exception) {
            throw unavailable();
        }
    }

    @Override
    public String decrypt(String envelope, Long userId, Long credentialId) {
        requireEnabled();
        if (envelope == null || userId == null || credentialId == null) {
            throw unavailable();
        }
        String[] parts = envelope.split(":", -1);
        if (parts.length != 5 || !"enc".equals(parts[0]) || !"v1".equals(parts[1])) {
            throw unavailable();
        }
        String keyId = normalize(parts[2]);
        Map<String, byte[]> keys = readKeyRing();
        byte[] key = keys.get(keyId);
        if (key == null) {
            throw unavailable();
        }
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] nonce = decoder.decode(parts[3]);
            byte[] ciphertext = decoder.decode(parts[4]);
            if (nonce.length != NONCE_BYTES || ciphertext.length < 16) {
                throw unavailable();
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(userId, credentialId));
            String plaintext = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            if (plaintext.isBlank()) {
                throw unavailable();
            }
            return plaintext;
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw unavailable();
        }
    }

    @Override
    public boolean isEnabled() {
        return properties != null && properties.isEnabled();
    }

    @Override
    public boolean needsRotation(String keyVersion) {
        requireEnabled();
        return !normalize(properties.getActiveKeyId()).equals(normalize(keyVersion));
    }

    @Override
    public void validateEnvelopeKeyVersion(String envelope, String keyVersion) {
        if (envelope == null || keyVersion == null) {
            throw unavailable();
        }
        String[] parts = envelope.split(":", -1);
        if (parts.length != 5
                || !"enc".equals(parts[0])
                || !"v1".equals(parts[1])
                || !normalize(keyVersion).equals(normalize(parts[2]))) {
            throw unavailable();
        }
    }

    public void validateConfiguration() {
        requireEnabled();
        Map<String, byte[]> keys = readKeyRing();
        if (!keys.containsKey(normalize(properties.getActiveKeyId()))) {
            throw unavailable();
        }
    }

    private Map<String, byte[]> readKeyRing() {
        String raw = properties.getKeyRing();
        if (raw == null || raw.isBlank()) {
            throw unavailable();
        }
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (String entry : raw.split(";")) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String[] pair = entry.split("=", 2);
            if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
                throw unavailable();
            }
            String keyId = pair[0].strip();
            if (!KEY_ID_PATTERN.matcher(keyId).matches() || result.containsKey(keyId)) {
                throw unavailable();
            }
            byte[] key = decodeKey(pair[1].strip());
            if (key.length != KEY_BYTES) {
                throw unavailable();
            }
            result.put(keyId, key);
        }
        String activeKeyId = normalize(properties.getActiveKeyId());
        if (result.isEmpty() || !KEY_ID_PATTERN.matcher(activeKeyId).matches()) {
            throw unavailable();
        }
        return result;
    }

    private byte[] decodeKey(String value) {
        try {
            try {
                return Base64.getUrlDecoder().decode(value);
            } catch (IllegalArgumentException ignored) {
                return Base64.getDecoder().decode(value);
            }
        } catch (IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    private byte[] aad(Long userId, Long credentialId) {
        return ("cv-role/ai-credential/v1/" + userId + "/" + credentialId).getBytes(StandardCharsets.UTF_8);
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw unavailable();
        }
    }

    private AiGatewayException unavailable() {
        return new AiGatewayException(AiFailureCode.CONFIGURATION_INVALID, "AI Credential 加密配置不可用");
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
