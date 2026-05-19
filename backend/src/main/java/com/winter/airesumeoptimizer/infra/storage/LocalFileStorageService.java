package com.winter.airesumeoptimizer.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private static final String STORAGE_TYPE_LOCAL = "LOCAL";

    private final LocalStoragePathResolver pathResolver;
    private final SafeFilenameGenerator safeFilenameGenerator;

    public LocalFileStorageService(
            LocalStoragePathResolver pathResolver,
            SafeFilenameGenerator safeFilenameGenerator) {
        this.pathResolver = pathResolver;
        this.safeFilenameGenerator = safeFilenameGenerator;
    }

    @Override
    public StoredFile store(StoreFileCommand command) {
        if (command == null || command.inputStream() == null || command.size() <= 0) {
            throw new FileStorageException("文件不能为空");
        }

        String originalFilename = StringUtils.cleanPath(command.originalFilename() == null
                ? "resume"
                : command.originalFilename());
        String safeFilename = safeFilenameGenerator.generate(originalFilename);
        String storageKey = pathResolver.generateStorageKey(command, safeFilename);
        Path targetPath = pathResolver.resolve(storageKey);

        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(command.inputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new FileStorageException("文件保存失败", exception);
        }

        return new StoredFile(
                storageKey,
                originalFilename,
                command.contentType(),
                command.size(),
                STORAGE_TYPE_LOCAL);
    }

    @Override
    public InputStream loadAsStream(String storageKey) {
        Path targetPath = pathResolver.resolve(storageKey);

        try {
            return Files.newInputStream(targetPath);
        } catch (IOException exception) {
            throw new FileStorageException("文件读取失败", exception);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        return Files.exists(pathResolver.resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }

        Path targetPath = pathResolver.resolve(storageKey);

        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException exception) {
            throw new FileStorageException("文件清理失败", exception);
        }
    }

    @Override
    public StoredFileMetadata getMetadata(String storageKey) {
        Path targetPath = pathResolver.resolve(storageKey);
        try {
            return new StoredFileMetadata(storageKey, Files.size(targetPath), STORAGE_TYPE_LOCAL);
        } catch (IOException exception) {
            throw new FileStorageException("文件元信息读取失败", exception);
        }
    }
}
