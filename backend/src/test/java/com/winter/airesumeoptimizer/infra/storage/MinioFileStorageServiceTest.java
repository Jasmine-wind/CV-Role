package com.winter.airesumeoptimizer.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MinioFileStorageServiceTest {

    private final MinioClient minioClient = mock(MinioClient.class);
    private final MinioStorageProperties properties = properties();
    private final MinioFileStorageService service = new MinioFileStorageService(
            minioClient,
            properties,
            new SafeFilenameGenerator());

    @Test
    void storeShouldCreateBucketAndPutObject() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        byte[] content = "resume-content".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = service.store(new StoreFileCommand(
                12L,
                "My Resume.pdf",
                "application/pdf",
                content.length,
                new ByteArrayInputStream(content),
                "resumes"));

        assertThat(storedFile.storageType()).isEqualTo("MINIO");
        assertThat(storedFile.storageKey())
                .startsWith("resumes/12/")
                .endsWith("-my_resume.pdf");
        assertThat(storedFile.originalFilename()).isEqualTo("My Resume.pdf");
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void storeShouldSkipBucketCreationWhenBucketExists() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        byte[] content = "resume-content".getBytes(StandardCharsets.UTF_8);

        service.store(new StoreFileCommand(
                1L,
                "resume.pdf",
                "application/pdf",
                content.length,
                new ByteArrayInputStream(content),
                "resumes"));

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void metadataShouldReturnObjectSize() throws Exception {
        StatObjectResponse response = mock(StatObjectResponse.class);
        when(response.size()).thenReturn(16L);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(response);

        StoredFileMetadata metadata = service.getMetadata("resumes/1/resume.pdf");

        assertThat(metadata.storageKey()).isEqualTo("resumes/1/resume.pdf");
        assertThat(metadata.storageType()).isEqualTo("MINIO");
        assertThat(metadata.size()).isEqualTo(16L);
    }

    @Test
    void loadShouldRejectTraversalObjectKey() {
        assertThatThrownBy(() -> service.loadAsBytes("../secret.pdf"))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("文件对象 key 不合法");
    }

    @Test
    void storeShouldRejectTraversalBizType() {
        assertThatThrownBy(() -> service.store(new StoreFileCommand(
                1L,
                "resume.pdf",
                "application/pdf",
                1,
                new ByteArrayInputStream(new byte[]{1}),
                "../resumes")))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("文件存储目录不合法");
    }

    private MinioStorageProperties properties() {
        MinioStorageProperties minioProperties = new MinioStorageProperties();
        minioProperties.setEndpoint("http://localhost:9000");
        minioProperties.setAccessKey("minio");
        minioProperties.setSecretKey("minio-password");
        minioProperties.setBucket("ai-resume-files");
        return minioProperties;
    }
}
