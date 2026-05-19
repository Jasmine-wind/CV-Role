package com.winter.airesumeoptimizer.infra.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void storeShouldGenerateStorageKeyAndReadMetadata() {
        LocalFileStorageService service = newService();
        byte[] content = "resume-content".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = service.store(new StoreFileCommand(
                12L,
                "My Resume.pdf",
                "application/pdf",
                content.length,
                new ByteArrayInputStream(content),
                "resumes"));

        assertThat(storedFile.storageType()).isEqualTo("LOCAL");
        assertThat(storedFile.storageKey())
                .startsWith("resumes/12/")
                .endsWith("-my_resume.pdf");
        assertThat(Path.of(storedFile.storageKey()).isAbsolute()).isFalse();
        assertThat(service.exists(storedFile.storageKey())).isTrue();
        assertThat(service.loadAsBytes(storedFile.storageKey())).isEqualTo(content);

        StoredFileMetadata metadata = service.getMetadata(storedFile.storageKey());
        assertThat(metadata.storageKey()).isEqualTo(storedFile.storageKey());
        assertThat(metadata.storageType()).isEqualTo("LOCAL");
        assertThat(metadata.size()).isEqualTo(content.length);
    }

    @Test
    void deleteShouldRemoveStoredFile() {
        LocalFileStorageService service = newService();
        byte[] content = "resume-content".getBytes(StandardCharsets.UTF_8);
        StoredFile storedFile = service.store(new StoreFileCommand(
                1L,
                "resume.pdf",
                "application/pdf",
                content.length,
                new ByteArrayInputStream(content),
                "resumes"));

        service.delete(storedFile.storageKey());

        assertThat(service.exists(storedFile.storageKey())).isFalse();
    }

    @Test
    void loadShouldRejectTraversalStorageKey() {
        LocalFileStorageService service = newService();

        assertThatThrownBy(() -> service.loadAsBytes("../secret.txt"))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("文件存储路径不合法");
    }

    @Test
    void storeShouldRejectInvalidUserId() {
        LocalFileStorageService service = newService();

        assertThatThrownBy(() -> service.store(new StoreFileCommand(
                0L,
                "resume.pdf",
                "application/pdf",
                1,
                new ByteArrayInputStream(new byte[]{1}),
                "resumes")))
                .isInstanceOf(FileStorageException.class)
                .hasMessage("文件所属用户不合法");
    }

    @Test
    void storeShouldSanitizeUnsafeOriginalFilename() {
        LocalFileStorageService service = newService();
        byte[] content = "resume-content".getBytes(StandardCharsets.UTF_8);

        StoredFile storedFile = service.store(new StoreFileCommand(
                1L,
                "../../中文 Resume 2026.PDF",
                "application/pdf",
                content.length,
                new ByteArrayInputStream(content),
                "resumes"));

        assertThat(storedFile.storageKey()).endsWith("-_resume_2026.pdf");
        assertThat(service.loadAsBytes(storedFile.storageKey())).isEqualTo(content);
    }

    @Test
    void storeShouldRejectTraversalBizType() {
        LocalFileStorageService service = newService();

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

    private LocalFileStorageService newService() {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBaseDir(tempDir.resolve("uploads").toString());
        return new LocalFileStorageService(
                new LocalStoragePathResolver(properties),
                new SafeFilenameGenerator());
    }
}
