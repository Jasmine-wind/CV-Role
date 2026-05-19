package com.winter.airesumeoptimizer.infra.storage;

public record StoredFile(
        String storageKey,
        String originalFilename,
        String contentType,
        long size,
        String storageType) {

    public String objectKey() {
        return storageKey;
    }
}
