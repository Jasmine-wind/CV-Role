package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionResultDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiResumeSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiRewriteSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionOutputParser;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionPromptService;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionService;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiRewriteSuggestionServiceImpl implements AiRewriteSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiRewriteSuggestionServiceImpl.class);

    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    private static final String MATCH_STATUS_SUCCESS = "SUCCESS";
    private static final String SUGGESTION_STATUS_SUCCESS = "SUCCESS";
    private static final String REWRITE_STATUS_SUCCESS = "SUCCESS";
    private static final String REWRITE_STATUS_FAILED = "FAILED";
    private static final String ACCEPT_STATUS_PENDING = "PENDING";
    private static final int MAX_ORIGINAL_TEXT_LENGTH = 3000;
    private static final int MAX_TARGET_SECTION_LENGTH = 100;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final Set<String> ALLOWED_REWRITE_TYPES = Set.of(
            "PROJECT",
            "SKILL",
            "INTERNSHIP",
            "SUMMARY",
            "EDUCATION",
            "OTHER");
    private static final Set<String> ALLOWED_ACCEPT_STATUSES = Set.of("PENDING", "ACCEPTED", "REJECTED");

    private final ResumeMapper resumeMapper;
    private final JobDescriptionMapper jobDescriptionMapper;
    private final AiJobMatchResultMapper aiJobMatchResultMapper;
    private final AiResumeSuggestionMapper aiResumeSuggestionMapper;
    private final AiRewriteSuggestionMapper aiRewriteSuggestionMapper;
    private final AiRewriteSuggestionPromptService aiRewriteSuggestionPromptService;
    private final AiRewriteSuggestionOutputParser aiRewriteSuggestionOutputParser;
    private final AiClientService aiClientService;
    private final ObjectMapper objectMapper;

    public AiRewriteSuggestionServiceImpl(
            ResumeMapper resumeMapper,
            JobDescriptionMapper jobDescriptionMapper,
            AiJobMatchResultMapper aiJobMatchResultMapper,
            AiResumeSuggestionMapper aiResumeSuggestionMapper,
            AiRewriteSuggestionMapper aiRewriteSuggestionMapper,
            AiRewriteSuggestionPromptService aiRewriteSuggestionPromptService,
            AiRewriteSuggestionOutputParser aiRewriteSuggestionOutputParser,
            AiClientService aiClientService,
            ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.aiJobMatchResultMapper = aiJobMatchResultMapper;
        this.aiResumeSuggestionMapper = aiResumeSuggestionMapper;
        this.aiRewriteSuggestionMapper = aiRewriteSuggestionMapper;
        this.aiRewriteSuggestionPromptService = aiRewriteSuggestionPromptService;
        this.aiRewriteSuggestionOutputParser = aiRewriteSuggestionOutputParser;
        this.aiClientService = aiClientService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AiRewriteSuggestion generate(
            Long userId,
            Long resumeId,
            String rewriteType,
            String targetSection,
            String originalText,
            Long jobDescriptionId,
            Long aiJobMatchResultId,
            Long aiResumeSuggestionId) {
        Resume resume = getOwnedResume(userId, resumeId);
        String normalizedRewriteType = normalizeRewriteType(rewriteType);
        String normalizedTargetSection = normalizeTargetSection(targetSection);
        String normalizedOriginalText = normalizeOriginalText(originalText);
        JobDescription jobDescription = getOptionalOwnedSuccessfulJobDescription(userId, jobDescriptionId);
        AiJobMatchResult matchResult = getOptionalSuccessfulMatchResult(resume.getId(), jobDescriptionId, aiJobMatchResultId);
        AiResumeSuggestion resumeSuggestion = getOptionalSuccessfulResumeSuggestion(
                resume.getId(),
                aiJobMatchResultId,
                aiResumeSuggestionId);

        AiRewriteSuggestion rewriteSuggestion = createPendingRewriteSuggestion(
                resume.getId(),
                jobDescription == null ? null : jobDescription.getId(),
                matchResult == null ? null : matchResult.getId(),
                resumeSuggestion == null ? null : resumeSuggestion.getId(),
                normalizedRewriteType,
                normalizedTargetSection,
                normalizedOriginalText);

        AiRewriteSuggestionPromptDTO prompt = aiRewriteSuggestionPromptService.buildPrompt(
                normalizedOriginalText,
                normalizedRewriteType,
                normalizedTargetSection,
                jobDescription == null ? null : jobDescription.getStructuredContent(),
                matchResult == null ? null : buildMatchResultPromptInput(matchResult),
                resumeSuggestion == null ? null : resumeSuggestion.getSuggestions());

        log.info("AI rewrite suggestion started: userId={}, resumeId={}, jobDescriptionId={}, aiJobMatchResultId={}, aiResumeSuggestionId={}, model={}",
                userId,
                resume.getId(),
                rewriteSuggestion.getJobDescriptionId(),
                rewriteSuggestion.getAiJobMatchResultId(),
                rewriteSuggestion.getAiResumeSuggestionId(),
                aiClientService.modelName());

        try {
            String aiOutput = aiClientService.complete(prompt.getPrompt());
            AiRewriteSuggestionResultDTO result = aiRewriteSuggestionOutputParser.parse(aiOutput);
            applySuccess(rewriteSuggestion, prompt.getPromptVersion(), result);
            save(rewriteSuggestion);
            log.info("AI rewrite suggestion succeeded: userId={}, resumeId={}, rewriteId={}, model={}",
                    userId,
                    resume.getId(),
                    rewriteSuggestion.getId(),
                    rewriteSuggestion.getModelName());
            return rewriteSuggestion;
        } catch (RuntimeException exception) {
            String errorMessage = normalizeErrorMessage(exception);
            applyFailed(rewriteSuggestion, prompt.getPromptVersion(), errorMessage);
            save(rewriteSuggestion);
            log.warn("AI rewrite suggestion failed: userId={}, resumeId={}, jobDescriptionId={}, aiJobMatchResultId={}, aiResumeSuggestionId={}, model={}, reason={}",
                    userId,
                    resume.getId(),
                    rewriteSuggestion.getJobDescriptionId(),
                    rewriteSuggestion.getAiJobMatchResultId(),
                    rewriteSuggestion.getAiResumeSuggestionId(),
                    aiClientService.modelName(),
                    LogSanitizer.sanitize(errorMessage));
            return rewriteSuggestion;
        }
    }

    @Override
    public List<AiRewriteSuggestion> listByResume(Long userId, Long resumeId, String rewriteType, String acceptStatus) {
        Resume resume = getOwnedResume(userId, resumeId);
        LambdaQueryWrapper<AiRewriteSuggestion> query = new LambdaQueryWrapper<AiRewriteSuggestion>()
                .eq(AiRewriteSuggestion::getResumeId, resume.getId());
        if (rewriteType != null && !rewriteType.isBlank()) {
            query.eq(AiRewriteSuggestion::getRewriteType, normalizeRewriteType(rewriteType));
        }
        if (acceptStatus != null && !acceptStatus.isBlank()) {
            query.eq(AiRewriteSuggestion::getAcceptStatus, normalizeAcceptStatus(acceptStatus));
        }
        return aiRewriteSuggestionMapper.selectList(query
                .orderByDesc(AiRewriteSuggestion::getUpdatedAt)
                .orderByDesc(AiRewriteSuggestion::getCreatedAt));
    }

    @Override
    @Transactional
    public AiRewriteSuggestion updateAcceptStatus(Long userId, Long rewriteId, String acceptStatus) {
        if (rewriteId == null) {
            throw new BusinessException(400, "局部改写建议 ID 不能为空");
        }
        AiRewriteSuggestion suggestion = aiRewriteSuggestionMapper.selectById(rewriteId);
        if (suggestion == null) {
            throw new BusinessException(404, "AI 局部改写建议不存在");
        }
        getOwnedResume(userId, suggestion.getResumeId());
        suggestion.setAcceptStatus(normalizeAcceptDecisionStatus(acceptStatus));
        suggestion.setUpdatedAt(LocalDateTime.now());
        aiRewriteSuggestionMapper.updateById(suggestion);
        return suggestion;
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

    private JobDescription getOptionalOwnedSuccessfulJobDescription(Long userId, Long jobDescriptionId) {
        if (jobDescriptionId == null) {
            return null;
        }
        JobDescription jobDescription = jobDescriptionMapper.selectOne(new LambdaQueryWrapper<JobDescription>()
                .eq(JobDescription::getId, jobDescriptionId)
                .eq(JobDescription::getUserId, userId));
        if (jobDescription == null) {
            throw new BusinessException(404, "岗位描述不存在");
        }
        if (!PARSE_STATUS_SUCCESS.equals(jobDescription.getParseStatus())) {
            throw new BusinessException(400, "岗位描述解析未成功，不能用于局部改写");
        }
        if (jobDescription.getStructuredContent() == null || jobDescription.getStructuredContent().isBlank()) {
            throw new BusinessException(400, "岗位描述结构化解析结果为空，不能用于局部改写");
        }
        return jobDescription;
    }

    private AiJobMatchResult getOptionalSuccessfulMatchResult(
            Long resumeId,
            Long jobDescriptionId,
            Long aiJobMatchResultId) {
        if (aiJobMatchResultId == null) {
            return null;
        }
        LambdaQueryWrapper<AiJobMatchResult> query = new LambdaQueryWrapper<AiJobMatchResult>()
                .eq(AiJobMatchResult::getId, aiJobMatchResultId)
                .eq(AiJobMatchResult::getResumeId, resumeId);
        if (jobDescriptionId != null) {
            query.eq(AiJobMatchResult::getJobDescriptionId, jobDescriptionId);
        }
        AiJobMatchResult matchResult = aiJobMatchResultMapper.selectOne(query);
        if (matchResult == null) {
            throw new BusinessException(404, "AI 岗位匹配结果不存在");
        }
        if (!MATCH_STATUS_SUCCESS.equals(matchResult.getMatchStatus())) {
            throw new BusinessException(400, "AI 岗位匹配未成功，不能用于局部改写");
        }
        return matchResult;
    }

    private AiResumeSuggestion getOptionalSuccessfulResumeSuggestion(
            Long resumeId,
            Long aiJobMatchResultId,
            Long aiResumeSuggestionId) {
        if (aiResumeSuggestionId == null) {
            return null;
        }
        LambdaQueryWrapper<AiResumeSuggestion> query = new LambdaQueryWrapper<AiResumeSuggestion>()
                .eq(AiResumeSuggestion::getId, aiResumeSuggestionId)
                .eq(AiResumeSuggestion::getResumeId, resumeId);
        if (aiJobMatchResultId != null) {
            query.eq(AiResumeSuggestion::getAiJobMatchResultId, aiJobMatchResultId);
        }
        AiResumeSuggestion suggestion = aiResumeSuggestionMapper.selectOne(query);
        if (suggestion == null) {
            throw new BusinessException(404, "AI 优化建议结果不存在");
        }
        if (!SUGGESTION_STATUS_SUCCESS.equals(suggestion.getSuggestionStatus())) {
            throw new BusinessException(400, "AI 优化建议未成功，不能用于局部改写");
        }
        return suggestion;
    }

    private String normalizeRewriteType(String rewriteType) {
        if (rewriteType == null || rewriteType.isBlank()) {
            throw new BusinessException(400, "改写对象类型不能为空");
        }
        String normalized = rewriteType.strip().toUpperCase();
        if (!ALLOWED_REWRITE_TYPES.contains(normalized)) {
            throw new BusinessException(400, "改写对象类型不合法");
        }
        return normalized;
    }

    private String normalizeTargetSection(String targetSection) {
        if (targetSection == null || targetSection.isBlank()) {
            throw new BusinessException(400, "目标简历部分不能为空");
        }
        String normalized = targetSection.strip();
        if (normalized.length() > MAX_TARGET_SECTION_LENGTH) {
            throw new BusinessException(400, "目标简历部分过长");
        }
        return normalized;
    }

    private String normalizeAcceptStatus(String acceptStatus) {
        String normalized = acceptStatus.strip().toUpperCase();
        if (!ALLOWED_ACCEPT_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "采纳状态不合法");
        }
        return normalized;
    }

    private String normalizeAcceptDecisionStatus(String acceptStatus) {
        String normalized = normalizeAcceptStatus(acceptStatus);
        if (ACCEPT_STATUS_PENDING.equals(normalized)) {
            throw new BusinessException(400, "采纳状态只能为 ACCEPTED 或 REJECTED");
        }
        return normalized;
    }

    private String normalizeOriginalText(String originalText) {
        if (originalText == null || originalText.isBlank()) {
            throw new BusinessException(400, "原文片段不能为空");
        }
        String normalized = originalText.strip();
        if (normalized.length() > MAX_ORIGINAL_TEXT_LENGTH) {
            throw new BusinessException(400, "原文片段过长");
        }
        return normalized;
    }

    private AiRewriteSuggestion createPendingRewriteSuggestion(
            Long resumeId,
            Long jobDescriptionId,
            Long aiJobMatchResultId,
            Long aiResumeSuggestionId,
            String rewriteType,
            String targetSection,
            String originalText) {
        AiRewriteSuggestion suggestion = new AiRewriteSuggestion();
        suggestion.setResumeId(resumeId);
        suggestion.setJobDescriptionId(jobDescriptionId);
        suggestion.setAiJobMatchResultId(aiJobMatchResultId);
        suggestion.setAiResumeSuggestionId(aiResumeSuggestionId);
        suggestion.setRewriteType(rewriteType);
        suggestion.setTargetSection(targetSection);
        suggestion.setOriginalText(originalText);
        suggestion.setAcceptStatus(ACCEPT_STATUS_PENDING);
        suggestion.setCreatedAt(LocalDateTime.now());
        return suggestion;
    }

    private String buildMatchResultPromptInput(AiJobMatchResult matchResult) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("overallScore", matchResult.getOverallScore());
        root.set("strongMatches", readJsonArrayOrText(matchResult.getStrongMatches()));
        root.set("weakMatches", readJsonArrayOrText(matchResult.getWeakMatches()));
        root.set("missingSkills", readJsonArrayOrText(matchResult.getMissingSkills()));
        root.set("weakExperienceDescriptions", readJsonArrayOrText(matchResult.getWeakExperienceDescriptions()));
        root.set("evidence", readJsonArrayOrText(matchResult.getEvidence()));
        root.set("riskNotes", readJsonArrayOrText(matchResult.getRiskNotes()));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "AI 匹配结果组装失败");
        }
    }

    private JsonNode readJsonArrayOrText(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node.isArray()) {
                return node;
            }
        } catch (JsonProcessingException ignored) {
            // Stored AI match fields should be JSON arrays. If legacy data is not JSON, keep the text as context input.
        }
        return objectMapper.getNodeFactory().textNode(value);
    }

    private void applySuccess(
            AiRewriteSuggestion rewriteSuggestion,
            String promptVersion,
            AiRewriteSuggestionResultDTO result) {
        rewriteSuggestion.setRewriteStatus(REWRITE_STATUS_SUCCESS);
        rewriteSuggestion.setRewrittenText(result.getRewrittenText());
        rewriteSuggestion.setRewriteReason(result.getRewriteReason());
        rewriteSuggestion.setCaution(buildCaution(result));
        rewriteSuggestion.setModelName(aiClientService.modelName());
        rewriteSuggestion.setPromptVersion(promptVersion);
        rewriteSuggestion.setErrorMessage(null);
    }

    private String buildCaution(AiRewriteSuggestionResultDTO result) {
        String caution = result.getCaution();
        if (!Boolean.TRUE.equals(result.getNeedUserSupplement()) || result.getSupplementQuestions().isEmpty()) {
            return caution;
        }
        return caution + "\n需要用户补充：" + String.join("；", result.getSupplementQuestions());
    }

    private void applyFailed(
            AiRewriteSuggestion rewriteSuggestion,
            String promptVersion,
            String errorMessage) {
        rewriteSuggestion.setRewriteStatus(REWRITE_STATUS_FAILED);
        rewriteSuggestion.setRewrittenText(null);
        rewriteSuggestion.setRewriteReason(null);
        rewriteSuggestion.setCaution(null);
        rewriteSuggestion.setModelName(aiClientService.modelName());
        rewriteSuggestion.setPromptVersion(promptVersion);
        rewriteSuggestion.setErrorMessage(truncateErrorMessage(errorMessage));
    }

    private void save(AiRewriteSuggestion rewriteSuggestion) {
        rewriteSuggestion.setUpdatedAt(LocalDateTime.now());
        aiRewriteSuggestionMapper.insert(rewriteSuggestion);
    }

    private String normalizeErrorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "AI 局部改写生成失败";
        }
        return exception.getMessage();
    }

    private String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
