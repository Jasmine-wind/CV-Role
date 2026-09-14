package com.winter.airesumeoptimizer.module.workspace.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.module.optimization.entity.JobTarget;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.JobTargetMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.service.impl.ResumeCanonicalDocumentServiceImpl;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceContentSaveRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceSourceOmissionRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentSaveResultVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentVO;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    /** 冻结的 V1 解析快照（structuredData 形态），也是任务输入快照。 */
    private static final String FROZEN_SNAPSHOT = """
            {
              "name": "张三",
              "phone": "13800000000",
              "structuredData": {
                "experiences": [
                  { "type": "WORK", "organization": "某公司", "role": "Java 开发",
                    "startDate": "2020", "endDate": "至今",
                    "description": "负责订单服务开发", "bullets": ["负责订单服务开发"] }
                ]
              }
            }
            """;

    private final ResumeVersionMapper resumeVersionMapper = mock(ResumeVersionMapper.class);
    private final OptimizationTaskMapper optimizationTaskMapper = mock(OptimizationTaskMapper.class);
    private final JobTargetMapper jobTargetMapper = mock(JobTargetMapper.class);
    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkspaceContentServiceImpl service = new WorkspaceContentServiceImpl(
            optimizationTaskMapper,
            resumeVersionMapper,
            jobTargetMapper,
            resumeMapper,
            new ResumeDocumentConverterImpl(objectMapper),
            new ResumeCanonicalDocumentServiceImpl(objectMapper),
            new WorkspaceSourceReferenceAssemblerImpl(),
            fileStorageService,
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
    void getContentShouldReadCanonicalV1SnapshotAtPristineRevision() throws Exception {
        String canonical = "{\"schemaVersion\":\"RESUME_DOCUMENT_V1\",\"basics\":{\"name\":\"张三\",\"contacts\":[]},\"sections\":[{\"id\":\"s-1\",\"kind\":\"EXPERIENCE\",\"title\":\"工作经历\",\"entries\":[{\"id\":\"e-1\",\"organization\":\"某公司\",\"role\":\"Java 开发\",\"school\":null,\"degree\":null,\"major\":null,\"startDate\":\"2020\",\"endDate\":\"至今\",\"location\":null,\"group\":null,\"skillItems\":null,\"bullets\":[]}]}]}";
        task.setResumeInputSnapshot(canonical);
        sourceVersion.setStructuredContent(canonical);
        targetVersion.setStructuredContent(canonical);
        dbContent.set(canonical);

        WorkspaceContentVO result = service.getContent(USER_ID, TASK_ID);

        assertThat(result.getDocument().getSchemaVersion()).isEqualTo(ResumeDocumentDTO.SCHEMA_VERSION);
        assertThat(result.getDocument().getSections().get(0).getEntries().get(0).getOrganization())
                .isEqualTo("某公司");
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
                                .organization("某公司")
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
    void getContentShouldReadLegacyGenericV1TargetWithoutMutatingIt() {
        String legacy = "{\"schemaVersion\":\"RESUME_DOCUMENT_V1\",\"basics\":{\"name\":\"张三\",\"contacts\":[{\"id\":\"c-1\",\"label\":\"邮箱\",\"value\":\"zhang@example.com\"}]},\"sections\":[{\"id\":\"s-1\",\"kind\":\"EXPERIENCE\",\"title\":\"工作经历\",\"entries\":[{\"id\":\"e-1\",\"heading\":\"某公司\",\"meta\":\"2020 - 至今\",\"bullets\":[{\"id\":\"b-1\",\"text\":\"负责服务开发\"}]}]}]}";
        dbRevision.set(3L);
        dbContent.set(legacy);

        WorkspaceContentVO result = service.getContent(USER_ID, TASK_ID);

        assertThat(result.getDocument().getSchemaVersion()).isEqualTo(ResumeDocumentDTO.SCHEMA_VERSION);
        assertThat(result.getDocument().getSections().get(0).getEntries().get(0).getOrganization())
                .isEqualTo("某公司");
        assertThat(result.getDocument().getSections().get(0).getEntries().get(0).getStartDate())
                .isEqualTo("2020");
    }

    @Test
    void getPersistedContentForRenderShouldRejectPristineSnapshotProjection() {
        assertThatThrownBy(() -> service.getPersistedContentForRender(USER_ID, TASK_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(409);
                    assertThat(exception.getMessage()).contains("保存");
                });
    }

    @Test
    void getPersistedContentForRenderShouldReadOnlySavedTargetDocument() throws Exception {
        ResumeDocumentDTO stored = editedDocument("已 CAS 保存的内容");
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(stored));

        WorkspaceContentVO result = service.getPersistedContentForRender(USER_ID, TASK_ID);

        assertThat(result.getRevision()).isEqualTo(1L);
        assertThat(result.getDocument()).usingRecursiveComparison().isEqualTo(stored);
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
    void sourceReferenceShouldUseTaskBoundVersionsAndFailClosedWithoutLegacyManifest() {
        resume.setOriginalFilename("candidate.pdf");
        resume.setFileType("PDF");

        var result = service.getSourceReference(USER_ID, TASK_ID);

        assertThat(result.optimizationTaskId()).isEqualTo(TASK_ID);
        assertThat(result.sourceResumeVersionId()).isEqualTo(SOURCE_VERSION_ID);
        assertThat(result.targetResumeVersionId()).isEqualTo(TARGET_VERSION_ID);
        assertThat(result.sourceFilename()).isEqualTo("candidate.pdf");
        assertThat(result.sourcePdfAvailable()).isTrue();
        assertThat(result.exportBlocked()).isTrue();
        assertThat(result.fidelityIssues()).extracting(issue -> issue.code())
                .contains("SOURCE_MANIFEST_UNAVAILABLE");
    }

    @Test
    void sourcePdfShouldAuthorizeThroughTaskAndHideStorageIdentity() {
        resume.setOriginalFilename("candidate.pdf");
        resume.setFileType("pdf");
        resume.setObjectKey("private/user-1/source.pdf");
        byte[] pdf = "%PDF-synthetic".getBytes(StandardCharsets.US_ASCII);
        when(fileStorageService.loadAsBytes("private/user-1/source.pdf")).thenReturn(pdf);

        var result = service.getSourcePdf(USER_ID, TASK_ID);

        assertThat(result.filename()).isEqualTo("candidate.pdf");
        assertThat(result.bytes()).isEqualTo(pdf);
        verify(fileStorageService).loadAsBytes("private/user-1/source.pdf");
    }

    @Test
    void sourceEndpointsShouldRejectCrossUserTaskBeforeStorageAccess() {
        when(optimizationTaskMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getSourceReference(OTHER_USER_ID, TASK_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("优化任务不存在");
        assertThatThrownBy(() -> service.getSourcePdf(OTHER_USER_ID, TASK_ID))
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
    void saveShouldReplaceForgedProvenanceWithTheFrozenServerManifest() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        targetVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        dbContent.set(frozenJson);

        ResumeDocumentDTO submitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        submitted.setSourceOccurrenceIds(List.of("forged-root"));
        submitted.setSourceOccurrenceTexts(Map.of("forged-root", "伪造原文"));
        ResumeDocumentBulletDTO bullet = submitted.getSections().get(0).getEntries().get(0).getBullets().get(0);
        bullet.setText("编辑后的职责");
        bullet.setSourceOccurrenceIds(List.of("forged-child"));
        bullet.setSourceRef(ResumeSourceRefDTO.builder().text("伪造原文")
                .sourceOccurrenceIds(List.of("forged-child")).build());

        WorkspaceContentSaveResultVO result = service.saveContent(
                USER_ID, TASK_ID, saveRequest(0L, submitted));

        assertThat(result.getDocument().getSourceOccurrenceIds())
                .containsExactly("occ-section", "occ-entry", "occ-bullet");
        ResumeDocumentBulletDTO saved = result.getDocument().getSections().get(0)
                .getEntries().get(0).getBullets().get(0);
        assertThat(saved.getText()).isEqualTo("编辑后的职责");
        assertThat(saved.getSourceOccurrenceIds()).containsExactly("occ-bullet");
        assertThat(dbContent.get()).doesNotContain("forged-root", "forged-child", "伪造原文");
    }

    @Test
    void ordinarySaveShouldIgnoreClientOmissionClaimsAndKeepOnlyStillUnmappedServerConfirmedIds() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);

        ResumeDocumentDTO current = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        current.getSections().get(0).getEntries().get(0).setBullets(List.of());
        current.setConfirmedSourceOmissionIds(List.of("occ-bullet"));
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(current));

        ResumeDocumentDTO submitted = objectMapper.readValue(dbContent.get(), ResumeDocumentDTO.class);
        submitted.setConfirmedSourceOmissionIds(List.of("occ-entry", "forged-occurrence"));
        WorkspaceContentSaveResultVO result = service.saveContent(
                USER_ID, TASK_ID, saveRequest(1L, submitted));

        assertThat(result.getDocument().getConfirmedSourceOmissionIds()).containsExactly("occ-bullet");
        assertThat(dbContent.get()).contains("occ-bullet").doesNotContain("forged-occurrence");
    }

    @Test
    void ordinarySaveDropsMalformedConfirmationsInsteadOfNormalizingThemIntoAuthority() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO current = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        current.getSections().get(0).getEntries().get(0).setBullets(List.of());
        current.setConfirmedSourceOmissionIds(List.of("occ-bullet", "occ-bullet"));
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(current));

        WorkspaceContentSaveResultVO duplicate = service.saveContent(
                USER_ID, TASK_ID, saveRequest(1L, current));
        assertThat(duplicate.getDocument().getConfirmedSourceOmissionIds()).isEmpty();

        ResumeDocumentDTO whitespace = objectMapper.readValue(dbContent.get(), ResumeDocumentDTO.class);
        whitespace.setConfirmedSourceOmissionIds(List.of(" occ-bullet"));
        dbContent.set(objectMapper.writeValueAsString(whitespace));
        WorkspaceContentSaveResultVO trimmed = service.saveContent(
                USER_ID, TASK_ID, saveRequest(2L, whitespace));
        assertThat(trimmed.getDocument().getConfirmedSourceOmissionIds()).isEmpty();
    }

    @Test
    void confirmAndUnconfirmIntentionalOmissionUseCasAndBringBlockerBack() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO omitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        omitted.getSections().get(0).getEntries().get(0).setBullets(List.of());
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(omitted));

        WorkspaceContentSaveResultVO confirmed = service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("occ-bullet")));

        assertThat(confirmed.isSaved()).isTrue();
        assertThat(confirmed.getRevision()).isEqualTo(2L);
        assertThat(confirmed.getDocument().getConfirmedSourceOmissionIds()).containsExactly("occ-bullet");
        assertThat(service.getSourceReference(USER_ID, TASK_ID).exportBlocked()).isFalse();

        WorkspaceContentSaveResultVO unconfirmed = service.unconfirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(2L, List.of("occ-bullet")));

        assertThat(unconfirmed.isSaved()).isTrue();
        assertThat(unconfirmed.getRevision()).isEqualTo(3L);
        assertThat(unconfirmed.getDocument().getConfirmedSourceOmissionIds()).isEmpty();
        assertThat(service.getSourceReference(USER_ID, TASK_ID).fidelityIssues())
                .extracting(issue -> issue.code()).contains("SOURCE_CONTENT_UNMAPPED");
    }

    @Test
    void confirmAndUnconfirmExpandOneRequestedAliasToTheWholeLogicalOccurrence() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        frozen.setSourceOccurrenceIds(List.of(
                "occ-section", "occ-entry", "occ-bullet", "occ-bullet-alias"));
        frozen.setSourceOccurrenceTexts(Map.of(
                "occ-section", "工作经历",
                "occ-entry", "某公司 Java 开发",
                "occ-bullet", "负责订单服务开发",
                "occ-bullet-alias", "负责订单服务开发"));
        frozen.setSourceOccurrencePrimaryIds(Map.of(
                "occ-section", "occ-section",
                "occ-entry", "occ-entry",
                "occ-bullet", "occ-bullet",
                "occ-bullet-alias", "occ-bullet"));
        ResumeDocumentBulletDTO frozenBullet = frozen.getSections().get(0).getEntries().get(0).getBullets().get(0);
        frozenBullet.setSourceOccurrenceIds(List.of("occ-bullet", "occ-bullet-alias"));
        frozenBullet.getSourceRef().setSourceOccurrenceIds(List.of("occ-bullet", "occ-bullet-alias"));
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO omitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        omitted.getSections().get(0).getEntries().get(0).setBullets(List.of());
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(omitted));

        WorkspaceContentSaveResultVO confirmed = service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(
                        1L, List.of("occ-bullet-alias")));
        assertThat(confirmed.getDocument().getConfirmedSourceOmissionIds())
                .containsExactly("occ-bullet", "occ-bullet-alias");
        assertThat(service.getSourceReference(USER_ID, TASK_ID).exportBlocked()).isFalse();

        WorkspaceContentSaveResultVO unconfirmed = service.unconfirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(2L, List.of("occ-bullet")));
        assertThat(unconfirmed.getDocument().getConfirmedSourceOmissionIds()).isEmpty();
        assertThat(service.getSourceReference(USER_ID, TASK_ID).exportBlocked()).isTrue();
    }

    @Test
    void projectOmissionExpandsOneRequestedOccurrenceToTheWholeFrozenProvenanceBoundary() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        frozen.getSections().get(0).setKind("PROJECT");
        List<String> order = new java.util.ArrayList<>(List.of(
                "occ-section", "occ-entry", "occ-field", "occ-tech",
                "occ-skill", "occ-description", "occ-bullet"));
        Map<String, String> texts = new LinkedHashMap<>(Map.of(
                "occ-section", "工作经历",
                "occ-entry", "某公司 Java 开发",
                "occ-field", "上海",
                "occ-tech", "Spring Boot",
                "occ-skill", "容量规划",
                "occ-description", "保障高峰稳定性",
                "occ-bullet", "负责订单服务开发"));
        Map<String, String> primaryIds = new LinkedHashMap<>();
        order.forEach(id -> primaryIds.put(id, id));
        frozen.setSourceOccurrenceIds(order);
        frozen.setSourceOccurrenceTexts(texts);
        frozen.setSourceOccurrencePrimaryIds(primaryIds);
        ResumeDocumentEntryDTO project = frozen.getSections().get(0).getEntries().get(0);
        project.setLocation("上海");
        project.setFieldSourceRefs(Map.of("location", ResumeSourceRefDTO.builder()
                .text("上海").sourceOccurrenceIds(List.of("occ-field")).build()));
        project.setTechStack(List.of("Spring Boot"));
        project.setTechStackSourceRefs(List.of(ResumeSourceRefDTO.builder()
                .text("Spring Boot").sourceOccurrenceIds(List.of("occ-tech")).build()));
        project.setSkillItems(List.of("容量规划"));
        project.setSkillItemSourceRefs(List.of(ResumeSourceRefDTO.builder()
                .text("容量规划").sourceOccurrenceIds(List.of("occ-skill")).build()));
        project.setSkillDescriptions(List.of("保障高峰稳定性"));
        project.setSkillDescriptionSourceRefs(List.of(ResumeSourceRefDTO.builder()
                .text("保障高峰稳定性").sourceOccurrenceIds(List.of("occ-description")).build()));
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO omitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        omitted.getSections().get(0).setEntries(List.of());
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(omitted));

        WorkspaceContentSaveResultVO confirmed = service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("occ-bullet")));

        assertThat(confirmed.getDocument().getConfirmedSourceOmissionIds())
                .containsExactly("occ-entry", "occ-field", "occ-tech",
                        "occ-skill", "occ-description", "occ-bullet");
        assertThat(service.getSourceReference(USER_ID, TASK_ID).fidelityIssues())
                .extracting(issue -> issue.code()).doesNotContain("PROJECT_BOUNDARY_LOST");

        WorkspaceContentSaveResultVO unconfirmed = service.unconfirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(2L, List.of("occ-entry")));
        assertThat(unconfirmed.getDocument().getConfirmedSourceOmissionIds()).isEmpty();
        assertThat(service.getSourceReference(USER_ID, TASK_ID).fidelityIssues())
                .extracting(issue -> issue.code()).contains("PROJECT_BOUNDARY_LOST");
    }

    @Test
    void projectOmissionRejectsADeletedChildWhenTheWholeFrozenEntryIsNotOmitted() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        frozen.getSections().get(0).setKind("PROJECT");
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO partiallyOmitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        partiallyOmitted.getSections().get(0).getEntries().get(0).setBullets(List.of());
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(partiallyOmitted));

        assertThatThrownBy(() -> service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("occ-bullet"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).contains("边界未完整省略");
                });
        assertThat(dbRevision.get()).isEqualTo(1L);
    }

    @Test
    void confirmRejectsUnknownAmbiguousAndDuplicateOccurrenceIdsWithoutMutation() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO omitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        omitted.getSections().get(0).getEntries().get(0).setBullets(List.of());
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(omitted));

        assertThatThrownBy(() -> service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("unknown"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("occ-bullet", "occ-bullet"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getCode()).isEqualTo(400));

        ResumeDocumentBulletDTO ambiguous = ResumeDocumentBulletDTO.builder()
                .id("new-bullet").text("错误归属").sourceOccurrenceIds(List.of("occ-bullet")).build();
        omitted.getSections().get(0).getEntries().get(0).setBullets(List.of(ambiguous));
        dbContent.set(objectMapper.writeValueAsString(omitted));
        assertThatThrownBy(() -> service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("occ-bullet"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getCode()).isEqualTo(400));
        assertThat(dbRevision.get()).isEqualTo(1L);
    }

    @Test
    void unconfirmRepairsMalformedPartialAliasStateAndDropsAllUnrelatedStaleIds() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocumentWithBullets("EXPERIENCE", 2, true);
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO omitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        omitted.getSections().get(0).getEntries().get(0).setBullets(List.of());
        omitted.setConfirmedSourceOmissionIds(List.of(
                "occ-bullet-alias", "occ-bullet-alias", "occ-entry", "unknown-stale"));
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(omitted));
        assertThat(service.getSourceReference(USER_ID, TASK_ID).fidelityIssues())
                .extracting(issue -> issue.code()).contains("CONFIRMED_OMISSION_INVALID");

        WorkspaceContentSaveResultVO repaired = service.unconfirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(
                        1L, List.of("occ-bullet")));

        assertThat(repaired.isSaved()).isTrue();
        assertThat(repaired.getRevision()).isEqualTo(2L);
        assertThat(repaired.getDocument().getConfirmedSourceOmissionIds()).isEmpty();
        assertThat(service.getSourceReference(USER_ID, TASK_ID).fidelityIssues())
                .extracting(issue -> issue.code())
                .contains("SOURCE_CONTENT_UNMAPPED")
                .doesNotContain("CONFIRMED_OMISSION_INVALID");
        verify(resumeVersionMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void unconfirmCanRemoveAnOrphanOnlyTaskLocalStaleValueWithoutGrantingItAuthority() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO current = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        current.setConfirmedSourceOmissionIds(List.of("unknown-stale"));
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(current));

        WorkspaceContentSaveResultVO result = service.unconfirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(
                        1L, List.of("unknown-stale")));

        assertThat(result.isSaved()).isTrue();
        assertThat(result.getDocument().getConfirmedSourceOmissionIds()).isEmpty();
        assertThat(dbRevision.get()).isEqualTo(2L);
        verify(resumeVersionMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void unconfirmRepairsARequestedKnownConfirmationThatBecameMappedAndIneligible() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO stale = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        stale.setConfirmedSourceOmissionIds(List.of("occ-bullet", "unknown-stale"));
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(stale));

        WorkspaceContentSaveResultVO repaired = service.unconfirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("occ-bullet")));

        assertThat(repaired.isSaved()).isTrue();
        assertThat(repaired.getDocument().getConfirmedSourceOmissionIds()).isEmpty();
        assertThat(service.getSourceReference(USER_ID, TASK_ID).exportBlocked()).isFalse();
        verify(resumeVersionMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void confirmCanonicalizesExistingStateWithoutPromotingAnUnrelatedPartialAlias() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocumentWithBullets("EXPERIENCE", 4, true);
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO omitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        ResumeDocumentEntryDTO entry = omitted.getSections().get(0).getEntries().get(0);
        ResumeDocumentBulletDTO ambiguous = ResumeDocumentBulletDTO.builder()
                .id("new-bullet").text("错误归属")
                .sourceOccurrenceIds(List.of("occ-bullet-2")).build();
        entry.setBullets(List.of(ambiguous));
        omitted.setConfirmedSourceOmissionIds(List.of(
                "occ-section",
                "occ-bullet",
                "occ-bullet-2", "occ-bullet-2-alias", "occ-bullet-2",
                "occ-bullet-3", "occ-bullet-3-alias",
                "unknown-stale"));
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(omitted));

        WorkspaceContentSaveResultVO confirmed = service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(
                        1L, List.of("occ-bullet-4-alias")));

        assertThat(confirmed.getDocument().getConfirmedSourceOmissionIds()).containsExactly(
                "occ-bullet-3", "occ-bullet-3-alias",
                "occ-bullet-4", "occ-bullet-4-alias");
        verify(resumeVersionMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void confirmValidatesEveryRequestedBlockBeforeTheSingleCasWrite() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocumentWithBullets("EXPERIENCE", 3, false);
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO mixed = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        ResumeDocumentEntryDTO entry = mixed.getSections().get(0).getEntries().get(0);
        ResumeDocumentBulletDTO ambiguous = ResumeDocumentBulletDTO.builder()
                .id("new-bullet").text("错误归属")
                .sourceOccurrenceIds(List.of("occ-bullet-2")).build();
        entry.setBullets(List.of(ambiguous, entry.getBullets().get(2)));
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(mixed));

        assertThatThrownBy(() -> service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(
                        1L, List.of("occ-bullet", "unknown"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(
                        1L, List.of("occ-bullet", "occ-bullet-2"))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(
                        1L, List.of("occ-bullet", "occ-bullet-3"))))
                .isInstanceOf(BusinessException.class);

        assertThat(dbRevision.get()).isEqualTo(1L);
        verify(resumeVersionMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void unconfirmValidatesTheWholeBatchBeforeAnyCasWrite() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocumentWithBullets("EXPERIENCE", 2, true);
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO omitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        omitted.getSections().get(0).getEntries().get(0).setBullets(List.of());
        omitted.setConfirmedSourceOmissionIds(List.of(
                "occ-bullet", "occ-bullet-alias",
                "occ-bullet-2", "occ-bullet-2-alias"));
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(omitted));

        assertThatThrownBy(() -> service.unconfirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(
                        1L, List.of("occ-bullet", "unknown-not-persisted"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于当前冻结简历");

        assertThat(dbRevision.get()).isEqualTo(1L);
        assertThat(objectMapper.readValue(dbContent.get(), ResumeDocumentDTO.class)
                .getConfirmedSourceOmissionIds()).containsExactly(
                        "occ-bullet", "occ-bullet-alias",
                        "occ-bullet-2", "occ-bullet-2-alias");
        verify(resumeVersionMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void confirmOfMultipleValidAliasBlocksUsesExactlyOneCas() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocumentWithBullets("EXPERIENCE", 2, true);
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO omitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        omitted.getSections().get(0).getEntries().get(0).setBullets(List.of());
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(omitted));

        WorkspaceContentSaveResultVO confirmed = service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(
                        1L, List.of("occ-bullet-alias", "occ-bullet-2")));

        assertThat(confirmed.getDocument().getConfirmedSourceOmissionIds()).containsExactly(
                "occ-bullet", "occ-bullet-alias", "occ-bullet-2", "occ-bullet-2-alias");
        assertThat(confirmed.getRevision()).isEqualTo(2L);
        verify(resumeVersionMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void unconfirmOfMultipleBlocksUsesOneCasAndRestoresEverySourceBlocker() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocumentWithBullets("EXPERIENCE", 2, true);
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO omitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        omitted.getSections().get(0).getEntries().get(0).setBullets(List.of());
        omitted.setConfirmedSourceOmissionIds(List.of(
                "occ-bullet", "occ-bullet-alias", "occ-bullet-2", "occ-bullet-2-alias"));
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(omitted));

        WorkspaceContentSaveResultVO unconfirmed = service.unconfirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(
                        1L, List.of("occ-bullet-alias", "occ-bullet-2")));

        assertThat(unconfirmed.getDocument().getConfirmedSourceOmissionIds()).isEmpty();
        assertThat(service.getSourceReference(USER_ID, TASK_ID).fidelityIssues())
                .filteredOn(issue -> "SOURCE_CONTENT_UNMAPPED".equals(issue.code()))
                .hasSize(2);
        verify(resumeVersionMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void partialProjectUnconfirmExpandsToTheWholeEntryAndRestoresBoundaryBlocker() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocumentWithBullets("PROJECT", 2, true);
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO omitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        omitted.getSections().get(0).setEntries(List.of());
        // Persisted legacy state is only a partial Project boundary. The requested physical alias is
        // a different block in that same server-owned entry, so unconfirm must still repair the whole unit.
        omitted.setConfirmedSourceOmissionIds(List.of("occ-entry"));
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(omitted));

        WorkspaceContentSaveResultVO unconfirmed = service.unconfirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(
                        1L, List.of("occ-bullet-2-alias")));

        assertThat(unconfirmed.getDocument().getConfirmedSourceOmissionIds()).isEmpty();
        assertThat(service.getSourceReference(USER_ID, TASK_ID).fidelityIssues())
                .extracting(issue -> issue.code())
                .contains("PROJECT_BOUNDARY_LOST", "SOURCE_CONTENT_UNMAPPED");
        verify(resumeVersionMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void omissionMutationRejectsStaleRevisionAndSameRevisionRaceHasOneCasWinner() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO omitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        omitted.getSections().get(0).getEntries().get(0).setBullets(List.of());
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(omitted));

        WorkspaceContentSaveResultVO stale = service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(0L, List.of("occ-bullet")));
        assertThat(stale.isConflict()).isTrue();
        assertThat(stale.getRevision()).isEqualTo(1L);
        assertThat(objectMapper.readValue(dbContent.get(), ResumeDocumentDTO.class)
                .getConfirmedSourceOmissionIds()).isNullOrEmpty();

        WorkspaceContentSaveResultVO winner = service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("occ-bullet")));
        WorkspaceContentSaveResultVO loser = service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("occ-bullet")));
        assertThat(winner.isSaved()).isTrue();
        assertThat(winner.getRevision()).isEqualTo(2L);
        assertThat(loser.isConflict()).isTrue();
        assertThat(loser.getRevision()).isEqualTo(2L);
        assertThat(dbRevision.get()).isEqualTo(2L);
    }

    @Test
    void concurrentOmissionRequestsOnTheSameRevisionHaveExactlyOneCasWinner() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO omitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        omitted.getSections().get(0).getEntries().get(0).setBullets(List.of());
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(omitted));

        CountDownLatch bothAtCas = new CountDownLatch(2);
        when(resumeVersionMapper.update(isNull(), any(UpdateWrapper.class))).thenAnswer(invocation -> {
            bothAtCas.countDown();
            if (!bothAtCas.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("both omission requests did not reach the CAS boundary");
            }
            synchronized (dbRevision) {
                return simulateConditionalUpdate(
                        invocation.getArgument(1), TARGET_VERSION_ID, dbRevision, dbContent);
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<WorkspaceContentSaveResultVO> first = executor.submit(() -> service.confirmSourceOmissions(
                    USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("occ-bullet"))));
            Future<WorkspaceContentSaveResultVO> second = executor.submit(() -> service.confirmSourceOmissions(
                    USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("occ-bullet"))));

            List<WorkspaceContentSaveResultVO> results = List.of(
                    first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results).filteredOn(WorkspaceContentSaveResultVO::isSaved).hasSize(1);
            assertThat(results).filteredOn(WorkspaceContentSaveResultVO::isConflict).hasSize(1);
            assertThat(dbRevision.get()).isEqualTo(2L);
            assertThat(objectMapper.readValue(dbContent.get(), ResumeDocumentDTO.class)
                    .getConfirmedSourceOmissionIds()).containsExactly("occ-bullet");
            verify(resumeVersionMapper, times(2)).update(isNull(), any(UpdateWrapper.class));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void confirmFailsClosedWhenFrozenManifestIsInvalid() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        frozen.setSourceOccurrenceIds(List.of("occ-section", "occ-entry", "occ-bullet", "occ-bullet"));
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO omitted = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        omitted.getSections().get(0).getEntries().get(0).setBullets(List.of());
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(omitted));

        assertThatThrownBy(() -> service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("occ-bullet"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> assertThat(exception.getCode()).isEqualTo(400));
        assertThat(dbRevision.get()).isEqualTo(1L);
        assertThat(objectMapper.readValue(dbContent.get(), ResumeDocumentDTO.class)
                .getConfirmedSourceOmissionIds()).isNullOrEmpty();
    }

    @Test
    void ordinarySaveRejectsResurrectionOfDeletedFrozenNodeIdAcrossRealRevisions() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        targetVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        dbContent.set(frozenJson);

        ResumeDocumentDTO deletion = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        deletion.getSections().get(0).getEntries().get(0).setBullets(List.of());
        WorkspaceContentSaveResultVO deleted = service.saveContent(
                USER_ID, TASK_ID, saveRequest(0L, deletion));
        assertThat(deleted.isSaved()).isTrue();
        assertThat(deleted.getRevision()).isEqualTo(1L);

        ResumeDocumentDTO recreated = objectMapper.readValue(dbContent.get(), ResumeDocumentDTO.class);
        recreated.getSections().get(0).getEntries().get(0).setBullets(List.of(
                ResumeDocumentBulletDTO.builder()
                        .id("s-1-e-1-b-1").text("伪造重建的来源节点").build()));

        assertThatThrownBy(() -> service.saveContent(
                USER_ID, TASK_ID, saveRequest(1L, recreated)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(400));
        assertThat(dbRevision.get()).isEqualTo(1L);
        assertThat(dbContent.get()).doesNotContain("伪造重建的来源节点", "s-1-e-1-b-1");
        verify(resumeVersionMapper, times(1)).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void ordinarySaveClearsStaleConfirmationWhenServerProvenanceIsRestored() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        String frozenJson = objectMapper.writeValueAsString(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
        ResumeDocumentDTO current = objectMapper.readValue(frozenJson, ResumeDocumentDTO.class);
        current.getSections().get(0).getEntries().get(0).getBullets().get(0).setSourceOccurrenceIds(List.of());
        current.getSections().get(0).getEntries().get(0).getBullets().get(0).setSourceRef(null);
        current.setConfirmedSourceOmissionIds(List.of("occ-bullet"));
        dbRevision.set(1L);
        dbContent.set(objectMapper.writeValueAsString(current));

        WorkspaceContentSaveResultVO result = service.saveContent(
                USER_ID, TASK_ID, saveRequest(1L, current));

        assertThat(result.getDocument().getSections().get(0).getEntries().get(0).getBullets().get(0)
                .getSourceOccurrenceIds()).containsExactly("occ-bullet");
        assertThat(result.getDocument().getConfirmedSourceOmissionIds()).isEmpty();
    }

    @Test
    void crossUserCannotMutateOmissionState() throws Exception {
        when(optimizationTaskMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.confirmSourceOmissions(
                OTHER_USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(0L, List.of("occ-bullet"))))
                .isInstanceOf(BusinessException.class).hasMessageContaining("优化任务不存在");
        assertThat(dbRevision.get()).isZero();
    }

    @Test
    void saveShouldWriteNormalizedDocumentAndIncrementRevision() {
        WorkspaceContentSaveResultVO result = service.saveContent(
                USER_ID, TASK_ID, saveRequest(0L, editedDocument("第一次编辑")));

        assertThat(result.isSaved()).isTrue();
        assertThat(result.isConflict()).isFalse();
        assertThat(result.getRevision()).isEqualTo(1L);
        ResumeDocumentDTO expected = editedDocument("第一次编辑");
        expected.setConfirmedSourceOmissionIds(List.of());
        assertThat(result.getDocument()).usingRecursiveComparison().isEqualTo(expected);
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
    void staleSaveShouldReturnConflictBeforeValidatingSubmittedNodeTopology() {
        service.saveContent(USER_ID, TASK_ID, saveRequest(0L, editedDocument("已保存内容")));
        ResumeDocumentDTO stale = editedDocument("过期内容");
        stale.setSections(new java.util.ArrayList<>(stale.getSections()));
        ResumeDocumentBulletDTO moved = stale.getSections().get(0).getEntries().get(0).getBullets().get(0);
        stale.getSections().add(ResumeDocumentSectionDTO.builder()
                .id("s-2").kind("EXPERIENCE").title("另一段经历")
                .entries(List.of(ResumeDocumentEntryDTO.builder()
                        .id("s-2-e-1").organization("另一家公司")
                        .bullets(List.of(moved)).build()))
                .build());
        stale.getSections().get(0).getEntries().get(0).setBullets(List.of());

        WorkspaceContentSaveResultVO result = service.saveContent(
                USER_ID, TASK_ID, saveRequest(0L, stale));

        assertThat(result.isConflict()).isTrue();
        assertThat(result.getRevision()).isEqualTo(1L);
        assertThat(dbContent.get()).contains("已保存内容");
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
        ResumeDocumentDTO expectedRestored = new ResumeCanonicalDocumentServiceImpl(objectMapper)
                .buildFromStructuredJson(FROZEN_SNAPSHOT).document();
        expectedRestored.setConfirmedSourceOmissionIds(List.of());
        assertThat(restored).usingRecursiveComparison().isEqualTo(expectedRestored);

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

    private ResumeDocumentDTO canonicalFrozenDocument() {
        ResumeSourceRefDTO sectionRef = ResumeSourceRefDTO.builder().text("工作经历")
                .sourceOccurrenceIds(List.of("occ-section")).build();
        ResumeSourceRefDTO entryRef = ResumeSourceRefDTO.builder().text("某公司 Java 开发")
                .sourceOccurrenceIds(List.of("occ-entry")).build();
        ResumeSourceRefDTO bulletRef = ResumeSourceRefDTO.builder().text("负责订单服务开发")
                .sourceOccurrenceIds(List.of("occ-bullet")).build();
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .sourceOccurrenceIds(List.of("occ-section", "occ-entry", "occ-bullet"))
                .sourceOccurrenceTexts(Map.of(
                        "occ-section", "工作经历",
                        "occ-entry", "某公司 Java 开发",
                        "occ-bullet", "负责订单服务开发"))
                .sourceOccurrencePrimaryIds(Map.of(
                        "occ-section", "occ-section",
                        "occ-entry", "occ-entry",
                        "occ-bullet", "occ-bullet"))
                .basics(com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO.builder()
                        .contacts(List.of()).build())
                .sections(List.of(ResumeDocumentSectionDTO.builder()
                        .id("s-1").kind("EXPERIENCE").title("工作经历")
                        .sourceRef(sectionRef).sourceOccurrenceIds(List.of("occ-section"))
                        .entries(List.of(ResumeDocumentEntryDTO.builder()
                                .id("s-1-e-1").organization("某公司").role("Java 开发")
                                .sourceRef(entryRef).sourceOccurrenceIds(List.of("occ-entry"))
                                .bullets(List.of(ResumeDocumentBulletDTO.builder()
                                        .id("s-1-e-1-b-1").text("负责订单服务开发")
                                        .sourceRef(bulletRef).sourceOccurrenceIds(List.of("occ-bullet"))
                                        .build()))
                                .build()))
                        .build()))
                .build();
    }

    private ResumeDocumentDTO canonicalFrozenDocumentWithBullets(
            String sectionKind, int bulletCount, boolean includeAliases) {
        ResumeDocumentDTO document = canonicalFrozenDocument();
        document.getSections().get(0).setKind(sectionKind);

        List<String> occurrenceIds = new java.util.ArrayList<>(List.of("occ-section", "occ-entry"));
        Map<String, String> occurrenceTexts = new LinkedHashMap<>();
        occurrenceTexts.put("occ-section", "工作经历");
        occurrenceTexts.put("occ-entry", "某公司 Java 开发");
        Map<String, String> primaryIds = new LinkedHashMap<>();
        primaryIds.put("occ-section", "occ-section");
        primaryIds.put("occ-entry", "occ-entry");
        List<ResumeDocumentBulletDTO> bullets = new java.util.ArrayList<>();
        for (int index = 1; index <= bulletCount; index++) {
            String suffix = index == 1 ? "" : "-" + index;
            String occurrenceId = "occ-bullet" + suffix;
            String aliasId = occurrenceId + "-alias";
            String text = "职责 " + index;
            List<String> bulletOccurrences = includeAliases
                    ? List.of(occurrenceId, aliasId) : List.of(occurrenceId);
            occurrenceIds.addAll(bulletOccurrences);
            occurrenceTexts.put(occurrenceId, text);
            primaryIds.put(occurrenceId, occurrenceId);
            if (includeAliases) {
                occurrenceTexts.put(aliasId, text);
                primaryIds.put(aliasId, occurrenceId);
            }
            ResumeSourceRefDTO sourceRef = ResumeSourceRefDTO.builder()
                    .text(text).sourceOccurrenceIds(bulletOccurrences).build();
            bullets.add(ResumeDocumentBulletDTO.builder()
                    .id("s-1-e-1-b-" + index).text(text)
                    .sourceRef(sourceRef).sourceOccurrenceIds(bulletOccurrences)
                    .build());
        }
        document.setSourceOccurrenceIds(occurrenceIds);
        document.setSourceOccurrenceTexts(occurrenceTexts);
        document.setSourceOccurrencePrimaryIds(primaryIds);
        document.getSections().get(0).getEntries().get(0).setBullets(bullets);
        return document;
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
                                .organization("某公司")
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
