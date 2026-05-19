package com.winter.airesumeoptimizer.infra.storage;

public record StoredFileMetadata(
        String storageKey,
        long size,
        String storageType) {
}
