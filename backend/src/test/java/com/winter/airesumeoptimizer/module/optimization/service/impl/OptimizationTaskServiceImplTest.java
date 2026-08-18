package com.winter.airesumeoptimizer.module.optimization.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.evidence.entity.EvidenceAnalysis;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionService;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import com.winter.airesumeoptimizer.module.optimization.entity.JobTarget;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.JobTargetMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.optimization.vo.OptimizationTaskVO;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OptimizationTaskServiceImplTest {

    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final JobDescriptionService jobDescriptionService = mock(JobDescriptionService.class);
    private final JobDescriptionMapper jobDescriptionMapper = mock(JobDescriptionMapper.class);
    private final JobTargetMapper jobTargetMapper = mock(JobTargetMapper.class);
    private final ResumeVersionMapper resumeVersionMapper = mock(ResumeVersionMapper.class);
    private final OptimizationTaskMapper optimizationTaskMapper = mock(OptimizationTaskMapper.class);
    private final AiJobMatchResultMapper aiJobMatchResultMapper = mock(AiJobMatchResultMapper.class);
    private final OptimizationTaskServiceImpl service = new OptimizationTaskServiceImpl(
            resumeMapper,
            jobDescriptionService,
            jobDescriptionMapper,
            jobTargetMapper,
            resumeVersionMapper,
            optimizationTaskMapper,
            aiJobMatchResultMapper,
            new ObjectMapper());

    private Resume resume;
    private JobDescription jobDescription;

    @BeforeEach
    void setUp() {
        resume = new Resume();
        resume.setId(10L);
        resume.setUserId(1L);
        resume.setOriginalFilename("resume.pdf");
        jobDescription = new JobDescription();
        jobDescription.setId(20L);
        jobDescription.setUserId(1L);
        jobDescription.setTitle("Java 后端");
        jobDescription.setRawText("Java 后端\n负责 Spring Boot 服务开发");

        when(resumeMapper.selectOne(any())).thenReturn(resume);
        when(jobDescriptionService.submit(any(), any())).thenReturn(JobDescriptionVO.builder().id(20L).build());
        when(jobDescriptionMapper.selectOne(any())).thenReturn(jobDescription);
        when(jobTargetMapper.selectOne(any())).thenReturn(null);
        when(jobTargetMapper.insert(any(JobTarget.class))).thenAnswer(invocation -> {
            JobTarget target = invocation.getArgument(0);
            target.setId(30L);
            return 1;
        });
        AtomicLong versionIds = new AtomicLong(40L);
        when(resumeVersionMapper.insert(any(ResumeVersion.class))).thenAnswer(invocation -> {
            ResumeVersion version = invocation.getArgument(0);
            version.setId(versionIds.getAndIncrement());
            return 1;
        });
        when(optimizationTaskMapper.insert(any(OptimizationTask.class))).thenAnswer(invocation -> {
            OptimizationTask task = invocation.getArgument(0);
            task.setId(50L);
            return 1;
        });
    }

    @Test
    void createShouldDeriveTargetedVersionWithoutChangingSourceResume() {
        OptimizationTaskVO result = service.create(
                1L,
                10L,
                "Java 后端",
                "Java 后端\n负责 Spring Boot 服务开发",
                "SYSTEM_DEFAULT_OPENAI_COMPATIBLE",
                "test-model");

        assertThat(result.getOptimizationTaskId()).isEqualTo(50L);
        assertThat(result.getSourceResumeVersionId()).isEqualTo(40L);
        assertThat(result.getTargetResumeVersionId()).isEqualTo(41L);
        assertThat(result.getJobTargetId()).isEqualTo(30L);
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getResumeName()).isEqualTo("resume.pdf");

        ArgumentCaptor<ResumeVersion> versionCaptor = ArgumentCaptor.forClass(ResumeVersion.class);
        verify(resumeVersionMapper, org.mockito.Mockito.times(2)).insert(versionCaptor.capture());
        ResumeVersion source = versionCaptor.getAllValues().get(0);
        ResumeVersion targeted = versionCaptor.getAllValues().get(1);
        assertThat(source.getVersionType()).isEqualTo("SOURCE");
        assertThat(source.getSourceVersionId()).isNull();
        assertThat(targeted.getVersionType()).isEqualTo("TARGETED");
        assertThat(targeted.getSourceVersionId()).isEqualTo(source.getId());
        assertThat(targeted.getJobTargetId()).isEqualTo(30L);
        assertThat(targeted.getStructuredContent()).isNull();
        verify(resumeMapper, never()).updateById(any(Resume.class));
    }

    @Test
    void createFromExistingShouldMigrateLegacyInputsWithoutDuplicatingJobDescription() {
        OptimizationTaskVO result = service.createFromExisting(
                1L,
                10L,
                20L,
                "SYSTEM_DEFAULT_OPENAI_COMPATIBLE",
                "test-model");

        assertThat(result.getOptimizationTaskId()).isEqualTo(50L);
        assertThat(result.getJobTargetId()).isEqualTo(30L);
        verify(jobDescriptionService, never()).submit(any(), any());
    }

    @Test
    void createShouldRejectResumeNotOwnedByCurrentUser() {
        when(resumeMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.create(
                2L,
                10L,
                "Java 后端",
                "Java 后端\n负责 Spring Boot 服务开发",
                "SYSTEM_DEFAULT_OPENAI_COMPATIBLE",
                "test-model"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("简历不存在");

        verify(jobTargetMapper, never()).insert(any(JobTarget.class));
        verify(resumeVersionMapper, never()).insert(any(ResumeVersion.class));
        verify(optimizationTaskMapper, never()).insert(any(OptimizationTask.class));
    }

    @Test
    void attachAsyncTaskShouldRejectMutationOfCompletedHistory() {
        when(optimizationTaskMapper.selectOne(any())).thenReturn(task("SUCCESS"));

        assertThatThrownBy(() -> service.attachAsyncTask(1L, 50L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("已完成的优化任务不能重试");

        verify(optimizationTaskMapper, never()).update(any(), any());
    }

    @Test
    void captureResumeSnapshotShouldCopyImmutableInputIntoBothVersionsAndTask() {
        OptimizationTask task = task("RUNNING");
        when(optimizationTaskMapper.selectOne(any())).thenReturn(task);
        when(resumeVersionMapper.update(any(), any())).thenReturn(1);
        when(optimizationTaskMapper.update(any(), any())).thenReturn(1);

        service.captureResumeSnapshot(1L, 50L, "{\"skills\":[\"Java\"]}");

        verify(resumeVersionMapper, org.mockito.Mockito.times(2)).update(any(), any());
        verify(optimizationTaskMapper).update(any(), any());
    }

    @Test
    void markSuccessShouldRequireSnapshotAndPersistResultConfiguration() {
        OptimizationTask task = task("RUNNING");
        task.setResumeInputSnapshot("{\"skills\":[\"Java\"]}");
        when(optimizationTaskMapper.selectOne(any())).thenReturn(task);
        when(resumeVersionMapper.selectOne(any())).thenReturn(sourceVersion());
        when(jobTargetMapper.selectOne(any())).thenReturn(jobTarget());
        when(optimizationTaskMapper.update(any(), any())).thenReturn(1);
        when(jobTargetMapper.update(any(), any())).thenReturn(1);

        service.markSuccess(
                1L,
                50L,
                JobDescriptionVO.builder().title("高级 Java 工程师").promptVersion("job-v1").build(),
                evidenceAnalysis(50L, 1L));

        verify(optimizationTaskMapper).update(any(), any());
        verify(jobTargetMapper).update(any(), any());
    }

    @Test
    void markSuccessShouldRejectEvidenceAnalysisFromAnotherTask() {
        OptimizationTask task = task("RUNNING");
        task.setResumeInputSnapshot("{\"skills\":[\"Java\"]}");
        when(optimizationTaskMapper.selectOne(any())).thenReturn(task);

        assertThatThrownBy(() -> service.markSuccess(1L, 50L, null, evidenceAnalysis(999L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位证据分析不属于当前优化任务");

        verify(optimizationTaskMapper, never()).update(any(), any());
    }

    @Test
    void markSuccessShouldRejectEvidenceAnalysisOwnedByAnotherUser() {
        OptimizationTask task = task("RUNNING");
        task.setResumeInputSnapshot("{\"skills\":[\"Java\"]}");
        when(optimizationTaskMapper.selectOne(any())).thenReturn(task);

        assertThatThrownBy(() -> service.markSuccess(1L, 50L, null, evidenceAnalysis(50L, 2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位证据分析不属于当前优化任务");

        verify(optimizationTaskMapper, never()).update(any(), any());
    }

    @Test
    void markSuccessShouldFailWhenInputSnapshotWasNotCaptured() {
        when(optimizationTaskMapper.selectOne(any())).thenReturn(task("RUNNING"));

        assertThatThrownBy(() -> service.markSuccess(1L, 50L, null, evidenceAnalysis(50L, 1L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("优化任务缺少简历输入快照");

        verify(optimizationTaskMapper, never()).update(any(), any());
    }

    private EvidenceAnalysis evidenceAnalysis(Long optimizationTaskId, Long userId) {
        EvidenceAnalysis analysis = new EvidenceAnalysis();
        analysis.setId(60L);
        analysis.setUserId(userId);
        analysis.setOptimizationTaskId(optimizationTaskId);
        analysis.setMatchedCount(1);
        analysis.setExpressionGapCount(1);
        analysis.setNoEvidenceCount(1);
        analysis.setModelName("test-model");
        analysis.setPromptVersion("evidence_match_v1");
        return analysis;
    }

    private ResumeVersion sourceVersion() {
        ResumeVersion version = new ResumeVersion();
        version.setId(40L);
        version.setUserId(1L);
        version.setResumeId(10L);
        return version;
    }

    private JobTarget jobTarget() {
        JobTarget target = new JobTarget();
        target.setId(30L);
        target.setUserId(1L);
        target.setLegacyJobDescriptionId(20L);
        return target;
    }

    private OptimizationTask task(String status) {
        OptimizationTask task = new OptimizationTask();
        task.setId(50L);
        task.setUserId(1L);
        task.setSourceResumeVersionId(40L);
        task.setTargetResumeVersionId(41L);
        task.setJobTargetId(30L);
        task.setStatus(status);
        return task;
    }
}
