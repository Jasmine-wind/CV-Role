package com.winter.airesumeoptimizer.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.storage.LocalStoragePathResolver;
import com.winter.airesumeoptimizer.module.export.dto.WorkspaceExportRequestDTO;
import com.winter.airesumeoptimizer.module.export.service.ExportArtifactCleanupService;
import com.winter.airesumeoptimizer.module.export.service.RenderedPdf;
import com.winter.airesumeoptimizer.module.export.service.WorkspaceExportService;
import com.winter.airesumeoptimizer.module.export.vo.ExportArtifactVO;
import com.winter.airesumeoptimizer.module.optimization.entity.JobTarget;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.JobTargetMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.optimization.service.OptimizationTaskService;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskType;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskService;
import com.winter.airesumeoptimizer.module.task.vo.AsyncTaskVO;
import com.winter.airesumeoptimizer.module.user.entity.User;
import com.winter.airesumeoptimizer.module.user.mapper.UserMapper;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Real PostgreSQL and local-storage validation for the recoverable export lifecycle. */
@SpringBootTest
@ActiveProfiles("phase9-e2e")
class ExportLifecyclePostgresIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private ResumeMapper resumeMapper;
    @Autowired private JobTargetMapper jobTargetMapper;
    @Autowired private ResumeVersionMapper resumeVersionMapper;
    @Autowired private OptimizationTaskMapper optimizationTaskMapper;
    @Autowired private OptimizationTaskService optimizationTaskService;
    @Autowired private ResumeService resumeService;
    @Autowired private AsyncTaskService asyncTaskService;
    @Autowired private ExportArtifactCleanupService cleanupService;
    @Autowired private WorkspaceExportService workspaceExportService;
    @Autowired private LocalStoragePathResolver storagePathResolver;

    @Test
    void deletePendingUpdateExceptionOrZeroRowsNeverDeletesTheObject() throws Exception {
        Fixture fixture = fixture("pending-prepare");
        String exceptionKey = storageKey("pending-exception");
        Path exceptionObject = writeObject(exceptionKey);
        Long exceptionArtifact = insertArtifact(fixture, exceptionKey);
        String exceptionSuffix = suffix();
        createArtifactUpdateTrigger(exceptionSuffix, exceptionArtifact, true);
        try {
            assertThatThrownBy(() -> cleanupService.deleteArtifact(fixture.userId(), exceptionArtifact))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("导出文件删除准备失败，请重试");
            assertThat(Files.exists(exceptionObject)).isTrue();
            assertThat(artifactStatus(exceptionArtifact)).isEqualTo("READY");
        } finally {
            dropTrigger("trg_export_update_" + exceptionSuffix, "export_artifacts",
                    "export_update_" + exceptionSuffix);
        }

        String zeroKey = storageKey("pending-zero");
        Path zeroObject = writeObject(zeroKey);
        Long zeroArtifact = insertArtifact(fixture, zeroKey);
        String zeroSuffix = suffix();
        createArtifactUpdateTrigger(zeroSuffix, zeroArtifact, false);
        try {
            assertThatThrownBy(() -> cleanupService.deleteArtifact(fixture.userId(), zeroArtifact))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("导出文件删除准备失败，请重试");
            assertThat(Files.exists(zeroObject)).isTrue();
            assertThat(artifactStatus(zeroArtifact)).isEqualTo("READY");
        } finally {
            dropTrigger("trg_export_update_" + zeroSuffix, "export_artifacts",
                    "export_update_" + zeroSuffix);
            Files.deleteIfExists(exceptionObject);
            Files.deleteIfExists(zeroObject);
        }
    }

    @Test
    void metadataDeleteFailureAfterObjectRemovalCanBeRetriedIdempotently() throws Exception {
        Fixture fixture = fixture("metadata-retry");
        String key = storageKey("metadata-retry");
        Path object = writeObject(key);
        Long artifactId = insertArtifact(fixture, key);
        String suffix = suffix();
        createArtifactDeleteSkipTrigger(suffix, artifactId);
        try {
            assertThatThrownBy(() -> cleanupService.deleteArtifact(fixture.userId(), artifactId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("导出文件删除未完成，请重试");
            assertThat(Files.exists(object)).isFalse();
            assertThat(artifactStatus(artifactId)).isEqualTo("DELETE_PENDING");
        } finally {
            dropTrigger("trg_export_delete_" + suffix, "export_artifacts",
                    "export_delete_" + suffix);
        }

        cleanupService.deleteArtifact(fixture.userId(), artifactId);

        assertThat(artifactStatus(artifactId)).isNull();
        assertThat(Files.exists(object)).isFalse();
    }

    @Test
    void partialMultiArtifactFailureRollsBackParentAndActiveAsyncStateWithRetryableRows() throws Exception {
        Fixture fixture = fixture("partial-parent");
        Long runningId = activeAsyncTask(fixture);
        AsyncTaskVO before = asyncTaskService.getTask(runningId, fixture.userId());

        String removedKey = storageKey("partial-removed");
        Path removedObject = writeObject(removedKey);
        Long removedArtifact = insertArtifact(fixture, removedKey);
        String failingKey = storageKey("partial-failing");
        Path failingDirectory = storagePathResolver.resolve(failingKey);
        Files.createDirectories(failingDirectory);
        Path child = Files.writeString(failingDirectory.resolve("child"), "synthetic");
        Long failingArtifact = insertArtifact(fixture, failingKey);
        try {
            assertThatThrownBy(() -> optimizationTaskService.delete(fixture.userId(), fixture.task().getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("导出文件删除失败，已保留记录，请重试");

            assertThat(Files.exists(removedObject)).isFalse();
            assertThat(Files.exists(failingDirectory)).isTrue();
            assertThat(artifactStatus(removedArtifact)).isEqualTo("DELETE_PENDING");
            assertThat(artifactStatus(failingArtifact)).isEqualTo("DELETE_PENDING");
            assertThat(optimizationTaskMapper.selectById(fixture.task().getId())).isNotNull();
            assertThat(resumeMapper.selectById(fixture.resumeId())).isNotNull();
            assertThat(asyncTaskService.getTask(runningId, fixture.userId()))
                    .usingRecursiveComparison().isEqualTo(before);
            assertThat(asyncTaskService.isActive(fixture.userId(), runningId)).isTrue();

            Files.delete(child);
            optimizationTaskService.delete(fixture.userId(), fixture.task().getId());

            assertThat(optimizationTaskMapper.selectById(fixture.task().getId())).isNull();
            assertThat(asyncTaskService.getTask(runningId, fixture.userId()).getStatus()).isEqualTo("CANCELLED");
            assertThat(artifactStatus(removedArtifact)).isNull();
            assertThat(artifactStatus(failingArtifact)).isNull();
            assertThat(Files.exists(failingDirectory)).isFalse();
        } finally {
            Files.deleteIfExists(child);
            Files.deleteIfExists(failingDirectory);
            Files.deleteIfExists(removedObject);
        }
    }

    @Test
    void parentDatabaseFailureAfterObjectRemovalRollsBackParentAndAsyncButKeepsRetryMetadata() throws Exception {
        Fixture fixture = fixture("parent-db-rollback");
        Long runningId = activeAsyncTask(fixture);
        String key = storageKey("parent-db-rollback");
        Path object = writeObject(key);
        Long artifactId = insertArtifact(fixture, key);
        String suffix = suffix();
        createTaskDeleteFailureTrigger(suffix, fixture.task().getId());
        try {
            assertThatThrownBy(() -> optimizationTaskService.delete(fixture.userId(), fixture.task().getId()))
                    .isInstanceOf(RuntimeException.class);

            assertThat(Files.exists(object)).isFalse();
            assertThat(artifactStatus(artifactId)).isEqualTo("DELETE_PENDING");
            assertThat(optimizationTaskMapper.selectById(fixture.task().getId())).isNotNull();
            assertThat(resumeVersionMapper.selectById(fixture.task().getTargetResumeVersionId())).isNotNull();
            assertThat(asyncTaskService.getTask(runningId, fixture.userId()).getStatus()).isEqualTo("RUNNING");
            assertThat(asyncTaskService.isActive(fixture.userId(), runningId)).isTrue();
        } finally {
            dropTrigger("trg_task_delete_" + suffix, "optimization_tasks",
                    "task_delete_" + suffix);
        }

        optimizationTaskService.delete(fixture.userId(), fixture.task().getId());
        assertThat(optimizationTaskMapper.selectById(fixture.task().getId())).isNull();
        assertThat(artifactStatus(artifactId)).isNull();
        assertThat(asyncTaskService.getTask(runningId, fixture.userId()).getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void taskDeletionSerializesWithExportCreationAndRemovesTheCommittedArtifact() throws Exception {
        assertParentDeletionSerializesWithExport(false);
    }

    @Test
    void resumeDeletionSerializesWithExportCreationAndRemovesTheCommittedArtifact() throws Exception {
        assertParentDeletionSerializesWithExport(true);
    }

    private void assertParentDeletionSerializesWithExport(boolean deleteResume) throws Exception {
        Fixture fixture = fixture(deleteResume ? "resume-export-race" : "task-export-race");
        RenderedPdf preview = workspaceExportService.preview(
                fixture.userId(), fixture.task().getId(), "classic", 1L);
        WorkspaceExportRequestDTO request = new WorkspaceExportRequestDTO();
        request.setTemplateId("classic");
        request.setExpectedRevision(1L);
        request.setPreviewReceipt(preview.previewReceipt());

        long advisoryKey = Math.abs(System.nanoTime());
        String suffix = suffix();
        createArtifactInsertWaitTrigger(suffix, fixture.task().getId(), advisoryKey);
        try (Connection lockConnection = dataSource.getConnection()) {
            advisoryLock(lockConnection, advisoryKey, true);
            CompletableFuture<ExportArtifactVO> export = CompletableFuture.supplyAsync(
                    () -> workspaceExportService.export(fixture.userId(), fixture.task().getId(), request));
            Path storedObject = awaitExportObject(fixture.userId());
            assertFutureBlocked(export);
            assertThat(artifactCount(fixture.task().getId())).isZero();

            CompletableFuture<Void> deletion = CompletableFuture.runAsync(() -> {
                if (deleteResume) {
                    resumeService.delete(fixture.userId(), fixture.resumeId());
                } else {
                    optimizationTaskService.delete(fixture.userId(), fixture.task().getId());
                }
            });
            assertFutureBlocked(deletion);

            advisoryLock(lockConnection, advisoryKey, false);
            ExportArtifactVO created = export.get(10, TimeUnit.SECONDS);
            deletion.get(10, TimeUnit.SECONDS);

            assertThat(created.getId()).isNotNull();
            assertThat(optimizationTaskMapper.selectById(fixture.task().getId())).isNull();
            assertThat(artifactCount(fixture.task().getId())).isZero();
            assertThat(Files.exists(storedObject)).isFalse();
            if (deleteResume) {
                assertThat(resumeMapper.selectById(fixture.resumeId())).isNull();
            } else {
                assertThat(resumeMapper.selectById(fixture.resumeId())).isNotNull();
            }
        } finally {
            dropTrigger("trg_export_insert_" + suffix, "export_artifacts",
                    "export_insert_" + suffix);
        }
    }

    private Fixture fixture(String name) throws Exception {
        User user = new User();
        String identity = UUID.randomUUID().toString().substring(0, 12);
        user.setUsername(name + "-" + identity);
        user.setEmail(name + "-" + identity + "@example.invalid");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        Resume resume = new Resume();
        resume.setUserId(user.getId());
        resume.setOriginalFilename(name + ".pdf");
        resume.setDisplayName(name);
        resume.setFileType("PDF");
        resume.setFileSize(100L);
        resume.setObjectKey("resumes/" + user.getId() + "/" + name + ".pdf");
        resume.setStorageType("LOCAL");
        resume.setUploadStatus("UPLOADED");
        resume.setCreatedAt(LocalDateTime.now());
        resume.setUpdatedAt(LocalDateTime.now());
        resumeMapper.insert(resume);

        String document = objectMapper.writeValueAsString(exportableDocument());
        JobTarget target = new JobTarget();
        target.setUserId(user.getId());
        target.setTitle("Java backend");
        target.setRawJd("Java backend role");
        target.setSourceType("USER_INPUT");
        target.setCreatedAt(LocalDateTime.now());
        target.setUpdatedAt(LocalDateTime.now());
        jobTargetMapper.insert(target);

        ResumeVersion source = version(user.getId(), resume.getId(), document, "SOURCE");
        resumeVersionMapper.insert(source);
        ResumeVersion targeted = version(user.getId(), resume.getId(), document, "TARGETED");
        targeted.setSourceVersionId(source.getId());
        targeted.setJobTargetId(target.getId());
        targeted.setSourceType("JOB_DERIVATION");
        targeted.setContentRevision(1L);
        resumeVersionMapper.insert(targeted);

        OptimizationTask task = new OptimizationTask();
        task.setUserId(user.getId());
        task.setSourceResumeVersionId(source.getId());
        task.setTargetResumeVersionId(targeted.getId());
        task.setJobTargetId(target.getId());
        task.setStatus("SUCCESS");
        task.setResumeInputSnapshot(document);
        task.setJobInputSnapshot(target.getRawJd());
        task.setPromptSnapshot("{}");
        task.setRulesSnapshot("{}");
        task.setAiSourceSnapshot("SYSTEM_DEFAULT");
        task.setTemplateVersion("NOT_SELECTED");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setFinishedAt(LocalDateTime.now());
        optimizationTaskMapper.insert(task);
        return new Fixture(user.getId(), resume.getId(), task);
    }

    private ResumeVersion version(Long userId, Long resumeId, String document, String type) {
        ResumeVersion version = new ResumeVersion();
        version.setUserId(userId);
        version.setResumeId(resumeId);
        version.setVersionType(type);
        version.setSourceType("PARSED_UPLOAD");
        version.setContentStatus("READY");
        version.setStructuredContent(document);
        version.setContentRevision(0L);
        version.setCreatedAt(LocalDateTime.now());
        version.setUpdatedAt(LocalDateTime.now());
        return version;
    }

    private ResumeDocumentDTO exportableDocument() {
        List<String> occurrenceIds = List.of(
                "occ-name", "occ-contact", "occ-section", "occ-entry", "occ-bullet");
        Map<String, String> occurrenceTexts = Map.of(
                "occ-name", "Integration User",
                "occ-contact", "integration@example.com",
                "occ-section", "工作经历",
                "occ-entry", "Example Technology · Java Engineer · 2022.01 · 至今",
                "occ-bullet", "负责 Java 服务开发与稳定性优化");
        Map<String, ResumeSourceRefDTO> occurrenceRefs = occurrenceTexts.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                        item -> ResumeSourceRefDTO.builder()
                                .text(item.getValue())
                                .sourceOccurrenceIds(List.of(item.getKey()))
                                .build()));
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .sourceOccurrenceIds(occurrenceIds)
                .sourceRef(ResumeSourceRefDTO.builder()
                        .text(occurrenceIds.stream().map(occurrenceTexts::get)
                                .collect(java.util.stream.Collectors.joining()))
                        .sourceOccurrenceIds(occurrenceIds)
                        .build())
                .sourceOccurrenceTexts(occurrenceTexts)
                .sourceOccurrenceRefs(occurrenceRefs)
                .sourceOccurrencePrimaryIds(Map.of(
                        "occ-name", "occ-name",
                        "occ-contact", "occ-contact",
                        "occ-section", "occ-section",
                        "occ-entry", "occ-entry",
                        "occ-bullet", "occ-bullet"))
                .basics(ResumeDocumentBasicsDTO.builder()
                        .name("Integration User")
                        .fieldSourceRefs(Map.of("name", ResumeSourceRefDTO.builder()
                                .text("Integration User").sourceOccurrenceIds(List.of("occ-name")).build()))
                        .contacts(new ArrayList<>(List.of(ResumeDocumentContactDTO.builder()
                                .id("contact-email")
                                .type("EMAIL")
                                .label("邮箱")
                                .value("integration@example.com")
                                .sourceOccurrenceIds(List.of("occ-contact"))
                                .build())))
                        .build())
                .sections(new ArrayList<>(List.of(ResumeDocumentSectionDTO.builder()
                        .id("section-experience")
                        .kind("EXPERIENCE")
                        .title("工作经历")
                        .sourceOccurrenceIds(List.of("occ-section"))
                        .entries(new ArrayList<>(List.of(ResumeDocumentEntryDTO.builder()
                                .id("entry-experience")
                                .organization("Example Technology")
                                .role("Java Engineer")
                                .startDate("2022.01")
                                .endDate("至今")
                                .sourceOccurrenceIds(List.of("occ-entry"))
                                .bullets(new ArrayList<>(List.of(ResumeDocumentBulletDTO.builder()
                                        .id("bullet-experience")
                                        .text("负责 Java 服务开发与稳定性优化")
                                        .sourceOccurrenceIds(List.of("occ-bullet"))
                                        .build())))
                                .build())))
                        .build())))
                .build();
    }

    private Long activeAsyncTask(Fixture fixture) {
        Long asyncId = asyncTaskService.createTask(fixture.userId(), AsyncTaskType.MATCH_ANALYSIS,
                "OPTIMIZATION_TASK", fixture.task().getId());
        asyncTaskService.markRunning(asyncId, "正在分析");
        fixture.task().setStatus("RUNNING");
        fixture.task().setAsyncTaskId(asyncId);
        optimizationTaskMapper.updateById(fixture.task());
        return asyncId;
    }

    private Long insertArtifact(Fixture fixture, String storageKey) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO export_artifacts (user_id, optimization_task_id, target_resume_version_id,
                    content_revision, template_id, template_version, renderer_version, storage_key,
                    file_size, checksum_sha256, page_count, missing_contact, page_limit_exceeded, overflow_detected)
                VALUES (?, ?, ?, 1, 'classic', '5', 'integration-test', ?, 1, ?, 1, false, false, false)
                RETURNING id
                """, Long.class, fixture.userId(), fixture.task().getId(),
                fixture.task().getTargetResumeVersionId(), storageKey, "a".repeat(64));
    }

    private Path writeObject(String storageKey) throws Exception {
        Path path = storagePathResolver.resolve(storageKey);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, "synthetic-pdf");
    }

    private String artifactStatus(Long artifactId) {
        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM export_artifacts WHERE id = ?", String.class, artifactId);
        return statuses.isEmpty() ? null : statuses.getFirst();
    }

    private long artifactCount(Long taskId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM export_artifacts WHERE optimization_task_id = ?", Long.class, taskId);
    }

    private String storageKey(String label) {
        return "export-lifecycle/" + label + "-" + UUID.randomUUID();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void createArtifactUpdateTrigger(String suffix, Long artifactId, boolean raise) {
        String body = raise
                ? "RAISE EXCEPTION 'synthetic update failure';"
                : "RETURN NULL;";
        jdbcTemplate.execute("CREATE FUNCTION export_update_" + suffix
                + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.id = " + artifactId
                + " AND NEW.status = 'DELETE_PENDING' THEN " + body
                + " END IF; RETURN NEW; END; $$");
        jdbcTemplate.execute("CREATE TRIGGER trg_export_update_" + suffix
                + " BEFORE UPDATE ON export_artifacts FOR EACH ROW EXECUTE FUNCTION export_update_"
                + suffix + "()");
    }

    private void createArtifactDeleteSkipTrigger(String suffix, Long artifactId) {
        jdbcTemplate.execute("CREATE FUNCTION export_delete_" + suffix
                + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF OLD.id = " + artifactId
                + " THEN RETURN NULL; END IF; RETURN OLD; END; $$");
        jdbcTemplate.execute("CREATE TRIGGER trg_export_delete_" + suffix
                + " BEFORE DELETE ON export_artifacts FOR EACH ROW EXECUTE FUNCTION export_delete_"
                + suffix + "()");
    }

    private void createTaskDeleteFailureTrigger(String suffix, Long taskId) {
        jdbcTemplate.execute("CREATE FUNCTION task_delete_" + suffix
                + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF OLD.id = " + taskId
                + " THEN RAISE EXCEPTION 'synthetic parent failure'; END IF; RETURN OLD; END; $$");
        jdbcTemplate.execute("CREATE TRIGGER trg_task_delete_" + suffix
                + " BEFORE DELETE ON optimization_tasks FOR EACH ROW EXECUTE FUNCTION task_delete_"
                + suffix + "()");
    }

    private void createArtifactInsertWaitTrigger(String suffix, Long taskId, long advisoryKey) {
        jdbcTemplate.execute("CREATE FUNCTION export_insert_" + suffix
                + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.optimization_task_id = " + taskId
                + " THEN PERFORM pg_advisory_xact_lock(" + advisoryKey
                + "); END IF; RETURN NEW; END; $$");
        jdbcTemplate.execute("CREATE TRIGGER trg_export_insert_" + suffix
                + " BEFORE INSERT ON export_artifacts FOR EACH ROW EXECUTE FUNCTION export_insert_"
                + suffix + "()");
    }

    private void dropTrigger(String trigger, String table, String function) {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + trigger + " ON " + table);
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + function + "()");
    }

    private void advisoryLock(Connection connection, long key, boolean lock) throws Exception {
        String sql = lock ? "SELECT pg_advisory_lock(?)" : "SELECT pg_advisory_unlock(?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, key);
            statement.execute();
        }
    }

    private Path awaitExportObject(Long userId) throws Exception {
        Path userRoot = storagePathResolver.resolve("exports/" + userId);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            if (Files.exists(userRoot)) {
                try (var paths = Files.walk(userRoot)) {
                    Path object = paths.filter(Files::isRegularFile).findFirst().orElse(null);
                    if (object != null) {
                        return object;
                    }
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("export did not create its local object");
    }

    private void assertFutureBlocked(CompletableFuture<?> future) throws Exception {
        try {
            future.get(500, TimeUnit.MILLISECONDS);
            throw new AssertionError("operation should still be waiting on the lifecycle fence");
        } catch (TimeoutException expected) {
            // Expected: the advisory or parent row lock still owns the serialization point.
        } catch (ExecutionException exception) {
            throw new AssertionError("operation failed before the lifecycle fence was released", exception);
        }
    }

    private record Fixture(Long userId, Long resumeId, OptimizationTask task) {
    }
}
