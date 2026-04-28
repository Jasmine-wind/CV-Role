package com.winter.airesumeoptimizer.infra.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LocalFileStorageService implements FileStorageService {

    private final Path baseDirectory;

    public LocalFileStorageService(FileStorageProperties properties) {
        this.baseDirectory = Path.of(properties.getBaseDir()).toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("文件不能为空");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null
                ? "resume"
                : file.getOriginalFilename());
        String objectKey = buildObjectKey(directory, originalFilename);
        Path targetPath = baseDirectory.resolve(objectKey).normalize();

        if (!targetPath.startsWith(baseDirectory)) {
            throw new FileStorageException("文件存储路径不合法");
        }

        try {
            Files.createDirectories(targetPath.getParent());
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new FileStorageException("文件保存失败", exception);
        }

        return new StoredFile(
                objectKey,
                originalFilename,
                file.getContentType(),
                file.getSize());
    }

    @Override
    public void delete(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        Path targetPath = baseDirectory.resolve(objectKey).normalize();
        if (!targetPath.startsWith(baseDirectory)) {
            throw new FileStorageException("文件存储路径不合法");
        }

        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException exception) {
            throw new FileStorageException("文件清理失败", exception);
        }
    }

    private String buildObjectKey(String directory, String originalFilename) {
        String safeDirectory = normalizeDirectory(directory);
        String extension = extractExtension(originalFilename);
        return safeDirectory + "/" + UUID.randomUUID() + extension;
    }

    private String normalizeDirectory(String directory) {
        String cleanDirectory = StringUtils.cleanPath(directory == null ? "default" : directory);
        cleanDirectory = cleanDirectory.replace("\\", "/");

        if (cleanDirectory.isBlank() || cleanDirectory.contains("..") || cleanDirectory.startsWith("/")) {
            throw new FileStorageException("文件存储目录不合法");
        }

        return cleanDirectory;
    }

    private String extractExtension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            return "";
        }
        return filename.substring(index).toLowerCase();
    }
}
