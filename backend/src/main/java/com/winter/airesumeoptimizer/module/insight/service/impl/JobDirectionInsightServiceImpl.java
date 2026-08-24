package com.winter.airesumeoptimizer.module.insight.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.evidence.entity.EvidenceAnalysis;
import com.winter.airesumeoptimizer.module.evidence.entity.EvidenceRequirement;
import com.winter.airesumeoptimizer.module.evidence.entity.RequirementEvidence;
import com.winter.airesumeoptimizer.module.evidence.mapper.EvidenceAnalysisMapper;
import com.winter.airesumeoptimizer.module.evidence.mapper.EvidenceRequirementMapper;
import com.winter.airesumeoptimizer.module.evidence.mapper.RequirementEvidenceMapper;
import com.winter.airesumeoptimizer.module.insight.service.JobDirectionInsightService;
import com.winter.airesumeoptimizer.module.insight.vo.JobDirectionCohortVO;
import com.winter.airesumeoptimizer.module.insight.vo.JobDirectionEvidenceVO;
import com.winter.airesumeoptimizer.module.insight.vo.JobDirectionInsightsVO;
import com.winter.airesumeoptimizer.module.insight.vo.JobDirectionRequirementSourceVO;
import com.winter.airesumeoptimizer.module.insight.vo.JobDirectionRequirementVO;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL-first, bounded read model for Phase 9. The service deliberately
 * recomputes from retained formal rows so retry replacement, parent deletion,
 * and time-window expiry cannot leave a second source of truth behind.
 */
@Service
public class JobDirectionInsightServiceImpl implements JobDirectionInsightService {

    static final int WINDOW_DAYS = 180;
    static final int MINIMUM_SAMPLE_SIZE = 8;
    static final int MAXIMUM_SAMPLE_SIZE = 20;
    private static final String TASK_STATUS_SUCCESS = "SUCCESS";
    private static final String VERSION_SOURCE = "SOURCE";
    private static final String SOURCE_PARSED_UPLOAD = "PARSED_UPLOAD";
    private static final String SOURCE_LEGACY_IMPORT = "LEGACY_IMPORT";

    private final OptimizationTaskMapper optimizationTaskMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final ResumeMapper resumeMapper;
    private final EvidenceAnalysisMapper evidenceAnalysisMapper;
    private final EvidenceRequirementMapper evidenceRequirementMapper;
    private final RequirementEvidenceMapper requirementEvidenceMapper;
    private final TechnicalRequirementAnchorRegistry anchorRegistry;

    @Autowired
    public JobDirectionInsightServiceImpl(
            OptimizationTaskMapper optimizationTaskMapper,
            ResumeVersionMapper resumeVersionMapper,
            ResumeMapper resumeMapper,
            EvidenceAnalysisMapper evidenceAnalysisMapper,
            EvidenceRequirementMapper evidenceRequirementMapper,
            RequirementEvidenceMapper requirementEvidenceMapper) {
        this(
                optimizationTaskMapper,
                resumeVersionMapper,
                resumeMapper,
                evidenceAnalysisMapper,
                evidenceRequirementMapper,
                requirementEvidenceMapper,
                new TechnicalRequirementAnchorRegistry());
    }

    JobDirectionInsightServiceImpl(
            OptimizationTaskMapper optimizationTaskMapper,
            ResumeVersionMapper resumeVersionMapper,
            ResumeMapper resumeMapper,
            EvidenceAnalysisMapper evidenceAnalysisMapper,
            EvidenceRequirementMapper evidenceRequirementMapper,
            RequirementEvidenceMapper requirementEvidenceMapper,
            TechnicalRequirementAnchorRegistry anchorRegistry) {
        this.optimizationTaskMapper = optimizationTaskMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.resumeMapper = resumeMapper;
        this.evidenceAnalysisMapper = evidenceAnalysisMapper;
        this.evidenceRequirementMapper = evidenceRequirementMapper;
        this.requirementEvidenceMapper = requirementEvidenceMapper;
        this.anchorRegistry = anchorRegistry;
    }

    @Override
    @Transactional(readOnly = true)
    public JobDirectionInsightsVO getInsights(Long userId) {
        validateUserId(userId);
        LocalDateTime windowStart = LocalDateTime.now().minusDays(WINDOW_DAYS);
        List<OptimizationTask> tasks = optimizationTaskMapper.selectList(
                new LambdaQueryWrapper<OptimizationTask>()
                        .eq(OptimizationTask::getUserId, userId)
                        .eq(OptimizationTask::getStatus, TASK_STATUS_SUCCESS)
                        .isNull(OptimizationTask::getLegacyMatchResultId)
                        .ge(OptimizationTask::getFinishedAt, windowStart)
                        .isNotNull(OptimizationTask::getFinishedAt)
                        .isNotNull(OptimizationTask::getSourceResumeVersionId)
                        .isNotNull(OptimizationTask::getResumeInputSnapshot)
                        .isNotNull(OptimizationTask::getJobInputSnapshot)
                        .orderByDesc(OptimizationTask::getFinishedAt)
                        .orderByDesc(OptimizationTask::getId));
        if (tasks == null || tasks.isEmpty()) {
            return JobDirectionInsightsVO.builder().cohorts(List.of()).build();
        }

        Map<Long, ResumeVersion> sourceVersions = ownedSourceVersions(userId, tasks);
        Map<Long, EvidenceAnalysis> analysesByTask = analysesByTask(userId, tasks);
        List<TaskSample> formalSamples = tasks.stream()
                .filter(task -> hasUsableTaskShape(task, sourceVersions, analysesByTask))
                .map(task -> new TaskSample(
                        task,
                        sourceVersions.get(task.getSourceResumeVersionId()),
                        analysesByTask.get(task.getId())))
                .toList();
        if (formalSamples.isEmpty()) {
            return JobDirectionInsightsVO.builder().cohorts(List.of()).build();
        }

        Map<Long, String> resumeNames = resumeNames(userId, sourceVersions.values());
        Map<BaselineKey, List<TaskSample>> byBaseline = new LinkedHashMap<>();
        for (TaskSample sample : formalSamples) {
            BaselineKey baseline = new BaselineKey(
                    sample.sourceVersion().getResumeId(),
                    sha256(sample.task().getResumeInputSnapshot()));
            byBaseline.computeIfAbsent(baseline, ignored -> new ArrayList<>()).add(sample);
        }

        List<JobDirectionCohortVO> cohorts = new ArrayList<>();
        for (Map.Entry<BaselineKey, List<TaskSample>> entry : byBaseline.entrySet()) {
            List<TaskSample> distinctJobs = latestDistinctJobs(entry.getValue());
            if (distinctJobs.size() < MINIMUM_SAMPLE_SIZE) {
                continue;
            }
            List<TaskSample> bounded = distinctJobs.subList(0, Math.min(MAXIMUM_SAMPLE_SIZE, distinctJobs.size()));
            cohorts.add(toCohort(
                    userId,
                    entry.getKey(),
                    List.copyOf(bounded),
                    resumeNames.get(entry.getKey().resumeId()),
                    windowStart));
        }
        cohorts.sort(Comparator
                .comparing(JobDirectionCohortVO::getSampleSize, Comparator.reverseOrder())
                .thenComparing(JobDirectionCohortVO::getNewestAnalysisAt, Comparator.reverseOrder())
                .thenComparing(JobDirectionCohortVO::getResumeId));
        return JobDirectionInsightsVO.builder().cohorts(List.copyOf(cohorts)).build();
    }

    private Map<Long, ResumeVersion> ownedSourceVersions(Long userId, List<OptimizationTask> tasks) {
        List<Long> ids = tasks.stream()
                .map(OptimizationTask::getSourceResumeVersionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ResumeVersion> versions = resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                .in(ResumeVersion::getId, ids)
                .eq(ResumeVersion::getUserId, userId)
                .eq(ResumeVersion::getVersionType, VERSION_SOURCE)
                .eq(ResumeVersion::getSourceType, SOURCE_PARSED_UPLOAD));
        Map<Long, ResumeVersion> result = new HashMap<>();
        for (ResumeVersion version : nullSafe(versions)) {
            if (version != null && version.getId() != null) {
                result.put(version.getId(), version);
            }
        }
        return result;
    }

    private Map<Long, String> resumeNames(Long userId, Iterable<ResumeVersion> versions) {
        List<Long> resumeIds = new ArrayList<>();
        for (ResumeVersion version : versions) {
            if (version != null && version.getResumeId() != null && !resumeIds.contains(version.getResumeId())) {
                resumeIds.add(version.getResumeId());
            }
        }
        if (resumeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (Resume resume : nullSafe(resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                .in(Resume::getId, resumeIds)
                .eq(Resume::getUserId, userId)))) {
            if (resume != null && resume.getId() != null) {
                names.put(resume.getId(), resume.getOriginalFilename());
            }
        }
        return names;
    }

    private Map<Long, EvidenceAnalysis> analysesByTask(Long userId, List<OptimizationTask> tasks) {
        List<Long> taskIds = tasks.stream().map(OptimizationTask::getId).filter(Objects::nonNull).toList();
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, EvidenceAnalysis> result = new HashMap<>();
        for (EvidenceAnalysis analysis : nullSafe(evidenceAnalysisMapper.selectList(
                new LambdaQueryWrapper<EvidenceAnalysis>()
                        .in(EvidenceAnalysis::getOptimizationTaskId, taskIds)
                        .eq(EvidenceAnalysis::getUserId, userId)))) {
            if (analysis != null && analysis.getOptimizationTaskId() != null) {
                result.put(analysis.getOptimizationTaskId(), analysis);
            }
        }
        return result;
    }

    private boolean hasUsableTaskShape(
            OptimizationTask task,
            Map<Long, ResumeVersion> sourceVersions,
            Map<Long, EvidenceAnalysis> analysesByTask) {
        if (task == null
                || task.getId() == null
                || task.getLegacyMatchResultId() != null
                || task.getFinishedAt() == null
                || blank(task.getResumeInputSnapshot())
                || blank(task.getJobInputSnapshot())) {
            return false;
        }
        ResumeVersion source = sourceVersions.get(task.getSourceResumeVersionId());
        EvidenceAnalysis analysis = analysesByTask.get(task.getId());
        return source != null
                && source.getResumeId() != null
                && VERSION_SOURCE.equals(source.getVersionType())
                && SOURCE_PARSED_UPLOAD.equals(source.getSourceType())
                && !SOURCE_LEGACY_IMPORT.equals(source.getSourceType())
                && Objects.equals(source.getStructuredContent(), task.getResumeInputSnapshot())
                && analysis != null
                && Objects.equals(task.getId(), analysis.getOptimizationTaskId());
    }

    /** One latest successful formal task per Unicode-whitespace-normalized frozen JD. */
    private List<TaskSample> latestDistinctJobs(List<TaskSample> samples) {
        List<TaskSample> ordered = samples.stream()
                .sorted(taskSampleComparator())
                .toList();
        Map<String, TaskSample> latestByJob = new LinkedHashMap<>();
        for (TaskSample sample : ordered) {
            latestByJob.putIfAbsent(sha256(canonicalText(sample.task().getJobInputSnapshot())), sample);
        }
        return List.copyOf(latestByJob.values());
    }

    private Comparator<TaskSample> taskSampleComparator() {
        return Comparator
                .comparing((TaskSample sample) -> sample.task().getFinishedAt(), Comparator.reverseOrder())
                .thenComparing(sample -> sample.task().getId(), Comparator.reverseOrder());
    }

    private JobDirectionCohortVO toCohort(
            Long userId,
            BaselineKey baseline,
            List<TaskSample> samples,
            String resumeName,
            LocalDateTime windowStart) {
        Map<Long, List<EvidenceRequirement>> requirementsByAnalysis = requirementsByAnalysis(userId, samples);
        Map<Long, List<RequirementEvidence>> evidencesByRequirement = evidencesByRequirement(
                userId,
                requirementsByAnalysis.values());
        Map<String, RequirementBucket> buckets = new LinkedHashMap<>();
        for (TaskSample sample : samples) {
            for (EvidenceRequirement requirement : requirementsByAnalysis
                    .getOrDefault(sample.analysis().getId(), List.of())) {
                if (requirement == null || blank(requirement.getRequirementText())) {
                    continue;
                }
                List<RequirementEvidence> sourceEvidences = evidencesByRequirement
                        .getOrDefault(requirement.getId(), List.of())
                        .stream()
                        .filter(evidence -> Objects.equals(
                                evidence.getSourceResumeVersionId(), sample.task().getSourceResumeVersionId()))
                        .toList();
                String effectiveLevel = effectiveMatchLevel(requirement.getMatchLevel(), sourceEvidences);
                if (effectiveLevel == null) {
                    continue;
                }
                RequirementGroup group = anchorRegistry.group(requirement.getRequirementText());
                RequirementBucket bucket = buckets.computeIfAbsent(
                        group.key(),
                        ignored -> new RequirementBucket(group));
                bucket.add(
                        sample.task().getId(),
                        requirement,
                        effectiveLevel,
                        sourceEvidences);
            }
        }

        int commonThreshold = Math.max(3, (samples.size() + 1) / 2);
        List<JobDirectionRequirementVO> commonRequirements = buckets.values().stream()
                .filter(bucket -> bucket.occurrenceCount() >= commonThreshold)
                .map(bucket -> bucket.toVO(samples.size()))
                .sorted(Comparator
                        .comparing(JobDirectionRequirementVO::getOccurrenceCount, Comparator.reverseOrder())
                        .thenComparing(requirement -> burden(requirement), Comparator.reverseOrder())
                        .thenComparing(JobDirectionRequirementVO::getLabel))
                .toList();
        LocalDateTime newest = samples.stream()
                .map(sample -> sample.task().getFinishedAt())
                .max(Comparator.naturalOrder())
                .orElse(null);
        return JobDirectionCohortVO.builder()
                .resumeId(baseline.resumeId())
                .resumeName(blank(resumeName) ? "我的简历" : resumeName)
                .sampleSize(samples.size())
                .minimumSampleSize(MINIMUM_SAMPLE_SIZE)
                .windowStart(windowStart)
                .newestAnalysisAt(newest)
                .commonRequirements(commonRequirements)
                .build();
    }

    private int burden(JobDirectionRequirementVO requirement) {
        return requirement.getPartialEvidenceCount() + requirement.getNoEvidenceCount();
    }

    private Map<Long, List<EvidenceRequirement>> requirementsByAnalysis(Long userId, List<TaskSample> samples) {
        List<Long> analysisIds = samples.stream().map(sample -> sample.analysis().getId()).toList();
        if (analysisIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<EvidenceRequirement>> result = new HashMap<>();
        for (EvidenceRequirement requirement : nullSafe(evidenceRequirementMapper.selectList(
                new LambdaQueryWrapper<EvidenceRequirement>()
                        .in(EvidenceRequirement::getEvidenceAnalysisId, analysisIds)
                        .eq(EvidenceRequirement::getUserId, userId)
                        .orderByAsc(EvidenceRequirement::getDisplayOrder)
                        .orderByAsc(EvidenceRequirement::getId)))) {
            if (requirement != null && requirement.getEvidenceAnalysisId() != null && requirement.getId() != null) {
                result.computeIfAbsent(requirement.getEvidenceAnalysisId(), ignored -> new ArrayList<>()).add(requirement);
            }
        }
        return result;
    }

    private Map<Long, List<RequirementEvidence>> evidencesByRequirement(
            Long userId,
            Iterable<List<EvidenceRequirement>> requirementLists) {
        List<Long> requirementIds = new ArrayList<>();
        for (List<EvidenceRequirement> requirements : requirementLists) {
            for (EvidenceRequirement requirement : requirements) {
                if (requirement != null && requirement.getId() != null) {
                    requirementIds.add(requirement.getId());
                }
            }
        }
        if (requirementIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<RequirementEvidence>> result = new HashMap<>();
        for (RequirementEvidence evidence : nullSafe(requirementEvidenceMapper.selectList(
                new LambdaQueryWrapper<RequirementEvidence>()
                        .in(RequirementEvidence::getEvidenceRequirementId, requirementIds)
                        .eq(RequirementEvidence::getUserId, userId)
                        .orderByAsc(RequirementEvidence::getId)))) {
            if (evidence != null && evidence.getEvidenceRequirementId() != null) {
                result.computeIfAbsent(evidence.getEvidenceRequirementId(), ignored -> new ArrayList<>()).add(evidence);
            }
        }
        return result;
    }

    /** A positive formal state without a quote from this task's SOURCE is rendered conservatively. */
    private String effectiveMatchLevel(String declared, List<RequirementEvidence> evidences) {
        if ("NO_EVIDENCE".equals(declared)) {
            return "NO_EVIDENCE";
        }
        if ("MATCHED".equals(declared) || "PARTIAL_EVIDENCE".equals(declared)) {
            return evidences == null || evidences.isEmpty() ? "NO_EVIDENCE" : declared;
        }
        return null;
    }

    private static String canonicalText(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFC);
        StringBuilder result = new StringBuilder(normalized.length());
        boolean pendingWhitespace = false;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingWhitespace = !result.isEmpty();
                continue;
            }
            if (pendingWhitespace) {
                result.append(' ');
                pendingWhitespace = false;
            }
            result.appendCodePoint(codePoint);
        }
        return result.toString();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(401, "请先登录");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record TaskSample(OptimizationTask task, ResumeVersion sourceVersion, EvidenceAnalysis analysis) {
    }

    private record BaselineKey(Long resumeId, String snapshotHash) {
    }

    private record RequirementGroup(String key, String anchorDisplay) {

        boolean anchored() {
            return anchorDisplay != null;
        }
    }

    private static final class RequirementBucket {

        private final RequirementGroup group;
        private final Map<Long, TaskContribution> contributions = new LinkedHashMap<>();
        private final Map<String, Integer> exactLabels = new HashMap<>();

        private RequirementBucket(RequirementGroup group) {
            this.group = group;
        }

        void add(
                Long taskId,
                EvidenceRequirement requirement,
                String effectiveLevel,
                List<RequirementEvidence> evidences) {
            exactLabels.merge(requirement.getRequirementText(), 1, Integer::sum);
            TaskContribution contribution = contributions.computeIfAbsent(taskId, ignored -> new TaskContribution());
            contribution.accept(effectiveLevel);
            contribution.sources.add(JobDirectionRequirementSourceVO.builder()
                    .optimizationTaskId(taskId)
                    .evidenceRequirementId(requirement.getId())
                    .requirementText(requirement.getRequirementText())
                    .matchLevel(effectiveLevel)
                    .evidences(evidences.stream()
                            .map(evidence -> JobDirectionEvidenceVO.builder()
                                    .requirementEvidenceId(evidence.getId())
                                    .sectionLabel(evidence.getSectionLabel())
                                    .evidenceText(evidence.getEvidenceText())
                                    .supportLevel(evidence.getSupportLevel())
                                    .build())
                            .toList())
                    .build());
        }

        int occurrenceCount() {
            return contributions.size();
        }

        JobDirectionRequirementVO toVO(int sampleSize) {
            int matched = 0;
            int partial = 0;
            int noEvidence = 0;
            List<JobDirectionRequirementSourceVO> sources = new ArrayList<>();
            for (TaskContribution contribution : contributions.values()) {
                switch (contribution.level) {
                    case "MATCHED" -> matched++;
                    case "PARTIAL_EVIDENCE" -> partial++;
                    default -> noEvidence++;
                }
                sources.addAll(contribution.sources);
            }
            sources.sort(Comparator
                    .comparing(JobDirectionRequirementSourceVO::getOptimizationTaskId)
                    .thenComparing(JobDirectionRequirementSourceVO::getEvidenceRequirementId));
            return JobDirectionRequirementVO.builder()
                    .label(group.anchored()
                            ? "包含 " + group.anchorDisplay() + " 的岗位要求"
                            : preferredExactLabel())
                    .occurrenceCount(occurrenceCount())
                    .sampleSize(sampleSize)
                    .matchedCount(matched)
                    .partialEvidenceCount(partial)
                    .noEvidenceCount(noEvidence)
                    .sources(List.copyOf(sources))
                    .build();
        }

        private String preferredExactLabel() {
            return exactLabels.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                            .thenComparing(Map.Entry.comparingByKey()))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse("岗位要求");
        }
    }

    private static final class TaskContribution {

        private String level = "MATCHED";
        private final List<JobDirectionRequirementSourceVO> sources = new ArrayList<>();

        void accept(String candidate) {
            if (supportRank(candidate) > supportRank(level)) {
                level = candidate;
            }
        }

        private int supportRank(String value) {
            return switch (value) {
                case "NO_EVIDENCE" -> 2;
                case "PARTIAL_EVIDENCE" -> 1;
                default -> 0;
            };
        }
    }

    /**
     * Deliberately tiny, code-owned literal registry. It recognizes spelling
     * variants only; it never claims that related technologies are equivalent.
     */
    static final class TechnicalRequirementAnchorRegistry {

        private static final Map<String, Anchor> ANCHORS = Map.ofEntries(
                Map.entry("java", new Anchor("Java", List.of("java"))),
                Map.entry("spring-boot", new Anchor("Spring Boot", List.of("spring boot"))),
                Map.entry("mysql", new Anchor("MySQL", List.of("mysql"))),
                Map.entry("postgresql", new Anchor("PostgreSQL", List.of("postgresql", "postgres"))),
                Map.entry("redis", new Anchor("Redis", List.of("redis"))),
                Map.entry("kafka", new Anchor("Kafka", List.of("kafka"))),
                Map.entry("rabbitmq", new Anchor("RabbitMQ", List.of("rabbitmq"))),
                Map.entry("docker", new Anchor("Docker", List.of("docker"))),
                Map.entry("kubernetes", new Anchor("Kubernetes", List.of("kubernetes", "k8s"))),
                Map.entry("jvm", new Anchor("JVM", List.of("jvm"))),
                Map.entry("python", new Anchor("Python", List.of("python"))),
                Map.entry("golang", new Anchor("Go", List.of("golang"))),
                Map.entry("typescript", new Anchor("TypeScript", List.of("typescript"))),
                Map.entry("javascript", new Anchor("JavaScript", List.of("javascript"))),
                Map.entry("react", new Anchor("React", List.of("react"))),
                Map.entry("vue", new Anchor("Vue", List.of("vue"))));

        RequirementGroup group(String requirementText) {
            String canonical = canonicalRequirement(requirementText);
            Set<String> found = new java.util.LinkedHashSet<>();
            for (Map.Entry<String, Anchor> entry : ANCHORS.entrySet()) {
                if (entry.getValue().matches(canonical)) {
                    found.add(entry.getKey());
                }
            }
            if (found.size() == 1) {
                String key = found.iterator().next();
                return new RequirementGroup("anchor:" + key, ANCHORS.get(key).display());
            }
            return new RequirementGroup("exact:" + canonical, null);
        }

        private String canonicalRequirement(String value) {
            return canonicalText(value).toLowerCase(Locale.ROOT);
        }

        private record Anchor(String display, List<String> literals) {

            boolean matches(String value) {
                return literals.stream().anyMatch(literal -> Pattern.compile(
                                "(?<![a-z0-9_+#])" + Pattern.quote(literal) + "(?![a-z0-9_+#])")
                        .matcher(value)
                        .find());
            }
        }
    }
}
