package com.winter.airesumeoptimizer.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeReviewResolveRequestDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeUnresolvedItemDTO;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import com.winter.airesumeoptimizer.module.resume.service.ResumeReviewService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeReviewVO;
import com.winter.airesumeoptimizer.module.user.entity.User;
import com.winter.airesumeoptimizer.module.user.mapper.UserMapper;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * PostgreSQL transaction validation for immutable canonical SOURCE replacement.
 * Calls cross the real Spring service proxy so each worker owns an independent transaction.
 */
@SpringBootTest
@ActiveProfiles("phase9-e2e")
class ResumeReviewPostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ResumeMapper resumeMapper;
    @Autowired
    private ResumeParseResultMapper resumeParseResultMapper;
    @Autowired
    private ResumeVersionMapper resumeVersionMapper;
    @Autowired
    private ResumeReviewService resumeReviewService;

    @Test
    void concurrentReplacementHasExactlyOneCasWinnerAndNoLosingOrphanSource() throws Exception {
        ReviewFixture fixture = reviewFixture(List.of(
                ResumeUnresolvedItemDTO.builder()
                        .id("concurrent-contact")
                        .kind(ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE)
                        .canonicalDraft("{\"type\":\"EMAIL\",\"label\":\"邮箱\",\"value\":\"initial@example.com\"}")
                        .reason("请确认联系方式")
                        .build()));
        int advisoryKey = 1_000_000_000 + Math.toIntExact(fixture.resumeId() % 100_000_000L);
        String functionName = "resume_review_race_" + fixture.resumeId();
        String triggerName = "trg_resume_review_race_" + fixture.resumeId();
        jdbcTemplate.execute("CREATE FUNCTION " + functionName + "() RETURNS trigger LANGUAGE plpgsql AS $$ "
                + "BEGIN PERFORM pg_advisory_xact_lock(" + advisoryKey + "); RETURN NEW; END; $$");
        jdbcTemplate.execute("CREATE TRIGGER " + triggerName + " BEFORE INSERT ON resume_versions "
                + "FOR EACH ROW WHEN (NEW.resume_id = " + fixture.resumeId()
                + " AND NEW.version_type = 'SOURCE') EXECUTE FUNCTION " + functionName + "()");

        ExecutorService workers = Executors.newFixedThreadPool(2);
        CompletableFuture<ReviewAttempt> first = null;
        CompletableFuture<ReviewAttempt> second = null;
        try {
            CountDownLatch start = new CountDownLatch(1);
            try (Connection gate = dataSource.getConnection()) {
                gate.createStatement().execute("SELECT pg_advisory_lock(" + advisoryKey + ")");
                first = CompletableFuture.supplyAsync(() -> {
                    await(start);
                    return resolveContact(fixture, "winner-a@example.com");
                }, workers);
                second = CompletableFuture.supplyAsync(() -> {
                    await(start);
                    return resolveContact(fixture, "winner-b@example.com");
                }, workers);
                start.countDown();

                // Reaching this PostgreSQL gate proves both independent transactions read the
                // original pointer/sidecar before either replacement can commit.
                try {
                    awaitAdvisoryWaiters(advisoryKey, 2);
                } finally {
                    gate.createStatement().execute("SELECT pg_advisory_unlock(" + advisoryKey + ")");
                }
            }

            ReviewAttempt firstResult = first.get(5, TimeUnit.SECONDS);
            ReviewAttempt secondResult = second.get(5, TimeUnit.SECONDS);
            List<ReviewAttempt> successes = List.of(firstResult, secondResult).stream()
                    .filter(attempt -> attempt.review() != null)
                    .toList();
            List<ReviewAttempt> failures = List.of(firstResult, secondResult).stream()
                    .filter(attempt -> attempt.failure() != null)
                    .toList();

            assertThat(successes).hasSize(1);
            assertThat(failures).hasSize(1);
            assertThat(failures.getFirst().failure().getCode()).isEqualTo(409);

            ResumeParseResult persisted = parseResult(fixture.resumeId());
            ResumeVersion latest = resumeVersionMapper.selectById(persisted.getCanonicalSourceVersionId());
            assertThat(persisted.getCanonicalSourceVersionId()).isNotEqualTo(fixture.initialSourceId());
            assertThat(latest.getStructuredContent())
                    .contains(successes.getFirst().contactValue())
                    .doesNotContain(failures.getFirst().contactValue());
            assertThat(sources(fixture.resumeId()))
                    .hasSize(2)
                    .extracting(ResumeVersion::getStructuredContent)
                    .noneMatch(content -> content.contains(failures.getFirst().contactValue()));
        } finally {
            if (first != null && !first.isDone()) {
                first.cancel(true);
            }
            if (second != null && !second.isDone()) {
                second.cancel(true);
            }
            workers.shutdownNow();
            workers.awaitTermination(5, TimeUnit.SECONDS);
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + triggerName + " ON resume_versions");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + functionName + "()");
        }
    }

    @Test
    void repeatedReplacementsPreservePriorSnapshotsAndMovePointerToLatest() throws Exception {
        ReviewFixture fixture = reviewFixture(List.of(
                ResumeUnresolvedItemDTO.builder()
                        .id("review-contact")
                        .kind(ResumeUnresolvedItemDTO.KIND_CONTACT_CANDIDATE)
                        .canonicalDraft("{\"type\":\"EMAIL\",\"label\":\"邮箱\",\"value\":\"review@example.com\"}")
                        .reason("请确认联系方式")
                        .build(),
                ResumeUnresolvedItemDTO.builder()
                        .id("review-fragment")
                        .kind(ResumeUnresolvedItemDTO.KIND_TEXT_FRAGMENT)
                        .canonicalDraft("{\"text\":\"第二次确认保留的内容\"}")
                        .reason("请确认游离内容")
                        .build()));

        ResumeReviewVO firstReview = resumeReviewService.resolve(fixture.userId(), fixture.resumeId(),
                ResumeReviewResolveRequestDTO.builder()
                        .itemId("review-contact")
                        .action("ACCEPT")
                        .build());
        Long firstReplacementId = parseResult(fixture.resumeId()).getCanonicalSourceVersionId();
        ResumeVersion firstReplacement = resumeVersionMapper.selectById(firstReplacementId);

        ResumeReviewVO secondReview = resumeReviewService.resolve(fixture.userId(), fixture.resumeId(),
                ResumeReviewResolveRequestDTO.builder()
                        .itemId("review-fragment")
                        .action("ACCEPT")
                        .build());

        ResumeParseResult persisted = parseResult(fixture.resumeId());
        ResumeVersion initial = resumeVersionMapper.selectById(fixture.initialSourceId());
        ResumeVersion latest = resumeVersionMapper.selectById(persisted.getCanonicalSourceVersionId());
        assertThat(firstReview.getQualityStatus()).isEqualTo("NEEDS_REVIEW");
        assertThat(secondReview.getQualityStatus()).isEqualTo("READY");
        assertThat(persisted.getCanonicalSourceVersionId())
                .isNotIn(fixture.initialSourceId(), firstReplacementId);
        assertThat(initial.getStructuredContent())
                .contains("最初的来源内容")
                .doesNotContain("review@example.com", "第二次确认保留的内容");
        assertThat(firstReplacement.getContentStatus()).isEqualTo("PENDING");
        assertThat(firstReplacement.getStructuredContent())
                .contains("review@example.com")
                .doesNotContain("第二次确认保留的内容");
        assertThat(latest.getContentStatus()).isEqualTo("READY");
        assertThat(latest.getStructuredContent())
                .contains("最初的来源内容", "review@example.com", "第二次确认保留的内容");
        assertThat(secondReview.getCanonicalDocument()).isEqualTo(latest.getStructuredContent());
        assertThat(sources(fixture.resumeId())).hasSize(3);
    }

    private ReviewFixture reviewFixture(List<ResumeUnresolvedItemDTO> unresolvedItems) throws Exception {
        User user = new User();
        String unique = Long.toUnsignedString(System.nanoTime(), 36);
        user.setUsername("resume-review-" + unique);
        user.setEmail("resume-review-" + unique + "@example.invalid");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        Resume resume = new Resume();
        resume.setUserId(user.getId());
        resume.setOriginalFilename("review.pdf");
        resume.setDisplayName("review");
        resume.setFileType("PDF");
        resume.setFileSize(100L);
        resume.setObjectKey("resumes/" + user.getId() + "/review.pdf");
        resume.setStorageType("LOCAL");
        resume.setUploadStatus("UPLOADED");
        resume.setCreatedAt(LocalDateTime.now());
        resume.setUpdatedAt(LocalDateTime.now());
        resumeMapper.insert(resume);

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
                                                                .text("最初的来源内容")
                                                                .build())))
                                                .build())))
                                .build())))
                .build();
        ResumeVersion source = new ResumeVersion();
        source.setUserId(user.getId());
        source.setResumeId(resume.getId());
        source.setVersionType("SOURCE");
        source.setSourceType("PARSED_UPLOAD");
        source.setContentStatus("PENDING");
        source.setStructuredContent(objectMapper.writeValueAsString(document));
        source.setContentRevision(0L);
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(LocalDateTime.now());
        resumeVersionMapper.insert(source);

        ResumeParseResult parseResult = new ResumeParseResult();
        parseResult.setResumeId(resume.getId());
        parseResult.setUserId(user.getId());
        parseResult.setParseStatus("SUCCESS");
        parseResult.setQualityStatus("NEEDS_REVIEW");
        parseResult.setUnresolvedItems(objectMapper.writeValueAsString(unresolvedItems));
        parseResult.setQualityIssues("[]");
        parseResult.setCanonicalSourceVersionId(source.getId());
        parseResult.setCreatedAt(LocalDateTime.now());
        parseResult.setUpdatedAt(LocalDateTime.now());
        resumeParseResultMapper.insert(parseResult);
        return new ReviewFixture(user.getId(), resume.getId(), source.getId());
    }

    private ReviewAttempt resolveContact(ReviewFixture fixture, String contactValue) {
        try {
            ResumeReviewVO review = resumeReviewService.resolve(fixture.userId(), fixture.resumeId(),
                    ResumeReviewResolveRequestDTO.builder()
                            .itemId("concurrent-contact")
                            .action("ACCEPT")
                            .contactValue(contactValue)
                            .build());
            return new ReviewAttempt(contactValue, review, null);
        } catch (BusinessException exception) {
            return new ReviewAttempt(contactValue, null, exception);
        }
    }

    private ResumeParseResult parseResult(Long resumeId) {
        return resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resumeId));
    }

    private List<ResumeVersion> sources(Long resumeId) {
        return resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getResumeId, resumeId)
                .eq(ResumeVersion::getVersionType, "SOURCE")
                .orderByAsc(ResumeVersion::getId));
    }

    private void awaitAdvisoryWaiters(int advisoryKey, int expectedWaiters) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            Integer waiters = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM pg_locks
                    WHERE locktype = 'advisory'
                      AND classid = 0
                      AND objid = CAST(? AS oid)
                      AND objsubid = 1
                      AND NOT granted
                    """, Integer.class, advisoryKey);
            if (waiters != null && waiters >= expectedWaiters) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("review transactions did not independently reach the PostgreSQL gate");
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("review workers did not receive their start signal");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("review worker was interrupted", exception);
        }
    }

    private record ReviewFixture(Long userId, Long resumeId, Long initialSourceId) {
    }

    private record ReviewAttempt(String contactValue, ResumeReviewVO review, BusinessException failure) {
    }
}
