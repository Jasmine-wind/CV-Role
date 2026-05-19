package com.winter.airesumeoptimizer.module.history.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiResumeSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiRewriteSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.ResumeAiAnalysisMapper;
import com.winter.airesumeoptimizer.module.history.service.AiHistoryService;
import com.winter.airesumeoptimizer.module.history.vo.AiResultDetailVO;
import com.winter.airesumeoptimizer.module.history.vo.AiResultPageVO;
import com.winter.airesumeoptimizer.module.history.vo.AiResultRecordVO;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AiHistoryServiceImpl implements AiHistoryService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final int PREVIEW_LENGTH = 120;
    private static final String TYPE_RESUME_DIAGNOSIS = "RESUME_DIAGNOSIS";
    private static final String TYPE_TARGET_JOB_PARSE = "TARGET_JOB_PARSE";
    private static final String TYPE_MATCH_ANALYSIS = "MATCH_ANALYSIS";
    private static final String TYPE_JOB_OPTIMIZATION_SUGGESTION = "JOB_OPTIMIZATION_SUGGESTION";
    private static final String TYPE_LOCAL_REWRITE = "LOCAL_REWRITE";
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            TYPE_RESUME_DIAGNOSIS,
            TYPE_TARGET_JOB_PARSE,
            TYPE_MATCH_ANALYSIS,
            TYPE_JOB_OPTIMIZATION_SUGGESTION,
            TYPE_LOCAL_REWRITE);
    private static final Set<String> SUPPORTED_STATUSES = Set.of("PENDING", "SUCCESS", "FAILED");

    private final ResumeMapper resumeMapper;
    private final JobDescriptionMapper jobDescriptionMapper;
    private final ResumeAiAnalysisMapper resumeAiAnalysisMapper;
    private final AiJobMatchResultMapper aiJobMatchResultMapper;
    private final AiResumeSuggestionMapper aiResumeSuggestionMapper;
    private final AiRewriteSuggestionMapper aiRewriteSuggestionMapper;
    private final ObjectMapper objectMapper;

    public AiHistoryServiceImpl(
            ResumeMapper resumeMapper,
            JobDescriptionMapper jobDescriptionMapper,
            ResumeAiAnalysisMapper resumeAiAnalysisMapper,
            AiJobMatchResultMapper aiJobMatchResultMapper,
            AiResumeSuggestionMapper aiResumeSuggestionMapper,
            AiRewriteSuggestionMapper aiRewriteSuggestionMapper,
            ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.resumeAiAnalysisMapper = resumeAiAnalysisMapper;
        this.aiJobMatchResultMapper = aiJobMatchResultMapper;
        this.aiResumeSuggestionMapper = aiResumeSuggestionMapper;
        this.aiRewriteSuggestionMapper = aiRewriteSuggestionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiResultPageVO list(
            Long userId,
            String resultType,
            Long resumeId,
            Long jobDescriptionId,
            String status,
            Integer page,
            Integer size) {
        validateUserId(userId);
        String normalizedType = normalizeType(resultType);
        String normalizedStatus = normalizeStatus(status);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);

        List<Resume> resumes = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId));
        List<JobDescription> jobDescriptions = jobDescriptionMapper.selectList(new LambdaQueryWrapper<JobDescription>()
                .eq(JobDescription::getUserId, userId));
        Map<Long, Resume> resumeMap = resumes.stream()
                .collect(Collectors.toMap(Resume::getId, Function.identity()));
        Map<Long, JobDescription> jobDescriptionMap = jobDescriptions.stream()
                .collect(Collectors.toMap(JobDescription::getId, Function.identity()));

        if (resumeId != null && !resumeMap.containsKey(resumeId)) {
            return emptyPage(safePage, safeSize);
        }
        if (jobDescriptionId != null && !jobDescriptionMap.containsKey(jobDescriptionId)) {
            return emptyPage(safePage, safeSize);
        }

        List<AiResultRecordVO> records = new ArrayList<>();
        if (shouldIncludeType(normalizedType, TYPE_RESUME_DIAGNOSIS) && jobDescriptionId == null) {
            records.addAll(listResumeDiagnoses(resumeMap, resumeId, normalizedStatus));
        }
        if (shouldIncludeType(normalizedType, TYPE_TARGET_JOB_PARSE) && resumeId == null) {
            records.addAll(listTargetJobParses(jobDescriptionMap, jobDescriptionId, normalizedStatus));
        }
        if (shouldIncludeType(normalizedType, TYPE_MATCH_ANALYSIS)) {
            records.addAll(listMatchAnalyses(resumeMap, jobDescriptionMap, resumeId, jobDescriptionId, normalizedStatus));
        }
        if (shouldIncludeType(normalizedType, TYPE_JOB_OPTIMIZATION_SUGGESTION)) {
            records.addAll(listOptimizationSuggestions(
                    resumeMap,
                    jobDescriptionMap,
                    resumeId,
                    jobDescriptionId,
                    normalizedStatus));
        }
        if (shouldIncludeType(normalizedType, TYPE_LOCAL_REWRITE)) {
            records.addAll(listLocalRewrites(resumeMap, jobDescriptionMap, resumeId, jobDescriptionId, normalizedStatus));
        }

        List<AiResultRecordVO> sortedRecords = records.stream()
                .sorted(Comparator.comparing(this::sortTime, this::compareNullableTime)
                        .thenComparing(AiResultRecordVO::getRecordId, Comparator.nullsLast(Long::compareTo))
                        .reversed())
                .toList();

        int total = sortedRecords.size();
        int fromIndex = Math.min((safePage - 1) * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        return AiResultPageVO.builder()
                .records(sortedRecords.subList(fromIndex, toIndex))
                .page(safePage)
                .size(safeSize)
                .total((long) total)
                .totalPages(totalPages)
                .build();
    }

    @Override
    public AiResultDetailVO detail(Long userId, String resultType, Long recordId) {
        validateUserId(userId);
        if (recordId == null) {
            throw new BusinessException(400, "AI 结果记录 ID 不能为空");
        }
        String normalizedType = normalizeType(resultType);
        return switch (normalizedType) {
            case TYPE_RESUME_DIAGNOSIS -> getResumeDiagnosisDetail(userId, recordId);
            case TYPE_TARGET_JOB_PARSE -> getTargetJobParseDetail(userId, recordId);
            case TYPE_MATCH_ANALYSIS -> getMatchAnalysisDetail(userId, recordId);
            case TYPE_JOB_OPTIMIZATION_SUGGESTION -> getOptimizationSuggestionDetail(userId, recordId);
            case TYPE_LOCAL_REWRITE -> getLocalRewriteDetail(userId, recordId);
            default -> throw new BusinessException(400, "AI 结果类型不支持");
        };
    }

    private AiResultDetailVO getResumeDiagnosisDetail(Long userId, Long recordId) {
        ResumeAiAnalysis analysis = resumeAiAnalysisMapper.selectById(recordId);
        if (analysis == null) {
            throw new BusinessException(404, "AI 结果不存在");
        }
        Resume resume = getOwnedResume(userId, analysis.getResumeId());
        return AiResultDetailVO.builder()
                .recordId(analysis.getId())
                .resultType(TYPE_RESUME_DIAGNOSIS)
                .title("简历诊断 - " + safeResumeName(resume))
                .status(analysis.getAnalysisStatus())
                .content(contentMap(
                        "score", nullableValue(analysis.getScore()),
                        "strengths", readJsonValue(analysis.getStrengths()),
                        "problems", readJsonValue(analysis.getProblems()),
                        "suggestionsSummary", readJsonValue(analysis.getSuggestionsSummary())))
                .resumeId(analysis.getResumeId())
                .resumeName(safeResumeName(resume))
                .modelName(analysis.getModelName())
                .promptVersion(analysis.getPromptVersion())
                .errorMessage(analysis.getErrorMessage())
                .createdAt(analysis.getCreatedAt())
                .updatedAt(analysis.getUpdatedAt())
                .build();
    }

    private AiResultDetailVO getTargetJobParseDetail(Long userId, Long recordId) {
        JobDescription jobDescription = jobDescriptionMapper.selectById(recordId);
        if (jobDescription == null || !userId.equals(jobDescription.getUserId())) {
            throw new BusinessException(404, "AI 结果不存在");
        }
        return AiResultDetailVO.builder()
                .recordId(jobDescription.getId())
                .resultType(TYPE_TARGET_JOB_PARSE)
                .title("目标岗位解析 - " + safeJobTitle(jobDescription))
                .status(jobDescription.getParseStatus())
                .content(contentMap(
                        "sourceType", nullableValue(jobDescription.getSourceType()),
                        "rawTextPreview", nullableValue(preview(jobDescription.getRawText())),
                        "structuredContent", readJsonValue(jobDescription.getStructuredContent())))
                .jobDescriptionId(jobDescription.getId())
                .jobTitle(safeJobTitle(jobDescription))
                .modelName(jobDescription.getModelName())
                .promptVersion(jobDescription.getPromptVersion())
                .errorMessage(jobDescription.getErrorMessage())
                .createdAt(jobDescription.getCreatedAt())
                .updatedAt(jobDescription.getUpdatedAt())
                .build();
    }

    private AiResultDetailVO getMatchAnalysisDetail(Long userId, Long recordId) {
        AiJobMatchResult match = aiJobMatchResultMapper.selectById(recordId);
        if (match == null) {
            throw new BusinessException(404, "AI 结果不存在");
        }
        Resume resume = getOwnedResume(userId, match.getResumeId());
        JobDescription jobDescription = getOwnedJobDescription(userId, match.getJobDescriptionId());
        return AiResultDetailVO.builder()
                .recordId(match.getId())
                .resultType(TYPE_MATCH_ANALYSIS)
                .title("匹配分析 - " + safeJobTitle(jobDescription))
                .status(match.getMatchStatus())
                .content(contentMap(
                        "overallScore", nullableValue(match.getOverallScore()),
                        "strongMatches", readJsonValue(match.getStrongMatches()),
                        "weakMatches", readJsonValue(match.getWeakMatches()),
                        "missingSkills", readJsonValue(match.getMissingSkills()),
                        "weakExperienceDescriptions", readJsonValue(match.getWeakExperienceDescriptions()),
                        "evidence", readJsonValue(match.getEvidence()),
                        "riskNotes", readJsonValue(match.getRiskNotes())))
                .resumeId(match.getResumeId())
                .resumeName(safeResumeName(resume))
                .jobDescriptionId(match.getJobDescriptionId())
                .jobTitle(safeJobTitle(jobDescription))
                .modelName(match.getModelName())
                .promptVersion(match.getPromptVersion())
                .errorMessage(match.getErrorMessage())
                .createdAt(match.getCreatedAt())
                .updatedAt(match.getUpdatedAt())
                .build();
    }

    private AiResultDetailVO getOptimizationSuggestionDetail(Long userId, Long recordId) {
        AiResumeSuggestion suggestion = aiResumeSuggestionMapper.selectById(recordId);
        if (suggestion == null) {
            throw new BusinessException(404, "AI 结果不存在");
        }
        Resume resume = getOwnedResume(userId, suggestion.getResumeId());
        JobDescription jobDescription = getOwnedJobDescription(userId, suggestion.getJobDescriptionId());
        return AiResultDetailVO.builder()
                .recordId(suggestion.getId())
                .resultType(TYPE_JOB_OPTIMIZATION_SUGGESTION)
                .title("岗位优化建议 - " + safeJobTitle(jobDescription))
                .status(suggestion.getSuggestionStatus())
                .content(contentMap(
                        "aiJobMatchResultId", nullableValue(suggestion.getAiJobMatchResultId()),
                        "suggestions", readJsonValue(suggestion.getSuggestions())))
                .resumeId(suggestion.getResumeId())
                .resumeName(safeResumeName(resume))
                .jobDescriptionId(suggestion.getJobDescriptionId())
                .jobTitle(safeJobTitle(jobDescription))
                .modelName(suggestion.getModelName())
                .promptVersion(suggestion.getPromptVersion())
                .errorMessage(suggestion.getErrorMessage())
                .createdAt(suggestion.getCreatedAt())
                .updatedAt(suggestion.getUpdatedAt())
                .build();
    }

    private AiResultDetailVO getLocalRewriteDetail(Long userId, Long recordId) {
        AiRewriteSuggestion rewrite = aiRewriteSuggestionMapper.selectById(recordId);
        if (rewrite == null) {
            throw new BusinessException(404, "AI 结果不存在");
        }
        Resume resume = getOwnedResume(userId, rewrite.getResumeId());
        JobDescription jobDescription = getOwnedJobDescription(userId, rewrite.getJobDescriptionId());
        return AiResultDetailVO.builder()
                .recordId(rewrite.getId())
                .resultType(TYPE_LOCAL_REWRITE)
                .title("局部改写 - " + safeSection(rewrite.getTargetSection()))
                .status(rewrite.getRewriteStatus())
                .content(contentMap(
                        "aiJobMatchResultId", nullableValue(rewrite.getAiJobMatchResultId()),
                        "aiResumeSuggestionId", nullableValue(rewrite.getAiResumeSuggestionId()),
                        "rewriteType", nullableValue(rewrite.getRewriteType()),
                        "targetSection", nullableValue(rewrite.getTargetSection()),
                        "originalText", nullableValue(rewrite.getOriginalText()),
                        "rewrittenText", nullableValue(rewrite.getRewrittenText()),
                        "rewriteReason", nullableValue(rewrite.getRewriteReason()),
                        "caution", nullableValue(rewrite.getCaution()),
                        "acceptStatus", nullableValue(rewrite.getAcceptStatus())))
                .resumeId(rewrite.getResumeId())
                .resumeName(safeResumeName(resume))
                .jobDescriptionId(rewrite.getJobDescriptionId())
                .jobTitle(safeJobTitle(jobDescription))
                .modelName(rewrite.getModelName())
                .promptVersion(rewrite.getPromptVersion())
                .errorMessage(rewrite.getErrorMessage())
                .createdAt(rewrite.getCreatedAt())
                .updatedAt(rewrite.getUpdatedAt())
                .build();
    }

    private List<AiResultRecordVO> listResumeDiagnoses(
            Map<Long, Resume> resumeMap,
            Long resumeId,
            String status) {
        if (resumeMap.isEmpty()) {
            return List.of();
        }
        List<Long> resumeIds = filterIds(resumeMap, resumeId);
        return resumeAiAnalysisMapper.selectList(new LambdaQueryWrapper<ResumeAiAnalysis>()
                        .in(ResumeAiAnalysis::getResumeId, resumeIds))
                .stream()
                .filter(analysis -> statusMatches(status, analysis.getAnalysisStatus()))
                .map(analysis -> {
                    Resume resume = resumeMap.get(analysis.getResumeId());
                    return AiResultRecordVO.builder()
                            .recordId(analysis.getId())
                            .resultType(TYPE_RESUME_DIAGNOSIS)
                            .title("简历诊断 - " + safeResumeName(resume))
                            .summary(preview(analysis.getSuggestionsSummary()))
                            .status(analysis.getAnalysisStatus())
                            .resumeId(analysis.getResumeId())
                            .resumeName(safeResumeName(resume))
                            .modelName(analysis.getModelName())
                            .promptVersion(analysis.getPromptVersion())
                            .errorMessage(analysis.getErrorMessage())
                            .createdAt(analysis.getCreatedAt())
                            .updatedAt(analysis.getUpdatedAt())
                            .build();
                })
                .toList();
    }

    private List<AiResultRecordVO> listTargetJobParses(
            Map<Long, JobDescription> jobDescriptionMap,
            Long jobDescriptionId,
            String status) {
        return filterJobs(jobDescriptionMap, jobDescriptionId).stream()
                .filter(jobDescription -> statusMatches(status, jobDescription.getParseStatus()))
                .map(jobDescription -> AiResultRecordVO.builder()
                        .recordId(jobDescription.getId())
                        .resultType(TYPE_TARGET_JOB_PARSE)
                        .title("目标岗位解析 - " + safeJobTitle(jobDescription))
                        .summary(preview(jobDescription.getStructuredContent()))
                        .status(jobDescription.getParseStatus())
                        .jobDescriptionId(jobDescription.getId())
                        .jobTitle(safeJobTitle(jobDescription))
                        .modelName(jobDescription.getModelName())
                        .promptVersion(jobDescription.getPromptVersion())
                        .errorMessage(jobDescription.getErrorMessage())
                        .createdAt(jobDescription.getCreatedAt())
                        .updatedAt(jobDescription.getUpdatedAt())
                        .build())
                .toList();
    }

    private List<AiResultRecordVO> listMatchAnalyses(
            Map<Long, Resume> resumeMap,
            Map<Long, JobDescription> jobDescriptionMap,
            Long resumeId,
            Long jobDescriptionId,
            String status) {
        if (resumeMap.isEmpty() || jobDescriptionMap.isEmpty()) {
            return List.of();
        }
        return aiJobMatchResultMapper.selectList(new LambdaQueryWrapper<AiJobMatchResult>()
                        .in(AiJobMatchResult::getResumeId, filterIds(resumeMap, resumeId))
                        .in(AiJobMatchResult::getJobDescriptionId, filterIds(jobDescriptionMap, jobDescriptionId)))
                .stream()
                .filter(match -> statusMatches(status, match.getMatchStatus()))
                .map(match -> {
                    Resume resume = resumeMap.get(match.getResumeId());
                    JobDescription jobDescription = jobDescriptionMap.get(match.getJobDescriptionId());
                    return AiResultRecordVO.builder()
                            .recordId(match.getId())
                            .resultType(TYPE_MATCH_ANALYSIS)
                            .title("匹配分析 - " + safeJobTitle(jobDescription))
                            .summary(preview(match.getRiskNotes()))
                            .status(match.getMatchStatus())
                            .resumeId(match.getResumeId())
                            .resumeName(safeResumeName(resume))
                            .jobDescriptionId(match.getJobDescriptionId())
                            .jobTitle(safeJobTitle(jobDescription))
                            .modelName(match.getModelName())
                            .promptVersion(match.getPromptVersion())
                            .errorMessage(match.getErrorMessage())
                            .createdAt(match.getCreatedAt())
                            .updatedAt(match.getUpdatedAt())
                            .build();
                })
                .toList();
    }

    private List<AiResultRecordVO> listOptimizationSuggestions(
            Map<Long, Resume> resumeMap,
            Map<Long, JobDescription> jobDescriptionMap,
            Long resumeId,
            Long jobDescriptionId,
            String status) {
        if (resumeMap.isEmpty() || jobDescriptionMap.isEmpty()) {
            return List.of();
        }
        return aiResumeSuggestionMapper.selectList(new LambdaQueryWrapper<AiResumeSuggestion>()
                        .in(AiResumeSuggestion::getResumeId, filterIds(resumeMap, resumeId))
                        .in(AiResumeSuggestion::getJobDescriptionId, filterIds(jobDescriptionMap, jobDescriptionId)))
                .stream()
                .filter(suggestion -> statusMatches(status, suggestion.getSuggestionStatus()))
                .map(suggestion -> {
                    Resume resume = resumeMap.get(suggestion.getResumeId());
                    JobDescription jobDescription = jobDescriptionMap.get(suggestion.getJobDescriptionId());
                    return AiResultRecordVO.builder()
                            .recordId(suggestion.getId())
                            .resultType(TYPE_JOB_OPTIMIZATION_SUGGESTION)
                            .title("岗位优化建议 - " + safeJobTitle(jobDescription))
                            .summary(preview(suggestion.getSuggestions()))
                            .status(suggestion.getSuggestionStatus())
                            .resumeId(suggestion.getResumeId())
                            .resumeName(safeResumeName(resume))
                            .jobDescriptionId(suggestion.getJobDescriptionId())
                            .jobTitle(safeJobTitle(jobDescription))
                            .modelName(suggestion.getModelName())
                            .promptVersion(suggestion.getPromptVersion())
                            .errorMessage(suggestion.getErrorMessage())
                            .createdAt(suggestion.getCreatedAt())
                            .updatedAt(suggestion.getUpdatedAt())
                            .build();
                })
                .toList();
    }

    private List<AiResultRecordVO> listLocalRewrites(
            Map<Long, Resume> resumeMap,
            Map<Long, JobDescription> jobDescriptionMap,
            Long resumeId,
            Long jobDescriptionId,
            String status) {
        if (resumeMap.isEmpty() || jobDescriptionMap.isEmpty()) {
            return List.of();
        }
        return aiRewriteSuggestionMapper.selectList(new LambdaQueryWrapper<AiRewriteSuggestion>()
                        .in(AiRewriteSuggestion::getResumeId, filterIds(resumeMap, resumeId))
                        .in(AiRewriteSuggestion::getJobDescriptionId, filterIds(jobDescriptionMap, jobDescriptionId)))
                .stream()
                .filter(rewrite -> statusMatches(status, rewrite.getRewriteStatus()))
                .map(rewrite -> {
                    Resume resume = resumeMap.get(rewrite.getResumeId());
                    JobDescription jobDescription = jobDescriptionMap.get(rewrite.getJobDescriptionId());
                    return AiResultRecordVO.builder()
                            .recordId(rewrite.getId())
                            .resultType(TYPE_LOCAL_REWRITE)
                            .title("局部改写 - " + safeSection(rewrite.getTargetSection()))
                            .summary(preview(rewrite.getRewrittenText()))
                            .status(rewrite.getRewriteStatus())
                            .resumeId(rewrite.getResumeId())
                            .resumeName(safeResumeName(resume))
                            .jobDescriptionId(rewrite.getJobDescriptionId())
                            .jobTitle(safeJobTitle(jobDescription))
                            .modelName(rewrite.getModelName())
                            .promptVersion(rewrite.getPromptVersion())
                            .errorMessage(rewrite.getErrorMessage())
                            .createdAt(rewrite.getCreatedAt())
                            .updatedAt(rewrite.getUpdatedAt())
                            .build();
                })
                .toList();
    }

    private List<Long> filterIds(Map<Long, ?> ownedMap, Long requestedId) {
        if (requestedId != null) {
            return List.of(requestedId);
        }
        return new ArrayList<>(ownedMap.keySet());
    }

    private List<JobDescription> filterJobs(Map<Long, JobDescription> jobDescriptionMap, Long requestedId) {
        if (requestedId != null) {
            JobDescription jobDescription = jobDescriptionMap.get(requestedId);
            return jobDescription == null ? List.of() : List.of(jobDescription);
        }
        return new ArrayList<>(jobDescriptionMap.values());
    }

    private Resume getOwnedResume(Long userId, Long resumeId) {
        if (resumeId == null) {
            throw new BusinessException(404, "AI 结果不存在");
        }
        Resume resume = resumeMapper.selectById(resumeId);
        if (resume == null || !userId.equals(resume.getUserId())) {
            throw new BusinessException(404, "AI 结果不存在");
        }
        return resume;
    }

    private JobDescription getOwnedJobDescription(Long userId, Long jobDescriptionId) {
        if (jobDescriptionId == null) {
            throw new BusinessException(404, "AI 结果不存在");
        }
        JobDescription jobDescription = jobDescriptionMapper.selectById(jobDescriptionId);
        if (jobDescription == null || !userId.equals(jobDescription.getUserId())) {
            throw new BusinessException(404, "AI 结果不存在");
        }
        return jobDescription;
    }

    private boolean shouldIncludeType(String requestedType, String candidateType) {
        return requestedType == null || requestedType.equals(candidateType);
    }

    private boolean statusMatches(String requestedStatus, String candidateStatus) {
        return requestedStatus == null || requestedStatus.equals(candidateStatus);
    }

    private String normalizeType(String resultType) {
        if (resultType == null || resultType.isBlank()) {
            return null;
        }
        String normalized = resultType.trim().toUpperCase();
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw new BusinessException(400, "AI 结果类型不支持");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase();
        if (!SUPPORTED_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "AI 结果状态不支持");
        }
        return normalized;
    }

    private int normalizePage(Integer page) {
        return page == null ? DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }
    }

    private AiResultPageVO emptyPage(int page, int size) {
        return AiResultPageVO.builder()
                .records(List.of())
                .page(page)
                .size(size)
                .total(0L)
                .totalPages(0)
                .build();
    }

    private LocalDateTime sortTime(AiResultRecordVO record) {
        return record.getUpdatedAt() == null ? record.getCreatedAt() : record.getUpdatedAt();
    }

    private int compareNullableTime(LocalDateTime first, LocalDateTime second) {
        if (first == null && second == null) {
            return 0;
        }
        if (first == null) {
            return -1;
        }
        if (second == null) {
            return 1;
        }
        return first.compareTo(second);
    }

    private String safeResumeName(Resume resume) {
        if (resume == null || resume.getOriginalFilename() == null || resume.getOriginalFilename().isBlank()) {
            return "未命名简历";
        }
        return resume.getOriginalFilename();
    }

    private String safeJobTitle(JobDescription jobDescription) {
        if (jobDescription == null || jobDescription.getTitle() == null || jobDescription.getTitle().isBlank()) {
            return "未命名目标岗位";
        }
        return jobDescription.getTitle();
    }

    private String safeSection(String targetSection) {
        if (targetSection == null || targetSection.isBlank()) {
            return "简历片段";
        }
        return targetSection;
    }

    private Map<String, Object> contentMap(Object... keysAndValues) {
        Map<String, Object> content = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            content.put((String) keysAndValues[index], keysAndValues[index + 1]);
        }
        return content;
    }

    private Object readJsonValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException exception) {
            return value;
        }
    }

    private Object nullableValue(Object value) {
        return value;
    }

    private String preview(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\t", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() <= PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_LENGTH) + "...";
    }
}
