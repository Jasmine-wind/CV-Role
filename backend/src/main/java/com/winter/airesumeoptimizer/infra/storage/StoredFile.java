package com.winter.airesumeoptimizer.infra.storage;

public record StoredFile(
        String objectKey,
        String originalFilename,
        String contentType,
        long size) {
}
