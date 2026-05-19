package com.winter.airesumeoptimizer.infra.storage;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorageService {

    StoredFile store(StoreFileCommand command);

    InputStream loadAsStream(String storageKey);

    default byte[] loadAsBytes(String storageKey) {
        try (InputStream inputStream = loadAsStream(storageKey)) {
            return inputStream.readAllBytes();
        } catch (IOException exception) {
            throw new FileStorageException("文件读取失败", exception);
        }
    }

    boolean exists(String storageKey);

    void delete(String storageKey);

    StoredFileMetadata getMetadata(String storageKey);
}
