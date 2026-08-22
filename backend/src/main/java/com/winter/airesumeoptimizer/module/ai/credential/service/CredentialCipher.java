package com.winter.airesumeoptimizer.module.ai.credential.service;

public interface CredentialCipher {

    EncryptedValue encrypt(String plaintext, Long userId, Long credentialId);

    String decrypt(String envelope, Long userId, Long credentialId);

    boolean isEnabled();

    boolean needsRotation(String keyVersion);

    void validateEnvelopeKeyVersion(String envelope, String keyVersion);

    default EncryptedValue rotate(String envelope, Long userId, Long credentialId) {
        return encrypt(decrypt(envelope, userId, credentialId), userId, credentialId);
    }

    record EncryptedValue(String envelope, String keyVersion) {
    }
}
