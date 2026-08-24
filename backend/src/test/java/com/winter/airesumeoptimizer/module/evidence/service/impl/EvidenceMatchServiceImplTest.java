package com.winter.airesumeoptimizer.module.evidence.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchOutcomeDTO;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceQuoteDTO;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceRequirementEvaluationDTO;
import com.winter.airesumeoptimizer.module.evidence.entity.EvidenceAnalysis;
import com.winter.airesumeoptimizer.module.evidence.entity.EvidenceRequirement;
import com.winter.airesumeoptimizer.module.evidence.entity.RequirementEvidence;
import com.winter.airesumeoptimizer.module.evidence.enums.EvidenceMatchLevel;
import com.winter.airesumeoptimizer.module.evidence.enums.EvidenceSupportLevel;
import com.winter.airesumeoptimizer.module.evidence.enums.RequirementImportance;
import com.winter.airesumeoptimizer.module.evidence.mapper.EvidenceAnalysisMapper;
import com.winter.airesumeoptimizer.module.evidence.mapper.EvidenceRequirementMapper;
import com.winter.airesumeoptimizer.module.evidence.mapper.RequirementEvidenceMapper;
import com.winter.airesumeoptimizer.module.evidence.service.EvidenceMatchingStrategy;
import com.winter.airesumeoptimizer.module.evidence.vo.EvidenceAnalysisResultVO;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import com.winter.airesumeoptimizer.module.optimization.entity.JobTarget;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.JobTargetMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.optimization.service.OptimizationTaskService;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EvidenceMatchServiceImplTest {

    private final OptimizationTaskMapper optimizationTaskMapper = mock(OptimizationTaskMapper.class);
    private final EvidenceAnalysisMapper evidenceAnalysisMapper = mock(EvidenceAnalysisMapper.class);
    private final EvidenceRequirementMapper evidenceRequirementMapper = mock(EvidenceRequirementMapper.class);
    private final RequirementEvidenceMapper requirementEvidenceMapper = mock(RequirementEvidenceMapper.class);
    private final EvidenceMatchingStrategy evidenceMatchingStrategy = mock(EvidenceMatchingStrategy.class);
    private final JobTargetMapper jobTargetMapper = mock(JobTargetMapper.class);
    private final ResumeVersionMapper resumeVersionMapper = mock(ResumeVersionMapper.class);
    private final OptimizationTaskService optimizationTaskService = mock(OptimizationTaskService.class);
    private final EvidenceMatchServiceImpl service = new EvidenceMatchServiceImpl(
            optimizationTaskMapper,
            evidenceAnalysisMapper,
            evidenceRequirementMapper,
            requirementEvidenceMapper,
            evidenceMatchingStrategy,
            jobTargetMapper,
            resumeVersionMapper,
            optimizationTaskService);

    @BeforeEach
    void setUp() {
        when(optimizationTaskMapper.selectOne(any())).thenReturn(task());
        when(jobTargetMapper.selectOne(any())).thenReturn(jobTarget());
        when(resumeVersionMapper.selectOne(any())).thenReturn(sourceVersion(), targetVersion());
        when(evidenceAnalysisMapper.selectOne(any())).thenReturn(null);
        AtomicLong analysisIds = new AtomicLong(70L);
        when(evidenceAnalysisMapper.insert(any(EvidenceAnalysis.class))).thenAnswer(invocation -> {
            EvidenceAnalysis analysis = invocation.getArgument(0);
            analysis.setId(analysisIds.getAndIncrement());
            return 1;
        });
        AtomicLong requirementIds = new AtomicLong(80L);
        when(evidenceRequirementMapper.insert(any(EvidenceRequirement.class))).thenAnswer(invocation -> {
            EvidenceRequirement requirement = invocation.getArgument(0);
            requirement.setId(requirementIds.getAndIncrement());
            return 1;
        });
        AtomicLong evidenceIds = new AtomicLong(90L);
        when(requirementEvidenceMapper.insert(any(RequirementEvidence.class))).thenAnswer(invocation -> {
            RequirementEvidence evidence = invocation.getArgument(0);
            evidence.setId(evidenceIds.getAndIncrement());
            return 1;
        });
    }

    @Test
    void analyzeShouldPersistTraceableRequirementsAndEvidenceFromFrozenSnapshot() {
        when(evidenceMatchingStrategy.match(any(), any(), any(), any(), any(), any())).thenReturn(outcome());

        EvidenceAnalysis analysis = service.analyze(1L, 50L, parsedJob());

        verify(evidenceMatchingStrategy).match(
                1L,
                50L,
                "岗位要求 Java",
                "{\"requiredSkills\":[\"Java\"]}",
                "{\"skills\":[\"熟悉 Java\"]}",
                null);
        assertThat(analysis.getOptimizationTaskId()).isEqualTo(50L);
        assertThat(analysis.getMatchedCount()).isEqualTo(1);
        assertThat(analysis.getPartialEvidenceCount()).isEqualTo(1);
        assertThat(analysis.getNoEvidenceCount()).isEqualTo(1);
        assertThat(analysis.getModelName()).isEqualTo("test-model");
        assertThat(analysis.getPromptVersion()).isEqualTo("evidence_match_v3");

        ArgumentCaptor<EvidenceRequirement> requirementCaptor =
                ArgumentCaptor.forClass(EvidenceRequirement.class);
        verify(evidenceRequirementMapper, org.mockito.Mockito.times(3)).insert(requirementCaptor.capture());
        assertThat(requirementCaptor.getAllValues())
                .extracting(EvidenceRequirement::getDisplayOrder)
                .containsExactly(0, 1, 2);

        ArgumentCaptor<RequirementEvidence> evidenceCaptor = ArgumentCaptor.forClass(RequirementEvidence.class);
        verify(requirementEvidenceMapper, org.mockito.Mockito.times(2)).insert(evidenceCaptor.capture());
        assertThat(evidenceCaptor.getAllValues())
                .allSatisfy(evidence -> assertThat(evidence.getSourceResumeVersionId()).isEqualTo(40L));
    }

    @Test
    void analyzeShouldReplaceExistingAnalysisOnRetry() {
        EvidenceAnalysis existing = new EvidenceAnalysis();
        existing.setId(77L);
        existing.setUserId(1L);
        existing.setOptimizationTaskId(50L);
        when(evidenceAnalysisMapper.selectOne(any())).thenReturn(existing);
        when(evidenceMatchingStrategy.match(any(), any(), any(), any(), any(), any())).thenReturn(outcome());

        service.analyze(1L, 50L, parsedJob());

        verify(evidenceAnalysisMapper).deleteById(77L);
        verify(evidenceAnalysisMapper).insert(any(EvidenceAnalysis.class));
    }

    @Test
    void analyzeShouldNotCompleteTaskWhenChildPersistenceFails() {
        when(evidenceMatchingStrategy.match(any(), any(), any(), any(), any(), any())).thenReturn(outcome());
        when(evidenceRequirementMapper.insert(any(EvidenceRequirement.class))).thenReturn(0);

        assertThatThrownBy(() -> service.analyze(1L, 50L, parsedJob()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("岗位证据分析保存失败");

        verify(optimizationTaskService, never()).markSuccess(any(), any(), any(), any());
    }

    @Test
    void analyzeShouldPropagateCompletionFailureSoTransactionCanRollbackResultReplacement() {
        when(evidenceMatchingStrategy.match(any(), any(), any(), any(), any(), any())).thenReturn(outcome());
        doThrow(new BusinessException(409, "优化任务当前状态不允许完成分析"))
                .when(optimizationTaskService)
                .markSuccess(any(), any(), any(), any());

        assertThatThrownBy(() -> service.analyze(1L, 50L, parsedJob()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("优化任务当前状态不允许完成分析");

        verify(evidenceAnalysisMapper).insert(any(EvidenceAnalysis.class));
        verify(optimizationTaskService).markSuccess(any(), any(), any(), any());
    }

    @Test
    void analyzeShouldRejectWhenResumeSnapshotMissing() {
        OptimizationTask task = task();
        task.setResumeInputSnapshot(null);
        when(optimizationTaskMapper.selectOne(any())).thenReturn(task);

        assertThatThrownBy(() -> service.analyze(1L, 50L, parsedJob()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("优化任务缺少简历输入快照");

        verify(evidenceMatchingStrategy, never()).match(any(), any(), any(), any(), any(), any());
    }

    @Test
    void analyzeShouldRejectWhenJobStructuredContentMissing() {
        assertThatThrownBy(() -> service.analyze(1L, 50L, JobDescriptionVO.builder().build()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("目标岗位结构化解析结果为空");

        verify(evidenceMatchingStrategy, never()).match(any(), any(), any(), any(), any(), any());
    }

    @Test
    void analyzeShouldRejectTaskNotOwnedByCurrentUser() {
        when(optimizationTaskMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.analyze(2L, 50L, parsedJob()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("优化任务不存在");
    }

    @Test
    void analyzeShouldRejectWrongResumeVersionRelationship() {
        ResumeVersion wrongTarget = targetVersion();
        wrongTarget.setSourceVersionId(999L);
        when(resumeVersionMapper.selectOne(any())).thenReturn(sourceVersion(), wrongTarget);

        assertThatThrownBy(() -> service.analyze(1L, 50L, parsedJob()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("优化任务与冻结简历版本不一致");

        verify(evidenceMatchingStrategy, never()).match(any(), any(), any(), any(), any(), any());
    }

    @Test
    void getResultShouldReturnNullWhenNoFormalAnalysisExists() {
        assertThat(service.getResult(1L, 50L)).isNull();
    }

    @Test
    void getResultShouldAssembleRequirementsWithGroupedEvidence() {
        EvidenceAnalysis analysis = new EvidenceAnalysis();
        analysis.setId(70L);
        analysis.setUserId(1L);
        analysis.setOptimizationTaskId(50L);
        analysis.setMatchedCount(1);
        analysis.setPartialEvidenceCount(0);
        analysis.setNoEvidenceCount(0);
        when(evidenceAnalysisMapper.selectOne(any())).thenReturn(analysis);
        EvidenceRequirement requirement = new EvidenceRequirement();
        requirement.setId(80L);
        requirement.setEvidenceAnalysisId(70L);
        requirement.setRequirementText("熟悉 Java");
        requirement.setImportance("REQUIRED");
        requirement.setMatchLevel("MATCHED");
        requirement.setConclusion("已有证据");
        requirement.setDisplayOrder(0);
        when(evidenceRequirementMapper.selectList(any())).thenReturn(List.of(requirement));
        RequirementEvidence evidence = new RequirementEvidence();
        evidence.setId(90L);
        evidence.setEvidenceRequirementId(80L);
        evidence.setSectionLabel("技能");
        evidence.setEvidenceText("熟悉 Java");
        evidence.setSupportLevel("SUFFICIENT");
        when(requirementEvidenceMapper.selectList(any())).thenReturn(List.of(evidence));

        EvidenceAnalysisResultVO result = service.getResult(1L, 50L);

        assertThat(result).isNotNull();
        assertThat(result.getEvidenceAnalysisId()).isEqualTo(70L);
        assertThat(result.getMatchedCount()).isEqualTo(1);
        assertThat(result.getRequirements()).hasSize(1);
        assertThat(result.getRequirements().get(0).getEvidences()).hasSize(1);
        assertThat(result.getRequirements().get(0).getEvidences().get(0).getEvidenceText()).isEqualTo("熟悉 Java");
    }

    private OptimizationTask task() {
        OptimizationTask task = new OptimizationTask();
        task.setId(50L);
        task.setUserId(1L);
        task.setSourceResumeVersionId(40L);
        task.setTargetResumeVersionId(41L);
        task.setJobTargetId(30L);
        task.setResumeInputSnapshot("{\"skills\":[\"熟悉 Java\"]}");
        task.setJobInputSnapshot("岗位要求 Java");
        return task;
    }

    private JobTarget jobTarget() {
        JobTarget target = new JobTarget();
        target.setId(30L);
        target.setUserId(1L);
        target.setLegacyJobDescriptionId(20L);
        target.setRawJd("岗位要求 Java");
        return target;
    }

    private ResumeVersion sourceVersion() {
        ResumeVersion version = new ResumeVersion();
        version.setId(40L);
        version.setUserId(1L);
        version.setResumeId(10L);
        version.setVersionType("SOURCE");
        version.setStructuredContent("{\"skills\":[\"熟悉 Java\"]}");
        return version;
    }

    private ResumeVersion targetVersion() {
        ResumeVersion version = new ResumeVersion();
        version.setId(41L);
        version.setUserId(1L);
        version.setResumeId(10L);
        version.setVersionType("TARGETED");
        version.setSourceVersionId(40L);
        version.setJobTargetId(30L);
        return version;
    }

    private JobDescriptionVO parsedJob() {
        return JobDescriptionVO.builder()
                .id(20L)
                .parseStatus("SUCCESS")
                .structuredContent("{\"requiredSkills\":[\"Java\"]}")
                .build();
    }

    private EvidenceMatchOutcomeDTO outcome() {
        return EvidenceMatchOutcomeDTO.builder()
                .modelName("test-model")
                .promptVersion("evidence_match_v3")
                .requirements(List.of(
                        EvidenceRequirementEvaluationDTO.builder()
                                .requirementText("熟悉 Java")
                                .importance(RequirementImportance.REQUIRED)
                                .matchLevel(EvidenceMatchLevel.MATCHED)
                                .conclusion("已有证据")
                                .suggestion("")
                                .evidences(List.of(EvidenceQuoteDTO.builder()
                                        .sectionLabel("技能")
                                        .quote("熟悉 Java")
                                        .supportLevel(EvidenceSupportLevel.SUFFICIENT)
                                        .build()))
                                .build(),
                        EvidenceRequirementEvaluationDTO.builder()
                                .requirementText("具备 Redis 经验")
                                .importance(RequirementImportance.REQUIRED)
                                .matchLevel(EvidenceMatchLevel.PARTIAL_EVIDENCE)
                                .conclusion("当前材料证据不完整")
                                .suggestion("建议完善已有内容")
                                .evidences(List.of(EvidenceQuoteDTO.builder()
                                        .sectionLabel("技能")
                                        .quote("熟悉 Redis")
                                        .supportLevel(EvidenceSupportLevel.PARTIAL)
                                        .build()))
                                .build(),
                        EvidenceRequirementEvaluationDTO.builder()
                                .requirementText("具备 Kafka 经验")
                                .importance(RequirementImportance.BONUS)
                                .matchLevel(EvidenceMatchLevel.NO_EVIDENCE)
                                .conclusion("当前材料未提供证据")
                                .suggestion("确认经历")
                                .evidences(List.of())
                                .build()))
                .build();
    }
}
