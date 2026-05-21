package com.winter.airesumeoptimizer.infra.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "minio")
public class MinioFileStorageService implements FileStorageService {

    private static final String STORAGE_TYPE_MINIO = "MINIO";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final DateTimeFormatter DATE_DIRECTORY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;
    private final SafeFilenameGenerator safeFilenameGenerator;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    public MinioFileStorageService(
            MinioClient minioClient,
            MinioStorageProperties properties,
            SafeFilenameGenerator safeFilenameGenerator) {
        this.minioClient = minioClient;
        this.properties = properties;
        this.safeFilenameGenerator = safeFilenameGenerator;
    }

    @Override
    public StoredFile store(StoreFileCommand command) {
        if (command == null || command.inputStream() == null || command.size() <= 0) {
            throw new FileStorageException("文件不能为空");
        }
        validateUserId(command.userId());

        String originalFilename = StringUtils.cleanPath(command.originalFilename() == null
                ? "resume"
                : command.originalFilename());
        String safeFilename = safeFilenameGenerator.generate(originalFilename);
        String objectKey = generateObjectKey(command, safeFilename);

        try {
            ensureBucket();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .stream(command.inputStream(), command.size(), -1)
                    .contentType(contentType(command.contentType()))
                    .build());
        } catch (Exception exception) {
            throw new FileStorageException("文件保存失败", exception);
        }

        return new StoredFile(
                objectKey,
                originalFilename,
                command.contentType(),
                command.size(),
                STORAGE_TYPE_MINIO);
    }

    @Override
    public InputStream loadAsStream(String storageKey) {
        String objectKey = normalizeObjectKey(storageKey);
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new FileStorageException("文件读取失败", exception);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return false;
        }
        String objectKey = normalizeObjectKey(storageKey);
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException exception) {
            if (isObjectMissing(exception)) {
                return false;
            }
            throw new FileStorageException("文件元信息读取失败", exception);
        } catch (Exception exception) {
            throw new FileStorageException("文件元信息读取失败", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        String objectKey = normalizeObjectKey(storageKey);
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
        } catch (ErrorResponseException exception) {
            if (!isObjectMissing(exception)) {
                throw new FileStorageException("文件清理失败", exception);
            }
        } catch (Exception exception) {
            throw new FileStorageException("文件清理失败", exception);
        }
    }

    @Override
    public StoredFileMetadata getMetadata(String storageKey) {
        String objectKey = normalizeObjectKey(storageKey);
        try {
            StatObjectResponse response = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
            return new StoredFileMetadata(objectKey, response.size(), STORAGE_TYPE_MINIO);
        } catch (Exception exception) {
            throw new FileStorageException("文件元信息读取失败", exception);
        }
    }

    private void ensureBucket() throws Exception {
        if (bucketReady.get()) {
            return;
        }
        synchronized (bucketReady) {
            if (bucketReady.get()) {
                return;
            }
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(bucket())
                    .build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(bucket())
                        .build());
            }
            bucketReady.set(true);
        }
    }

    private String generateObjectKey(StoreFileCommand command, String safeFilename) {
        String safeBizType = normalizeDirectory(command.bizType() == null ? "default" : command.bizType());
        String dateDirectory = LocalDate.now().format(DATE_DIRECTORY_FORMATTER);
        return safeBizType + "/" + command.userId() + "/" + dateDirectory + "/" + UUID.randomUUID() + "-" + safeFilename;
    }

    private String normalizeDirectory(String directory) {
        String cleanDirectory = StringUtils.cleanPath(directory == null ? "default" : directory).replace("\\", "/");
        if (cleanDirectory.isBlank()
                || cleanDirectory.contains("..")
                || cleanDirectory.startsWith("/")
                || cleanDirectory.endsWith("/")) {
            throw new FileStorageException("文件存储目录不合法");
        }
        return cleanDirectory;
    }

    private String normalizeObjectKey(String storageKey) {
        String objectKey = StringUtils.cleanPath(storageKey == null ? "" : storageKey).replace("\\", "/");
        if (objectKey.isBlank()
                || objectKey.indexOf('\0') >= 0
                || objectKey.startsWith("/")
                || objectKey.equals("..")
                || objectKey.contains("../")
                || objectKey.contains("/..")) {
            throw new FileStorageException("文件对象 key 不合法");
        }
        return objectKey;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new FileStorageException("文件所属用户不合法");
        }
    }

    private String contentType(String contentType) {
        return contentType == null || contentType.isBlank() ? DEFAULT_CONTENT_TYPE : contentType;
    }

    private String bucket() {
        String bucket = properties.getBucket();
        if (bucket == null || bucket.isBlank()) {
            throw new FileStorageException("MinIO bucket 不能为空");
        }
        return bucket;
    }

    private boolean isObjectMissing(ErrorResponseException exception) {
        String code = exception.errorResponse() == null ? "" : exception.errorResponse().code();
        return "NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NoSuchBucket".equals(code);
    }
}
