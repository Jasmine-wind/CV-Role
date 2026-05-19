package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchEvidenceDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchItemDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionItemDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiResumeSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiRewriteSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.service.JobOptimizationReportService;
import com.winter.airesumeoptimizer.module.analysis.vo.JobOptimizationReportVO;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class JobOptimizationReportServiceImpl implements JobOptimizationReportService {

    private static final String MATCH_STATUS_SUCCESS = "SUCCESS";
    private static final String SUGGESTION_STATUS_SUCCESS = "SUCCESS";
    private static final String REWRITE_STATUS_SUCCESS = "SUCCESS";
    private static final String ACCEPT_STATUS_ACCEPTED = "ACCEPTED";
    private static final String ACCEPT_STATUS_PENDING = "PENDING";
    private static final String ACCEPT_STATUS_REJECTED = "REJECTED";
    private static final int HIGH_MATCH_SCORE = 80;
    private static final int MEDIUM_MATCH_SCORE = 60;

    private final ResumeMapper resumeMapper;
    private final JobDescriptionMapper jobDescriptionMapper;
    private final AiJobMatchResultMapper aiJobMatchResultMapper;
    private final AiResumeSuggestionMapper aiResumeSuggestionMapper;
    private final AiRewriteSuggestionMapper aiRewriteSuggestionMapper;
    private final ObjectMapper objectMapper;

    public JobOptimizationReportServiceImpl(
            ResumeMapper resumeMapper,
            JobDescriptionMapper jobDescriptionMapper,
            AiJobMatchResultMapper aiJobMatchResultMapper,
            AiResumeSuggestionMapper aiResumeSuggestionMapper,
            AiRewriteSuggestionMapper aiRewriteSuggestionMapper,
            ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.aiJobMatchResultMapper = aiJobMatchResultMapper;
        this.aiResumeSuggestionMapper = aiResumeSuggestionMapper;
        this.aiRewriteSuggestionMapper = aiRewriteSuggestionMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public JobOptimizationReportVO getReport(Long userId, Long resumeId, Long jobDescriptionId) {
        Resume resume = getOwnedResume(userId, resumeId);
        JobDescription jobDescription = getOwnedJobDescription(userId, jobDescriptionId);
        AiJobMatchResult matchResult = getSuccessfulMatchResult(resume.getId(), jobDescription.getId());
        List<JobOptimizationReportVO.WarningVO> warnings = new ArrayList<>();

        List<AiJobMatchItemDTO> strongMatches = readList(
                matchResult.getStrongMatches(),
                new TypeReference<List<AiJobMatchItemDTO>>() {
                },
                "MATCH_STRONG_MATCHES_PARSE_FAILED",
                "强匹配项解析失败，已返回空列表",
                "MATCH",
                warnings);
        List<AiJobMatchItemDTO> weakMatches = readList(
                matchResult.getWeakMatches(),
                new TypeReference<List<AiJobMatchItemDTO>>() {
                },
                "MATCH_WEAK_MATCHES_PARSE_FAILED",
                "弱匹配项解析失败，已返回空列表",
                "MATCH",
                warnings);
        List<AiJobMatchItemDTO> missingSkills = readList(
                matchResult.getMissingSkills(),
                new TypeReference<List<AiJobMatchItemDTO>>() {
                },
                "MATCH_MISSING_SKILLS_PARSE_FAILED",
                "缺失技能解析失败，已返回空列表",
                "MATCH",
                warnings);
        List<String> riskTips = readList(
                matchResult.getRiskNotes(),
                new TypeReference<List<String>>() {
                },
                "MATCH_RISK_TIPS_PARSE_FAILED",
                "风险提示解析失败，已返回空列表",
                "MATCH",
                warnings);
        List<AiJobMatchEvidenceDTO> matchEvidence = readList(
                matchResult.getEvidence(),
                new TypeReference<List<AiJobMatchEvidenceDTO>>() {
                },
                "MATCH_EVIDENCE_PARSE_FAILED",
                "匹配依据解析失败，已返回空列表",
                "MATCH",
                warnings);
        appendMatchEvidenceWarnings(strongMatches, weakMatches, missingSkills, matchEvidence, warnings);

        AiResumeSuggestion suggestion = getOptionalSuggestion(resume.getId(), jobDescription.getId(), matchResult.getId());
        List<AiResumeSuggestionItemDTO> suggestions = readSuggestions(suggestion, warnings);
        appendSuggestionEvidenceWarnings(suggestions, warnings);
        List<AiResumeSuggestionItemDTO> highPrioritySuggestions = filterSuggestionsByPriority(suggestions, "HIGH");
        List<AiResumeSuggestionItemDTO> mediumPrioritySuggestions = filterSuggestionsByPriority(suggestions, "MEDIUM");
        List<AiResumeSuggestionItemDTO> lowPrioritySuggestions = filterSuggestionsByPriority(suggestions, "LOW");

        List<AiRewriteSuggestion> rewriteEntities = getRewriteSuggestions(resume.getId(), jobDescription.getId(), matchResult.getId());
        List<JobOptimizationReportVO.RewriteSuggestionItemVO> rewriteSuggestions = rewriteEntities.stream()
                .map(this::toRewriteSuggestionItem)
                .toList();
        List<JobOptimizationReportVO.RewriteSuggestionItemVO> acceptedRewriteSuggestions = filterRewriteSuggestionsByAcceptStatus(
                rewriteSuggestions,
                ACCEPT_STATUS_ACCEPTED);
        List<JobOptimizationReportVO.RewriteSuggestionItemVO> pendingRewriteSuggestions = filterRewriteSuggestionsByAcceptStatus(
                rewriteSuggestions,
                ACCEPT_STATUS_PENDING);
        List<JobOptimizationReportVO.RewriteSuggestionItemVO> rejectedRewriteSuggestions = filterRewriteSuggestionsByAcceptStatus(
                rewriteSuggestions,
                ACCEPT_STATUS_REJECTED);
        appendRewriteWarnings(rewriteEntities, warnings);

        if (suggestion == null) {
            warnings.add(warning("SUGGESTION_RESULT_MISSING", "暂无岗位优化建议，可先生成优化建议", "SUGGESTION"));
        } else if (!SUGGESTION_STATUS_SUCCESS.equals(suggestion.getSuggestionStatus())) {
            warnings.add(warning("SUGGESTION_RESULT_NOT_SUCCESS", "岗位优化建议未成功，已返回空建议列表", "SUGGESTION"));
        }
        if (rewriteEntities.isEmpty()) {
            warnings.add(warning("REWRITE_RESULT_MISSING", "暂无局部改写建议，可选择关键内容生成改写建议", "REWRITE"));
        }

        return JobOptimizationReportVO.builder()
                .resumeId(resume.getId())
                .resumeName(resume.getOriginalFilename())
                .jobDescriptionId(jobDescription.getId())
                .jobTitle(jobDescription.getTitle())
                .matchScore(matchResult.getOverallScore())
                .matchLevel(toMatchLevel(matchResult.getOverallScore()))
                .strongMatches(strongMatches)
                .weakMatches(weakMatches)
                .missingSkills(missingSkills)
                .riskTips(riskTips)
                .matchEvidence(matchEvidence)
                .suggestionSummary(toSuggestionSummary(suggestions))
                .highPrioritySuggestions(highPrioritySuggestions)
                .mediumPrioritySuggestions(mediumPrioritySuggestions)
                .lowPrioritySuggestions(lowPrioritySuggestions)
                .rewriteSuggestions(rewriteSuggestions)
                .acceptedRewriteSuggestions(acceptedRewriteSuggestions)
                .pendingRewriteSuggestions(pendingRewriteSuggestions)
                .rejectedRewriteSuggestions(rejectedRewriteSuggestions)
                .nextStepChecklist(buildNextStepChecklist(
                        highPrioritySuggestions,
                        missingSkills,
                        weakMatches,
                        pendingRewriteSuggestions,
                        rewriteSuggestions,
                        riskTips))
                .modelInfo(buildModelInfo(matchResult, suggestion, rewriteEntities))
                .generatedAt(LocalDateTime.now())
                .warnings(warnings)
                .build();
    }

    private Resume getOwnedResume(Long userId, Long resumeId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
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

    private JobDescription getOwnedJobDescription(Long userId, Long jobDescriptionId) {
        if (jobDescriptionId == null) {
            throw new BusinessException(400, "岗位描述 ID 不能为空");
        }
        JobDescription jobDescription = jobDescriptionMapper.selectOne(new LambdaQueryWrapper<JobDescription>()
                .eq(JobDescription::getId, jobDescriptionId)
                .eq(JobDescription::getUserId, userId));
        if (jobDescription == null) {
            throw new BusinessException(404, "岗位描述不存在");
        }
        return jobDescription;
    }

    private AiJobMatchResult getSuccessfulMatchResult(Long resumeId, Long jobDescriptionId) {
        AiJobMatchResult matchResult = aiJobMatchResultMapper.selectOne(new LambdaQueryWrapper<AiJobMatchResult>()
                .eq(AiJobMatchResult::getResumeId, resumeId)
                .eq(AiJobMatchResult::getJobDescriptionId, jobDescriptionId));
        if (matchResult == null) {
            throw new BusinessException(404, "AI 岗位匹配结果不存在，请先生成岗位匹配结果");
        }
        if (!MATCH_STATUS_SUCCESS.equals(matchResult.getMatchStatus())) {
            throw new BusinessException(400, "AI 岗位匹配未成功，不能生成岗位优化报告");
        }
        return matchResult;
    }

    private AiResumeSuggestion getOptionalSuggestion(Long resumeId, Long jobDescriptionId, Long aiJobMatchResultId) {
        return aiResumeSuggestionMapper.selectOne(new LambdaQueryWrapper<AiResumeSuggestion>()
                .eq(AiResumeSuggestion::getResumeId, resumeId)
                .eq(AiResumeSuggestion::getJobDescriptionId, jobDescriptionId)
                .eq(AiResumeSuggestion::getAiJobMatchResultId, aiJobMatchResultId));
    }

    private List<AiRewriteSuggestion> getRewriteSuggestions(Long resumeId, Long jobDescriptionId, Long aiJobMatchResultId) {
        return aiRewriteSuggestionMapper.selectList(new LambdaQueryWrapper<AiRewriteSuggestion>()
                .eq(AiRewriteSuggestion::getResumeId, resumeId)
                .eq(AiRewriteSuggestion::getJobDescriptionId, jobDescriptionId)
                .eq(AiRewriteSuggestion::getAiJobMatchResultId, aiJobMatchResultId)
                .orderByDesc(AiRewriteSuggestion::getUpdatedAt)
                .orderByDesc(AiRewriteSuggestion::getCreatedAt));
    }

    private List<AiResumeSuggestionItemDTO> readSuggestions(
            AiResumeSuggestion suggestion,
            List<JobOptimizationReportVO.WarningVO> warnings) {
        if (suggestion == null || !SUGGESTION_STATUS_SUCCESS.equals(suggestion.getSuggestionStatus())) {
            return List.of();
        }
        return readList(
                suggestion.getSuggestions(),
                new TypeReference<List<AiResumeSuggestionItemDTO>>() {
                },
                "SUGGESTION_ITEMS_PARSE_FAILED",
                "岗位优化建议解析失败，已返回空建议列表",
                "SUGGESTION",
                warnings);
    }

    private <T> List<T> readList(
            String value,
            TypeReference<List<T>> typeReference,
            String warningCode,
            String warningMessage,
            String warningSource,
            List<JobOptimizationReportVO.WarningVO> warnings) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            List<T> result = objectMapper.readValue(value, typeReference);
            if (result == null) {
                return List.of();
            }
            return result;
        } catch (JsonProcessingException exception) {
            warnings.add(warning(warningCode, warningMessage, warningSource));
            return List.of();
        }
    }

    private List<AiResumeSuggestionItemDTO> filterSuggestionsByPriority(
            List<AiResumeSuggestionItemDTO> suggestions,
            String priority) {
        return suggestions.stream()
                .filter(suggestion -> priority.equals(normalize(suggestion.getPriority())))
                .toList();
    }

    private List<JobOptimizationReportVO.RewriteSuggestionItemVO> filterRewriteSuggestionsByAcceptStatus(
            List<JobOptimizationReportVO.RewriteSuggestionItemVO> suggestions,
            String acceptStatus) {
        return suggestions.stream()
                .filter(suggestion -> acceptStatus.equals(normalize(suggestion.getAcceptStatus())))
                .toList();
    }

    private JobOptimizationReportVO.SuggestionSummaryVO toSuggestionSummary(List<AiResumeSuggestionItemDTO> suggestions) {
        int highCount = filterSuggestionsByPriority(suggestions, "HIGH").size();
        int mediumCount = filterSuggestionsByPriority(suggestions, "MEDIUM").size();
        int lowCount = filterSuggestionsByPriority(suggestions, "LOW").size();
        return JobOptimizationReportVO.SuggestionSummaryVO.builder()
                .totalCount(suggestions.size())
                .highPriorityCount(highCount)
                .mediumPriorityCount(mediumCount)
                .lowPriorityCount(lowCount)
                .build();
    }

    private JobOptimizationReportVO.RewriteSuggestionItemVO toRewriteSuggestionItem(AiRewriteSuggestion suggestion) {
        return JobOptimizationReportVO.RewriteSuggestionItemVO.builder()
                .rewriteId(suggestion.getId())
                .rewriteType(suggestion.getRewriteType())
                .targetSection(suggestion.getTargetSection())
                .originalText(suggestion.getOriginalText())
                .rewrittenText(suggestion.getRewrittenText())
                .rewriteReason(suggestion.getRewriteReason())
                .caution(suggestion.getCaution())
                .acceptStatus(suggestion.getAcceptStatus())
                .aiResumeSuggestionId(suggestion.getAiResumeSuggestionId())
                .updatedAt(suggestion.getUpdatedAt())
                .build();
    }

    private List<JobOptimizationReportVO.NextStepItemVO> buildNextStepChecklist(
            List<AiResumeSuggestionItemDTO> highPrioritySuggestions,
            List<AiJobMatchItemDTO> missingSkills,
            List<AiJobMatchItemDTO> weakMatches,
            List<JobOptimizationReportVO.RewriteSuggestionItemVO> pendingRewriteSuggestions,
            List<JobOptimizationReportVO.RewriteSuggestionItemVO> rewriteSuggestions,
            List<String> riskTips) {
        List<JobOptimizationReportVO.NextStepItemVO> items = new ArrayList<>();
        if (!highPrioritySuggestions.isEmpty()) {
            items.add(nextStep("REVIEW_HIGH_PRIORITY_SUGGESTIONS", "优先处理高优先级岗位优化建议", "SUGGESTION"));
        }
        if (!missingSkills.isEmpty()) {
            items.add(nextStep("REVIEW_MISSING_SKILLS", "检查是否真实掌握缺失技能，掌握则补充到技能或项目描述中", "MATCH"));
        }
        if (!weakMatches.isEmpty()) {
            items.add(nextStep("IMPROVE_WEAK_MATCHES", "优化弱匹配经历表达，突出岗位相关内容", "MATCH"));
        }
        if (!pendingRewriteSuggestions.isEmpty()) {
            items.add(nextStep("REVIEW_PENDING_REWRITES", "逐条确认是否采纳局部改写建议", "REWRITE"));
        }
        if (rewriteSuggestions.isEmpty()) {
            items.add(nextStep("GENERATE_REWRITE_SUGGESTIONS", "可选择关键项目经历生成局部改写建议", "REWRITE"));
        }
        if (!riskTips.isEmpty()) {
            items.add(nextStep("REVIEW_RISK_TIPS", "查看风险提示，避免在简历中伪造未发生的经历或量化指标", "MATCH"));
        }
        return items;
    }

    private List<JobOptimizationReportVO.ModelInfoVO> buildModelInfo(
            AiJobMatchResult matchResult,
            AiResumeSuggestion suggestion,
            List<AiRewriteSuggestion> rewriteSuggestions) {
        List<JobOptimizationReportVO.ModelInfoVO> modelInfo = new ArrayList<>();
        modelInfo.add(modelInfo(
                "MATCH",
                matchResult.getId(),
                matchResult.getModelName(),
                matchResult.getPromptVersion(),
                matchResult.getMatchStatus(),
                matchResult.getUpdatedAt()));
        if (suggestion != null) {
            modelInfo.add(modelInfo(
                    "SUGGESTION",
                    suggestion.getId(),
                    suggestion.getModelName(),
                    suggestion.getPromptVersion(),
                    suggestion.getSuggestionStatus(),
                    suggestion.getUpdatedAt()));
        }
        modelInfo.addAll(rewriteSuggestions.stream()
                .map(rewrite -> modelInfo(
                        "REWRITE",
                        rewrite.getId(),
                        rewrite.getModelName(),
                        rewrite.getPromptVersion(),
                        rewrite.getRewriteStatus(),
                        rewrite.getUpdatedAt()))
                .toList());
        return modelInfo;
    }

    private void appendRewriteWarnings(
            List<AiRewriteSuggestion> rewriteSuggestions,
            List<JobOptimizationReportVO.WarningVO> warnings) {
        boolean hasFailedRewrite = rewriteSuggestions.stream()
                .map(AiRewriteSuggestion::getRewriteStatus)
                .filter(Objects::nonNull)
                .anyMatch(status -> !REWRITE_STATUS_SUCCESS.equals(status));
        if (hasFailedRewrite) {
            warnings.add(warning("REWRITE_RESULT_NOT_SUCCESS", "存在未成功的局部改写建议，请查看改写状态", "REWRITE"));
        }
        boolean hasMissingReason = rewriteSuggestions.stream()
                .filter(rewrite -> REWRITE_STATUS_SUCCESS.equals(rewrite.getRewriteStatus()))
                .anyMatch(rewrite -> isBlank(rewrite.getRewriteReason()));
        if (hasMissingReason) {
            warnings.add(warning("REWRITE_REASON_MISSING", "部分改写建议缺少改写理由，请结合原文确认", "REWRITE"));
        }
        boolean hasMissingCaution = rewriteSuggestions.stream()
                .filter(rewrite -> REWRITE_STATUS_SUCCESS.equals(rewrite.getRewriteStatus()))
                .anyMatch(rewrite -> isBlank(rewrite.getCaution()));
        if (hasMissingCaution) {
            warnings.add(warning("REWRITE_CAUTION_MISSING", "部分改写建议缺少注意事项，请确认没有新增虚假事实", "REWRITE"));
        }
    }

    private void appendMatchEvidenceWarnings(
            List<AiJobMatchItemDTO> strongMatches,
            List<AiJobMatchItemDTO> weakMatches,
            List<AiJobMatchItemDTO> missingSkills,
            List<AiJobMatchEvidenceDTO> matchEvidence,
            List<JobOptimizationReportVO.WarningVO> warnings) {
        if (matchEvidence.isEmpty()) {
            warnings.add(warning("MATCH_EVIDENCE_MISSING", "当前匹配结果缺少详细依据，建议重新生成匹配分析", "MATCH"));
        }
        boolean hasMissingItemReason = hasMissingReason(strongMatches)
                || hasMissingReason(weakMatches)
                || hasMissingReason(missingSkills);
        if (hasMissingItemReason) {
            warnings.add(warning("MATCH_REASON_MISSING", "部分匹配结论缺少原因说明，建议结合匹配依据确认", "MATCH"));
        }
    }

    private boolean hasMissingReason(List<AiJobMatchItemDTO> items) {
        return items.stream().anyMatch(item -> isBlank(item.getReason()));
    }

    private void appendSuggestionEvidenceWarnings(
            List<AiResumeSuggestionItemDTO> suggestions,
            List<JobOptimizationReportVO.WarningVO> warnings) {
        boolean hasMissingEvidence = suggestions.stream()
                .anyMatch(suggestion -> suggestion.getEvidence() == null || suggestion.getEvidence().isEmpty());
        if (hasMissingEvidence) {
            warnings.add(warning("SUGGESTION_EVIDENCE_MISSING", "部分优化建议缺少依据，使用前请结合原简历确认", "SUGGESTION"));
        }
        boolean hasMissingCaution = suggestions.stream().anyMatch(suggestion -> isBlank(suggestion.getCaution()));
        if (hasMissingCaution) {
            warnings.add(warning("SUGGESTION_CAUTION_MISSING", "部分优化建议缺少注意事项，请确认没有新增虚假事实", "SUGGESTION"));
        }
    }

    private JobOptimizationReportVO.ModelInfoVO modelInfo(
            String sourceType,
            Long sourceId,
            String modelName,
            String promptVersion,
            String status,
            LocalDateTime updatedAt) {
        return JobOptimizationReportVO.ModelInfoVO.builder()
                .sourceType(sourceType)
                .sourceId(sourceId)
                .modelName(modelName)
                .promptVersion(promptVersion)
                .status(status)
                .updatedAt(updatedAt)
                .build();
    }

    private JobOptimizationReportVO.NextStepItemVO nextStep(String key, String text, String source) {
        return JobOptimizationReportVO.NextStepItemVO.builder()
                .key(key)
                .text(text)
                .source(source)
                .status("PENDING")
                .build();
    }

    private JobOptimizationReportVO.WarningVO warning(String code, String message, String source) {
        return JobOptimizationReportVO.WarningVO.builder()
                .code(code)
                .message(message)
                .source(source)
                .build();
    }

    private String toMatchLevel(Integer score) {
        if (score == null) {
            return null;
        }
        if (score >= HIGH_MATCH_SCORE) {
            return "HIGH";
        }
        if (score >= MEDIUM_MATCH_SCORE) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
