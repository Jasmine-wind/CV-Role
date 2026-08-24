package com.winter.airesumeoptimizer.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Small real-adapter lifecycle check. CI supplies an ephemeral MinIO service;
 * normal unit runs remain network-free and use the adapter unit tests instead.
 */
@EnabledIfEnvironmentVariable(named = "MINIO_INTEGRATION_ENABLED", matches = "true")
class MinioFileStorageIntegrationTest {

    @Test
    void storesReadsMetadataAndDeletesAPrivateObject() {
        byte[] content = "synthetic phase9 storage fixture".getBytes(StandardCharsets.UTF_8);
        MinioFileStorageService storage = new MinioFileStorageService(
                io.minio.MinioClient.builder()
                        .endpoint(required("TEST_MINIO_ENDPOINT"))
                        .credentials(required("TEST_MINIO_ACCESS_KEY"), required("TEST_MINIO_SECRET_KEY"))
                        .build(),
                properties(),
                new SafeFilenameGenerator());

        StoredFile stored = storage.store(new StoreFileCommand(
                42L,
                "synthetic-resume.pdf",
                "application/pdf",
                (long) content.length,
                new ByteArrayInputStream(content),
                "phase9-integration-" + UUID.randomUUID()));

        assertThat(stored.storageType()).isEqualTo("MINIO");
        assertThat(storage.exists(stored.storageKey())).isTrue();
        assertThat(storage.getMetadata(stored.storageKey()).size()).isEqualTo(content.length);
        assertThat(storage.loadAsBytes(stored.storageKey())).isEqualTo(content);

        storage.delete(stored.storageKey());
        assertThat(storage.exists(stored.storageKey())).isFalse();
    }

    private MinioStorageProperties properties() {
        MinioStorageProperties properties = new MinioStorageProperties();
        properties.setEndpoint(required("TEST_MINIO_ENDPOINT"));
        properties.setAccessKey(required("TEST_MINIO_ACCESS_KEY"));
        properties.setSecretKey(required("TEST_MINIO_SECRET_KEY"));
        properties.setBucket(required("TEST_MINIO_BUCKET"));
        return properties;
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing test-only environment variable " + name);
        }
        return value;
    }
}
