package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionResultDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiResumeSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.service.AiResumeSuggestionOutputParser;
import com.winter.airesumeoptimizer.module.analysis.service.AiResumeSuggestionPromptService;
import com.winter.airesumeoptimizer.module.analysis.service.AiResumeSuggestionService;
import com.winter.airesumeoptimizer.module.embedding.dto.RagContextDTO;
import com.winter.airesumeoptimizer.module.embedding.service.ResumeRagService;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiResumeSuggestionServiceImpl implements AiResumeSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiResumeSuggestionServiceImpl.class);

    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    private static final String MATCH_STATUS_SUCCESS = "SUCCESS";
    private static final String SUGGESTION_STATUS_SUCCESS = "SUCCESS";
    private static final String SUGGESTION_STATUS_FAILED = "FAILED";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final int RAG_TOP_K = 3;

    private final ResumeMapper resumeMapper;
    private final ResumeParseResultMapper resumeParseResultMapper;
    private final JobDescriptionMapper jobDescriptionMapper;
    private final AiJobMatchResultMapper aiJobMatchResultMapper;
    private final AiResumeSuggestionMapper aiResumeSuggestionMapper;
    private final AiResumeSuggestionPromptService aiResumeSuggestionPromptService;
    private final AiResumeSuggestionOutputParser aiResumeSuggestionOutputParser;
    private final AiClientService aiClientService;
    private final ObjectMapper objectMapper;
    private final ResumeRagService resumeRagService;

    public AiResumeSuggestionServiceImpl(
            ResumeMapper resumeMapper,
            ResumeParseResultMapper resumeParseResultMapper,
            JobDescriptionMapper jobDescriptionMapper,
            AiJobMatchResultMapper aiJobMatchResultMapper,
            AiResumeSuggestionMapper aiResumeSuggestionMapper,
            AiResumeSuggestionPromptService aiResumeSuggestionPromptService,
            AiResumeSuggestionOutputParser aiResumeSuggestionOutputParser,
            AiClientService aiClientService,
            ObjectMapper objectMapper,
            ResumeRagService resumeRagService) {
        this.resumeMapper = resumeMapper;
        this.resumeParseResultMapper = resumeParseResultMapper;
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.aiJobMatchResultMapper = aiJobMatchResultMapper;
        this.aiResumeSuggestionMapper = aiResumeSuggestionMapper;
        this.aiResumeSuggestionPromptService = aiResumeSuggestionPromptService;
        this.aiResumeSuggestionOutputParser = aiResumeSuggestionOutputParser;
        this.aiClientService = aiClientService;
        this.objectMapper = objectMapper;
        this.resumeRagService = resumeRagService;
    }

    @Override
    @Transactional
    public AiResumeSuggestion generate(Long userId, Long resumeId, Long jobDescriptionId, Long aiJobMatchResultId) {
        Resume resume = getOwnedResume(userId, resumeId);
        ResumeParseResult parseResult = getSuccessfulResumeParseResult(resume.getId());
        JobDescription jobDescription = getOwnedSuccessfulJobDescription(userId, jobDescriptionId);
        AiJobMatchResult matchResult = getSuccessfulMatchResult(
                resume.getId(),
                jobDescription.getId(),
                aiJobMatchResultId);
        RagContextDTO ragContext = resumeRagService.buildContext(userId, resume.getId(), jobDescription.getId(), RAG_TOP_K);
        AiResumeSuggestionPromptDTO prompt = aiResumeSuggestionPromptService.buildPrompt(
                parseResult.getStructuredJson(),
                jobDescription.getStructuredContent(),
                buildMatchResultPromptInput(matchResult),
                ragContext.getContextText());

        log.info("AI resume suggestion started: userId={}, resumeId={}, jobDescriptionId={}, aiJobMatchResultId={}, model={}, ragUsed={}, ragMatchCount={}",
                userId,
                resume.getId(),
                jobDescription.getId(),
                matchResult.getId(),
                aiClientService.modelName(),
                ragContext.isUsed(),
                ragContext.getMatchCount());

        try {
            String aiOutput = aiClientService.complete(prompt.getPrompt());
            AiResumeSuggestionResultDTO result = aiResumeSuggestionOutputParser.parse(aiOutput);
            AiResumeSuggestion savedSuggestion = saveSuccess(
                    resume.getId(),
                    jobDescription.getId(),
                    matchResult.getId(),
                    prompt.getPromptVersion(),
                    result);
            log.info("AI resume suggestion succeeded: userId={}, resumeId={}, jobDescriptionId={}, aiJobMatchResultId={}, model={}",
                    userId,
                    resume.getId(),
                    jobDescription.getId(),
                    matchResult.getId(),
                    savedSuggestion.getModelName());
            return savedSuggestion;
        } catch (RuntimeException exception) {
            String errorMessage = normalizeErrorMessage(exception);
            AiResumeSuggestion failedSuggestion = saveFailed(
                    resume.getId(),
                    jobDescription.getId(),
                    matchResult.getId(),
                    prompt.getPromptVersion(),
                    errorMessage);
            log.warn("AI resume suggestion failed: userId={}, resumeId={}, jobDescriptionId={}, aiJobMatchResultId={}, model={}, reason={}",
                    userId,
                    resume.getId(),
                    jobDescription.getId(),
                    matchResult.getId(),
                    aiClientService.modelName(),
                    LogSanitizer.sanitize(errorMessage));
            return failedSuggestion;
        }
    }

    @Override
    public AiResumeSuggestion getByResumeAndJobDescription(Long userId, Long resumeId, Long jobDescriptionId) {
        Resume resume = getOwnedResume(userId, resumeId);
        JobDescription jobDescription = getOwnedJobDescription(userId, jobDescriptionId);
        AiResumeSuggestion suggestion = aiResumeSuggestionMapper.selectOne(new LambdaQueryWrapper<AiResumeSuggestion>()
                .eq(AiResumeSuggestion::getResumeId, resume.getId())
                .eq(AiResumeSuggestion::getJobDescriptionId, jobDescription.getId()));
        if (suggestion == null) {
            throw new BusinessException(404, "AI 优化建议结果不存在");
        }
        return suggestion;
    }

    @Override
    public AiResumeSuggestion getByResumeAndMatchResult(Long userId, Long resumeId, Long aiJobMatchResultId) {
        getOwnedResume(userId, resumeId);
        if (aiJobMatchResultId == null) {
            throw new BusinessException(400, "AI 匹配结果 ID 不能为空");
        }
        AiResumeSuggestion suggestion = aiResumeSuggestionMapper.selectOne(new LambdaQueryWrapper<AiResumeSuggestion>()
                .eq(AiResumeSuggestion::getResumeId, resumeId)
                .eq(AiResumeSuggestion::getAiJobMatchResultId, aiJobMatchResultId));
        if (suggestion == null) {
            throw new BusinessException(404, "AI 优化建议结果不存在");
        }
        return suggestion;
    }

    @Override
    public List<AiResumeSuggestion> listByResume(Long userId, Long resumeId) {
        Resume resume = getOwnedResume(userId, resumeId);
        return aiResumeSuggestionMapper.selectList(new LambdaQueryWrapper<AiResumeSuggestion>()
                .eq(AiResumeSuggestion::getResumeId, resume.getId())
                .orderByDesc(AiResumeSuggestion::getUpdatedAt)
                .orderByDesc(AiResumeSuggestion::getCreatedAt));
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

    private ResumeParseResult getSuccessfulResumeParseResult(Long resumeId) {
        ResumeParseResult parseResult = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resumeId));
        if (parseResult == null) {
            throw new BusinessException(400, "请先完成简历解析");
        }
        if (!PARSE_STATUS_SUCCESS.equals(parseResult.getParseStatus())) {
            throw new BusinessException(400, "简历解析未成功，不能生成优化建议");
        }
        if (parseResult.getStructuredJson() == null || parseResult.getStructuredJson().isBlank()) {
            throw new BusinessException(400, "简历结构化解析结果为空，不能生成优化建议");
        }
        return parseResult;
    }

    private JobDescription getOwnedSuccessfulJobDescription(Long userId, Long jobDescriptionId) {
        JobDescription jobDescription = getOwnedJobDescription(userId, jobDescriptionId);
        if (!PARSE_STATUS_SUCCESS.equals(jobDescription.getParseStatus())) {
            throw new BusinessException(400, "目标岗位解析未成功，不能生成岗位优化建议");
        }
        if (jobDescription.getStructuredContent() == null || jobDescription.getStructuredContent().isBlank()) {
            throw new BusinessException(400, "目标岗位结构化解析结果为空，不能生成岗位优化建议");
        }
        return jobDescription;
    }

    private JobDescription getOwnedJobDescription(Long userId, Long jobDescriptionId) {
        if (jobDescriptionId == null) {
            throw new BusinessException(400, "目标岗位 ID 不能为空");
        }

        JobDescription jobDescription = jobDescriptionMapper.selectOne(new LambdaQueryWrapper<JobDescription>()
                .eq(JobDescription::getId, jobDescriptionId)
                .eq(JobDescription::getUserId, userId));
        if (jobDescription == null) {
            throw new BusinessException(404, "目标岗位不存在");
        }
        return jobDescription;
    }

    private AiJobMatchResult getSuccessfulMatchResult(Long resumeId, Long jobDescriptionId, Long aiJobMatchResultId) {
        LambdaQueryWrapper<AiJobMatchResult> query = new LambdaQueryWrapper<AiJobMatchResult>()
                .eq(AiJobMatchResult::getResumeId, resumeId)
                .eq(AiJobMatchResult::getJobDescriptionId, jobDescriptionId);
        if (aiJobMatchResultId != null) {
            query.eq(AiJobMatchResult::getId, aiJobMatchResultId);
        }

        AiJobMatchResult matchResult = aiJobMatchResultMapper.selectOne(query);
        if (matchResult == null) {
            throw new BusinessException(404, "匹配分析结果不存在");
        }
        if (!MATCH_STATUS_SUCCESS.equals(matchResult.getMatchStatus())) {
            throw new BusinessException(400, "匹配分析未成功，不能生成岗位优化建议");
        }
        return matchResult;
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

    private com.fasterxml.jackson.databind.JsonNode readJsonArrayOrText(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(value);
            if (node.isArray()) {
                return node;
            }
        } catch (JsonProcessingException ignored) {
            // Stored AI match fields should be JSON arrays. If legacy data is not JSON, keep the text as evidence input.
        }
        return objectMapper.getNodeFactory().textNode(value);
    }

    private AiResumeSuggestion saveSuccess(
            Long resumeId,
            Long jobDescriptionId,
            Long aiJobMatchResultId,
            String promptVersion,
            AiResumeSuggestionResultDTO result) {
        try {
            AiResumeSuggestion suggestion = getOrCreateSuggestion(resumeId, jobDescriptionId, aiJobMatchResultId);
            suggestion.setSuggestionStatus(SUGGESTION_STATUS_SUCCESS);
            suggestion.setSuggestions(objectMapper.writeValueAsString(result.getSuggestions()));
            suggestion.setModelName(aiClientService.modelName());
            suggestion.setPromptVersion(promptVersion);
            suggestion.setErrorMessage(null);
            save(suggestion);
            return suggestion;
        } catch (JsonProcessingException exception) {
            return saveFailed(resumeId, jobDescriptionId, aiJobMatchResultId, promptVersion, "AI 优化建议结果序列化失败");
        }
    }

    private AiResumeSuggestion saveFailed(
            Long resumeId,
            Long jobDescriptionId,
            Long aiJobMatchResultId,
            String promptVersion,
            String errorMessage) {
        AiResumeSuggestion suggestion = getOrCreateSuggestion(resumeId, jobDescriptionId, aiJobMatchResultId);
        suggestion.setSuggestionStatus(SUGGESTION_STATUS_FAILED);
        suggestion.setSuggestions(null);
        suggestion.setModelName(aiClientService.modelName());
        suggestion.setPromptVersion(promptVersion);
        suggestion.setErrorMessage(truncateErrorMessage(errorMessage));
        save(suggestion);
        return suggestion;
    }

    private AiResumeSuggestion getOrCreateSuggestion(Long resumeId, Long jobDescriptionId, Long aiJobMatchResultId) {
        AiResumeSuggestion suggestion = aiResumeSuggestionMapper.selectOne(new LambdaQueryWrapper<AiResumeSuggestion>()
                .eq(AiResumeSuggestion::getAiJobMatchResultId, aiJobMatchResultId));
        if (suggestion != null) {
            return suggestion;
        }

        suggestion = new AiResumeSuggestion();
        suggestion.setResumeId(resumeId);
        suggestion.setJobDescriptionId(jobDescriptionId);
        suggestion.setAiJobMatchResultId(aiJobMatchResultId);
        suggestion.setCreatedAt(LocalDateTime.now());
        return suggestion;
    }

    private void save(AiResumeSuggestion suggestion) {
        suggestion.setUpdatedAt(LocalDateTime.now());
        if (suggestion.getId() == null) {
            aiResumeSuggestionMapper.insert(suggestion);
        } else {
            aiResumeSuggestionMapper.updateById(suggestion);
        }
    }

    private String normalizeErrorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "AI 优化建议生成失败";
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
