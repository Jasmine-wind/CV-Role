package com.winter.airesumeoptimizer.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MinioHealthIndicatorTest {

    private final MinioClient minioClient = mock(MinioClient.class);
    private final MinioStorageProperties properties = properties();
    private final MinioHealthIndicator indicator = new MinioHealthIndicator(minioClient, properties);

    @Test
    void reportsUpWhenAuthenticatedMinioRequestSucceedsWithoutExposingDetails() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).isEmpty();
    }

    @Test
    void reportsDownWithoutExposingFailureDetails() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenThrow(new IllegalStateException("sensitive endpoint failure"));

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).isEmpty();
    }

    @Test
    void reportsDownWithoutCallingMinioWhenBucketIsBlank() throws Exception {
        properties.setBucket(" ");

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
        verify(minioClient, never()).bucketExists(any(BucketExistsArgs.class));
    }

    @Test
    void isOnlyRegisteredForMinioStorage() {
        new ApplicationContextRunner()
                .withUserConfiguration(MinioHealthIndicator.class)
                .withBean(MinioClient.class, () -> minioClient)
                .withBean(MinioStorageProperties.class, () -> properties)
                .withPropertyValues("app.storage.type=local")
                .run(context -> assertThat(context).doesNotHaveBean(MinioHealthIndicator.class));

        new ApplicationContextRunner()
                .withUserConfiguration(MinioHealthIndicator.class)
                .withBean(MinioClient.class, () -> minioClient)
                .withBean(MinioStorageProperties.class, () -> properties)
                .withPropertyValues("app.storage.type=minio")
                .run(context -> assertThat(context).hasSingleBean(MinioHealthIndicator.class));
    }

    private MinioStorageProperties properties() {
        MinioStorageProperties result = new MinioStorageProperties();
        result.setBucket("ai-resume-files");
        return result;
    }
}
