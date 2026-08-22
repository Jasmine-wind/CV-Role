package com.winter.airesumeoptimizer.module.ai.credential.service;

/**
 * Short-lived in-memory provider material. This type must never cross a controller,
 * entity, task snapshot, usage or logging boundary.
 */
public record DecryptedCredentialMaterial(
        String apiKey,
        String baseUrl,
        String model,
        String configJson,
        Long credentialId,
        Long credentialRevision) {
}
