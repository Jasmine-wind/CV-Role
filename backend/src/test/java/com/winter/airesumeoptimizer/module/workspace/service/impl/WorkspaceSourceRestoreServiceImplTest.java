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
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceContentSaveRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceSourceOmissionRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceSourceRestoreRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.enums.WorkspaceSourceRestoreScope;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentSaveResultVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Structure Fidelity Resolver 的 dedicated restore API 行为。
 *
 * <p>Restore 是唯一的 frozen ID resurrection 通道，因此安全边界必须比 omission 更严格：
 * 请求只命名 occurrence，边界由服务端解析；恢复只从 frozen snapshot deep copy；
 * 写库前重新跑 authoritative fidelity；wrong merge 与部分缺失边界一律 fail closed。
 */
class WorkspaceSourceRestoreServiceImplTest {

    static {
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

    private final AtomicLong dbRevision = new AtomicLong(0L);
    private final AtomicReference<String> dbContent = new AtomicReference<>("");

    @BeforeEach
    void setUp() {
        task = new OptimizationTask();
        task.setId(TASK_ID);
        task.setUserId(USER_ID);
        task.setSourceResumeVersionId(SOURCE_VERSION_ID);
        task.setTargetResumeVersionId(TARGET_VERSION_ID);
        task.setJobTargetId(JOB_TARGET_ID);
        task.setStatus("SUCCESS");

        sourceVersion = new ResumeVersion();
        sourceVersion.setId(SOURCE_VERSION_ID);
        sourceVersion.setUserId(USER_ID);
        sourceVersion.setResumeId(RESUME_ID);
        sourceVersion.setVersionType("SOURCE");
        sourceVersion.setSourceType("PARSED_UPLOAD");
        sourceVersion.setContentStatus("READY");
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
        targetVersion.setContentRevision(0L);

        JobTarget jobTarget = new JobTarget();
        jobTarget.setId(JOB_TARGET_ID);
        jobTarget.setUserId(USER_ID);
        Resume resume = new Resume();
        resume.setId(RESUME_ID);
        resume.setUserId(USER_ID);

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
        when(resumeVersionMapper.update(isNull(), any(UpdateWrapper.class))).thenAnswer(invocation ->
                simulateConditionalUpdate(invocation.getArgument(1), TARGET_VERSION_ID, dbRevision, dbContent));
    }

    /** 冻结 SOURCE 与任务快照：一个 EXPERIENCE entry，带一个可删的 bullet。 */
    private void freeze(ResumeDocumentDTO frozen) {
        String frozenJson = serialize(frozen);
        sourceVersion.setStructuredContent(frozenJson);
        task.setResumeInputSnapshot(frozenJson);
    }

    /** 持久化 TARGET：直接写入给定文档，模拟之前已通过编辑/删除保存的状态。 */
    private void persist(ResumeDocumentDTO target, long revision) {
        dbRevision.set(revision);
        dbContent.set(serialize(target));
    }

    private String serialize(ResumeDocumentDTO document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private ResumeDocumentDTO readDb() {
        try {
            return objectMapper.readValue(dbContent.get(), ResumeDocumentDTO.class);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private WorkspaceSourceReferenceVO fidelity() {
        return service.getSourceReference(USER_ID, TASK_ID);
    }

    private WorkspaceSourceReferenceVO.SourceBlock blockOf(WorkspaceSourceReferenceVO fidelity, String occurrenceId) {
        return fidelity.sourceBlocks().stream()
                .filter(block -> block.occurrenceIds().contains(occurrenceId))
                .findFirst().orElseThrow();
    }

    private int simulateConditionalUpdate(
            UpdateWrapper<?> wrapper, Long expectedTargetId, AtomicLong revision, AtomicReference<String> content) {
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

    private Object extractParam(String sql, Map<String, Object> params, String column) {
        if (sql == null) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile(java.util.regex.Pattern.quote(column) + "\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(MPGENVAL\\d+)\\}")
                .matcher(sql);
        return matcher.find() ? params.get(matcher.group(1)) : null;
    }

    private Collection<Object> wrapperParamValues(org.mockito.invocation.InvocationOnMock invocation) {
        Object argument = invocation.getArgument(0);
        if (!(argument instanceof AbstractWrapper<?, ?, ?> wrapper)) {
            return List.of();
        }
        wrapper.getSqlSegment();
        return wrapper.getParamNameValuePairs().values();
    }

    private WorkspaceContentSaveRequestDTO saveRequest(long expectedRevision, ResumeDocumentDTO document) {
        WorkspaceContentSaveRequestDTO request = new WorkspaceContentSaveRequestDTO();
        request.setExpectedRevision(expectedRevision);
        request.setDocument(document);
        return request;
    }

    private ResumeDocumentDTO withoutBullet(ResumeDocumentDTO frozen, String bulletId) {
        ResumeDocumentDTO target = objectMapper.convertValue(frozen, ResumeDocumentDTO.class);
        ResumeDocumentEntryDTO entry = target.getSections().get(0).getEntries().get(0);
        entry.setBullets(entry.getBullets().stream()
                .filter(bullet -> !bulletId.equals(bullet.getId())).toList());
        return target;
    }

    /** SOURCE 基线：section / entry / bullet 各一个 occurrence。 */
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
                .basics(ResumeDocumentBasicsDTO.builder().contacts(List.of()).build())
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

    /** 冻结文档：给定章节类型与 bullet 数量；includeAliases 时每个 bullet 带一个物理 alias。 */
    private ResumeDocumentDTO frozenDocumentWithBullets(String sectionKind, int bulletCount, boolean includeAliases) {
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

    /** 两个完整 Project entry 的冻结文档：A 保留，B 可被整体删除后恢复。 */
    private ResumeDocumentDTO twoProjectFrozenDocument() {
        ResumeSourceRefDTO sectionRef = ResumeSourceRefDTO.builder().text("项目经历")
                .sourceOccurrenceIds(List.of("occ-project-section")).build();
        ResumeSourceRefDTO aEntryRef = ResumeSourceRefDTO.builder().text("OmniGateway · 网关开发")
                .sourceOccurrenceIds(List.of("occ-a-entry")).build();
        ResumeSourceRefDTO aBulletRef = ResumeSourceRefDTO.builder().text("设计高可用多智能体网关")
                .sourceOccurrenceIds(List.of("occ-a-bullet")).build();
        ResumeSourceRefDTO bEntryRef = ResumeSourceRefDTO.builder().text("缓存平台 · 缓存开发")
                .sourceOccurrenceIds(List.of("occ-b-entry")).build();
        ResumeSourceRefDTO bOneRef = ResumeSourceRefDTO.builder().text("负责 Redis 热点缓存与缓存一致性")
                .sourceOccurrenceIds(List.of("occ-b-bullet-1")).build();
        ResumeSourceRefDTO bTwoRef = ResumeSourceRefDTO.builder().text("落地多级缓存治理")
                .sourceOccurrenceIds(List.of("occ-b-bullet-2")).build();
        List<String> order = List.of("occ-project-section", "occ-a-entry", "occ-a-bullet",
                "occ-b-entry", "occ-b-bullet-1", "occ-b-bullet-2");
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("occ-project-section", "项目经历");
        texts.put("occ-a-entry", "OmniGateway · 网关开发");
        texts.put("occ-a-bullet", "设计高可用多智能体网关");
        texts.put("occ-b-entry", "缓存平台 · 缓存开发");
        texts.put("occ-b-bullet-1", "负责 Redis 热点缓存与缓存一致性");
        texts.put("occ-b-bullet-2", "落地多级缓存治理");
        Map<String, String> primaryIds = new LinkedHashMap<>();
        order.forEach(id -> primaryIds.put(id, id));
        return ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .sourceOccurrenceIds(order)
                .sourceOccurrenceTexts(texts)
                .sourceOccurrencePrimaryIds(primaryIds)
                .basics(ResumeDocumentBasicsDTO.builder().contacts(List.of()).build())
                .sections(List.of(ResumeDocumentSectionDTO.builder()
                        .id("s-1").kind("PROJECT").title("项目经历")
                        .sourceRef(sectionRef).sourceOccurrenceIds(List.of("occ-project-section"))
                        .entries(List.of(
                                ResumeDocumentEntryDTO.builder()
                                        .id("s-1-e-1").organization("OmniGateway").role("网关开发")
                                        .sourceRef(aEntryRef).sourceOccurrenceIds(List.of("occ-a-entry"))
                                        .bullets(List.of(ResumeDocumentBulletDTO.builder()
                                                .id("s-1-e-1-b-1").text("设计高可用多智能体网关")
                                                .sourceRef(aBulletRef).sourceOccurrenceIds(List.of("occ-a-bullet"))
                                                .build()))
                                        .build(),
                                ResumeDocumentEntryDTO.builder()
                                        .id("s-1-e-2").organization("缓存平台").role("缓存开发")
                                        .sourceRef(bEntryRef).sourceOccurrenceIds(List.of("occ-b-entry"))
                                        .bullets(List.of(
                                                ResumeDocumentBulletDTO.builder()
                                                        .id("s-1-e-2-b-1").text("负责 Redis 热点缓存与缓存一致性")
                                                        .sourceRef(bOneRef).sourceOccurrenceIds(List.of("occ-b-bullet-1"))
                                                        .build(),
                                                ResumeDocumentBulletDTO.builder()
                                                        .id("s-1-e-2-b-2").text("落地多级缓存治理")
                                                        .sourceRef(bTwoRef).sourceOccurrenceIds(List.of("occ-b-bullet-2"))
                                                        .build()))
                                        .build()))
                        .build()))
                .build();
    }

    private ResumeDocumentDTO withoutProjectEntry(ResumeDocumentDTO frozen, String entryId) {
        ResumeDocumentDTO target = objectMapper.convertValue(frozen, ResumeDocumentDTO.class);
        target.getSections().get(0).setEntries(target.getSections().get(0).getEntries().stream()
                .filter(entry -> !entryId.equals(entry.getId())).toList());
        return target;
    }

    private ResumeDocumentDTO withStrayBulletInEntry(
            ResumeDocumentDTO target, String entryId, String occurrenceId) {
        ResumeDocumentDTO stray = objectMapper.convertValue(target, ResumeDocumentDTO.class);
        for (ResumeDocumentEntryDTO entry : stray.getSections().get(0).getEntries()) {
            if (!entryId.equals(entry.getId())) {
                continue;
            }
            List<ResumeDocumentBulletDTO> bullets = new java.util.ArrayList<>(entry.getBullets());
            bullets.add(ResumeDocumentBulletDTO.builder()
                    .id("stray-bullet").text("错误归属内容")
                    .sourceOccurrenceIds(List.of(occurrenceId))
                    .build());
            entry.setBullets(bullets);
        }
        return stray;
    }

    // ---------------------------------------------------------------------
    // Deleted bullet restore
    // ---------------------------------------------------------------------

    @Test
    void deletedBulletRestoreRebuildsExactFrozenNodeAndClearsTheBlocker() {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        freeze(frozen);
        ResumeDocumentDTO deleted = withoutBullet(frozen, "s-1-e-1-b-1");
        persist(deleted, 1L);

        WorkspaceSourceReferenceVO before = fidelity();
        WorkspaceSourceReferenceVO.SourceBlock bulletBlock = blockOf(before, "occ-bullet");
        assertThat(before.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("SOURCE_CONTENT_UNMAPPED");
        assertThat(before.exportBlocked()).isTrue();
        assertThat(bulletBlock.restoreEligible()).isTrue();
        assertThat(bulletBlock.restoreScope()).isEqualTo(WorkspaceSourceRestoreScope.BULLET);
        assertThat(bulletBlock.restoreBlockedReason()).isNull();

        WorkspaceContentSaveResultVO result = service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-bullet")));

        assertThat(result.isSaved()).isTrue();
        assertThat(result.getRevision()).isEqualTo(2L);
        ResumeDocumentEntryDTO entry = result.getDocument().getSections().get(0).getEntries().get(0);
        assertThat(entry.getOrganization()).isEqualTo("某公司");
        assertThat(entry.getRole()).isEqualTo("Java 开发");
        assertThat(entry.getBullets()).hasSize(1);
        ResumeDocumentBulletDTO restored = entry.getBullets().get(0);
        assertThat(restored.getId()).isEqualTo("s-1-e-1-b-1");
        assertThat(restored.getText()).isEqualTo("负责订单服务开发");
        assertThat(restored.getSourceOccurrenceIds()).containsExactly("occ-bullet");
        assertThat(restored.getSourceRef().getSourceOccurrenceIds()).containsExactly("occ-bullet");

        WorkspaceSourceReferenceVO after = fidelity();
        assertThat(after.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .doesNotContain("SOURCE_CONTENT_UNMAPPED");
        assertThat(after.exportBlocked()).isFalse();
        WorkspaceSourceReferenceVO.SourceBlock restoredBlock = blockOf(after, "occ-bullet");
        assertThat(restoredBlock.status().name()).isEqualTo("EXACT");
        assertThat(blockOf(after, "occ-bullet").targetNodeIds()).hasSize(1);
        WorkspaceSourceReferenceVO.TargetMapping mapping = after.mappings().stream()
                .filter(item -> item.targetNodeId().endsWith("/bullet:s-1-e-1-b-1"))
                .findFirst().orElseThrow();
        assertThat(mapping.textChanged()).isFalse();
        assertThat(mapping.sourceOccurrenceIds()).containsExactly("occ-bullet");
    }

    @Test
    void deletedMiddleBulletRestoresFrozenSiblingOrderDeterministically() {
        ResumeDocumentDTO frozen = frozenDocumentWithBullets("EXPERIENCE", 3, true);
        freeze(frozen);
        ResumeDocumentDTO deleted = withoutBullet(frozen, "s-1-e-1-b-2");
        persist(deleted, 1L);

        WorkspaceContentSaveResultVO result = service.restoreSourceContent(
                USER_ID, TASK_ID,
                new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-bullet-2")));

        assertThat(result.isSaved()).isTrue();
        List<ResumeDocumentBulletDTO> bullets = result.getDocument()
                .getSections().get(0).getEntries().get(0).getBullets();
        assertThat(bullets).extracting(ResumeDocumentBulletDTO::getId)
                .containsExactly("s-1-e-1-b-1", "s-1-e-1-b-2", "s-1-e-1-b-3");
        assertThat(blockOf(fidelity(), "occ-bullet-2-alias").status().name()).isEqualTo("EXACT");
    }

    @Test
    void bulletRestoreStaysEligibleWhenAncestorContainersLegitimatelyCarryTheOccurrenceIds() {
        // Real parser output assigns each occurrence to its ancestor containers as well. A deleted
        // bullet must stay restorable while those container claims remain authenticated — they are
        // not "another mapping", and regressing this would break every real-world bullet restore.
        ResumeDocumentDTO frozen = ResumeDocumentDTO.builder()
                .schemaVersion(ResumeDocumentDTO.SCHEMA_VERSION)
                .sourceOccurrenceIds(List.of("occ-entry", "occ-bullet", "occ-section"))
                .sourceOccurrenceTexts(Map.of(
                        "occ-entry", "某公司 Java 开发",
                        "occ-bullet", "负责订单服务开发",
                        "occ-section", "工作经历"))
                .sourceOccurrencePrimaryIds(Map.of(
                        "occ-entry", "occ-entry",
                        "occ-bullet", "occ-bullet",
                        "occ-section", "occ-section"))
                .basics(ResumeDocumentBasicsDTO.builder().contacts(List.of()).build())
                .sections(List.of(ResumeDocumentSectionDTO.builder()
                        .id("s-1").kind("EXPERIENCE").title("工作经历")
                        .sourceRef(ResumeSourceRefDTO.builder().text("工作经历")
                                .sourceOccurrenceIds(List.of("occ-section")).build())
                        .sourceOccurrenceIds(List.of("occ-section"))
                        .entries(List.of(ResumeDocumentEntryDTO.builder()
                                .id("s-1-e-1").organization("某公司").role("Java 开发")
                                .sourceRef(ResumeSourceRefDTO.builder()
                                        .text("某公司 Java 开发\n负责订单服务开发")
                                        .sourceOccurrenceIds(List.of("occ-entry", "occ-bullet")).build())
                                .sourceOccurrenceIds(List.of("occ-entry", "occ-bullet"))
                                .bullets(List.of(ResumeDocumentBulletDTO.builder()
                                        .id("s-1-e-1-b-1").text("负责订单服务开发")
                                        .sourceRef(ResumeSourceRefDTO.builder().text("负责订单服务开发")
                                                .sourceOccurrenceIds(List.of("occ-bullet")).build())
                                        .sourceOccurrenceIds(List.of("occ-bullet"))
                                        .build()))
                                .build()))
                        .build()))
                .build();
        freeze(frozen);
        ResumeDocumentDTO deleted = objectMapper.convertValue(frozen, ResumeDocumentDTO.class);
        deleted.getSections().get(0).getEntries().get(0).setBullets(List.of());
        persist(deleted, 1L);

        WorkspaceSourceReferenceVO before = fidelity();
        assertThat(before.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .doesNotContain("SOURCE_MANIFEST_INVALID");
        WorkspaceSourceReferenceVO.SourceBlock block = blockOf(before, "occ-bullet");
        assertThat(block.status().name()).isEqualTo("UNMAPPED");
        assertThat(block.restoreEligible()).isTrue();
        assertThat(block.restoreScope()).isEqualTo(WorkspaceSourceRestoreScope.BULLET);

        WorkspaceContentSaveResultVO result = service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-bullet")));

        assertThat(result.isSaved()).isTrue();
        assertThat(result.getDocument().getSections().get(0).getEntries().get(0).getBullets())
                .extracting(ResumeDocumentBulletDTO::getId).containsExactly("s-1-e-1-b-1");
        assertThat(blockOf(fidelity(), "occ-bullet").status().name()).isEqualTo("EXACT");
    }

    @Test
    void ordinarySaveStillRejectsFrozenIdResurrectionAfterADedicatedDelete() {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        freeze(frozen);
        ResumeDocumentDTO deleted = withoutBullet(frozen, "s-1-e-1-b-1");
        persist(deleted, 1L);

        ResumeDocumentDTO resurrection = readDb();
        resurrection.getSections().get(0).getEntries().get(0).setBullets(List.of(
                ResumeDocumentBulletDTO.builder()
                        .id("s-1-e-1-b-1").text("伪造重建的来源节点").build()));

        assertThatThrownBy(() -> service.saveContent(USER_ID, TASK_ID, saveRequest(1L, resurrection)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).contains("已删除的来源节点不能复用原 ID");
                });
        assertThat(dbRevision.get()).isEqualTo(1L);
        assertThat(dbContent.get()).doesNotContain("伪造重建的来源节点");
        verify(resumeVersionMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    // ---------------------------------------------------------------------
    // Restore vs confirmed omission
    // ---------------------------------------------------------------------

    @Test
    void restoreRemovesTheConfirmedOmissionOfTheRestoredBoundary() {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        freeze(frozen);
        ResumeDocumentDTO deleted = withoutBullet(frozen, "s-1-e-1-b-1");
        persist(deleted, 1L);

        WorkspaceContentSaveResultVO confirmed = service.confirmSourceOmissions(
                USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("occ-bullet")));
        assertThat(confirmed.isSaved()).isTrue();
        assertThat(fidelity().exportBlocked()).isFalse();
        assertThat(blockOf(fidelity(), "occ-bullet").omissionConfirmed()).isTrue();

        WorkspaceContentSaveResultVO restored = service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(2L, List.of("occ-bullet")));

        assertThat(restored.isSaved()).isTrue();
        assertThat(restored.getRevision()).isEqualTo(3L);
        assertThat(restored.getDocument().getConfirmedSourceOmissionIds()).isEmpty();
        WorkspaceSourceReferenceVO after = fidelity();
        WorkspaceSourceReferenceVO.SourceBlock block = blockOf(after, "occ-bullet");
        assertThat(block.status().name()).isEqualTo("EXACT");
        assertThat(block.omissionConfirmed()).isFalse();
        assertThat(after.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .doesNotContain("SOURCE_CONTENT_UNMAPPED", "CONFIRMED_OMISSION_INVALID");
        assertThat(after.confirmedOmissionCount()).isZero();
        assertThat(after.exportBlocked()).isFalse();
    }

    // ---------------------------------------------------------------------
    // Whole Project restore
    // ---------------------------------------------------------------------

    @Test
    void wholeProjectRestoreRebuildsTheCompleteFrozenEntryWithProvenance() {
        ResumeDocumentDTO frozen = twoProjectFrozenDocument();
        freeze(frozen);
        ResumeDocumentDTO deleted = withoutProjectEntry(frozen, "s-1-e-2");
        persist(deleted, 1L);

        WorkspaceSourceReferenceVO before = fidelity();
        assertThat(before.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("PROJECT_BOUNDARY_LOST", "SOURCE_CONTENT_UNMAPPED");
        assertThat(blockOf(before, "occ-b-entry").restoreEligible()).isTrue();
        assertThat(blockOf(before, "occ-b-entry").restoreScope())
                .isEqualTo(WorkspaceSourceRestoreScope.PROJECT_ENTRY);
        assertThat(blockOf(before, "occ-b-bullet-1").restoreScope())
                .isEqualTo(WorkspaceSourceRestoreScope.PROJECT_ENTRY);

        WorkspaceContentSaveResultVO result = service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-b-bullet-1")));

        assertThat(result.isSaved()).isTrue();
        assertThat(result.getRevision()).isEqualTo(2L);
        List<ResumeDocumentEntryDTO> entries = result.getDocument().getSections().get(0).getEntries();
        assertThat(entries).extracting(ResumeDocumentEntryDTO::getId)
                .containsExactly("s-1-e-1", "s-1-e-2");
        ResumeDocumentEntryDTO restored = entries.get(1);
        assertThat(restored.getOrganization()).isEqualTo("缓存平台");
        assertThat(restored.getRole()).isEqualTo("缓存开发");
        assertThat(restored.getSourceOccurrenceIds()).containsExactly("occ-b-entry");
        assertThat(restored.getBullets()).extracting(ResumeDocumentBulletDTO::getId)
                .containsExactly("s-1-e-2-b-1", "s-1-e-2-b-2");
        assertThat(restored.getBullets().get(0).getSourceOccurrenceIds())
                .containsExactly("occ-b-bullet-1");

        WorkspaceSourceReferenceVO after = fidelity();
        assertThat(after.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .doesNotContain("PROJECT_BOUNDARY_LOST", "SOURCE_CONTENT_UNMAPPED");
        assertThat(after.exportBlocked()).isFalse();
    }

    @Test
    void wrongMergeBoundaryIsRejectedAndTheBoundaryBlockerStays() {
        ResumeDocumentDTO frozen = twoProjectFrozenDocument();
        freeze(frozen);
        ResumeDocumentDTO wrongMerge = withStrayBulletInEntry(
                withoutProjectEntry(frozen, "s-1-e-2"), "s-1-e-1", "occ-b-bullet-1");
        persist(wrongMerge, 1L);

        WorkspaceSourceReferenceVO before = fidelity();
        assertThat(before.fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("PROJECT_BOUNDARY_LOST", "AMBIGUOUS_MAPPING");
        WorkspaceSourceReferenceVO.SourceBlock boundaryBlock = blockOf(before, "occ-b-entry");
        assertThat(boundaryBlock.restoreEligible()).isFalse();
        assertThat(boundaryBlock.restoreBlockedReason()).isEqualTo("BOUNDARY_HAS_OTHER_MAPPINGS");

        assertThatThrownBy(() -> service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-b-entry"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).contains("存在其它映射");
                });
        assertThatThrownBy(() -> service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-b-bullet-1"))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getMessage()).contains("存在其它映射"));

        assertThat(dbRevision.get()).isEqualTo(1L);
        verify(resumeVersionMapper, never()).update(isNull(), any(UpdateWrapper.class));
        assertThat(fidelity().fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("PROJECT_BOUNDARY_LOST");
    }

    @Test
    void partiallyMappedBoundaryRejectsRestoreOfACleanSiblingBlock() {
        ResumeDocumentDTO frozen = twoProjectFrozenDocument();
        freeze(frozen);
        ResumeDocumentDTO wrongMerge = withStrayBulletInEntry(
                withoutProjectEntry(frozen, "s-1-e-2"), "s-1-e-1", "occ-b-bullet-2");
        persist(wrongMerge, 1L);

        // The requested occurrence itself is clean; the boundary is not, so nothing may be restored.
        WorkspaceSourceReferenceVO.SourceBlock cleanBlock = blockOf(fidelity(), "occ-b-bullet-1");
        assertThat(cleanBlock.restoreEligible()).isFalse();
        assertThat(cleanBlock.restoreBlockedReason()).isEqualTo("BOUNDARY_HAS_OTHER_MAPPINGS");

        assertThatThrownBy(() -> service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-b-bullet-1"))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getMessage()).contains("存在其它映射"));
        assertThat(dbRevision.get()).isEqualTo(1L);
        assertThat(fidelity().fidelityIssues()).extracting(WorkspaceSourceReferenceVO.FidelityIssue::code)
                .contains("PROJECT_BOUNDARY_LOST");
    }

    // ---------------------------------------------------------------------
    // Contact restore
    // ---------------------------------------------------------------------

    private ResumeDocumentDTO frozenContactDocument() {
        ResumeDocumentDTO document = canonicalFrozenDocument();
        document.setSourceOccurrenceIds(List.of("occ-section", "occ-entry", "occ-bullet", "occ-contact"));
        document.setSourceOccurrenceTexts(Map.of(
                "occ-section", "工作经历",
                "occ-entry", "某公司 Java 开发",
                "occ-bullet", "负责订单服务开发",
                "occ-contact", "13800000000"));
        document.setSourceOccurrencePrimaryIds(Map.of(
                "occ-section", "occ-section",
                "occ-entry", "occ-entry",
                "occ-bullet", "occ-bullet",
                "occ-contact", "occ-contact"));
        document.setBasics(ResumeDocumentBasicsDTO.builder()
                .contacts(List.of(ResumeDocumentContactDTO.builder()
                        .id("c-1").type("PHONE").label("电话").value("13800000000")
                        .sourceRef(ResumeSourceRefDTO.builder().text("13800000000")
                                .sourceOccurrenceIds(List.of("occ-contact")).build())
                        .sourceOccurrenceIds(List.of("occ-contact"))
                        .build()))
                .build());
        return document;
    }

    @Test
    void deletedContactRestoresTheExactFrozenContactAndItsProvenance() {
        ResumeDocumentDTO frozen = frozenContactDocument();
        freeze(frozen);
        ResumeDocumentDTO deleted = objectMapper.convertValue(frozen, ResumeDocumentDTO.class);
        deleted.getBasics().setContacts(List.of());
        persist(deleted, 1L);

        WorkspaceSourceReferenceVO.SourceBlock block = blockOf(fidelity(), "occ-contact");
        assertThat(block.restoreEligible()).isTrue();
        assertThat(block.restoreScope()).isEqualTo(WorkspaceSourceRestoreScope.CONTACT);

        WorkspaceContentSaveResultVO result = service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-contact")));

        assertThat(result.isSaved()).isTrue();
        List<ResumeDocumentContactDTO> contacts = result.getDocument().getBasics().getContacts();
        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).getId()).isEqualTo("c-1");
        assertThat(contacts.get(0).getValue()).isEqualTo("13800000000");
        assertThat(contacts.get(0).getSourceOccurrenceIds()).containsExactly("occ-contact");
        assertThat(fidelity().exportBlocked()).isFalse();
    }

    @Test
    void duplicateContactValueBlocksTheRestoreInsteadOfCreatingADuplicate() {
        ResumeDocumentDTO frozen = frozenContactDocument();
        freeze(frozen);
        ResumeDocumentDTO replaced = objectMapper.convertValue(frozen, ResumeDocumentDTO.class);
        replaced.getBasics().setContacts(List.of(ResumeDocumentContactDTO.builder()
                .id("c-2").type("PHONE").label("电话").value("13800000000")
                .build()));
        persist(replaced, 1L);

        WorkspaceSourceReferenceVO.SourceBlock block = blockOf(fidelity(), "occ-contact");
        assertThat(block.restoreEligible()).isFalse();
        assertThat(block.restoreBlockedReason()).isEqualTo("TARGET_VALUE_CONFLICT");

        assertThatThrownBy(() -> service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-contact"))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getMessage()).contains("已存在对应内容"));
        assertThat(dbRevision.get()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------------
    // Fail-closed security batch
    // ---------------------------------------------------------------------

    @Test
    void restoreRequestBatchFailsClosedWithoutAnyWrite() {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        freeze(frozen);
        ResumeDocumentDTO deleted = withoutBullet(frozen, "s-1-e-1-b-1");
        persist(deleted, 1L);

        // Unknown / forged occurrence.
        assertThatThrownBy(() -> service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("unknown-occurrence"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).contains("不属于当前冻结简历");
                });
        // Duplicate request IDs.
        assertThatThrownBy(() -> service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-bullet", "occ-bullet"))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getMessage()).contains("无效或重复"));
        // Empty IDs.
        assertThatThrownBy(() -> service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of())))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getMessage()).contains("不能为空"));
        // Missing revision.
        assertThatThrownBy(() -> service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(null, List.of("occ-bullet"))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getMessage()).contains("缺少内容版本号"));
        // Cross-user task fails closed before any source resolution.
        when(optimizationTaskMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.restoreSourceContent(
                OTHER_USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-bullet"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(404);
                    assertThat(exception.getMessage()).contains("优化任务不存在");
                });
        when(optimizationTaskMapper.selectOne(any())).thenAnswer(invocation ->
                wrapperParamValues(invocation).contains(TASK_ID) ? task : null);

        // Stale expectedRevision returns a conflict result instead of writing.
        WorkspaceContentSaveResultVO stale = service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(0L, List.of("occ-bullet")));
        assertThat(stale.isSaved()).isFalse();
        assertThat(stale.isConflict()).isTrue();
        assertThat(stale.getRevision()).isEqualTo(1L);

        // No mutation happened for any rejected request.
        assertThat(dbRevision.get()).isEqualTo(1L);
        verify(resumeVersionMapper, never()).update(isNull(), any(UpdateWrapper.class));
    }

    @Test
    void invalidFrozenManifestRejectsRestoreWithAClearTaskLevelMessage() {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        frozen.setSourceOccurrenceIds(List.of("occ-section", "occ-entry", "occ-bullet", "occ-bullet"));
        freeze(frozen);
        ResumeDocumentDTO deleted = withoutBullet(frozen, "s-1-e-1-b-1");
        persist(deleted, 1L);

        assertThatThrownBy(() -> service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-bullet"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).contains("原文校验数据异常");
                });
        assertThat(dbRevision.get()).isEqualTo(1L);
    }

    @Test
    void alreadyRestoredBoundaryCannotBeRestoredTwice() {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        freeze(frozen);
        ResumeDocumentDTO deleted = withoutBullet(frozen, "s-1-e-1-b-1");
        persist(deleted, 1L);
        service.restoreSourceContent(USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-bullet")));

        WorkspaceContentSaveResultVO stale = service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-bullet")));
        assertThat(stale.isConflict()).isTrue();
        assertThat(stale.getRevision()).isEqualTo(2L);

        assertThatThrownBy(() -> service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(2L, List.of("occ-bullet"))))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).contains("无法安全自动恢复");
                });
        assertThat(dbRevision.get()).isEqualTo(2L);
    }

    @Test
    void parentAncestryMismatchRejectsRestoreAndFallsBackToTheSafeRecoveryPaths() {
        ResumeDocumentDTO frozen = twoProjectFrozenDocument();
        freeze(frozen);
        ResumeDocumentDTO wrongKind = withoutProjectEntry(frozen, "s-1-e-2");
        wrongKind.getSections().get(0).setKind("EXPERIENCE");
        persist(wrongKind, 1L);

        WorkspaceSourceReferenceVO.SourceBlock block = blockOf(fidelity(), "occ-b-entry");
        assertThat(block.restoreEligible()).isFalse();
        assertThat(block.restoreBlockedReason()).isEqualTo("PARENT_LINEAGE_MISMATCH");

        assertThatThrownBy(() -> service.restoreSourceContent(
                USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-b-entry"))))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getMessage()).contains("来源归属关系不一致"));
        assertThat(dbRevision.get()).isEqualTo(1L);
    }

    // ---------------------------------------------------------------------
    // CAS
    // ---------------------------------------------------------------------

    @Test
    void restoreAndOmissionOnTheSameRevisionHaveExactlyOneCasWinner() throws Exception {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        freeze(frozen);
        ResumeDocumentDTO deleted = withoutBullet(frozen, "s-1-e-1-b-1");
        persist(deleted, 1L);

        CountDownLatch bothAtCas = new CountDownLatch(2);
        when(resumeVersionMapper.update(isNull(), any(UpdateWrapper.class))).thenAnswer(invocation -> {
            bothAtCas.countDown();
            if (!bothAtCas.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("restore and omission did not reach the CAS boundary together");
            }
            synchronized (dbRevision) {
                return simulateConditionalUpdate(
                        invocation.getArgument(1), TARGET_VERSION_ID, dbRevision, dbContent);
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<WorkspaceContentSaveResultVO> restore = executor.submit(() -> service.restoreSourceContent(
                    USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-bullet"))));
            Future<WorkspaceContentSaveResultVO> omission = executor.submit(() -> service.confirmSourceOmissions(
                    USER_ID, TASK_ID, new WorkspaceSourceOmissionRequestDTO(1L, List.of("occ-bullet"))));

            List<WorkspaceContentSaveResultVO> results = List.of(
                    restore.get(10, TimeUnit.SECONDS), omission.get(10, TimeUnit.SECONDS));
            assertThat(results).filteredOn(WorkspaceContentSaveResultVO::isSaved).hasSize(1);
            assertThat(results).filteredOn(WorkspaceContentSaveResultVO::isConflict).hasSize(1);
            assertThat(dbRevision.get()).isEqualTo(2L);

            // Exactly one outcome is persisted: never "restored + still omitted".
            ResumeDocumentDTO persisted = readDb();
            boolean bulletRestored = persisted.getSections().get(0).getEntries().get(0).getBullets().stream()
                    .anyMatch(bullet -> "s-1-e-1-b-1".equals(bullet.getId()));
            boolean omitted = persisted.getConfirmedSourceOmissionIds() != null
                    && persisted.getConfirmedSourceOmissionIds().contains("occ-bullet");
            assertThat(bulletRestored).isNotEqualTo(omitted);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void requestNamingSeveralBoundariesAtOnceIsRejected() {
        ResumeDocumentDTO frozen = twoProjectFrozenDocument();
        freeze(frozen);
        ResumeDocumentDTO deleted = withoutProjectEntry(frozen, "s-1-e-2");
        persist(deleted, 1L);

        // occ-b-entry belongs to the missing Project B boundary; occ-section does not.
        assertThatThrownBy(() -> service.restoreSourceContent(USER_ID, TASK_ID,
                new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-b-entry", "occ-section"))))
                .isInstanceOf(BusinessException.class);
        assertThat(dbRevision.get()).isEqualTo(1L);
    }

    @Test
    void restoredBulletKeepsWorkingThroughSubsequentOrdinarySaves() {
        ResumeDocumentDTO frozen = canonicalFrozenDocument();
        freeze(frozen);
        ResumeDocumentDTO deleted = withoutBullet(frozen, "s-1-e-1-b-1");
        persist(deleted, 1L);

        service.restoreSourceContent(USER_ID, TASK_ID, new WorkspaceSourceRestoreRequestDTO(1L, List.of("occ-bullet")));
        ResumeDocumentDTO afterRestore = readDb();
        afterRestore.getSections().get(0).getEntries().get(0).getBullets().get(0)
                .setText("恢复后又被人工编辑");

        WorkspaceContentSaveResultVO saved = service.saveContent(
                USER_ID, TASK_ID, saveRequest(2L, afterRestore));

        assertThat(saved.isSaved()).isTrue();
        assertThat(saved.getRevision()).isEqualTo(3L);
        ResumeDocumentBulletDTO bullet = saved.getDocument().getSections().get(0).getEntries().get(0).getBullets().get(0);
        assertThat(bullet.getId()).isEqualTo("s-1-e-1-b-1");
        assertThat(bullet.getText()).isEqualTo("恢复后又被人工编辑");
        // Server provenance survives the ordinary save; the restored node is a first-class frozen node again.
        assertThat(bullet.getSourceOccurrenceIds()).containsExactly("occ-bullet");
        WorkspaceSourceReferenceVO fidelity = fidelity();
        assertThat(fidelity.exportBlocked()).isFalse();
        Set<String> codes = fidelity.fidelityIssues().stream()
                .map(WorkspaceSourceReferenceVO.FidelityIssue::code).collect(Collectors.toSet());
        assertThat(codes).doesNotContain("SOURCE_CONTENT_UNMAPPED");
    }
}
