package com.winter.airesumeoptimizer.infra.storage;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LocalStoragePathResolver {

    private static final DateTimeFormatter DATE_DIRECTORY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final Path baseDirectory;

    public LocalStoragePathResolver(FileStorageProperties properties) {
        this.baseDirectory = Path.of(properties.getBaseDir()).toAbsolutePath().normalize();
    }

    public String generateStorageKey(StoreFileCommand command, String safeFilename) {
        Long userId = command.userId();
        if (userId == null || userId <= 0) {
            throw new FileStorageException("文件所属用户不合法");
        }
        String safeBizType = normalizeDirectory(command.bizType() == null ? "default" : command.bizType());
        String dateDirectory = LocalDate.now().format(DATE_DIRECTORY_FORMATTER);
        return safeBizType + "/" + userId + "/" + dateDirectory + "/" + UUID.randomUUID() + "-" + safeFilename;
    }

    public Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new FileStorageException("文件对象 key 不能为空");
        }
        if (storageKey.contains("\\") || storageKey.indexOf('\0') >= 0) {
            throw new FileStorageException("文件存储路径不合法");
        }

        Path targetPath = baseDirectory.resolve(storageKey).normalize();
        if (!targetPath.startsWith(baseDirectory)) {
            throw new FileStorageException("文件存储路径不合法");
        }
        return targetPath;
    }

    private String normalizeDirectory(String directory) {
        String cleanDirectory = StringUtils.cleanPath(directory == null ? "default" : directory);
        cleanDirectory = cleanDirectory.replace("\\", "/");

        if (cleanDirectory.isBlank() || cleanDirectory.contains("..") || cleanDirectory.startsWith("/")) {
            throw new FileStorageException("文件存储目录不合法");
        }

        return cleanDirectory;
    }
}
