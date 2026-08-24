package com.winter.airesumeoptimizer.module.demo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.infra.ai.AiSource;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.infra.storage.StoreFileCommand;
import com.winter.airesumeoptimizer.infra.storage.StoredFile;
import com.winter.airesumeoptimizer.module.evidence.service.EvidenceMatchService;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionParseService;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import com.winter.airesumeoptimizer.module.optimization.entity.JobTarget;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.JobTargetMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import com.winter.airesumeoptimizer.module.user.entity.User;
import com.winter.airesumeoptimizer.module.user.mapper.UserMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Non-production-only seed. It creates a regular owned user and storage object,
 * then runs each synthetic task through the formal JD parse and Evidence seams.
 * It never creates a special account, role, endpoint, or authorization bypass.
 */
@Component
@Profile("demo")
@Order(100)
@ConditionalOnProperty(prefix = "app.demo", name = "enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    private static final int DEMO_TASK_COUNT = 8;
    private static final String DEMO_RESUME_SNAPSHOT = """
            {"name":"Demo Candidate","email":"demo@example.invalid",
            "rawText":"Demo Candidate\\nJava\\n负责 Java 后端服务开发\\n具备团队协作经验\\n熟悉 Spring Boot 与 Redis"}
            """;
    private static final byte[] SYNTHETIC_PDF = ("%PDF-1.4\n% synthetic demo resume only\n"
            + "1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n").getBytes(StandardCharsets.US_ASCII);
    private static final AiSelectionSnapshot DEMO_SELECTION = new AiSelectionSnapshot(
            AiSource.SYSTEM_DEFAULT,
            AiSelectionSnapshot.OPENAI_COMPATIBLE,
            null,
            null,
            "https://demo.invalid/v1",
            "demo-deterministic",
            "{}",
            null);

    private final UserMapper userMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeParseResultMapper resumeParseResultMapper;
    private final JobDescriptionMapper jobDescriptionMapper;
    private final JobTargetMapper jobTargetMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final OptimizationTaskMapper optimizationTaskMapper;
    private final JobDescriptionParseService jobDescriptionParseService;
    private final EvidenceMatchService evidenceMatchService;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final String username;
    private final String email;
    private final String password;

    public DemoDataInitializer(
            UserMapper userMapper,
            ResumeMapper resumeMapper,
            ResumeParseResultMapper resumeParseResultMapper,
            JobDescriptionMapper jobDescriptionMapper,
            JobTargetMapper jobTargetMapper,
            ResumeVersionMapper resumeVersionMapper,
            OptimizationTaskMapper optimizationTaskMapper,
            JobDescriptionParseService jobDescriptionParseService,
            EvidenceMatchService evidenceMatchService,
            FileStorageService fileStorageService,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate,
            @Value("${app.demo.user.username:demo-user}") String username,
            @Value("${app.demo.user.email:demo@example.invalid}") String email,
            @Value("${app.demo.user.password:}") String password) {
        this.userMapper = userMapper;
        this.resumeMapper = resumeMapper;
        this.resumeParseResultMapper = resumeParseResultMapper;
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.jobTargetMapper = jobTargetMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.optimizationTaskMapper = optimizationTaskMapper;
        this.jobDescriptionParseService = jobDescriptionParseService;
        this.evidenceMatchService = evidenceMatchService;
        this.fileStorageService = fileStorageService;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Demo 环境必须通过 DEMO_USER_PASSWORD 注入登录密码");
        }
        DemoSeedState seed = transactionTemplate.execute(status -> createOrVerifyBaseSeed());
        if (seed == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (int index = 0; index < DEMO_TASK_COUNT; index++) {
            int taskIndex = index;
            DemoTaskSeed task = transactionTemplate.execute(status -> createTaskRows(
                    seed.userId(),
                    seed.resumeId(),
                    taskIndex,
                    now.minusDays(taskIndex + 1)));
            if (task == null) {
                throw new IllegalStateException("Demo 优化任务创建失败");
            }
            completeFormalTask(task);
        }
        log.info("Demo seed created: username={}, resumeId={}, formalTasks={}",
                username,
                seed.resumeId(),
                DEMO_TASK_COUNT);
    }

    private DemoSeedState createOrVerifyBaseSeed() {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (existing != null) {
            Long completedTasks = optimizationTaskMapper.selectCount(new LambdaQueryWrapper<OptimizationTask>()
                    .eq(OptimizationTask::getUserId, existing.getId())
                    .eq(OptimizationTask::getStatus, "SUCCESS")
                    .isNull(OptimizationTask::getLegacyMatchResultId));
            if (completedTasks != null && completedTasks == DEMO_TASK_COUNT) {
                log.info("Demo seed already present: username={}", username);
                return null;
            }
            throw new IllegalStateException("Demo seed is incomplete; use the explicit demo reset command before restart");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setNickname("演示用户");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        requireInsert(userMapper.insert(user), "Demo 用户创建失败");

        StoredFile stored = fileStorageService.store(new StoreFileCommand(
                user.getId(),
                "synthetic-java-resume.pdf",
                "application/pdf",
                (long) SYNTHETIC_PDF.length,
                new ByteArrayInputStream(SYNTHETIC_PDF),
                "resumes"));
        Resume resume = new Resume();
        resume.setUserId(user.getId());
        resume.setOriginalFilename(stored.originalFilename());
        resume.setFileType("PDF");
        resume.setFileSize(stored.size());
        resume.setObjectKey(stored.storageKey());
        resume.setStorageType(stored.storageType());
        resume.setUploadStatus("UPLOADED");
        resume.setCreatedAt(now);
        resume.setUpdatedAt(now);
        requireInsert(resumeMapper.insert(resume), "Demo 简历创建失败");

        ResumeParseResult parseResult = new ResumeParseResult();
        parseResult.setResumeId(resume.getId());
        parseResult.setParseStatus("SUCCESS");
        parseResult.setExtractedText("Demo Candidate Java");
        parseResult.setCleanedText("Demo Candidate Java");
        parseResult.setStructuredJson(DEMO_RESUME_SNAPSHOT);
        parseResult.setCreatedAt(now);
        parseResult.setUpdatedAt(now);
        requireInsert(resumeParseResultMapper.insert(parseResult), "Demo 简历解析结果创建失败");
        return new DemoSeedState(user.getId(), resume.getId());
    }

    private DemoTaskSeed createTaskRows(Long userId, Long resumeId, int index, LocalDateTime completedAt) {
        String title = "Java 后端岗位示例 " + (index + 1);
        String rawJd = title + "：要求熟悉 Java，具备良好的团队协作能力。";
        JobDescription jobDescription = new JobDescription();
        jobDescription.setUserId(userId);
        jobDescription.setTitle(title);
        jobDescription.setSourceType("USER_INPUT");
        jobDescription.setRawText(rawJd);
        jobDescription.setParseStatus("PENDING");
        jobDescription.setCreatedAt(completedAt);
        jobDescription.setUpdatedAt(completedAt);
        requireInsert(jobDescriptionMapper.insert(jobDescription), "Demo JD 创建失败");

        JobTarget target = new JobTarget();
        target.setUserId(userId);
        target.setLegacyJobDescriptionId(jobDescription.getId());
        target.setTitle(title);
        target.setRawJd(rawJd);
        target.setSourceType("USER_INPUT");
        target.setCreatedAt(completedAt);
        target.setUpdatedAt(completedAt);
        requireInsert(jobTargetMapper.insert(target), "Demo 目标岗位创建失败");

        ResumeVersion source = new ResumeVersion();
        source.setUserId(userId);
        source.setResumeId(resumeId);
        source.setVersionType("SOURCE");
        source.setSourceType("PARSED_UPLOAD");
        source.setContentStatus("READY");
        source.setStructuredContent(DEMO_RESUME_SNAPSHOT);
        source.setContentRevision(0L);
        source.setCreatedAt(completedAt);
        source.setUpdatedAt(completedAt);
        requireInsert(resumeVersionMapper.insert(source), "Demo SOURCE 版本创建失败");

        ResumeVersion targeted = new ResumeVersion();
        targeted.setUserId(userId);
        targeted.setResumeId(resumeId);
        targeted.setSourceVersionId(source.getId());
        targeted.setJobTargetId(target.getId());
        targeted.setVersionType("TARGETED");
        targeted.setSourceType("JOB_DERIVATION");
        targeted.setContentStatus("READY");
        targeted.setStructuredContent(DEMO_RESUME_SNAPSHOT);
        targeted.setContentRevision(0L);
        targeted.setCreatedAt(completedAt);
        targeted.setUpdatedAt(completedAt);
        requireInsert(resumeVersionMapper.insert(targeted), "Demo TARGET 版本创建失败");

        OptimizationTask task = new OptimizationTask();
        task.setUserId(userId);
        task.setSourceResumeVersionId(source.getId());
        task.setTargetResumeVersionId(targeted.getId());
        task.setJobTargetId(target.getId());
        task.setStatus("RUNNING");
        task.setResumeInputSnapshot(DEMO_RESUME_SNAPSHOT);
        task.setJobInputSnapshot(rawJd);
        task.setPromptSnapshot("{}");
        task.setRulesSnapshot("{}");
        task.setProviderSnapshot(AiSelectionSnapshot.OPENAI_COMPATIBLE);
        task.setAiSourceSnapshot(AiSource.SYSTEM_DEFAULT.name());
        task.setAiProviderSnapshot(AiSelectionSnapshot.OPENAI_COMPATIBLE);
        task.setAiBaseUrlSnapshot(DEMO_SELECTION.baseUrl());
        task.setAiConfigSnapshot(DEMO_SELECTION.configJson());
        task.setModelSnapshot(DEMO_SELECTION.model());
        task.setTemplateVersion("NOT_SELECTED");
        task.setStartedAt(completedAt.minusMinutes(1));
        task.setCreatedAt(completedAt.minusMinutes(1));
        task.setUpdatedAt(completedAt);
        requireInsert(optimizationTaskMapper.insert(task), "Demo 优化任务创建失败");
        return new DemoTaskSeed(userId, task.getId(), jobDescription.getId());
    }

    private void completeFormalTask(DemoTaskSeed task) {
        // Demo tasks use the same parse → formal Evidence services as ordinary analysis;
        // the seed never inserts derived Evidence rows itself.
        JobDescriptionVO parsedJob = jobDescriptionParseService.parse(
                task.userId(),
                task.jobDescriptionId(),
                DEMO_SELECTION,
                task.optimizationTaskId());
        if (!"SUCCESS".equals(parsedJob.getParseStatus())) {
            throw new IllegalStateException("Demo JD 解析失败");
        }
        evidenceMatchService.analyze(task.userId(), task.optimizationTaskId(), parsedJob, DEMO_SELECTION);
    }

    private void requireInsert(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }

    private record DemoSeedState(Long userId, Long resumeId) {
    }

    private record DemoTaskSeed(Long userId, Long optimizationTaskId, Long jobDescriptionId) {
    }
}
