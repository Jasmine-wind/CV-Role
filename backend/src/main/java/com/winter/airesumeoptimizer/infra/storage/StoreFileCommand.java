package com.winter.airesumeoptimizer.infra.storage;

import java.io.InputStream;

public record StoreFileCommand(
        Long userId,
        String originalFilename,
        String contentType,
        long size,
        InputStream inputStream,
        String bizType) {
}
