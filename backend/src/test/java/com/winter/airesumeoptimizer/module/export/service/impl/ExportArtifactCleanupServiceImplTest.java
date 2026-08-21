package com.winter.airesumeoptimizer.module.export.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.module.export.entity.ExportArtifact;
import com.winter.airesumeoptimizer.module.export.mapper.ExportArtifactMapper;
import com.winter.airesumeoptimizer.module.optimization.entity.JobTarget;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.JobTargetMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** 删除状态必须持久化，失败可由同一 artifact 重试；父删除不得绕过对象清理。 */
@ExtendWith(MockitoExtension.class)
class ExportArtifactCleanupServiceImplTest {

    static {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeVersion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), JobTarget.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), OptimizationTask.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ExportArtifact.class);
    }

    @Mock private ResumeVersionMapper resumeVersionMapper;
    @Mock private JobTargetMapper jobTargetMapper;
    @Mock private OptimizationTaskMapper optimizationTaskMapper;
    @Mock private ExportArtifactMapper exportArtifactMapper;
    @Mock private FileStorageService fileStorageService;

    private ExportArtifactCleanupServiceImpl service;

    private static final Long USER_ID = 1L;
    private static final Long RESUME_ID = 100L;

    @BeforeEach
    void setUp() {
        service = new ExportArtifactCleanupServiceImpl(
                resumeVersionMapper,
                jobTargetMapper,
                optimizationTaskMapper,
                exportArtifactMapper,
                fileStorageService,
                new NoOpTransactionManager());
    }

    @Test
    void deleteArtifactsForResumeMarksPendingDeletesStorageThenRecord() {
        givenResumeArtifacts();
        when(exportArtifactMapper.update(isNull(), any())).thenReturn(1);
        when(exportArtifactMapper.delete(any())).thenReturn(1);

        service.deleteArtifactsForResume(USER_ID, RESUME_ID);

        verify(exportArtifactMapper, times(2)).update(isNull(), any());
        verify(fileStorageService).delete("exports/1/a.pdf");
        verify(fileStorageService).delete("exports/1/b.pdf");
        verify(exportArtifactMapper, times(2)).delete(any());
    }

    @Test
    void storageFailureKeepsDeletePendingMetadataAndCanBeRetried() {
        ExportArtifact artifact = artifact(30L, 20L, "exports/1/a.pdf");
        when(exportArtifactMapper.selectOne(any())).thenReturn(artifact);
        when(exportArtifactMapper.update(isNull(), any())).thenReturn(1);
        when(exportArtifactMapper.delete(any())).thenReturn(1);
        doThrow(new FileStorageException("storage unavailable"))
                .doNothing()
                .when(fileStorageService).delete("exports/1/a.pdf");

        assertThatThrownBy(() -> service.deleteArtifact(USER_ID, 30L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(500));
        assertThat(artifact.getStatus()).isEqualTo("DELETE_PENDING");
        verify(exportArtifactMapper, never()).delete(any());

        service.deleteArtifact(USER_ID, 30L);

        verify(fileStorageService, times(2)).delete("exports/1/a.pdf");
        verify(exportArtifactMapper).delete(any());
    }

    @Test
    void jobDescriptionCleanupTraversesJobTargetAndTaskBeforeParentCascade() {
        JobTarget target = new JobTarget();
        target.setId(11L);
        OptimizationTask task = task(20L, 10L);
        when(jobTargetMapper.selectList(any())).thenReturn(List.of(target));
        when(optimizationTaskMapper.selectList(any())).thenReturn(List.of(task));
        when(exportArtifactMapper.selectList(any())).thenReturn(List.of(
                artifact(30L, 20L, "exports/1/a.pdf")));
        when(exportArtifactMapper.update(isNull(), any())).thenReturn(1);
        when(exportArtifactMapper.delete(any())).thenReturn(1);

        service.deleteArtifactsForJobDescription(USER_ID, 200L);

        verify(fileStorageService).delete("exports/1/a.pdf");
        verify(exportArtifactMapper).delete(any());
    }

    @Test
    void deleteArtifactsForResumeIsNoOpWithoutVersions() {
        when(resumeVersionMapper.selectList(any())).thenReturn(List.of());

        service.deleteArtifactsForResume(USER_ID, RESUME_ID);

        verify(exportArtifactMapper, never()).selectList(any());
        verify(fileStorageService, never()).delete(any());
    }

    @Test
    void foreignArtifactCannotBeDeleted() {
        when(exportArtifactMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.deleteArtifact(USER_ID, 999L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(404));
        verify(fileStorageService, never()).delete(any());
    }

    private void givenResumeArtifacts() {
        when(resumeVersionMapper.selectList(any())).thenReturn(List.of(version(10L)));
        when(optimizationTaskMapper.selectList(any())).thenReturn(List.of(task(20L, 10L)));
        when(exportArtifactMapper.selectList(any())).thenReturn(List.of(
                artifact(30L, 20L, "exports/1/a.pdf"),
                artifact(31L, 20L, "exports/1/b.pdf")));
    }

    private ResumeVersion version(long id) {
        ResumeVersion version = new ResumeVersion();
        version.setId(id);
        version.setUserId(USER_ID);
        version.setResumeId(RESUME_ID);
        return version;
    }

    private OptimizationTask task(long id, long targetVersionId) {
        OptimizationTask task = new OptimizationTask();
        task.setId(id);
        task.setUserId(USER_ID);
        task.setTargetResumeVersionId(targetVersionId);
        return task;
    }

    private ExportArtifact artifact(long id, long taskId, String storageKey) {
        ExportArtifact artifact = new ExportArtifact();
        artifact.setId(id);
        artifact.setUserId(USER_ID);
        artifact.setOptimizationTaskId(taskId);
        artifact.setStorageKey(storageKey);
        artifact.setStatus("READY");
        return artifact;
    }

    private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
