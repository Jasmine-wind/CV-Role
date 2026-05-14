package com.winter.airesumeoptimizer.module.history.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.ResumeAiAnalysisMapper;
import com.winter.airesumeoptimizer.module.history.service.HistoryService;
import com.winter.airesumeoptimizer.module.history.vo.HistoryAiAnalysisVO;
import com.winter.airesumeoptimizer.module.history.vo.HistoryDetailVO;
import com.winter.airesumeoptimizer.module.history.vo.HistoryListVO;
import com.winter.airesumeoptimizer.module.history.vo.HistoryMatchResultVO;
import com.winter.airesumeoptimizer.module.history.vo.HistoryPageVO;
import com.winter.airesumeoptimizer.module.history.vo.HistoryParseResultVO;
import com.winter.airesumeoptimizer.module.history.vo.HistoryResumeVO;
import com.winter.airesumeoptimizer.module.job.entity.Job;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.entity.JobMatchResult;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.job.mapper.JobMapper;
import com.winter.airesumeoptimizer.module.job.mapper.JobMatchResultMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class HistoryServiceImpl implements HistoryService {

    private static final String STATUS_NOT_STARTED = "NOT_STARTED";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 50;
    private static final int PREVIEW_LENGTH = 120;
    private static final String MATCH_SOURCE_JOB = "JOB";
    private static final String MATCH_SOURCE_AI_JOB_DESCRIPTION = "AI_JOB_DESCRIPTION";
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ResumeMapper resumeMapper;
    private final ResumeParseResultMapper resumeParseResultMapper;
    private final ResumeAiAnalysisMapper resumeAiAnalysisMapper;
    private final JobMatchResultMapper jobMatchResultMapper;
    private final AiJobMatchResultMapper aiJobMatchResultMapper;
    private final JobMapper jobMapper;
    private final JobDescriptionMapper jobDescriptionMapper;
    private final ObjectMapper objectMapper;

    public HistoryServiceImpl(
            ResumeMapper resumeMapper,
            ResumeParseResultMapper resumeParseResultMapper,
            ResumeAiAnalysisMapper resumeAiAnalysisMapper,
            JobMatchResultMapper jobMatchResultMapper,
            AiJobMatchResultMapper aiJobMatchResultMapper,
            JobMapper jobMapper,
            JobDescriptionMapper jobDescriptionMapper,
            ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.resumeParseResultMapper = resumeParseResultMapper;
        this.resumeAiAnalysisMapper = resumeAiAnalysisMapper;
        this.jobMatchResultMapper = jobMatchResultMapper;
        this.aiJobMatchResultMapper = aiJobMatchResultMapper;
        this.jobMapper = jobMapper;
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public HistoryPageVO list(Long userId, Integer page, Integer size) {
        validateUserId(userId);
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);

        List<HistoryListVO> allRecords = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, userId))
                .stream()
                .map(this::toListVO)
                .sorted(Comparator.comparing(HistoryListVO::getUpdatedAt, this::compareNullableTime).reversed())
                .toList();

        int total = allRecords.size();
        int fromIndex = Math.min((safePage - 1) * safeSize, total);
        int toIndex = Math.min(fromIndex + safeSize, total);
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        return HistoryPageVO.builder()
                .records(allRecords.subList(fromIndex, toIndex))
                .page(safePage)
                .size(safeSize)
                .total((long) total)
                .totalPages(totalPages)
                .build();
    }

    @Override
    public HistoryDetailVO detail(Long userId, Long resumeId) {
        Resume resume = getOwnedResume(userId, resumeId);
        ResumeParseResult parseResult = getParseResult(resume.getId());
        ResumeAiAnalysis aiAnalysis = getAiAnalysis(resume.getId());
        List<HistoryMatchResultVO> matchResults = getMatchResults(resume.getId());
        HistoryMatchResultVO latestMatch = matchResults.stream().findFirst().orElse(null);

        return HistoryDetailVO.builder()
                .recordId(resume.getId())
                .resumeId(resume.getId())
                .resume(toResumeVO(resume))
                .parseResult(toParseResultVO(parseResult))
                .aiAnalysis(toAiAnalysisVO(aiAnalysis))
                .latestMatch(latestMatch)
                .matchResults(matchResults)
                .updatedAt(resolveUpdatedAt(resume, parseResult, aiAnalysis,
                        latestMatch == null ? null : latestMatch.getMatchUpdatedAt()))
                .build();
    }

    private HistoryListVO toListVO(Resume resume) {
        ResumeParseResult parseResult = getParseResult(resume.getId());
        ResumeAiAnalysis aiAnalysis = getAiAnalysis(resume.getId());
        HistoryMatchResultVO latestMatch = getLatestMatchResult(resume.getId());

        return HistoryListVO.builder()
                .recordId(resume.getId())
                .resumeId(resume.getId())
                .resumeName(resume.getOriginalFilename())
                .fileType(resume.getFileType())
                .fileSize(resume.getFileSize())
                .uploadStatus(resume.getUploadStatus())
                .uploadTime(resume.getCreatedAt())
                .parseStatus(parseResult == null ? STATUS_NOT_STARTED : parseResult.getParseStatus())
                .analysisStatus(aiAnalysis == null ? STATUS_NOT_STARTED : aiAnalysis.getAnalysisStatus())
                .analysisScore(aiAnalysis == null ? null : aiAnalysis.getScore())
                .latestJobId(latestMatch == null ? null : latestMatch.getJobId())
                .latestJobDescriptionId(latestMatch == null ? null : latestMatch.getJobDescriptionId())
                .latestMatchSource(latestMatch == null ? null : latestMatch.getMatchSource())
                .latestJobTitle(latestMatch == null ? null : latestMatch.getJobTitle())
                .latestCompanyName(latestMatch == null ? null : latestMatch.getCompanyName())
                .latestMatchScore(latestMatch == null ? null : latestMatch.getMatchScore())
                .updatedAt(resolveUpdatedAt(resume, parseResult, aiAnalysis,
                        latestMatch == null ? null : latestMatch.getMatchUpdatedAt()))
                .build();
    }

    private HistoryResumeVO toResumeVO(Resume resume) {
        return HistoryResumeVO.builder()
                .resumeId(resume.getId())
                .resumeName(resume.getOriginalFilename())
                .fileType(resume.getFileType())
                .fileSize(resume.getFileSize())
                .uploadStatus(resume.getUploadStatus())
                .uploadTime(resume.getCreatedAt())
                .build();
    }

    private HistoryParseResultVO toParseResultVO(ResumeParseResult parseResult) {
        if (parseResult == null) {
            return HistoryParseResultVO.builder()
                    .parseStatus(STATUS_NOT_STARTED)
                    .build();
        }

        return HistoryParseResultVO.builder()
                .parseStatus(parseResult.getParseStatus())
                .extractedTextPreview(preview(parseResult.getExtractedText()))
                .parseErrorMessage(parseResult.getErrorMessage())
                .parseUpdatedAt(parseResult.getUpdatedAt())
                .build();
    }

    private HistoryAiAnalysisVO toAiAnalysisVO(ResumeAiAnalysis aiAnalysis) {
        if (aiAnalysis == null) {
            return HistoryAiAnalysisVO.builder()
                    .analysisStatus(STATUS_NOT_STARTED)
                    .build();
        }

        return HistoryAiAnalysisVO.builder()
                .analysisStatus(aiAnalysis.getAnalysisStatus())
                .analysisScore(aiAnalysis.getScore())
                .strengthsPreview(preview(aiAnalysis.getStrengths()))
                .problemsPreview(preview(aiAnalysis.getProblems()))
                .suggestionsSummary(preview(aiAnalysis.getSuggestionsSummary()))
                .analysisErrorMessage(aiAnalysis.getErrorMessage())
                .analysisUpdatedAt(aiAnalysis.getUpdatedAt())
                .build();
    }

    private HistoryMatchResultVO toMatchResultVO(JobMatchResult matchResult) {
        Job job = getJob(matchResult.getJobId());
        return HistoryMatchResultVO.builder()
                .matchId(matchResult.getId())
                .jobId(matchResult.getJobId())
                .matchSource(MATCH_SOURCE_JOB)
                .jobTitle(job == null ? null : job.getTitle())
                .companyName(job == null ? null : job.getCompanyName())
                .jobCategory(job == null ? null : job.getJobCategory())
                .matchScore(matchResult.getMatchScore())
                .matchReason(matchResult.getMatchReason())
                .suggestionsPreview(preview(matchResult.getSuggestions()))
                .matchUpdatedAt(matchResult.getUpdatedAt())
                .build();
    }

    private HistoryMatchResultVO toMatchResultVO(AiJobMatchResult matchResult) {
        JobDescription jobDescription = getJobDescription(matchResult.getJobDescriptionId());
        return HistoryMatchResultVO.builder()
                .matchId(matchResult.getId())
                .jobDescriptionId(matchResult.getJobDescriptionId())
                .matchSource(MATCH_SOURCE_AI_JOB_DESCRIPTION)
                .jobTitle(jobDescription == null ? null : jobDescription.getTitle())
                .companyName("岗位描述")
                .jobCategory("AI 匹配")
                .matchScore(matchResult.getOverallScore())
                .matchReason(preview(matchResult.getStrongMatches()))
                .suggestionsPreview(preview(matchResult.getRiskNotes()))
                .matchUpdatedAt(matchResult.getUpdatedAt())
                .build();
    }

    private Resume getOwnedResume(Long userId, Long resumeId) {
        validateUserId(userId);
        if (resumeId == null) {
            throw new BusinessException(400, "简历 ID 不能为空");
        }

        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId));
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        return resume;
    }

    private ResumeParseResult getParseResult(Long resumeId) {
        return resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resumeId));
    }

    private ResumeAiAnalysis getAiAnalysis(Long resumeId) {
        return resumeAiAnalysisMapper.selectOne(new LambdaQueryWrapper<ResumeAiAnalysis>()
                .eq(ResumeAiAnalysis::getResumeId, resumeId));
    }

    private HistoryMatchResultVO getLatestMatchResult(Long resumeId) {
        HistoryMatchResultVO latestLegacyMatch = jobMatchResultMapper.selectList(new LambdaQueryWrapper<JobMatchResult>()
                        .eq(JobMatchResult::getResumeId, resumeId)
                        .orderByDesc(JobMatchResult::getUpdatedAt)
                        .orderByDesc(JobMatchResult::getId))
                .stream()
                .findFirst()
                .map(this::toMatchResultVO)
                .orElse(null);

        HistoryMatchResultVO latestAiMatch = aiJobMatchResultMapper.selectList(new LambdaQueryWrapper<AiJobMatchResult>()
                        .eq(AiJobMatchResult::getResumeId, resumeId)
                        .orderByDesc(AiJobMatchResult::getUpdatedAt)
                        .orderByDesc(AiJobMatchResult::getId))
                .stream()
                .findFirst()
                .map(this::toMatchResultVO)
                .orElse(null);

        return latest(latestLegacyMatch, latestAiMatch);
    }

    private List<HistoryMatchResultVO> getMatchResults(Long resumeId) {
        List<HistoryMatchResultVO> legacyMatches = jobMatchResultMapper.selectList(new LambdaQueryWrapper<JobMatchResult>()
                        .eq(JobMatchResult::getResumeId, resumeId)
                        .orderByDesc(JobMatchResult::getUpdatedAt)
                        .orderByDesc(JobMatchResult::getId))
                .stream()
                .map(this::toMatchResultVO)
                .toList();
        List<HistoryMatchResultVO> aiMatches = aiJobMatchResultMapper.selectList(new LambdaQueryWrapper<AiJobMatchResult>()
                        .eq(AiJobMatchResult::getResumeId, resumeId)
                        .orderByDesc(AiJobMatchResult::getUpdatedAt)
                        .orderByDesc(AiJobMatchResult::getId))
                .stream()
                .map(this::toMatchResultVO)
                .toList();

        return java.util.stream.Stream.concat(legacyMatches.stream(), aiMatches.stream())
                .sorted(Comparator.comparing(HistoryMatchResultVO::getMatchUpdatedAt, this::compareNullableTime)
                        .reversed())
                .toList();
    }

    private Job getJob(Long jobId) {
        if (jobId == null) {
            return null;
        }
        return jobMapper.selectById(jobId);
    }

    private JobDescription getJobDescription(Long jobDescriptionId) {
        if (jobDescriptionId == null) {
            return null;
        }
        return jobDescriptionMapper.selectById(jobDescriptionId);
    }

    private HistoryMatchResultVO latest(HistoryMatchResultVO first, HistoryMatchResultVO second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return compareNullableTime(first.getMatchUpdatedAt(), second.getMatchUpdatedAt()) >= 0 ? first : second;
    }

    private LocalDateTime resolveUpdatedAt(
            Resume resume,
            ResumeParseResult parseResult,
            ResumeAiAnalysis aiAnalysis,
            LocalDateTime latestMatchUpdatedAt) {
        LocalDateTime updatedAt = resume.getUpdatedAt();
        updatedAt = max(updatedAt, parseResult == null ? null : parseResult.getUpdatedAt());
        updatedAt = max(updatedAt, aiAnalysis == null ? null : aiAnalysis.getUpdatedAt());
        updatedAt = max(updatedAt, latestMatchUpdatedAt);
        return updatedAt;
    }

    private LocalDateTime max(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
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

    private String preview(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = normalizePreviewText(value);
        if (normalized.length() <= PREVIEW_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_LENGTH);
    }

    private String normalizePreviewText(String value) {
        String trimmed = value.trim();

        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                List<String> items = objectMapper.readValue(trimmed, STRING_LIST_TYPE);
                if (!items.isEmpty()) {
                    return String.join("；", items).replaceAll("\\s+", " ").trim();
                }
            } catch (JsonProcessingException exception) {
                return trimmed.replaceAll("\\s+", " ").trim();
            }
        }

        return trimmed.replaceAll("\\s+", " ").trim();
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
    }
}
