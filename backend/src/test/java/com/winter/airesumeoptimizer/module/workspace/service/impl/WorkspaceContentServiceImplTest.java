package com.winter.airesumeoptimizer.module.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.optimization.entity.JobTarget;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.JobTargetMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceContentSaveRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentSaveResultVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentVO;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkspaceContentServiceImplTest {

    static {
        // 纯单元测试没有 MyBatis 上下文，手动初始化 Lambda 列解析需要的表信息缓存。
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""), OptimizationTask.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""), ResumeVersion.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""), JobTarget.class);
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""), Resume.class);
    }

    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long TASK_ID = 50L;
    private static final Long RESUME_ID = 10L;
    private static final Long SOURCE_VERSION_ID = 40L;
    private static final Long TARGET_VERSION_ID = 41L;
    private static final Long JOB_TARGET_ID = 30L;

    /** 冻结的 V1 解析快照（displayModel 形态），也是任务输入快照。 */
    private static final String FROZEN_SNAPSHOT = """
            {
              "name": "张三",
              "phone": "13800000000",
              "displayModel": {
                "workExperienceCards": [
                  { "company": "某公司", "position": "Java 开发", "timeRange": "2020 - 至今",
                    "responsibilities": ["负责订单服务开发"] }
                ]
              }
            }
            """;

    private final ResumeVersionMapper resumeVersionMapper = mock(ResumeVersionMapper.class);
    private final OptimizationTaskMapper optimizationTaskMapper = mock(OptimizationTaskMapper.class);
    private final JobTargetMapper jobTargetMapper = mock(JobTargetMapper.class);
    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkspaceContentServiceImpl service = new WorkspaceContentServiceImpl(
            optimizationTaskMapper,
            resumeVersionMapper,
            jobTargetMapper,
            resumeMapper,
            new ResumeDocumentConverterImpl(objectMapper),
            objectMapper);

    private OptimizationTask task;
    private ResumeVersion sourceVersion;
    private ResumeVersion targetVersion;
    private JobTarget jobTarget;
    private Resume resume;

    /** 模拟数据库中的 TARGET revision 与内容，支持乐观并发语义。 */
    private final AtomicLong dbRevision = new AtomicLong(0L);
    private final AtomicReference<String> dbContent = new AtomicReference<>(FROZEN_SNAPSHOT);

    @BeforeEach
    void setUp() {
        task = new OptimizationTask();
        task.setId(TASK_ID);
        task.setUserId(USER_ID);
        task.setSourceResumeVersionId(SOURCE_VERSION_ID);
        task.setTargetResumeVersionId(TARGET_VERSION_ID);
        task.setJobTargetId(JOB_TARGET_ID);
        task.setStatus("SUCCESS");
        task.setResumeInputSnapshot(FROZEN_SNAPSHOT);

        sourceVersion = new ResumeVersion();
        sourceVersion.setId(SOURCE_VERSION_ID);
        sourceVersion.setUserId(USER_ID);
        sourceVersion.setResumeId(RESUME_ID);
        sourceVersion.setVersionType("SOURCE");
        sourceVersion.setSourceType("PARSED_UPLOAD");
        sourceVersion.setContentStatus("READY");
        sourceVersion.setStructuredContent(FROZEN_SNAPSHOT);
        sourceVersion.setContentRevision(0L);

        targetVersion = new ResumeVersion();
        targetVersion.setId(TARGET_VERSION_ID);
        targetVersion.setUserId(USER_ID);
        targetVersion.setResumeId(RESUME_ID);
        targetVersion.setSourceVersionId(SOURCE_VERSION_ID);
        targetVersion.setJobTargetId(JOB_TARGET_ID);
        targetVersion.setVersionType("TARGETED");
        targetVersion.setSourceType("JOB_DERIVATION");
        targetVersion.setContentStatus("READY");
        targetVersion.setStructuredContent(FROZEN_SNAPSHOT);
        targetVersion.setContentRevision(0L);

        jobTarget = new JobTarget();
        jobTarget.setId(JOB_TARGET_ID);
        jobTarget.setUserId(USER_ID);

        resume = new Resume();
        resume.setId(RESUME_ID);
        resume.setUserId(USER_ID);

        dbRevision.set(0L);
        dbContent.set(FROZEN_SNAPSHOT);

        when(optimizationTaskMapper.selectOne(any())).thenAnswer(invocation ->
                wrapperParamValues(invocation).contains(TASK_ID) ? task : null);
        when(optimizationTaskMapper.selectCount(any())).thenReturn(1L);
        when(jobTargetMapper.selectOne(any())).thenReturn(jobTarget);
        when(resumeMapper.selectOne(any())).thenReturn(resume);
        when(resumeVersionMapper.selectOne(any())).thenAnswer(invocation -> {
            Collection<Object> values = wrapperParamValues(invocation);
            if (values.contains(SOURCE_VERSION_ID)) {
                return sourceVersion;
            }
            if (values.contains(TARGET_VERSION_ID)) {
                targetVersion.setContentRevision(dbRevision.get());
                targetVersion.setStructuredContent(dbContent.get());
                return targetVersion;
            }
            return null;
        });
        // 模拟数据库的条件更新：仅当 expectedRevision 命中当前 revision 时写入并递增。
        when(resumeVersionMapper.update(isNull(), any(UpdateWrapper.class))).thenAnswer(invocation ->
                simulateConditionalUpdate(invocation.getArgument(1), TARGET_VERSION_ID, dbRevision, dbContent));
    }

    /**
     * 按 SQL 中实际的条件参数模拟条件更新，避免用参数值集合猜测 expectedRevision。
     */
    private int simulateConditionalUpdate(
            UpdateWrapper<?> wrapper,
            Long expectedTargetId,
            AtomicLong revision,
            AtomicReference<String> content) {
        Object targetId = whereParam(wrapper, "id");
        Object expected = whereParam(wrapper, "content_revision");
        if (!expectedTargetId.equals(targetId)
                || !(expected instanceof Long expectedRevision)
                || expectedRevision != revision.get()) {
            return 0;
        }
        revision.set(revision.get() + 1);
        Object serialized = setParam(wrapper, "structured_content");
        if (serialized instanceof String text) {
            content.set(text);
        }
        return 1;
    }

    private Object whereParam(UpdateWrapper<?> wrapper, String column) {
        return extractParam(wrapper.getSqlSegment(), wrapper.getParamNameValuePairs(), column);
    }

    private Object setParam(UpdateWrapper<?> wrapper, String column) {
        return extractParam(wrapper.getSqlSet(), wrapper.getParamNameValuePairs(), column);
    }

    private Object extractParam(String sql, java.util.Map<String, Object> params, String column) {
        if (sql == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile(java.util.regex.Pattern.quote(column) + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}")
                .matcher(sql);
        return matcher.find() ? params.get(matcher.group(1)) : null;
    }

    @Test
    void getContentShouldResolveTargetFromTaskAndConvertFrozenSnapshot() {
        WorkspaceContentVO result = service.getContent(USER_ID, TASK_ID);

        assertThat(result.getOptimizationTaskId()).isEqualTo(TASK_ID);
        assertThat(result.getRevision()).isZero();
        assertThat(result.getDocument().getBasics().getName()).isEqualTo("张三");
        assertThat(result.getDocument().getSections())
                .extracting(ResumeDocumentSectionDTO::getTitle)
                .containsExactly("工作经历");
    }

    @Test
    void getContentShouldReturnLastPersistedDocumentWhenRevisionPositive() throws Exception {
        ResumeDocumentDTO stored = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO.builder()
                        .contacts(List.of())
                        .build())
                .sections(List.of(ResumeDocumentSectionDTO.builder()
                        .id("s-1")
                        .kind("EXPERIENCE")
                        .title("工作经历")
                        .entries(List.of(ResumeDocumentEntryDTO.builder()
                                .id("s-1-e-1")
                                .heading("某公司 · Java 开发")
                                .bullets(List.of(ResumeDocumentBulletDTO.builder()
                                        .id("s-1-e-1-b-1")
                                        .text("用户已经改过的内容")
                                        .build()))
                                .build()))
                        .build()))
                .build();
        dbRevision.set(3L);
        dbContent.set(objectMapper.writeValueAsString(stored));

        WorkspaceContentVO result = service.getContent(USER_ID, TASK_ID);

        assertThat(result.getRevision()).isEqualTo(3L);
        assertThat(result.getDocument().getSections().get(0).getEntries().get(0).getBullets().get(0).getText())
                .isEqualTo("用户已经改过的内容");
    }

    @Test
    void getContentShouldRejectTaskThatHasNotFinishedAnalysis() {
        task.setStatus("RUNNING");

        assertThatThrownBy(() -> service.getContent(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("岗位分析尚未完成");
    }

    @Test
    void getContentShouldRejectUnknownOrCrossUserTask() {
        when(optimizationTaskMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getContent(OTHER_USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("优化任务不存在");
    }

    @Test
    void getContentShouldFailClosedWhenFrozenContentMissing() {
        task.setResumeInputSnapshot(null);
        sourceVersion.setStructuredContent(" ");

        assertThatThrownBy(() -> service.getContent(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历版本关系不一致");
    }

    @Test
    void getContentShouldFailClosedWhenFrozenSnapshotOrPristineTargetDiffersFromSource() {
        task.setResumeInputSnapshot("{\"different\":true}");
        assertThatThrownBy(() -> service.getContent(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("冻结简历内容不一致");

        task.setResumeInputSnapshot(FROZEN_SNAPSHOT);
        dbContent.set("{\"differentTarget\":true}");
        assertThatThrownBy(() -> service.getContent(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("冻结简历内容不一致");
    }

    @Test
    void getContentShouldRejectWhenTargetVersionNotOwned() {
        when(resumeVersionMapper.selectOne(any())).thenAnswer(invocation -> {
            Collection<Object> values = wrapperParamValues(invocation);
            return values.contains(SOURCE_VERSION_ID) ? sourceVersion : null;
        });

        assertThatThrownBy(() -> service.getContent(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历版本不存在");
    }

    @Test
    void getContentShouldRejectMissingOwnedJobTargetOrResume() {
        when(jobTargetMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.getContent(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标岗位不存在");

        when(jobTargetMapper.selectOne(any())).thenReturn(jobTarget);
        when(resumeMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.getContent(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历不存在");
    }

    @Test
    void getContentShouldFailClosedWhenTargetIsSharedByMultipleTasks() {
        when(optimizationTaskMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> service.getContent(USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多个优化任务引用");
    }

    @Test
    void saveShouldWriteNormalizedDocumentAndIncrementRevision() {
        WorkspaceContentSaveResultVO result = service.saveContent(
                USER_ID, TASK_ID, saveRequest(0L, editedDocument("第一次编辑")));

        assertThat(result.isSaved()).isTrue();
        assertThat(result.isConflict()).isFalse();
        assertThat(result.getRevision()).isEqualTo(1L);
        assertThat(result.getDocument()).usingRecursiveComparison()
                .isEqualTo(editedDocument("第一次编辑"));
        assertThat(dbRevision.get()).isEqualTo(1L);
        assertThat(dbContent.get()).contains("第一次编辑");
        // SOURCE 与任务快照不得被 Workspace 修改。
        assertThat(sourceVersion.getStructuredContent()).isEqualTo(FROZEN_SNAPSHOT);
        assertThat(sourceVersion.getContentRevision()).isZero();
        assertThat(task.getResumeInputSnapshot()).isEqualTo(FROZEN_SNAPSHOT);
    }

    @Test
    void saveShouldTargetOnlyTheTaskTargetVersion() {
        service.saveContent(USER_ID, TASK_ID, saveRequest(0L, editedDocument("定向写入")));

        ArgumentCaptor<UpdateWrapper<ResumeVersion>> captor = updateCaptor();
        Collection<Object> values = captor.getValue().getParamNameValuePairs().values();
        assertThat(values).contains(TARGET_VERSION_ID, SOURCE_VERSION_ID, JOB_TARGET_ID, USER_ID, 0L, 1L);
        assertThat(whereParam(captor.getValue(), "id")).isEqualTo(TARGET_VERSION_ID);
        assertThat(whereParam(captor.getValue(), "source_version_id")).isEqualTo(SOURCE_VERSION_ID);
        assertThat(captor.getValue().getSqlSegment()).contains("content_revision");
        assertThat(captor.getValue().getSqlSet()).contains("structured_content", "content_revision");
    }

    @Test
    void saveShouldReturnConflictAndKeepServerContentWhenRevisionStale() {
        // 另一个客户端已经保存到 revision 2。
        service.saveContent(USER_ID, TASK_ID, saveRequest(0L, editedDocument("另一端的编辑")));
        service.saveContent(USER_ID, TASK_ID, saveRequest(1L, editedDocument("另一端的第二次编辑")));

        WorkspaceContentSaveResultVO staleResult = service.saveContent(
                USER_ID, TASK_ID, saveRequest(1L, editedDocument("过期的草稿")));

        assertThat(staleResult.isSaved()).isFalse();
        assertThat(staleResult.isConflict()).isTrue();
        assertThat(staleResult.getRevision()).isEqualTo(2L);
        assertThat(dbRevision.get()).isEqualTo(2L);
        assertThat(dbContent.get()).contains("另一端的第二次编辑");
        assertThat(dbContent.get()).doesNotContain("过期的草稿");
    }

    @Test
    void saveShouldAllowOnlyOneWinnerWhenTwoClientsRaceOnSameRevision() {
        WorkspaceContentSaveResultVO firstTab = service.saveContent(
                USER_ID, TASK_ID, saveRequest(0L, editedDocument("标签页一")));
        WorkspaceContentSaveResultVO secondTab = service.saveContent(
                USER_ID, TASK_ID, saveRequest(0L, editedDocument("标签页二")));

        assertThat(firstTab.isSaved()).isTrue();
        assertThat(firstTab.getRevision()).isEqualTo(1L);
        assertThat(secondTab.isSaved()).isFalse();
        assertThat(secondTab.isConflict()).isTrue();
        assertThat(secondTab.getRevision()).isEqualTo(1L);
        assertThat(dbContent.get()).contains("标签页一");
        assertThat(dbContent.get()).doesNotContain("标签页二");
    }

    @Test
    void saveShouldRejectForgedVersionChain() {
        targetVersion.setSourceVersionId(999L);

        assertThatThrownBy(() -> service.saveContent(USER_ID, TASK_ID, saveRequest(0L, editedDocument("伪造链"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历版本关系不一致");
        assertThat(dbRevision.get()).isZero();
    }

    @Test
    void saveShouldRejectWhenTargetJobTargetDoesNotMatchTask() {
        targetVersion.setJobTargetId(31L);

        assertThatThrownBy(() -> service.saveContent(USER_ID, TASK_ID, saveRequest(0L, editedDocument("错岗位"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历版本关系不一致");
    }

    @Test
    void saveShouldRejectWhenTargetIsSourceVersion() {
        targetVersion.setVersionType("SOURCE");

        assertThatThrownBy(() -> service.saveContent(USER_ID, TASK_ID, saveRequest(0L, editedDocument("写源版本"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历版本关系不一致");
    }

    @Test
    void saveShouldRejectMissingOrInvalidExpectedRevisionAndDocument() {
        WorkspaceContentSaveRequestDTO missingRevision = new WorkspaceContentSaveRequestDTO();
        missingRevision.setDocument(editedDocument("缺少版本号"));
        assertThatThrownBy(() -> service.saveContent(USER_ID, TASK_ID, missingRevision))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少内容版本号");

        WorkspaceContentSaveRequestDTO negativeRevision = saveRequest(-1L, editedDocument("错误版本号"));
        assertThatThrownBy(() -> service.saveContent(USER_ID, TASK_ID, negativeRevision))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容版本号不正确");

        WorkspaceContentSaveRequestDTO overflowRevision = saveRequest(Long.MAX_VALUE, editedDocument("溢出版本号"));
        assertThatThrownBy(() -> service.saveContent(USER_ID, TASK_ID, overflowRevision))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容版本号不正确");

        WorkspaceContentSaveRequestDTO missingDocument = new WorkspaceContentSaveRequestDTO();
        missingDocument.setExpectedRevision(0L);
        assertThatThrownBy(() -> service.saveContent(USER_ID, TASK_ID, missingDocument))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("简历内容不能为空");
    }

    @Test
    void restoreShouldRegenerateFrozenSnapshotAsNewRevisionWithoutTouchingSource() {
        service.saveContent(USER_ID, TASK_ID, saveRequest(0L, editedDocument("用户编辑过的内容")));
        assertThat(dbContent.get()).contains("用户编辑过的内容");

        WorkspaceContentSaveResultVO result = service.restorePreOptimizationContent(USER_ID, TASK_ID, 1L);

        assertThat(result.isSaved()).isTrue();
        assertThat(result.getRevision()).isEqualTo(2L);
        assertThat(dbContent.get()).doesNotContain("用户编辑过的内容");

        // 恢复结果必须与首次按快照生成的文档完全一致（确定性转换）。
        ResumeDocumentDTO restored = service.getContent(USER_ID, TASK_ID).getDocument();
        assertThat(restored).usingRecursiveComparison()
                .isEqualTo(new ResumeDocumentConverterImpl(objectMapper).fromParsedSnapshot(FROZEN_SNAPSHOT));

        // SOURCE / 快照 / 证据侧数据保持不变。
        assertThat(sourceVersion.getStructuredContent()).isEqualTo(FROZEN_SNAPSHOT);
        assertThat(sourceVersion.getContentRevision()).isZero();
        assertThat(task.getResumeInputSnapshot()).isEqualTo(FROZEN_SNAPSHOT);
    }

    @Test
    void restoreShouldReturnConflictWhenRevisionStale() {
        service.saveContent(USER_ID, TASK_ID, saveRequest(0L, editedDocument("并发编辑")));

        WorkspaceContentSaveResultVO result = service.restorePreOptimizationContent(USER_ID, TASK_ID, 0L);

        assertThat(result.isSaved()).isFalse();
        assertThat(result.isConflict()).isTrue();
        assertThat(result.getRevision()).isEqualTo(1L);
        assertThat(dbContent.get()).contains("并发编辑");
    }

    @Test
    void twoTasksOnSameResumeShouldHaveIndependentTargets() {
        Long secondTaskId = 60L;
        Long secondTargetVersionId = 42L;
        OptimizationTask secondTask = new OptimizationTask();
        secondTask.setId(secondTaskId);
        secondTask.setUserId(USER_ID);
        secondTask.setSourceResumeVersionId(SOURCE_VERSION_ID);
        secondTask.setTargetResumeVersionId(secondTargetVersionId);
        secondTask.setJobTargetId(JOB_TARGET_ID);
        secondTask.setStatus("SUCCESS");
        secondTask.setResumeInputSnapshot(FROZEN_SNAPSHOT);

        ResumeVersion secondTarget = new ResumeVersion();
        secondTarget.setId(secondTargetVersionId);
        secondTarget.setUserId(USER_ID);
        secondTarget.setResumeId(RESUME_ID);
        secondTarget.setSourceVersionId(SOURCE_VERSION_ID);
        secondTarget.setJobTargetId(JOB_TARGET_ID);
        secondTarget.setVersionType("TARGETED");
        secondTarget.setSourceType("JOB_DERIVATION");
        secondTarget.setContentStatus("READY");
        secondTarget.setStructuredContent(FROZEN_SNAPSHOT);
        secondTarget.setContentRevision(0L);

        when(optimizationTaskMapper.selectOne(any())).thenAnswer(invocation -> {
            Collection<Object> values = wrapperParamValues(invocation);
            if (values.contains(TASK_ID)) {
                return task;
            }
            return values.contains(secondTaskId) ? secondTask : null;
        });
        AtomicLong secondDbRevision = new AtomicLong(0L);
        AtomicReference<String> secondDbContent = new AtomicReference<>(FROZEN_SNAPSHOT);
        when(resumeVersionMapper.selectOne(any())).thenAnswer(invocation -> {
            Collection<Object> values = wrapperParamValues(invocation);
            if (values.contains(SOURCE_VERSION_ID)) {
                return sourceVersion;
            }
            if (values.contains(TARGET_VERSION_ID)) {
                targetVersion.setContentRevision(dbRevision.get());
                targetVersion.setStructuredContent(dbContent.get());
                return targetVersion;
            }
            if (values.contains(secondTargetVersionId)) {
                secondTarget.setContentRevision(secondDbRevision.get());
                secondTarget.setStructuredContent(secondDbContent.get());
                return secondTarget;
            }
            return null;
        });
        when(resumeVersionMapper.update(isNull(), any(UpdateWrapper.class))).thenAnswer(invocation -> {
            UpdateWrapper<?> wrapper = invocation.getArgument(1);
            Object targetId = whereParam(wrapper, "id");
            if (secondTargetVersionId.equals(targetId)) {
                return simulateConditionalUpdate(wrapper, secondTargetVersionId, secondDbRevision, secondDbContent);
            }
            return simulateConditionalUpdate(wrapper, TARGET_VERSION_ID, dbRevision, dbContent);
        });

        // 编辑任务 A 的 TARGET。
        WorkspaceContentSaveResultVO taskAResult = service.saveContent(
                USER_ID, TASK_ID, saveRequest(0L, editedDocument("任务A的编辑")));

        assertThat(taskAResult.isSaved()).isTrue();
        assertThat(dbContent.get()).contains("任务A的编辑");
        // 任务 B 的 TARGET 与 SOURCE 不受影响。
        assertThat(secondDbRevision.get()).isZero();
        assertThat(secondDbContent.get()).isEqualTo(FROZEN_SNAPSHOT);
        assertThat(sourceVersion.getStructuredContent()).isEqualTo(FROZEN_SNAPSHOT);

        // 任务 B 仍可基于自己的 revision 0 保存。
        WorkspaceContentSaveResultVO taskBResult = service.saveContent(
                USER_ID, secondTaskId, saveRequest(0L, editedDocument("任务B的编辑")));
        assertThat(taskBResult.isSaved()).isTrue();
        assertThat(secondDbContent.get()).contains("任务B的编辑");
        assertThat(dbContent.get()).contains("任务A的编辑");
        assertThat(dbContent.get()).doesNotContain("任务B的编辑");
    }

    private WorkspaceContentSaveRequestDTO saveRequest(long expectedRevision, ResumeDocumentDTO document) {
        WorkspaceContentSaveRequestDTO request = new WorkspaceContentSaveRequestDTO();
        request.setExpectedRevision(expectedRevision);
        request.setDocument(document);
        return request;
    }

    private ResumeDocumentDTO editedDocument(String bulletText) {
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .basics(com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO.builder()
                        .contacts(List.of())
                        .build())
                .sections(List.of(ResumeDocumentSectionDTO.builder()
                        .id("s-1")
                        .kind("EXPERIENCE")
                        .title("工作经历")
                        .entries(List.of(ResumeDocumentEntryDTO.builder()
                                .id("s-1-e-1")
                                .heading("某公司 · Java 开发")
                                .bullets(List.of(ResumeDocumentBulletDTO.builder()
                                        .id("s-1-e-1-b-1")
                                        .text(bulletText)
                                        .build()))
                                .build()))
                        .build()))
                .build();
    }

    private Collection<Object> wrapperParamValues(org.mockito.invocation.InvocationOnMock invocation) {
        // MyBatis-Plus 的单参 selectOne 是 default 方法，Mockito 下会委托到两参重载，
        // 打桩注册期间传入的 wrapper 可能为 null；参数占位符也要先构建 SQL 才会填充。
        Object argument = invocation.getArgument(0);
        if (!(argument instanceof AbstractWrapper<?, ?, ?> wrapper)) {
            return List.of();
        }
        wrapper.getSqlSegment();
        return wrapper.getParamNameValuePairs().values();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<UpdateWrapper<ResumeVersion>> updateCaptor() {
        ArgumentCaptor<UpdateWrapper<ResumeVersion>> captor = ArgumentCaptor.forClass(UpdateWrapper.class);
        org.mockito.Mockito.verify(resumeVersionMapper).update(isNull(), captor.capture());
        return captor;
    }
}
