package com.winter.airesumeoptimizer.infra.storage;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "app.storage", name = "type", havingValue = "minio")
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;
    private final MinioStorageProperties properties;

    public MinioHealthIndicator(MinioClient minioClient, MinioStorageProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!StringUtils.hasText(properties.getBucket())) {
            return Health.down().build();
        }
        try {
            // A successful response proves endpoint reachability and credentials.
            // The storage service can create a missing bucket on its first write.
            minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
            return Health.up().build();
        } catch (Exception exception) {
            // Do not attach endpoint, bucket, credentials, or exception details.
            return Health.down().build();
        }
    }
}
