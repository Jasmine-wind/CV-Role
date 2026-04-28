package com.winter.airesumeoptimizer.infra.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    StoredFile store(MultipartFile file, String directory);

    void delete(String objectKey);
}
