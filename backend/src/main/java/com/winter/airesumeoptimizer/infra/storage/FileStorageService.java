package com.winter.airesumeoptimizer.infra.storage;

import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    StoredFile store(MultipartFile file, String directory);

    InputStream open(String objectKey);

    void delete(String objectKey);
}
