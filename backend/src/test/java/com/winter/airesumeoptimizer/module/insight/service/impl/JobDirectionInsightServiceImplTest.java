package com.winter.airesumeoptimizer.module.insight.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.winter.airesumeoptimizer.module.evidence.entity.EvidenceAnalysis;
import com.winter.airesumeoptimizer.module.evidence.entity.EvidenceRequirement;
import com.winter.airesumeoptimizer.module.evidence.entity.RequirementEvidence;
import com.winter.airesumeoptimizer.module.evidence.mapper.EvidenceAnalysisMapper;
import com.winter.airesumeoptimizer.module.evidence.mapper.EvidenceRequirementMapper;
import com.winter.airesumeoptimizer.module.evidence.mapper.RequirementEvidenceMapper;
import com.winter.airesumeoptimizer.module.insight.vo.JobDirectionCohortVO;
import com.winter.airesumeoptimizer.module.insight.vo.JobDirectionRequirementVO;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobDirectionInsightServiceImplTest {

    static {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), OptimizationTask.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ResumeVersion.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Resume.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), EvidenceAnalysis.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), EvidenceRequirement.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RequirementEvidence.class);
    }

    private final OptimizationTaskMapper taskMapper = mock(OptimizationTaskMapper.class);
    private final ResumeVersionMapper resumeVersionMapper = mock(ResumeVersionMapper.class);
    private final ResumeMapper resumeMapper = mock(ResumeMapper.class);
    private final EvidenceAnalysisMapper analysisMapper = mock(EvidenceAnalysisMapper.class);
    private final EvidenceRequirementMapper requirementMapper = mock(EvidenceRequirementMapper.class);
    private final RequirementEvidenceMapper requirementEvidenceMapper = mock(RequirementEvidenceMapper.class);
    private final JobDirectionInsightServiceImpl service = new JobDirectionInsightServiceImpl(
            taskMapper,
            resumeVersionMapper,
            resumeMapper,
            analysisMapper,
            requirementMapper,
            requirementEvidenceMapper);

    private List<OptimizationTask> tasks;
    private List<ResumeVersion> sourceVersions;
    private List<EvidenceAnalysis> analyses;
    private List<EvidenceRequirement> requirements;
    private List<RequirementEvidence> evidences;

    @BeforeEach
    void setUp() {
        tasks = new ArrayList<>();
        sourceVersions = new ArrayList<>();
        analyses = new ArrayList<>();
        requirements = new ArrayList<>();
        evidences = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            long taskId = 100L + index;
            long sourceVersionId = 200L + index;
            long analysisId = 300L + index;
            tasks.add(task(taskId, sourceVersionId, "Java 后端岗位 " + index, index));
            sourceVersions.add(sourceVersion(sourceVersionId));
            analyses.add(analysis(analysisId, taskId));

            String level = switch (index) {
                case 0, 1, 2 -> "MATCHED";
                case 3, 4, 5 -> "PARTIAL_EVIDENCE";
                default -> "NO_EVIDENCE";
            };
            EvidenceRequirement java = requirement(
                    400L + index,
                    analysisId,
                    switch (index % 3) {
                        case 0 -> "Java";
                        case 1 -> "熟悉 Java";
                        default -> "Java 后端经验";
                    },
                    level);
            requirements.add(java);
            if (!"NO_EVIDENCE".equals(level)) {
                evidences.add(evidence(500L + index, java.getId(), sourceVersionId));
            }
            if (index < 4) {
                requirements.add(requirement(600L + index, analysisId, "具备团队协作能力", "MATCHED"));
                evidences.add(evidence(700L + index, 600L + index, sourceVersionId));
            }
        }
        Resume resume = new Resume();
        resume.setId(10L);
        resume.setUserId(1L);
        resume.setOriginalFilename("synthetic-java-resume.pdf");

        when(taskMapper.selectList(any())).thenReturn(tasks);
        when(resumeVersionMapper.selectList(any())).thenReturn(sourceVersions);
        when(resumeMapper.selectList(any())).thenReturn(List.of(resume));
        when(analysisMapper.selectList(any())).thenReturn(analyses);
        when(requirementMapper.selectList(any())).thenReturn(requirements);
        when(requirementEvidenceMapper.selectList(any())).thenReturn(evidences);
    }

    @Test
    void aggregatesCompatibleFrozenBaselineWithLiteralJavaAnchorAndThreeStateDistribution() {
        JobDirectionCohortVO cohort = service.getInsights(1L).getCohorts().getFirst();

        assertThat(cohort.getSampleSize()).isEqualTo(8);
        assertThat(cohort.getMinimumSampleSize()).isEqualTo(8);
        assertThat(cohort.getResumeName()).isEqualTo("synthetic-java-resume.pdf");
        JobDirectionRequirementVO java = cohort.getCommonRequirements().stream()
                .filter(requirement -> requirement.getLabel().equals("包含 Java 的岗位要求"))
                .findFirst()
                .orElseThrow();
        assertThat(java.getOccurrenceCount()).isEqualTo(8);
        assertThat(java.getMatchedCount()).isEqualTo(3);
        assertThat(java.getPartialEvidenceCount()).isEqualTo(3);
        assertThat(java.getNoEvidenceCount()).isEqualTo(2);
        assertThat(java.getSources()).hasSize(8);
        assertThat(java.getSources())
                .filteredOn(source -> !"NO_EVIDENCE".equals(source.getMatchLevel()))
                .allSatisfy(source -> assertThat(source.getEvidences()).isNotEmpty());

        JobDirectionRequirementVO exact = cohort.getCommonRequirements().stream()
                .filter(requirement -> requirement.getLabel().equals("具备团队协作能力"))
                .findFirst()
                .orElseThrow();
        assertThat(exact.getOccurrenceCount()).isEqualTo(4);
    }

    @Test
    void keepsOnlyLatestTaskForTheSameFrozenJobAndCapsTheSampleAtTwenty() {
        OptimizationTask olderDuplicate = task(999L, 299L, "Java 后端岗位 0", 9);
        olderDuplicate.setFinishedAt(LocalDateTime.now().minusDays(10));
        ResumeVersion duplicateVersion = sourceVersion(299L);
        EvidenceAnalysis duplicateAnalysis = analysis(399L, 999L);
        EvidenceRequirement duplicateRequirement = requirement(899L, 399L, "Java", "NO_EVIDENCE");
        tasks.add(olderDuplicate);
        sourceVersions.add(duplicateVersion);
        analyses.add(duplicateAnalysis);
        requirements.add(duplicateRequirement);

        JobDirectionCohortVO cohort = service.getInsights(1L).getCohorts().getFirst();
        JobDirectionRequirementVO java = cohort.getCommonRequirements().stream()
                .filter(requirement -> requirement.getLabel().equals("包含 Java 的岗位要求"))
                .findFirst()
                .orElseThrow();

        assertThat(cohort.getSampleSize()).isEqualTo(8);
        assertThat(java.getSources())
                .extracting(source -> source.getOptimizationTaskId())
                .doesNotContain(999L);
    }

    @Test
    void hidesCohortsBelowTheEightDistinctFrozenJobThreshold() {
        tasks.removeLast();
        sourceVersions.removeLast();
        analyses.removeLast();

        assertThat(service.getInsights(1L).getCohorts()).isEmpty();
    }

    @Test
    void usesOnlyTheTwentyMostRecentDistinctFrozenJobsPerCohort() {
        for (int index = 8; index < 25; index++) {
            long taskId = 100L + index;
            long sourceVersionId = 200L + index;
            long analysisId = 300L + index;
            long requirementId = 400L + index;
            tasks.add(task(taskId, sourceVersionId, "Java 后端岗位 " + index, index));
            sourceVersions.add(sourceVersion(sourceVersionId));
            analyses.add(analysis(analysisId, taskId));
            requirements.add(requirement(requirementId, analysisId, "Java", "MATCHED"));
            evidences.add(evidence(500L + index, requirementId, sourceVersionId));
        }

        JobDirectionRequirementVO java = service.getInsights(1L).getCohorts().getFirst().getCommonRequirements().stream()
                .filter(requirement -> requirement.getLabel().equals("包含 Java 的岗位要求"))
                .findFirst()
                .orElseThrow();

        assertThat(java.getSampleSize()).isEqualTo(20);
        assertThat(java.getSources())
                .extracting(source -> source.getOptimizationTaskId())
                .doesNotContain(124L);
    }

    @Test
    void excludesSuccessTasksWithoutAFormalEvidenceAnalysis() {
        analyses.removeFirst();

        assertThat(service.getInsights(1L).getCohorts()).isEmpty();
    }

    @Test
    void usesTheMostConservativeCoverageForRepeatedRequirementsInOneJob() {
        EvidenceRequirement noEvidenceDuplicate = requirement(
                9999L,
                analyses.getFirst().getId(),
                "熟悉 Java",
                "NO_EVIDENCE");
        requirements.add(noEvidenceDuplicate);

        JobDirectionRequirementVO java = service.getInsights(1L).getCohorts().getFirst().getCommonRequirements().stream()
                .filter(requirement -> requirement.getLabel().equals("包含 Java 的岗位要求"))
                .findFirst()
                .orElseThrow();

        assertThat(java.getMatchedCount()).isEqualTo(2);
        assertThat(java.getPartialEvidenceCount()).isEqualTo(3);
        assertThat(java.getNoEvidenceCount()).isEqualTo(3);
    }

    @Test
    void excludesLegacyTasksEvenIfTheyHaveOtherwiseFormalEvidenceRows() {
        tasks.getFirst().setLegacyMatchResultId(777L);

        assertThat(service.getInsights(1L).getCohorts()).isEmpty();
    }

    @Test
    void excludesLegacyImportedSourceVersionsEvenIfTheTaskLooksFormal() {
        sourceVersions.getFirst().setSourceType("LEGACY_IMPORT");

        assertThat(service.getInsights(1L).getCohorts()).isEmpty();
    }

    @Test
    void deduplicatesUnicodeWhitespaceVariantsOfTheSameFrozenJobSnapshot() {
        tasks.getFirst().setJobInputSnapshot("Java backend");
        OptimizationTask newestDuplicate = task(1000L, 2000L, "Java\u00A0backend", -1);
        EvidenceAnalysis newestAnalysis = analysis(3000L, 1000L);
        EvidenceRequirement newestRequirement = requirement(4000L, 3000L, "Java", "MATCHED");
        tasks.add(newestDuplicate);
        sourceVersions.add(sourceVersion(2000L));
        analyses.add(newestAnalysis);
        requirements.add(newestRequirement);
        evidences.add(evidence(5000L, 4000L, 2000L));

        JobDirectionCohortVO cohort = service.getInsights(1L).getCohorts().getFirst();

        assertThat(cohort.getSampleSize()).isEqualTo(8);
        JobDirectionRequirementVO java = cohort.getCommonRequirements().stream()
                .filter(requirement -> requirement.getLabel().equals("包含 Java 的岗位要求"))
                .findFirst()
                .orElseThrow();
        assertThat(java.getSources())
                .extracting(source -> source.getOptimizationTaskId())
                .contains(1000L)
                .doesNotContain(100L);
    }

    @Test
    void keepsDistinctFrozenJdsDistinctWhenTheirNonWhitespaceContentDiffers() {
        tasks.getFirst().setJobInputSnapshot("Java backend");
        OptimizationTask distinctJob = task(1001L, 2001L, "Java backend!", -1);
        tasks.add(distinctJob);
        sourceVersions.add(sourceVersion(2001L));
        analyses.add(analysis(3001L, 1001L));
        EvidenceRequirement requirement = requirement(4001L, 3001L, "Java", "MATCHED");
        requirements.add(requirement);
        evidences.add(evidence(5001L, 4001L, 2001L));

        assertThat(service.getInsights(1L).getCohorts().getFirst().getSampleSize()).isEqualTo(9);
    }

    @Test
    void keepsUnderscoredIdentifiersOutOfLiteralJavaAnchorGrouping() {
        requirements.stream()
                .filter(requirement -> requirement.getRequirementText().contains("Java"))
                .forEach(requirement -> requirement.setRequirementText("java_script"));

        JobDirectionCohortVO cohort = service.getInsights(1L).getCohorts().getFirst();

        assertThat(cohort.getCommonRequirements())
                .extracting(JobDirectionRequirementVO::getLabel)
                .contains("java_script")
                .doesNotContain("包含 Java 的岗位要求");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void neverAppliesAGlobalRawTaskLimitBeforePerCohortDeduplication() {
        service.getInsights(1L);

        ArgumentCaptor<LambdaQueryWrapper<OptimizationTask>> queryCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(taskMapper).selectList(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment()).doesNotContain("LIMIT");
    }

    @Test
    void doesNotMixAChangedFrozenSnapshotEvenWhenTheResumeIdMatches() {
        OptimizationTask changedSnapshot = task(888L, 288L, "Java 新岗位", 8);
        changedSnapshot.setResumeInputSnapshot("{\"skills\":[\"Java\",\"Kafka\"]}");
        tasks.add(changedSnapshot);
        sourceVersions.add(sourceVersion(288L));
        analyses.add(analysis(388L, 888L));
        requirements.add(requirement(8888L, 388L, "Java", "MATCHED"));
        evidences.add(evidence(9888L, 8888L, 288L));

        assertThat(service.getInsights(1L).getCohorts()).hasSize(1);
        assertThat(service.getInsights(1L).getCohorts().getFirst().getSampleSize()).isEqualTo(8);
    }

    private OptimizationTask task(long id, long sourceVersionId, String job, int recencyOffset) {
        OptimizationTask task = new OptimizationTask();
        task.setId(id);
        task.setUserId(1L);
        task.setStatus("SUCCESS");
        task.setSourceResumeVersionId(sourceVersionId);
        task.setJobTargetId(id + 1000);
        task.setResumeInputSnapshot("{\"skills\":[\"Java\"]}");
        task.setJobInputSnapshot(job);
        task.setFinishedAt(LocalDateTime.now().minusDays(recencyOffset + 1));
        return task;
    }

    private ResumeVersion sourceVersion(long id) {
        ResumeVersion version = new ResumeVersion();
        version.setId(id);
        version.setUserId(1L);
        version.setResumeId(10L);
        version.setVersionType("SOURCE");
        version.setSourceType("PARSED_UPLOAD");
        version.setStructuredContent("{\"skills\":[\"Java\"]}");
        return version;
    }

    private EvidenceAnalysis analysis(long id, long taskId) {
        EvidenceAnalysis analysis = new EvidenceAnalysis();
        analysis.setId(id);
        analysis.setUserId(1L);
        analysis.setOptimizationTaskId(taskId);
        return analysis;
    }

    private EvidenceRequirement requirement(long id, long analysisId, String text, String level) {
        EvidenceRequirement requirement = new EvidenceRequirement();
        requirement.setId(id);
        requirement.setUserId(1L);
        requirement.setEvidenceAnalysisId(analysisId);
        requirement.setRequirementText(text);
        requirement.setMatchLevel(level);
        requirement.setDisplayOrder(0);
        return requirement;
    }

    private RequirementEvidence evidence(long id, long requirementId, long sourceVersionId) {
        RequirementEvidence evidence = new RequirementEvidence();
        evidence.setId(id);
        evidence.setUserId(1L);
        evidence.setEvidenceRequirementId(requirementId);
        evidence.setSourceResumeVersionId(sourceVersionId);
        evidence.setSectionLabel("技能");
        evidence.setEvidenceText("熟悉 Java");
        evidence.setSupportLevel("SUFFICIENT");
        return evidence;
    }
}
