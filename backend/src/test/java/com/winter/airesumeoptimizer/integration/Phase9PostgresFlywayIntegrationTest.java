package com.winter.airesumeoptimizer.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.storage.LocalStoragePathResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import com.winter.airesumeoptimizer.infra.ai.AiInvocationContext;
import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.infra.ai.AiSource;
import com.winter.airesumeoptimizer.infra.ai.AiUsageMetrics;
import com.winter.airesumeoptimizer.module.ai.usage.entity.AiUsageRecord;
import com.winter.airesumeoptimizer.module.ai.usage.mapper.AiUsageRecordMapper;
import com.winter.airesumeoptimizer.module.ai.usage.service.AiUsageRecordPersistence;
import com.winter.airesumeoptimizer.module.ai.usage.service.AiUsageRetentionService;
import com.winter.airesumeoptimizer.module.evidence.entity.EvidenceAnalysis;
import com.winter.airesumeoptimizer.module.evidence.entity.EvidenceRequirement;
import com.winter.airesumeoptimizer.module.evidence.entity.RequirementEvidence;
import com.winter.airesumeoptimizer.module.evidence.mapper.EvidenceAnalysisMapper;
import com.winter.airesumeoptimizer.module.evidence.mapper.EvidenceRequirementMapper;
import com.winter.airesumeoptimizer.module.evidence.mapper.RequirementEvidenceMapper;
import com.winter.airesumeoptimizer.module.insight.service.JobDirectionInsightService;
import com.winter.airesumeoptimizer.module.insight.vo.JobDirectionCohortVO;
import com.winter.airesumeoptimizer.module.observability.service.ProductObservabilityService;
import com.winter.airesumeoptimizer.module.optimization.controller.OptimizationTaskController;
import com.winter.airesumeoptimizer.module.optimization.entity.JobTarget;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.JobTargetMapper;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionSubmitDTO;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionService;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.optimization.service.OptimizationTaskService;
import com.winter.airesumeoptimizer.module.task.enums.AsyncTaskType;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskService;
import com.winter.airesumeoptimizer.module.task.vo.AsyncTaskVO;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeReviewResolveRequestDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeUnresolvedItemDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAiRepairCoordinator;
import com.winter.airesumeoptimizer.module.resume.service.ResumeParseClaimService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeReviewService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeReviewVO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import com.winter.airesumeoptimizer.module.user.entity.User;
import com.winter.airesumeoptimizer.module.user.mapper.UserMapper;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Intentionally uses the CI PostgreSQL service rather than mocked MVC slices:
 * Flyway, composite ownership FKs, read-time Insight, and REQUIRES_NEW usage
 * persistence all execute against the production SQL dialect.
 */
@SpringBootTest(properties = "app.ai.usage.retention.enabled=true")
@ActiveProfiles("phase9-e2e")
class Phase9PostgresFlywayIntegrationTest {

    private static final String SNAPSHOT = "{\"name\":\"Integration User\",\"rawText\":\"Java\\n负责 Java 后端服务开发\"}";
    private static final String SOURCE_A_DOCUMENT = "{\"schemaVersion\":\"RESUME_DOCUMENT_V1\",\"marker\":\"SOURCE_A\"}";
    private static final String SOURCE_B_DOCUMENT = "{\"schemaVersion\":\"RESUME_DOCUMENT_V1\",\"marker\":\"SOURCE_B\"}";

    @Autowired
    private Flyway flyway;
    @Autowired
    private OptimizationTaskController optimizationTaskController;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ResumeMapper resumeMapper;
    @Autowired
    private ResumeParseResultMapper resumeParseResultMapper;
    @Autowired
    private ResumeAiRepairCoordinator repairCoordinator;
    @Autowired
    private ResumeParseClaimService parseClaimService;
    @Autowired
    private ResumeReviewService resumeReviewService;
    @Autowired
    private ResumeService resumeService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JobTargetMapper jobTargetMapper;
    @Autowired
    private JobDescriptionService jobDescriptionService;
    @Autowired
    private ResumeVersionMapper resumeVersionMapper;
    @Autowired
    private OptimizationTaskMapper optimizationTaskMapper;
    @Autowired
    private OptimizationTaskService optimizationTaskService;
    @Autowired
    private EvidenceAnalysisMapper evidenceAnalysisMapper;
    @Autowired
    private EvidenceRequirementMapper evidenceRequirementMapper;
    @Autowired
    private RequirementEvidenceMapper requirementEvidenceMapper;
    @Autowired
    private JobDirectionInsightService insightService;
    @Autowired
    private AiUsageRecordPersistence usageRecordPersistence;
    @Autowired
    private AiUsageRecordMapper usageRecordMapper;
    @Autowired
    private AiUsageRetentionService usageRetentionService;
    @Autowired
    private ProductObservabilityService observabilityService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private AsyncTaskService asyncTaskService;

    @Test
    void freshFlywaySchemaEnforcesOwnershipAndDerivesInsightWithoutPersistedAggregate() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("38");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'resume_parse_results' AND column_name IN ('parse_generation', 'parse_token')",
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'resume_parse_results' AND column_name = 'user_id' AND is_nullable = 'NO'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'resume_parse_results' AND column_name IN ('extraction_page_count', 'extraction_page_count_known', 'extraction_image_content_present')",
                Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'resume_ai_repair_attempts'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'ai_usage_records' AND column_name IN ('gateway_attempt_count', 'provider_dispatch_count') AND is_nullable = 'NO' AND column_default = '1'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid WHERE t.relname = 'ai_usage_records' AND c.conname IN ('ck_ai_usage_records_gateway_attempt_count_positive', 'ck_ai_usage_records_dispatch_count_positive')",
                Integer.class)).isEqualTo(2);
        User owner = user("integration-owner");
        User other = user("integration-other");
        Resume resume = resume(owner.getId());

        for (int index = 0; index < 8; index++) {
            seedFormalTask(owner.getId(), resume.getId(), index);
        }
        seedFormalTask(other.getId(), resume(other.getId()).getId(), 0);

        JobDirectionCohortVO cohort = insightService.getInsights(owner.getId()).getCohorts().getFirst();
        assertThat(cohort.getSampleSize()).isEqualTo(8);
        assertThat(cohort.getCommonRequirements())
                .anySatisfy(requirement -> {
                    assertThat(requirement.getLabel()).isEqualTo("包含 Java 的岗位要求");
                    assertThat(requirement.getOccurrenceCount()).isEqualTo(8);
                    assertThat(requirement.getSources()).allSatisfy(source ->
                            assertThat(source.getOptimizationTaskId()).isNotNull());
                });
        assertThat(insightService.getInsights(other.getId()).getCohorts()).isEmpty();
        assertThat(observabilityService.snapshot(
                LocalDateTime.now().minusDays(30), LocalDateTime.now().plusMinutes(1))
                .getAnalysisSuccesses()).isGreaterThanOrEqualTo(8);
    }

    @Autowired
    private LocalStoragePathResolver storagePathResolver;

    @Test
    void optimizationDeletionStorageFailureRollsBackAsyncCancellation() throws Exception {
        User owner = user("async-delete-rollback");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        OptimizationTask task = optimizationTaskMapper.selectList(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getUserId, owner.getId())).getFirst();
        Long runningId = asyncTaskService.createTask(owner.getId(), AsyncTaskType.MATCH_ANALYSIS,
                "OPTIMIZATION_TASK", task.getId());
        Long pendingId = asyncTaskService.createTask(owner.getId(), AsyncTaskType.MATCH_ANALYSIS,
                "OPTIMIZATION_TASK", task.getId());
        asyncTaskService.markRunning(runningId, "正在分析");
        task.setStatus("RUNNING");
        task.setAsyncTaskId(runningId);
        optimizationTaskMapper.updateById(task);
        AsyncTaskVO before = asyncTaskService.getTask(runningId, owner.getId());

        String storageKey = "async-rollback-" + UUID.randomUUID();
        Path directory = storagePathResolver.resolve(storageKey);
        Files.createDirectories(directory);
        Path child = Files.writeString(directory.resolve("child"), "synthetic");
        try {
            // A nonempty directory deterministically fails real local object deletion.
            jdbcTemplate.update("""
                    INSERT INTO export_artifacts (user_id, optimization_task_id, target_resume_version_id,
                        content_revision, template_id, template_version, renderer_version, storage_key,
                        file_size, checksum_sha256, page_count, missing_contact, page_limit_exceeded, overflow_detected)
                    VALUES (?, ?, ?, 1, 'classic', 'v5', 'test', ?, 1, ?, 1, false, false, false)
                    """, owner.getId(), task.getId(), task.getTargetResumeVersionId(), storageKey, "a".repeat(64));

            assertThatThrownBy(() -> optimizationTaskService.delete(owner.getId(), task.getId()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("导出文件删除失败，已保留记录，请重试");

            assertThat(optimizationTaskMapper.selectById(task.getId()).getStatus()).isEqualTo("RUNNING");
            assertThat(resumeVersionMapper.selectById(task.getTargetResumeVersionId())).isNotNull();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM export_artifacts WHERE storage_key = ?", String.class, storageKey))
                    .isEqualTo("DELETE_PENDING");
            AsyncTaskVO after = asyncTaskService.getTask(runningId, owner.getId());
            assertThat(after).usingRecursiveComparison().isEqualTo(before);
            assertThat(asyncTaskService.getTask(pendingId, owner.getId()).getStatus()).isEqualTo("PENDING");
            assertThat(asyncTaskService.isActive(owner.getId(), runningId)).isTrue();
            assertThat(asyncTaskService.isActive(owner.getId(), pendingId)).isTrue();

            Files.delete(child);
            optimizationTaskService.delete(owner.getId(), task.getId());
            assertThat(optimizationTaskMapper.selectById(task.getId())).isNull();
            assertThat(asyncTaskService.getTask(runningId, owner.getId()).getStatus()).isEqualTo("CANCELLED");
            assertThat(asyncTaskService.getTask(pendingId, owner.getId()).getStatus()).isEqualTo("CANCELLED");
            asyncTaskService.markSuccess(runningId, "OPTIMIZATION_TASK", task.getId(), "stale");
            asyncTaskService.markFailed(pendingId, "STALE", "stale");
            assertThat(asyncTaskService.getTask(runningId, owner.getId()).getStatus()).isEqualTo("CANCELLED");
            assertThat(asyncTaskService.getTask(pendingId, owner.getId()).getStatus()).isEqualTo("CANCELLED");
        } finally {
            Files.deleteIfExists(child);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void parentDeletionCancelsAsyncWorkAndStaleCompletionCannotReviveIt() {
        User owner = user("async-deletion-owner");
        Resume resume = resume(owner.getId());
        Long taskId = asyncTaskService.createTask(
                owner.getId(), AsyncTaskType.RESUME_PARSE, "RESUME", resume.getId());
        asyncTaskService.markRunning(taskId, "正在解析");

        resumeService.delete(owner.getId(), resume.getId());

        AsyncTaskVO canceled = asyncTaskService.getTask(taskId, owner.getId());
        assertThat(canceled.getStatus()).isEqualTo("CANCELLED");
        asyncTaskService.markSuccess(taskId, "RESUME_PARSE_RESULT", resume.getId(), "不应发布");
        assertThat(asyncTaskService.getTask(taskId, owner.getId()).getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void resumeDeletionSerializesWithAsyncSubmissionAndCancelsTheCommittedWorker() throws Exception {
        User owner = user("resume-submit-delete-race");
        Resume resume = resume(owner.getId());
        CountDownLatch submissionEntered = new CountDownLatch(1);
        CountDownLatch releaseSubmission = new CountDownLatch(1);

        CompletableFuture<Long> submission = CompletableFuture.supplyAsync(() ->
                transactionTemplate.execute(status -> {
                    resumeService.lockForAsyncTaskSubmission(owner.getId(), resume.getId());
                    Long asyncTaskId = asyncTaskService.createTask(
                            owner.getId(), AsyncTaskType.RESUME_PARSE, "RESUME", resume.getId());
                    submissionEntered.countDown();
                    awaitLatch(releaseSubmission);
                    return asyncTaskId;
                }));
        assertThat(submissionEntered.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> deletion = CompletableFuture.runAsync(
                () -> resumeService.delete(owner.getId(), resume.getId()));
        assertFutureBlocked(deletion);

        releaseSubmission.countDown();
        Long asyncTaskId = submission.get(5, TimeUnit.SECONDS);
        deletion.get(5, TimeUnit.SECONDS);

        assertThat(resumeMapper.selectById(resume.getId())).isNull();
        assertThat(asyncTaskService.getTask(asyncTaskId, owner.getId()).getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void optimizationTaskDeletionSerializesWithAsyncAttachmentAndCancelsTheCommittedWorker() throws Exception {
        User owner = user("optimization-submit-delete-race");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        OptimizationTask task = optimizationTaskMapper.selectList(new LambdaQueryWrapper<OptimizationTask>()
                        .eq(OptimizationTask::getUserId, owner.getId()))
                .getFirst();
        task.setStatus("FAILED");
        optimizationTaskMapper.updateById(task);
        CountDownLatch submissionEntered = new CountDownLatch(1);
        CountDownLatch releaseSubmission = new CountDownLatch(1);

        CompletableFuture<Long> submission = CompletableFuture.supplyAsync(() ->
                transactionTemplate.execute(status -> {
                    optimizationTaskService.lockForAsyncSubmission(owner.getId(), task.getId());
                    Long asyncTaskId = asyncTaskService.createTask(
                            owner.getId(), AsyncTaskType.MATCH_ANALYSIS,
                            "OPTIMIZATION_TASK", task.getId());
                    optimizationTaskService.attachAsyncTask(owner.getId(), task.getId(), asyncTaskId);
                    submissionEntered.countDown();
                    awaitLatch(releaseSubmission);
                    return asyncTaskId;
                }));
        assertThat(submissionEntered.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> deletion = CompletableFuture.runAsync(
                () -> optimizationTaskService.delete(owner.getId(), task.getId()));
        assertFutureBlocked(deletion);

        releaseSubmission.countDown();
        Long asyncTaskId = submission.get(5, TimeUnit.SECONDS);
        deletion.get(5, TimeUnit.SECONDS);

        assertThat(optimizationTaskMapper.selectById(task.getId())).isNull();
        assertThat(asyncTaskService.getTask(asyncTaskId, owner.getId()).getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void jobDescriptionDeletionSerializesWithNewOptimizationTaskCreation() throws Exception {
        User owner = user("job-submit-delete-race");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0, SOURCE_A_DOCUMENT);
        OptimizationTask existingTask = optimizationTaskMapper.selectList(
                        new LambdaQueryWrapper<OptimizationTask>()
                                .eq(OptimizationTask::getUserId, owner.getId()))
                .getFirst();
        JobDescriptionSubmitDTO jobRequest = new JobDescriptionSubmitDTO();
        jobRequest.setTitle("Java backend");
        jobRequest.setRawText("Java backend role");
        Long jobDescriptionId = jobDescriptionService.submit(owner.getId(), jobRequest).getId();
        jdbcTemplate.update("UPDATE job_targets SET legacy_job_description_id = ? WHERE id = ?",
                jobDescriptionId, existingTask.getJobTargetId());
        jdbcTemplate.update("""
                INSERT INTO resume_parse_results
                    (resume_id, user_id, parse_status, structured_json, quality_status,
                     quality_issues, unresolved_items, canonical_source_version_id)
                VALUES (?, ?, 'SUCCESS', '{}', 'READY', '[]', '[]', ?)
                """, resume.getId(), owner.getId(), existingTask.getSourceResumeVersionId());
        CountDownLatch taskLocked = new CountDownLatch(1);
        CountDownLatch releaseTaskLock = new CountDownLatch(1);

        CompletableFuture<Void> taskBlocker = CompletableFuture.runAsync(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    optimizationTaskMapper.selectOwnedForUpdate(owner.getId(), existingTask.getId());
                    taskLocked.countDown();
                    awaitLatch(releaseTaskLock);
                }));
        assertThat(taskLocked.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> deletion = CompletableFuture.runAsync(
                () -> jobDescriptionService.delete(owner.getId(), jobDescriptionId));
        awaitJobDescriptionRowLock(jobDescriptionId);
        assertFutureBlocked(deletion);

        CompletableFuture<?> creation = CompletableFuture.supplyAsync(() ->
                optimizationTaskService.createFromExisting(
                        owner.getId(), resume.getId(), jobDescriptionId, "SYSTEM_DEFAULT", "test-model"));
        try {
            assertFutureBlocked(creation);
        } finally {
            releaseTaskLock.countDown();
        }
        taskBlocker.get(5, TimeUnit.SECONDS);
        deletion.get(5, TimeUnit.SECONDS);

        assertThatThrownBy(() -> creation.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(BusinessException.class)
                .hasRootCauseMessage("目标岗位不存在");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM async_tasks WHERE user_id = ? AND biz_type = 'OPTIMIZATION_TASK'",
                Long.class, owner.getId())).isZero();
    }

    @Test
    void formalTaskAttachmentAndCallbacksRequireExactAsyncBusinessBinding() {
        User owner = user("async-business-binding");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        OptimizationTask task = optimizationTaskMapper.selectList(new LambdaQueryWrapper<OptimizationTask>()
                        .eq(OptimizationTask::getUserId, owner.getId()))
                .getFirst();
        task.setStatus("FAILED");
        task.setAsyncTaskId(null);
        optimizationTaskMapper.updateById(task);

        Long wrongType = asyncTaskService.createTask(
                owner.getId(), AsyncTaskType.RESUME_PARSE, "OPTIMIZATION_TASK", task.getId());
        Long wrongBizType = asyncTaskService.createTask(
                owner.getId(), AsyncTaskType.MATCH_ANALYSIS, "RESUME", task.getId());
        Long wrongBizId = asyncTaskService.createTask(
                owner.getId(), AsyncTaskType.MATCH_ANALYSIS, "OPTIMIZATION_TASK", task.getId() + 1);
        for (Long invalidId : List.of(wrongType, wrongBizType, wrongBizId)) {
            assertThatThrownBy(() -> optimizationTaskService.attachAsyncTask(
                    owner.getId(), task.getId(), invalidId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("岗位分析正在进行中或已完成");
        }
        assertThat(optimizationTaskMapper.selectById(task.getId()).getAsyncTaskId()).isNull();

        Long validId = asyncTaskService.createTask(
                owner.getId(), AsyncTaskType.MATCH_ANALYSIS, "OPTIMIZATION_TASK", task.getId());
        optimizationTaskService.attachAsyncTask(owner.getId(), task.getId(), validId);
        assertThat(optimizationTaskMapper.selectById(task.getId()).getAsyncTaskId()).isEqualTo(validId);

        jdbcTemplate.update("UPDATE async_tasks SET biz_type = 'RESUME' WHERE id = ?", validId);
        assertThatThrownBy(() -> optimizationTaskService.markRunning(owner.getId(), task.getId(), validId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("优化任务执行已失效");
        assertThat(optimizationTaskMapper.selectById(task.getId()).getStatus()).isEqualTo("PENDING");

        jdbcTemplate.update("UPDATE async_tasks SET biz_type = 'OPTIMIZATION_TASK' WHERE id = ?", validId);
        optimizationTaskService.markRunning(owner.getId(), task.getId(), validId);
        assertThat(optimizationTaskMapper.selectById(task.getId()).getStatus()).isEqualTo("RUNNING");
    }

    @Test
    void lateFormalSuccessAndFailureCallbacksCannotMutateTheCurrentRetry() {
        User owner = user("late-formal-callback-owner");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        OptimizationTask task = optimizationTaskMapper.selectList(new LambdaQueryWrapper<OptimizationTask>()
                        .eq(OptimizationTask::getUserId, owner.getId()))
                .getFirst();
        EvidenceAnalysis existingAnalysis = evidenceAnalysisMapper.selectOne(
                new LambdaQueryWrapper<EvidenceAnalysis>()
                        .eq(EvidenceAnalysis::getOptimizationTaskId, task.getId()));
        Long oldAsyncTaskId = asyncTaskService.createTask(
                owner.getId(), AsyncTaskType.MATCH_ANALYSIS,
                "OPTIMIZATION_TASK", task.getId());
        asyncTaskService.markFailed(oldAsyncTaskId, "OLD_ATTEMPT", "旧执行已结束");
        Long currentAsyncTaskId = asyncTaskService.createTask(
                owner.getId(), AsyncTaskType.MATCH_ANALYSIS,
                "OPTIMIZATION_TASK", task.getId());
        task.setAsyncTaskId(currentAsyncTaskId);
        task.setStatus("RUNNING");
        optimizationTaskMapper.updateById(task);

        optimizationTaskService.markFailed(
                owner.getId(), task.getId(), oldAsyncTaskId, "LATE_FAILED", "旧 worker 晚到");
        assertThatThrownBy(() -> optimizationTaskService.markSuccess(
                owner.getId(), task.getId(), oldAsyncTaskId, null, existingAnalysis))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("优化任务执行已失效");

        OptimizationTask stillCurrent = optimizationTaskMapper.selectById(task.getId());
        assertThat(stillCurrent.getStatus()).isEqualTo("RUNNING");
        assertThat(stillCurrent.getAsyncTaskId()).isEqualTo(currentAsyncTaskId);

        optimizationTaskService.markFailed(
                owner.getId(), task.getId(), currentAsyncTaskId, "CURRENT_FAILED", "当前执行失败");
        assertThat(optimizationTaskMapper.selectById(task.getId()).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void durableAiRepairReservationIsSingleOwnerAndNeverReclaimed() {
        User owner = user("repair-coordination-owner");
        Resume resume = resume(owner.getId());
        String repairKey = "repair-" + UUID.randomUUID();

        ResumeAiRepairCoordinator.RepairReservation first =
                repairCoordinator.reserve(owner.getId(), resume.getId(), repairKey);
        ResumeAiRepairCoordinator.RepairReservation second =
                repairCoordinator.reserve(owner.getId(), resume.getId(), repairKey);

        assertThat(first.state()).isEqualTo(ResumeAiRepairCoordinator.State.OWNER);
        assertThat(first.ownerToken()).isNotBlank();
        assertThat(second.state()).isEqualTo(ResumeAiRepairCoordinator.State.IN_PROGRESS);

        repairCoordinator.fail(owner.getId(), repairKey, first.ownerToken(),
                "AI 结构化补全失败", 1);

        ResumeAiRepairCoordinator.RepairReservation terminal =
                repairCoordinator.reserve(owner.getId(), resume.getId(), repairKey);
        assertThat(terminal.state()).isEqualTo(ResumeAiRepairCoordinator.State.FAILED_AFTER_DISPATCH);
        assertThat(terminal.providerDispatched()).isTrue();
        assertThat(terminal.ownerToken()).isNull();

        String safeRetryKey = "repair-safe-retry-" + UUID.randomUUID();
        ResumeAiRepairCoordinator.RepairReservation retryOwner =
                repairCoordinator.reserve(owner.getId(), resume.getId(), safeRetryKey);
        repairCoordinator.fail(owner.getId(), safeRetryKey, retryOwner.ownerToken(),
                "本地预检失败", 0);
        assertThat(repairCoordinator.reserve(owner.getId(), resume.getId(), safeRetryKey).state())
                .isEqualTo(ResumeAiRepairCoordinator.State.OWNER);
    }

    @Test
    void parseClaimCommitsBeforeOuterParseWorkSoNewerWorkerCanSupersedeIt() {
        User owner = user("parse-claim-transaction-owner");
        Resume resume = resume(owner.getId());

        ResumeParseClaimService.ResumeParseClaim first = transactionTemplate.execute(status -> {
            ResumeParseClaimService.ResumeParseClaim initial = parseClaimService.acquire(resume.getId());
            CompletableFuture<ResumeParseClaimService.ResumeParseClaim> secondFuture =
                    CompletableFuture.supplyAsync(() -> parseClaimService.acquire(resume.getId()));
            try {
                ResumeParseClaimService.ResumeParseClaim second = secondFuture.get(5, TimeUnit.SECONDS);
                assertThat(second.generation()).isEqualTo(initial.generation() + 1);
            } catch (Exception exception) {
                throw new AssertionError("a newer parse claim should not wait for the outer parse transaction", exception);
            }
            status.setRollbackOnly();
            return initial;
        });

        assertThat(first).isNotNull();
        ResumeParseResult persisted = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resume.getId()));
        assertThat(persisted.getParseGeneration()).isEqualTo(first.generation() + 1);
        assertThat(persisted.getParseToken()).isNotEqualTo(first.token());
    }

    @Test
    void deletedResumeRejectsLateParseCasAndSourceMaterialization() {
        User owner = user("deleted-parse-claim");
        Resume resume = resume(owner.getId());
        LocalDateTime now = LocalDateTime.now();
        assertThat(resumeParseResultMapper.ensureParseRow(resume.getId(), now)).isEqualTo(1);
        String token = UUID.randomUUID().toString();
        Long generation = resumeParseResultMapper.claimParseGeneration(resume.getId(), token, now);
        assertThat(generation).isNotNull();

        resumeService.delete(owner.getId(), resume.getId());

        ResumeVersion lateSource = new ResumeVersion();
        lateSource.setUserId(owner.getId());
        lateSource.setResumeId(resume.getId());
        lateSource.setVersionType("SOURCE");
        lateSource.setSourceType("PARSED_UPLOAD");
        lateSource.setContentStatus("READY");
        lateSource.setStructuredContent(SOURCE_A_DOCUMENT);
        lateSource.setContentRevision(0L);
        lateSource.setCreatedAt(now);
        lateSource.setUpdatedAt(now);
        assertThat(resumeVersionMapper.insertIfCurrentParseClaim(
                lateSource, resume.getId(), generation, token)).isZero();
        assertThat(lateSource.getId()).isNull();
        ResumeParseResult lateResult = new ResumeParseResult();
        lateResult.setResumeId(resume.getId());
        lateResult.setParseStatus("SUCCESS");
        lateResult.setQualityStatus("READY");
        lateResult.setUpdatedAt(now);
        assertThat(resumeParseResultMapper.updateIfCurrent(
                lateResult, resume.getId(), generation, token)).isZero();
        assertThat(resumeParseResultMapper.touchIfCurrent(resume.getId(), generation, token, now)).isZero();
        assertThat(resumeMapper.selectById(resume.getId())).isNull();
        assertThat(resumeParseResultMapper.selectCount(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resume.getId()))).isZero();
        assertThat(resumeVersionMapper.selectCount(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getResumeId, resume.getId()))).isZero();
    }

    @Test
    void parseGenerationCasRejectsStaleSourceAndReleasesTerminalToken() {
        User owner = user("parse-cas-owner");
        Resume resume = resume(owner.getId());
        LocalDateTime now = LocalDateTime.now();

        assertThat(resumeParseResultMapper.ensureParseRow(resume.getId(), now)).isEqualTo(1);
        jdbcTemplate.update(
                "UPDATE resume_parse_results SET extraction_page_count = 3, extraction_page_count_known = TRUE, extraction_image_content_present = TRUE WHERE resume_id = ?",
                resume.getId());
        String staleToken = UUID.randomUUID().toString();
        Long staleGeneration = resumeParseResultMapper.claimParseGeneration(
                resume.getId(), staleToken, now);
        assertThat(staleGeneration).isEqualTo(1L);
        ResumeParseResult claimed = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resume.getId()));
        assertThat(claimed.getParseStatus()).isEqualTo("PROCESSING");
        assertThat(claimed.getQualityStatus()).isEqualTo("PENDING");
        assertThat(claimed.getExtractionPageCount()).isNull();
        assertThat(claimed.getExtractionPageCountKnown()).isNull();
        assertThat(claimed.getExtractionImageContentPresent()).isNull();

        String currentToken = UUID.randomUUID().toString();
        Long currentGeneration = resumeParseResultMapper.claimParseGeneration(
                resume.getId(), currentToken, LocalDateTime.now());
        assertThat(currentGeneration).isEqualTo(2L);
        assertThat(resumeParseResultMapper.touchIfCurrent(
                resume.getId(), staleGeneration, staleToken, LocalDateTime.now())).isZero();

        ResumeVersion staleSource = new ResumeVersion();
        staleSource.setUserId(owner.getId());
        staleSource.setResumeId(resume.getId());
        staleSource.setVersionType("SOURCE");
        staleSource.setSourceType("PARSED_UPLOAD");
        staleSource.setContentStatus("READY");
        staleSource.setStructuredContent("{\"schemaVersion\":\"RESUME_DOCUMENT_V1\",\"marker\":\"STALE\"}");
        staleSource.setContentRevision(0L);
        staleSource.setCreatedAt(now);
        staleSource.setUpdatedAt(now);
        assertThat(resumeVersionMapper.insertIfCurrentParseClaim(
                staleSource, resume.getId(), staleGeneration, staleToken)).isZero();
        assertThat(staleSource.getId()).isNull();

        ResumeVersion currentSource = new ResumeVersion();
        currentSource.setUserId(owner.getId());
        currentSource.setResumeId(resume.getId());
        currentSource.setVersionType("SOURCE");
        currentSource.setSourceType("PARSED_UPLOAD");
        currentSource.setContentStatus("READY");
        currentSource.setStructuredContent("{\"schemaVersion\":\"RESUME_DOCUMENT_V1\",\"marker\":\"CURRENT\"}");
        currentSource.setContentRevision(0L);
        currentSource.setCreatedAt(now);
        currentSource.setUpdatedAt(now);
        assertThat(resumeVersionMapper.insertIfCurrentParseClaim(
                currentSource, resume.getId(), currentGeneration, currentToken)).isEqualTo(1);
        assertThat(currentSource.getId()).isNotNull();

        ResumeParseResult terminal = new ResumeParseResult();
        terminal.setResumeId(resume.getId());
        terminal.setParseStatus("SUCCESS");
        terminal.setExtractedText("source");
        terminal.setQualityStatus("READY");
        terminal.setUnresolvedItems("[]");
        terminal.setCanonicalSourceVersionId(currentSource.getId());
        terminal.setUpdatedAt(LocalDateTime.now());
        assertThat(resumeParseResultMapper.updateIfCurrent(
                terminal, resume.getId(), currentGeneration, currentToken)).isEqualTo(1);

        ResumeParseResult persisted = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resume.getId()));
        assertThat(persisted.getParseGeneration()).isEqualTo(currentGeneration);
        assertThat(persisted.getParseStatus()).isEqualTo("SUCCESS");
        assertThat(persisted.getParseToken()).isNull();
        assertThat(persisted.getCanonicalSourceVersionId()).isEqualTo(currentSource.getId());

        ResumeParseResult staleResult = new ResumeParseResult();
        staleResult.setResumeId(resume.getId());
        staleResult.setParseStatus("FAILED");
        staleResult.setQualityStatus("FAILED");
        staleResult.setUpdatedAt(LocalDateTime.now());
        assertThat(resumeParseResultMapper.updateIfCurrent(
                staleResult, resume.getId(), staleGeneration, staleToken)).isZero();
        assertThat(resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resume.getId())).getParseStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void historicalAnalysisResultUsesTaskFrozenSourceAfterCurrentCanonicalPointerMoves() {
        User owner = user("frozen-source-owner");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0, SOURCE_A_DOCUMENT);

        OptimizationTask task = optimizationTaskMapper.selectOne(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getUserId, owner.getId())
                .orderByDesc(OptimizationTask::getCreatedAt)
                .last("LIMIT 1"));
        ResumeVersion sourceA = resumeVersionMapper.selectById(task.getSourceResumeVersionId());
        assertThat(sourceA.getStructuredContent()).isEqualTo(SOURCE_A_DOCUMENT);

        ResumeVersion sourceB = new ResumeVersion();
        sourceB.setUserId(owner.getId());
        sourceB.setResumeId(resume.getId());
        sourceB.setVersionType("SOURCE");
        sourceB.setSourceType("PARSED_UPLOAD");
        sourceB.setContentStatus("READY");
        sourceB.setStructuredContent(SOURCE_B_DOCUMENT);
        sourceB.setContentRevision(0L);
        sourceB.setCreatedAt(LocalDateTime.now());
        sourceB.setUpdatedAt(LocalDateTime.now());
        resumeVersionMapper.insert(sourceB);

        ResumeParseResult currentParse = new ResumeParseResult();
        currentParse.setResumeId(resume.getId());
        currentParse.setUserId(owner.getId());
        currentParse.setParseStatus("SUCCESS");        currentParse.setQualityStatus("READY");
        currentParse.setUnresolvedItems("[]");
        currentParse.setCanonicalSourceVersionId(sourceB.getId());
        currentParse.setCreatedAt(LocalDateTime.now());
        currentParse.setUpdatedAt(LocalDateTime.now());
        resumeParseResultMapper.insert(currentParse);

        var result = optimizationTaskController.getAnalysisResult(
                task.getId(),
                new TestingAuthenticationToken(
                        new AuthenticatedUser(owner.getId(), owner.getUsername()), "phase9-test"));

        assertThat(result.getData().getSourceCanonicalDocument()).isEqualTo(SOURCE_A_DOCUMENT);
        assertThat(result.getData().getSourceCanonicalDocument()).doesNotContain("SOURCE_B");
        RequirementEvidence evidence = requirementEvidenceMapper.selectOne(new LambdaQueryWrapper<RequirementEvidence>()
                .eq(RequirementEvidence::getUserId, owner.getId()));
        assertThat(evidence.getSourceResumeVersionId()).isEqualTo(sourceA.getId());
    }

    @Test
    void insightKeepsFrozenSourceHistoryButImmediatelyReflectsTheWindowAndParentDeletion() {
        User owner = user("insight-lifecycle-owner");
        Resume resume = resume(owner.getId());
        for (int index = 0; index < 8; index++) {
            seedFormalTask(owner.getId(), resume.getId(), index);
        }

        assertThat(insightService.getInsights(owner.getId()).getCohorts()).hasSize(1);
        ResumeVersion target = resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getUserId, owner.getId())
                        .eq(ResumeVersion::getVersionType, "TARGETED"))
                .getFirst();
        target.setStructuredContent("{\"target\":\"subsequent edit\"}");
        resumeVersionMapper.updateById(target);
        assertThat(insightService.getInsights(owner.getId()).getCohorts().getFirst().getSampleSize()).isEqualTo(8);

        OptimizationTask oldestTask = optimizationTaskMapper.selectList(new LambdaQueryWrapper<OptimizationTask>()
                        .eq(OptimizationTask::getUserId, owner.getId())
                        .orderByAsc(OptimizationTask::getFinishedAt))
                .getFirst();
        oldestTask.setFinishedAt(LocalDateTime.now().minusDays(181));
        optimizationTaskMapper.updateById(oldestTask);
        assertThat(insightService.getInsights(owner.getId()).getCohorts()).isEmpty();

        oldestTask.setFinishedAt(LocalDateTime.now());
        optimizationTaskMapper.updateById(oldestTask);
        assertThat(insightService.getInsights(owner.getId()).getCohorts()).hasSize(1);
        resumeService.delete(owner.getId(), resume.getId());
        assertThat(insightService.getInsights(owner.getId()).getCohorts()).isEmpty();
        assertThat(jobTargetMapper.selectList(new LambdaQueryWrapper<JobTarget>()
                .eq(JobTarget::getUserId, owner.getId()))).isEmpty();
    }

    @Test
    void upgradesAnIsolatedReleasedV22SchemaToTheCurrentFlywayState() {
        String schema = "phase9_upgrade_" + Long.toUnsignedString(System.nanoTime(), 36);
        try {
            Flyway releasedV22 = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .createSchemas(true)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("22"))
                    .load();
            releasedV22.migrate();
            assertThat(releasedV22.info().current().getVersion().getVersion()).isEqualTo("22");

            Long legacyUserId = jdbcTemplate.queryForObject(
                    "INSERT INTO " + schema + ".users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                    Long.class,
                    "phase9-legacy-user-" + schema,
                    "phase9-legacy-" + schema + "@example.invalid",
                    "phase9-test-password");
            Long legacyResumeId = jdbcTemplate.queryForObject(
                    "INSERT INTO " + schema + ".resumes (user_id, original_filename, file_type, file_size, object_key, upload_status) "
                            + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
                    Long.class,
                    legacyUserId,
                    "legacy-java.pdf",
                    "PDF",
                    100L,
                    "resumes/legacy/legacy-java.pdf",
                    "UPLOADED");

            Flyway upgraded = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .createSchemas(true)
                    .locations("classpath:db/migration")
                    .load();
            upgraded.migrate();

            assertThat(upgraded.info().current().getVersion().getVersion()).isEqualTo("38");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'resumes' AND column_name = 'display_name'",
                    Integer.class,
                    schema)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT display_name FROM " + schema + ".resumes WHERE id = ?",
                    String.class,
                    legacyResumeId)).isEqualTo("legacy-java");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT is_nullable FROM information_schema.columns WHERE table_schema = ? AND table_name = 'resumes' AND column_name = 'display_name'",
                    String.class,
                    schema)).isEqualTo("NO");
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE " + schema + ".resumes SET display_name = NULL WHERE id = ?",
                    legacyResumeId)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = 'ai_usage_records'",
                    Integer.class,
                    schema)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'ai_usage_records' AND column_name IN ('gateway_attempt_count', 'provider_dispatch_count') AND is_nullable = 'NO' AND column_default = '1'",
                    Integer.class,
                    schema)).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid JOIN pg_namespace n ON n.oid = t.relnamespace WHERE n.nspname = ? AND t.relname = 'ai_usage_records' AND c.conname IN ('ck_ai_usage_records_gateway_attempt_count_positive', 'ck_ai_usage_records_dispatch_count_positive')",
                    Integer.class,
                    schema)).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'resume_parse_results' AND column_name = 'canonical_source_version_id'",
                    Integer.class,
                    schema)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'resume_parse_results' AND column_name = 'canonical_document'",
                    Integer.class,
                    schema)).isEqualTo(0);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'resume_parse_results' AND column_name IN ('parse_generation', 'parse_token')",
                    Integer.class,
                    schema)).isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'resume_parse_results' AND column_name = 'user_id' AND is_nullable = 'NO'",
                    Integer.class,
                    schema)).isEqualTo(1);
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void userIdentifierMigrationCanonicalizesSafeHistoryAndEnforcesUnambiguousWrites() {
        String schema = "user_identifier_upgrade_" + Long.toUnsignedString(System.nanoTime(), 36);
        try {
            Flyway releasedV36 = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .createSchemas(true)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("36"))
                    .load();
            releasedV36.migrate();
            Long userId = jdbcTemplate.queryForObject(
                    "INSERT INTO " + schema + ".users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                    Long.class,
                    "  LegacyUser  ",
                    "  Legacy.User@Example.COM  ",
                    "test-password");

            Flyway upgraded = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .createSchemas(true)
                    .locations("classpath:db/migration")
                    .load();
            upgraded.migrate();

            assertThat(upgraded.info().current().getVersion().getVersion()).isEqualTo("38");
            assertThat(jdbcTemplate.queryForMap(
                    "SELECT username, email FROM " + schema + ".users WHERE id = ?", userId))
                    .containsEntry("username", "LegacyUser")
                    .containsEntry("email", "legacy.user@example.com");
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO " + schema + ".users (username, email, password_hash) VALUES (?, ?, ?)",
                    "other-user", "LEGACY.USER@EXAMPLE.COM", "test-password"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO " + schema + ".users (username, email, password_hash) VALUES (?, ?, ?)",
                    "cross-field@example.com", "different@example.com", "test-password"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "INSERT INTO " + schema + ".users (username, email, password_hash) VALUES (?, ?, ?)",
                    " padded-user ", "padded@example.com", "test-password"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void userIdentifierMigrationFailsClosedWhenCanonicalizationWouldMergeAccounts() {
        String schema = "user_identifier_conflict_" + Long.toUnsignedString(System.nanoTime(), 36);
        try {
            Flyway releasedV36 = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .createSchemas(true)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("36"))
                    .load();
            releasedV36.migrate();
            jdbcTemplate.update(
                    "INSERT INTO " + schema + ".users (username, email, password_hash) VALUES (?, ?, ?), (?, ?, ?)",
                    "legacy-one", "Collision@Example.com", "test-password",
                    "legacy-two", "collision@example.com", "test-password");

            Flyway upgraded = Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(schema)
                    .defaultSchema(schema)
                    .createSchemas(true)
                    .locations("classpath:db/migration")
                    .load();

            assertThatThrownBy(upgraded::migrate)
                    .hasStackTraceContaining("email normalization would merge accounts");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + schema + ".users", Long.class)).isEqualTo(2L);
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void canonicalPointerRejectsTargetedVersionAtDatabaseBoundary() {
        User owner = user("canonical-pointer-owner");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        List<ResumeVersion> versions = resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getUserId, owner.getId())
                .eq(ResumeVersion::getResumeId, resume.getId()));
        ResumeVersion source = versions.stream()
                .filter(version -> "SOURCE".equals(version.getVersionType()))
                .findFirst()
                .orElseThrow();
        ResumeVersion targeted = versions.stream()
                .filter(version -> "TARGETED".equals(version.getVersionType()))
                .findFirst()
                .orElseThrow();

        assertThat(jdbcTemplate.update(
                "INSERT INTO resume_parse_results (resume_id, user_id, parse_status, canonical_source_version_id) VALUES (?, ?, 'SUCCESS', ?)",
                resume.getId(),
                owner.getId(),
                source.getId())).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE resume_parse_results SET canonical_source_version_id = ? WHERE resume_id = ?",
                targeted.getId(),
                resume.getId()))
                .hasMessageContaining("canonical_source_version_id must reference a SOURCE version");
    }

    @Test
    void canonicalPointerRejectsSourceOwnedByAnotherUser() {
        User owner = user("canonical-pointer-tenant-owner");
        Resume resume = resume(owner.getId());
        ResumeVersion source = reviewSource(owner.getId(), resume.getId(), SOURCE_A_DOCUMENT);
        assertThat(jdbcTemplate.update(
                "INSERT INTO resume_parse_results (resume_id, user_id, parse_status, canonical_source_version_id) VALUES (?, ?, 'SUCCESS', ?)",
                resume.getId(), owner.getId(), source.getId())).isEqualTo(1);

        Resume otherResumeSameUser = resume(owner.getId());
        ResumeVersion crossResumeSource = reviewSource(owner.getId(), otherResumeSameUser.getId(), SOURCE_B_DOCUMENT);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE resume_parse_results SET canonical_source_version_id = ? WHERE resume_id = ?",
                crossResumeSource.getId(), resume.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        User otherOwner = user("canonical-pointer-tenant-other");
        Resume otherResume = resume(otherOwner.getId());
        ResumeVersion foreignSource = reviewSource(otherOwner.getId(), otherResume.getId(), SOURCE_B_DOCUMENT);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE resume_parse_results SET canonical_source_version_id = ? WHERE resume_id = ?",
                foreignSource.getId(), resume.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void referencedSourceCannotBeRetypedOrReparented() {
        User owner = user("frozen-source-shape-owner");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        ResumeVersion source = resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getUserId, owner.getId())
                        .eq(ResumeVersion::getResumeId, resume.getId())
                        .eq(ResumeVersion::getVersionType, "SOURCE"))
                .getFirst();
        ResumeVersion targeted = resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getUserId, owner.getId())
                        .eq(ResumeVersion::getResumeId, resume.getId())
                        .eq(ResumeVersion::getVersionType, "TARGETED"))
                .getFirst();
        Long jobTargetId = targeted.getJobTargetId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE resume_versions SET version_type = 'TARGETED', source_version_id = ?, job_target_id = ? WHERE id = ?",
                source.getId(), jobTargetId, source.getId()))
                .hasMessageContaining("resume version ownership is immutable");
    }

    @Test
    void referencedSourceCannotBeDeletedAndCascadeAwayHistory() {
        User owner = user("referenced-source-delete-owner");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        ResumeVersion source = resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                        .eq(ResumeVersion::getResumeId, resume.getId())
                        .eq(ResumeVersion::getVersionType, "SOURCE"))
                .getFirst();

        assertThatThrownBy(() -> resumeVersionMapper.deleteById(source.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(resumeVersionMapper.selectById(source.getId())).isNotNull();
        assertThat(optimizationTaskMapper.selectList(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getSourceResumeVersionId, source.getId()))).isNotEmpty();
    }

    @Test
    void databaseRejectsCrossResumeSourceVersionReferences() {
        User owner = user("cross-resume-version-owner");
        Resume firstResume = resume(owner.getId());
        Resume secondResume = resume(owner.getId());
        ResumeVersion source = new ResumeVersion();
        source.setUserId(owner.getId());
        source.setResumeId(firstResume.getId());
        source.setVersionType("SOURCE");
        source.setSourceType("PARSED_UPLOAD");
        source.setContentStatus("READY");
        source.setStructuredContent(SOURCE_A_DOCUMENT);
        source.setContentRevision(0L);
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(LocalDateTime.now());
        resumeVersionMapper.insert(source);

        JobTarget target = new JobTarget();
        target.setUserId(owner.getId());
        target.setTitle("Cross resume target");
        target.setRawJd("需要后端开发经验");
        target.setSourceType("USER_INPUT");
        target.setCreatedAt(LocalDateTime.now());
        target.setUpdatedAt(LocalDateTime.now());
        jobTargetMapper.insert(target);
        ResumeVersion crossResumeTarget = new ResumeVersion();
        crossResumeTarget.setUserId(owner.getId());
        crossResumeTarget.setResumeId(secondResume.getId());
        crossResumeTarget.setSourceVersionId(source.getId());
        crossResumeTarget.setJobTargetId(target.getId());
        crossResumeTarget.setVersionType("TARGETED");
        crossResumeTarget.setSourceType("JOB_DERIVATION");
        crossResumeTarget.setContentStatus("READY");
        crossResumeTarget.setStructuredContent(SOURCE_A_DOCUMENT);
        crossResumeTarget.setContentRevision(0L);
        crossResumeTarget.setCreatedAt(LocalDateTime.now());
        crossResumeTarget.setUpdatedAt(LocalDateTime.now());

        assertThatThrownBy(() -> resumeVersionMapper.insert(crossResumeTarget))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void targetedVersionOwnershipCannotBeReparentedAfterPublication() {
        User owner = user("target-version-reparent-owner");
        Resume firstResume = resume(owner.getId());
        Resume secondResume = resume(owner.getId());
        seedFormalTask(owner.getId(), firstResume.getId(), 0);
        OptimizationTask task = optimizationTaskMapper.selectOne(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getUserId, owner.getId())
                .last("LIMIT 1"));
        ResumeVersion otherSource = reviewSource(owner.getId(), secondResume.getId(), SOURCE_B_DOCUMENT);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE resume_versions SET resume_id = ?, source_version_id = ? WHERE id = ?",
                secondResume.getId(), otherSource.getId(), task.getTargetResumeVersionId()))
                .hasMessageContaining("resume version ownership is immutable");
    }

    @Test
    void evidenceRequirementParentCannotChangeBeforeEvidencePublication() {
        User owner = user("empty-requirement-reparent-owner");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        seedFormalTask(owner.getId(), resume.getId(), 1);
        List<EvidenceAnalysis> analyses = evidenceAnalysisMapper.selectList(
                new LambdaQueryWrapper<EvidenceAnalysis>()
                        .eq(EvidenceAnalysis::getUserId, owner.getId())
                        .orderByAsc(EvidenceAnalysis::getId));
        EvidenceRequirement requirement = evidenceRequirementMapper.selectOne(
                new LambdaQueryWrapper<EvidenceRequirement>()
                        .eq(EvidenceRequirement::getEvidenceAnalysisId, analyses.get(0).getId()));
        requirementEvidenceMapper.delete(new LambdaQueryWrapper<RequirementEvidence>()
                .eq(RequirementEvidence::getEvidenceRequirementId, requirement.getId()));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE evidence_requirements SET evidence_analysis_id = ? WHERE id = ?",
                analyses.get(1).getId(), requirement.getId()))
                .hasMessageContaining("cannot be reparented");
    }

    @Test
    void evidenceAnalysisParentCannotChangeBeforeEvidencePublication() {
        User owner = user("empty-analysis-reparent-owner");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        seedFormalTask(owner.getId(), resume.getId(), 1);
        List<OptimizationTask> tasks = optimizationTaskMapper.selectList(
                new LambdaQueryWrapper<OptimizationTask>()
                        .eq(OptimizationTask::getUserId, owner.getId())
                        .orderByAsc(OptimizationTask::getId));
        EvidenceAnalysis analysis = evidenceAnalysisMapper.selectOne(
                new LambdaQueryWrapper<EvidenceAnalysis>()
                        .eq(EvidenceAnalysis::getOptimizationTaskId, tasks.get(0).getId()));
        EvidenceRequirement requirement = evidenceRequirementMapper.selectOne(
                new LambdaQueryWrapper<EvidenceRequirement>()
                        .eq(EvidenceRequirement::getEvidenceAnalysisId, analysis.getId()));
        requirementEvidenceMapper.delete(new LambdaQueryWrapper<RequirementEvidence>()
                .eq(RequirementEvidence::getEvidenceRequirementId, requirement.getId()));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE evidence_analyses SET optimization_task_id = ? WHERE id = ?",
                tasks.get(1).getId(), analysis.getId()))
                .hasMessageContaining("cannot be reparented");
    }

    @Test
    void formalTaskInputEdgesCannotChangeBeforeEvidencePublication() {
        User owner = user("empty-task-reparent-owner");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        OptimizationTask task = optimizationTaskMapper.selectOne(
                new LambdaQueryWrapper<OptimizationTask>()
                        .eq(OptimizationTask::getUserId, owner.getId())
                        .last("LIMIT 1"));
        EvidenceAnalysis analysis = evidenceAnalysisMapper.selectOne(
                new LambdaQueryWrapper<EvidenceAnalysis>()
                        .eq(EvidenceAnalysis::getOptimizationTaskId, task.getId()));
        EvidenceRequirement requirement = evidenceRequirementMapper.selectOne(
                new LambdaQueryWrapper<EvidenceRequirement>()
                        .eq(EvidenceRequirement::getEvidenceAnalysisId, analysis.getId()));
        requirementEvidenceMapper.delete(new LambdaQueryWrapper<RequirementEvidence>()
                .eq(RequirementEvidence::getEvidenceRequirementId, requirement.getId()));
        ResumeVersion alternateSource = reviewSource(owner.getId(), resume.getId(), SOURCE_B_DOCUMENT);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE optimization_tasks SET source_resume_version_id = ? WHERE id = ?",
                alternateSource.getId(), task.getId()))
                .hasMessageContaining("formal task input ownership is immutable");
    }

    @Test
    void databaseRejectsRequirementEvidenceFromAnotherTaskOnTheSameResume() {
        User owner = user("cross-task-evidence-owner");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        seedFormalTask(owner.getId(), resume.getId(), 1);

        List<OptimizationTask> tasks = optimizationTaskMapper.selectList(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getUserId, owner.getId())
                .orderByDesc(OptimizationTask::getCreatedAt));
        OptimizationTask firstTask = tasks.get(0);
        OptimizationTask secondTask = tasks.get(1);
        EvidenceAnalysis firstAnalysis = evidenceAnalysisMapper.selectOne(new LambdaQueryWrapper<EvidenceAnalysis>()
                .eq(EvidenceAnalysis::getOptimizationTaskId, firstTask.getId()));
        EvidenceRequirement requirement = evidenceRequirementMapper.selectOne(
                new LambdaQueryWrapper<EvidenceRequirement>()
                        .eq(EvidenceRequirement::getEvidenceAnalysisId, firstAnalysis.getId()));

        RequirementEvidence evidence = new RequirementEvidence();
        evidence.setUserId(owner.getId());
        evidence.setEvidenceRequirementId(requirement.getId());
        evidence.setSourceResumeVersionId(secondTask.getSourceResumeVersionId());
        evidence.setSectionLabel("技能");
        evidence.setEvidenceText("Java");
        evidence.setSupportLevel("SUFFICIENT");
        evidence.setCreatedAt(LocalDateTime.now());

        assertThat(firstTask.getSourceResumeVersionId()).isNotEqualTo(secondTask.getSourceResumeVersionId());
        assertThatThrownBy(() -> requirementEvidenceMapper.insert(evidence))
                .hasMessageContaining("owning task SOURCE");
    }

    @Test
    void evidenceRequirementCannotBeReparentedAfterEvidenceIsPublished() {
        User owner = user("evidence-reparent-owner");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        seedFormalTask(owner.getId(), resume.getId(), 1);

        List<OptimizationTask> tasks = optimizationTaskMapper.selectList(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getUserId, owner.getId())
                .orderByDesc(OptimizationTask::getCreatedAt));
        EvidenceAnalysis firstAnalysis = evidenceAnalysisMapper.selectOne(new LambdaQueryWrapper<EvidenceAnalysis>()
                .eq(EvidenceAnalysis::getOptimizationTaskId, tasks.get(0).getId()));
        EvidenceAnalysis secondAnalysis = evidenceAnalysisMapper.selectOne(new LambdaQueryWrapper<EvidenceAnalysis>()
                .eq(EvidenceAnalysis::getOptimizationTaskId, tasks.get(1).getId()));
        EvidenceRequirement requirement = evidenceRequirementMapper.selectOne(
                new LambdaQueryWrapper<EvidenceRequirement>()
                        .eq(EvidenceRequirement::getEvidenceAnalysisId, firstAnalysis.getId()));
        requirement.setEvidenceAnalysisId(secondAnalysis.getId());

        assertThatThrownBy(() -> evidenceRequirementMapper.updateById(requirement))
                .hasMessageContaining("cannot be reparented");
    }

    @Test
    void databaseRejectsRequirementEvidenceFromAnotherResume() {
        User owner = user("cross-resume-evidence-owner");
        Resume firstResume = resume(owner.getId());
        Resume secondResume = resume(owner.getId());
        seedFormalTask(owner.getId(), firstResume.getId(), 0);

        OptimizationTask task = optimizationTaskMapper.selectOne(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getUserId, owner.getId())
                .orderByDesc(OptimizationTask::getCreatedAt)
                .last("LIMIT 1"));
        EvidenceRequirement requirement = evidenceRequirementMapper.selectOne(
                new LambdaQueryWrapper<EvidenceRequirement>()
                        .eq(EvidenceRequirement::getUserId, owner.getId()));
        ResumeVersion foreignSource = reviewSource(owner.getId(), secondResume.getId(), SOURCE_A_DOCUMENT);

        RequirementEvidence evidence = new RequirementEvidence();
        evidence.setUserId(owner.getId());
        evidence.setEvidenceRequirementId(requirement.getId());
        evidence.setSourceResumeVersionId(foreignSource.getId());
        evidence.setSectionLabel("技能");
        evidence.setEvidenceText("Java");
        evidence.setSupportLevel("SUFFICIENT");
        evidence.setCreatedAt(LocalDateTime.now());

        assertThat(task.getSourceResumeVersionId()).isNotEqualTo(foreignSource.getId());
        assertThatThrownBy(() -> requirementEvidenceMapper.insert(evidence))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("owning task SOURCE");
    }

    @Test
    void taskSourceCannotChangeAfterFormalEvidenceIsPublished() {
        User owner = user("task-source-freeze-owner");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        OptimizationTask task = optimizationTaskMapper.selectList(new LambdaQueryWrapper<OptimizationTask>()
                        .eq(OptimizationTask::getUserId, owner.getId()))
                .getFirst();
        ResumeVersion alternate = reviewSource(owner.getId(), resume.getId(), SOURCE_B_DOCUMENT);
        task.setSourceResumeVersionId(alternate.getId());

        assertThatThrownBy(() -> optimizationTaskMapper.updateById(task))
                .hasMessageContaining("formal task input ownership is immutable");
    }

    @Test
    void canonicalSourcePointerAlsoFreezesSourceContent() {
        User owner = user("canonical-source-freeze-owner");
        Resume resume = resume(owner.getId());
        ResumeVersion source = new ResumeVersion();
        source.setUserId(owner.getId());
        source.setResumeId(resume.getId());
        source.setVersionType("SOURCE");
        source.setSourceType("PARSED_UPLOAD");
        source.setContentStatus("READY");
        source.setStructuredContent(SOURCE_A_DOCUMENT);
        source.setContentRevision(0L);
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(LocalDateTime.now());
        resumeVersionMapper.insert(source);

        assertThat(jdbcTemplate.update(
                "INSERT INTO resume_parse_results (resume_id, user_id, parse_status, quality_status, canonical_source_version_id) VALUES (?, ?, 'SUCCESS', 'READY', ?)",
                resume.getId(), owner.getId(), source.getId())).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE resume_versions SET structured_content = ? WHERE id = ?",
                SOURCE_B_DOCUMENT, source.getId()))
                .hasMessageContaining("SOURCE version is frozen");
    }

    @Test
    void reviewReplacementAllowsOldSourceToRemainReferencedAndKeepsBothSnapshots() throws Exception {
        User owner = user("review-replacement-owner");
        Resume resume = resume(owner.getId());
        ResumeDocumentDTO document = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder()
                        .name("Review Owner")
                        .contacts(new java.util.ArrayList<>())
                        .build())
                .sections(new java.util.ArrayList<>(List.of(
                        ResumeDocumentSectionDTO.builder()
                                .id("review-section")
                                .kind("OTHER")
                                .title("其他")
                                .entries(new java.util.ArrayList<>(List.of(
                                        ResumeDocumentEntryDTO.builder()
                                                .bullets(new java.util.ArrayList<>(List.of(
                                                        ResumeDocumentBulletDTO.builder()
                                                                .text("保留的来源内容")
                                                                .build())))
                                                .build())))
                                .build())))
                .build();
        ResumeVersion source = new ResumeVersion();
        source.setUserId(owner.getId());
        source.setResumeId(resume.getId());
        source.setVersionType("SOURCE");
        source.setSourceType("PARSED_UPLOAD");
        source.setContentStatus("PENDING");
        source.setStructuredContent(objectMapper.writeValueAsString(document));
        source.setContentRevision(0L);
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(LocalDateTime.now());
        resumeVersionMapper.insert(source);

        JobTarget target = new JobTarget();
        target.setUserId(owner.getId());
        target.setTitle("Review target");
        target.setRawJd("需要后端开发经验");
        target.setSourceType("USER_INPUT");
        target.setCreatedAt(LocalDateTime.now());
        target.setUpdatedAt(LocalDateTime.now());
        jobTargetMapper.insert(target);
        ResumeVersion targeted = new ResumeVersion();
        targeted.setUserId(owner.getId());
        targeted.setResumeId(resume.getId());
        targeted.setSourceVersionId(source.getId());
        targeted.setJobTargetId(target.getId());
        targeted.setVersionType("TARGETED");
        targeted.setSourceType("JOB_DERIVATION");
        targeted.setContentStatus("READY");
        targeted.setStructuredContent(source.getStructuredContent());
        targeted.setContentRevision(0L);
        targeted.setCreatedAt(LocalDateTime.now());
        targeted.setUpdatedAt(LocalDateTime.now());
        resumeVersionMapper.insert(targeted);

        OptimizationTask task = new OptimizationTask();
        task.setUserId(owner.getId());
        task.setSourceResumeVersionId(source.getId());
        task.setTargetResumeVersionId(targeted.getId());
        task.setJobTargetId(target.getId());
        task.setStatus("SUCCESS");
        task.setResumeInputSnapshot(source.getStructuredContent());
        task.setJobInputSnapshot(target.getRawJd());
        task.setPromptSnapshot("{}");
        task.setRulesSnapshot("{}");
        task.setTemplateVersion("NOT_SELECTED");
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        optimizationTaskMapper.insert(task);

        ResumeParseResult parseResult = new ResumeParseResult();
        parseResult.setResumeId(resume.getId());
        parseResult.setUserId(owner.getId());
        parseResult.setParseStatus("SUCCESS");
        parseResult.setQualityStatus("NEEDS_REVIEW");
        parseResult.setUnresolvedItems(objectMapper.writeValueAsString(List.of(
                ResumeUnresolvedItemDTO.builder()
                        .id("review-contact")
                        .kind(ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE)
                        .canonicalDraft("{\"type\":\"EMAIL\",\"label\":\"邮箱\",\"value\":\"review@example.com\"}")
                        .reason("请确认联系方式")
                        .build())));
        parseResult.setQualityIssues("[]");
        parseResult.setCanonicalSourceVersionId(source.getId());
        parseResult.setCreatedAt(LocalDateTime.now());
        parseResult.setUpdatedAt(LocalDateTime.now());
        resumeParseResultMapper.insert(parseResult);

        ResumeReviewVO review = resumeReviewService.resolve(owner.getId(), resume.getId(),
                ResumeReviewResolveRequestDTO.builder()
                        .itemId("review-contact")
                        .action("ACCEPT")
                        .build());

        assertThat(review.getQualityStatus()).isEqualTo("READY");
        ResumeParseResult persisted = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resume.getId()));
        assertThat(persisted.getCanonicalSourceVersionId()).isNotEqualTo(source.getId());
        ResumeVersion oldSource = resumeVersionMapper.selectById(source.getId());
        ResumeVersion replacement = resumeVersionMapper.selectById(persisted.getCanonicalSourceVersionId());
        assertThat(oldSource.getStructuredContent()).isEqualTo(source.getStructuredContent());
        assertThat(oldSource.getContentStatus()).isEqualTo("PENDING");
        assertThat(replacement.getContentStatus()).isEqualTo("READY");
        assertThat(replacement.getStructuredContent()).contains("review@example.com");
        assertThat(optimizationTaskMapper.selectById(task.getId()).getSourceResumeVersionId())
                .isEqualTo(source.getId());
        assertThat(resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getResumeId, resume.getId())
                .eq(ResumeVersion::getVersionType, "SOURCE"))).hasSize(2);
    }

    @Test
    void reviewCasFailureRollsBackReplacementWithoutLeavingAnOrphanSource() throws Exception {
        User owner = user("review-cas-rollback-owner");
        Resume resume = resume(owner.getId());
        ResumeDocumentDTO document = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(ResumeDocumentBasicsDTO.builder()
                        .name("Review CAS Owner")
                        .contacts(new java.util.ArrayList<>())
                        .build())
                .sections(new java.util.ArrayList<>())
                .build();
        String documentJson = objectMapper.writeValueAsString(document);
        ResumeVersion source = reviewSource(owner.getId(), resume.getId(), documentJson);
        ResumeVersion alternate = reviewSource(owner.getId(), resume.getId(), documentJson);
        ResumeParseResult parseResult = new ResumeParseResult();
        parseResult.setResumeId(resume.getId());
        parseResult.setUserId(owner.getId());
        parseResult.setParseStatus("SUCCESS");
        parseResult.setQualityStatus("NEEDS_REVIEW");
        parseResult.setUnresolvedItems(objectMapper.writeValueAsString(List.of(
                ResumeUnresolvedItemDTO.builder()
                        .id("review-cas-contact")
                        .kind(ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE)
                        .canonicalDraft("{\"type\":\"EMAIL\",\"label\":\"邮箱\",\"value\":\"cas@example.com\"}")
                        .reason("请确认联系方式")
                        .build())));
        parseResult.setQualityIssues("[]");
        parseResult.setCanonicalSourceVersionId(source.getId());
        parseResult.setCreatedAt(LocalDateTime.now());
        parseResult.setUpdatedAt(LocalDateTime.now());
        resumeParseResultMapper.insert(parseResult);

        String triggerName = "trg_phase32_review_cas_" + resume.getId();
        String functionName = "phase32_review_cas_" + resume.getId();
        jdbcTemplate.execute("CREATE FUNCTION " + functionName + "() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN UPDATE resume_parse_results SET canonical_source_version_id = " + alternate.getId()
                + " WHERE resume_id = NEW.resume_id; RETURN NEW; END; $$");
        jdbcTemplate.execute("CREATE TRIGGER " + triggerName + " AFTER INSERT ON resume_versions "
                + "FOR EACH ROW WHEN (NEW.resume_id = " + resume.getId() + " AND NEW.version_type = 'SOURCE') "
                + "EXECUTE FUNCTION " + functionName + "()");
        try {
            assertThatThrownBy(() -> resumeReviewService.resolve(owner.getId(), resume.getId(),
                    ResumeReviewResolveRequestDTO.builder()
                            .itemId("review-cas-contact")
                            .action("ACCEPT")
                            .build()))
                    .isInstanceOfSatisfying(com.winter.airesumeoptimizer.common.exception.BusinessException.class,
                            exception -> assertThat(exception.getCode()).isEqualTo(409));
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + triggerName + " ON resume_versions");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + functionName + "()");
        }

        ResumeParseResult persisted = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resume.getId()));
        assertThat(persisted.getCanonicalSourceVersionId()).isEqualTo(source.getId());
        assertThat(resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getResumeId, resume.getId())
                .eq(ResumeVersion::getVersionType, "SOURCE"))).hasSize(2);
        assertThat(resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getResumeId, resume.getId())
                .eq(ResumeVersion::getVersionType, "SOURCE")))
                .extracting(ResumeVersion::getStructuredContent)
                .allMatch(documentJson::equals);
    }

    @Test
    void taskDeletionKeepsOnlyUnattributedLedgerMetadataForTheRetainedUser() {
        User owner = user("usage-deletion-owner");
        Resume resume = resume(owner.getId());
        seedFormalTask(owner.getId(), resume.getId(), 0);
        OptimizationTask task = optimizationTaskMapper.selectList(new LambdaQueryWrapper<OptimizationTask>()
                        .eq(OptimizationTask::getUserId, owner.getId()))
                .getFirst();

        AiUsageRecord record = new AiUsageRecord();
        record.setUserId(owner.getId());
        record.setOptimizationTaskId(task.getId());
        record.setOperation("EVIDENCE_MATCH");
        record.setSource("SYSTEM_DEFAULT");
        record.setProvider("OPENAI_COMPATIBLE");
        record.setModel("phase9-fake");
        record.setOutcome("SUCCESS");
        record.setLatencyMs(1L);
        record.setCreatedAt(LocalDateTime.now());
        usageRecordMapper.insert(record);

        optimizationTaskMapper.deleteById(task.getId());
        AiUsageRecord retained = usageRecordMapper.selectById(record.getId());
        assertThat(retained).isNotNull();
        assertThat(retained.getUserId()).isEqualTo(owner.getId());
        assertThat(retained.getOptimizationTaskId()).isNull();
    }

    @Test
    void providerAttemptSurvivesOuterBusinessRollbackAndRawLedgerRowsExpire() {
        User owner = user("usage-owner");
        AiSelectionSnapshot selection = new AiSelectionSnapshot(
                AiSource.SYSTEM_DEFAULT,
                AiSelectionSnapshot.OPENAI_COMPATIBLE,
                null,
                null,
                "https://phase9-e2e.invalid/v1",
                "phase9-fake",
                "{}",
                null);
        AiUsageRecord record = new AiUsageRecord();
        record.setUserId(owner.getId());
        record.setOperation("JOB_DESCRIPTION_PARSE");
        record.setSource("SYSTEM_DEFAULT");
        record.setProvider("OPENAI_COMPATIBLE");
        record.setModel("phase9-fake");
        record.setOutcome("SUCCESS");
        record.setLatencyMs(1L);
        record.setPromptTokens(1);
        record.setCompletionTokens(1);
        record.setTotalTokens(2);
        record.setCreatedAt(LocalDateTime.now());

        transactionTemplate.executeWithoutResult(status -> {
            usageRecordPersistence.persist(record);
            status.setRollbackOnly();
        });
        assertThat(usageRecordMapper.selectById(record.getId())).isNotNull();
        assertThat(usageRecordMapper.selectById(record.getId()).getProviderDispatchCount()).isEqualTo(1);

        AiUsageRecord expired = new AiUsageRecord();
        expired.setUserId(owner.getId());
        expired.setOperation("CREDENTIAL_TEST");
        expired.setSource("SYSTEM_DEFAULT");
        expired.setProvider("OPENAI_COMPATIBLE");
        expired.setModel("phase9-fake");
        expired.setOutcome("FAILURE");
        expired.setFailureCode("PROVIDER_UNAVAILABLE");
        expired.setLatencyMs(1L);
        expired.setCreatedAt(LocalDateTime.now().minusDays(91));
        usageRecordMapper.insert(expired);

        assertThat(usageRetentionService.purgeExpired()).isGreaterThanOrEqualTo(1);
        assertThat(usageRecordMapper.selectById(expired.getId())).isNull();
        assertThat(usageRecordMapper.selectById(record.getId())).isNotNull();
    }

    private void awaitJobDescriptionRowLock(Long jobDescriptionId) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            try {
                jdbcTemplate.queryForObject(
                        "SELECT id FROM job_descriptions WHERE id = ? FOR UPDATE NOWAIT",
                        Long.class,
                        jobDescriptionId);
            } catch (DataAccessException locked) {
                Throwable cause = locked.getMostSpecificCause();
                if (cause instanceof SQLException sqlException
                        && "55P03".equals(sqlException.getSQLState())) {
                    return;
                }
                throw locked;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("job description deletion did not acquire its lifecycle lock");
    }

    private void assertFutureBlocked(CompletableFuture<?> future) throws Exception {
        try {
            future.get(750, TimeUnit.MILLISECONDS);
            throw new AssertionError("parent deletion must wait for the submission transaction");
        } catch (TimeoutException expected) {
            // The parent row/task lock is held by the submission transaction.
        } catch (ExecutionException exception) {
            throw new AssertionError("parent deletion failed before the submission lock was released", exception);
        }
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("submission transaction did not receive the release signal");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("submission transaction was interrupted", exception);
        }
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username + "-" + System.nanoTime());
        user.setEmail(username + "-" + System.nanoTime() + "@example.invalid");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    private Resume resume(Long userId) {
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setOriginalFilename("integration-java.pdf");
        resume.setDisplayName("integration-java");
        resume.setFileType("PDF");
        resume.setFileSize(100L);
        resume.setObjectKey("resumes/" + userId + "/integration-java.pdf");
        resume.setStorageType("LOCAL");
        resume.setUploadStatus("UPLOADED");
        resume.setCreatedAt(LocalDateTime.now());
        resume.setUpdatedAt(LocalDateTime.now());
        resumeMapper.insert(resume);
        return resume;
    }

    private ResumeVersion reviewSource(Long userId, Long resumeId, String document) {
        ResumeVersion source = new ResumeVersion();
        source.setUserId(userId);
        source.setResumeId(resumeId);
        source.setVersionType("SOURCE");
        source.setSourceType("PARSED_UPLOAD");
        source.setContentStatus("PENDING");
        source.setStructuredContent(document);
        source.setContentRevision(0L);
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(LocalDateTime.now());
        resumeVersionMapper.insert(source);
        return source;
    }

    private void seedFormalTask(Long userId, Long resumeId, int index) {
        seedFormalTask(userId, resumeId, index, SNAPSHOT);
    }

    private void seedFormalTask(Long userId, Long resumeId, int index, String sourceSnapshot) {
        LocalDateTime completedAt = LocalDateTime.now().minusDays(index + 1);
        JobTarget target = new JobTarget();
        target.setUserId(userId);
        target.setTitle("Java 岗位 " + index);
        target.setRawJd("Java 后端岗位 " + index + "，要求熟悉 Java");
        target.setSourceType("USER_INPUT");
        target.setCreatedAt(completedAt);
        target.setUpdatedAt(completedAt);
        jobTargetMapper.insert(target);

        ResumeVersion source = new ResumeVersion();
        source.setUserId(userId);
        source.setResumeId(resumeId);
        source.setVersionType("SOURCE");
        source.setSourceType("PARSED_UPLOAD");
        source.setContentStatus("READY");
        source.setStructuredContent(sourceSnapshot);
        source.setContentRevision(0L);
        source.setCreatedAt(completedAt);
        source.setUpdatedAt(completedAt);
        resumeVersionMapper.insert(source);

        ResumeVersion targeted = new ResumeVersion();
        targeted.setUserId(userId);
        targeted.setResumeId(resumeId);
        targeted.setSourceVersionId(source.getId());
        targeted.setJobTargetId(target.getId());
        targeted.setVersionType("TARGETED");
        targeted.setSourceType("JOB_DERIVATION");
        targeted.setContentStatus("READY");
        targeted.setStructuredContent(SNAPSHOT);
        targeted.setContentRevision(0L);
        targeted.setCreatedAt(completedAt);
        targeted.setUpdatedAt(completedAt);
        resumeVersionMapper.insert(targeted);

        OptimizationTask task = new OptimizationTask();
        task.setUserId(userId);
        task.setSourceResumeVersionId(source.getId());
        task.setTargetResumeVersionId(targeted.getId());
        task.setJobTargetId(target.getId());
        task.setStatus("SUCCESS");
        task.setResumeInputSnapshot(SNAPSHOT);
        task.setJobInputSnapshot(target.getRawJd());
        task.setPromptSnapshot("{}");
        task.setRulesSnapshot("{}");
        task.setAiSourceSnapshot("SYSTEM_DEFAULT");
        task.setTemplateVersion("NOT_SELECTED");
        task.setCreatedAt(completedAt);
        task.setUpdatedAt(completedAt);
        task.setFinishedAt(completedAt);
        optimizationTaskMapper.insert(task);

        EvidenceAnalysis analysis = new EvidenceAnalysis();
        analysis.setUserId(userId);
        analysis.setOptimizationTaskId(task.getId());
        analysis.setMatchedCount(1);
        analysis.setPartialEvidenceCount(0);
        analysis.setNoEvidenceCount(0);
        analysis.setModelName("phase9-fake");
        analysis.setPromptVersion("phase9-e2e");
        analysis.setCreatedAt(completedAt);
        analysis.setUpdatedAt(completedAt);
        evidenceAnalysisMapper.insert(analysis);

        EvidenceRequirement requirement = new EvidenceRequirement();
        requirement.setUserId(userId);
        requirement.setEvidenceAnalysisId(analysis.getId());
        requirement.setRequirementText(index % 2 == 0 ? "Java" : "熟悉 Java");
        requirement.setImportance("REQUIRED");
        requirement.setMatchLevel("MATCHED");
        requirement.setConclusion("冻结材料中包含 Java。");
        requirement.setDisplayOrder(0);
        requirement.setCreatedAt(completedAt);
        evidenceRequirementMapper.insert(requirement);

        RequirementEvidence evidence = new RequirementEvidence();
        evidence.setUserId(userId);
        evidence.setEvidenceRequirementId(requirement.getId());
        evidence.setSourceResumeVersionId(source.getId());
        evidence.setSectionLabel("技能");
        evidence.setEvidenceText("Java");
        evidence.setSupportLevel("SUFFICIENT");
        evidence.setCreatedAt(completedAt);
        requirementEvidenceMapper.insert(evidence);
    }
}
