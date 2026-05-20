package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchResultDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchOutputParser;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchPromptService;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchService;
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
public class AiJobMatchServiceImpl implements AiJobMatchService {

    private static final Logger log = LoggerFactory.getLogger(AiJobMatchServiceImpl.class);

    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    private static final String MATCH_STATUS_SUCCESS = "SUCCESS";
    private static final String MATCH_STATUS_FAILED = "FAILED";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final int RAG_TOP_K = 3;

    private final ResumeMapper resumeMapper;
    private final ResumeParseResultMapper resumeParseResultMapper;
    private final JobDescriptionMapper jobDescriptionMapper;
    private final AiJobMatchResultMapper aiJobMatchResultMapper;
    private final AiJobMatchPromptService aiJobMatchPromptService;
    private final AiJobMatchOutputParser aiJobMatchOutputParser;
    private final AiClientService aiClientService;
    private final ObjectMapper objectMapper;
    private final ResumeRagService resumeRagService;

    public AiJobMatchServiceImpl(
            ResumeMapper resumeMapper,
            ResumeParseResultMapper resumeParseResultMapper,
            JobDescriptionMapper jobDescriptionMapper,
            AiJobMatchResultMapper aiJobMatchResultMapper,
            AiJobMatchPromptService aiJobMatchPromptService,
            AiJobMatchOutputParser aiJobMatchOutputParser,
            AiClientService aiClientService,
            ObjectMapper objectMapper,
            ResumeRagService resumeRagService) {
        this.resumeMapper = resumeMapper;
        this.resumeParseResultMapper = resumeParseResultMapper;
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.aiJobMatchResultMapper = aiJobMatchResultMapper;
        this.aiJobMatchPromptService = aiJobMatchPromptService;
        this.aiJobMatchOutputParser = aiJobMatchOutputParser;
        this.aiClientService = aiClientService;
        this.objectMapper = objectMapper;
        this.resumeRagService = resumeRagService;
    }

    @Override
    @Transactional
    public AiJobMatchResult match(Long userId, Long resumeId, Long jobDescriptionId) {
        Resume resume = getOwnedResume(userId, resumeId);
        ResumeParseResult parseResult = getSuccessfulResumeParseResult(resume.getId());
        JobDescription jobDescription = getOwnedSuccessfulJobDescription(userId, jobDescriptionId);
        RagContextDTO ragContext = resumeRagService.buildContext(userId, resume.getId(), jobDescription.getId(), RAG_TOP_K);
        AiJobMatchPromptDTO prompt = aiJobMatchPromptService.buildPrompt(
                parseResult.getStructuredJson(),
                jobDescription.getStructuredContent(),
                parseResult.getExtractedText(),
                ragContext.getContextText());

        log.info("AI job match started: userId={}, resumeId={}, jobDescriptionId={}, model={}, ragUsed={}, ragMatchCount={}",
                userId,
                resume.getId(),
                jobDescription.getId(),
                aiClientService.modelName(),
                ragContext.isUsed(),
                ragContext.getMatchCount());

        try {
            String aiOutput = aiClientService.complete(prompt.getPrompt());
            AiJobMatchResultDTO result = aiJobMatchOutputParser.parse(aiOutput);
            AiJobMatchResult savedResult = saveSuccess(
                    resume.getId(),
                    jobDescription.getId(),
                    prompt.getPromptVersion(),
                    result);
            log.info("AI job match succeeded: userId={}, resumeId={}, jobDescriptionId={}, score={}, model={}",
                    userId,
                    resume.getId(),
                    jobDescription.getId(),
                    savedResult.getOverallScore(),
                    savedResult.getModelName());
            return savedResult;
        } catch (RuntimeException exception) {
            String errorMessage = normalizeErrorMessage(exception);
            AiJobMatchResult failedResult = saveFailed(
                    resume.getId(),
                    jobDescription.getId(),
                    prompt.getPromptVersion(),
                    errorMessage);
            log.warn("AI job match failed: userId={}, resumeId={}, jobDescriptionId={}, model={}, reason={}",
                    userId,
                    resume.getId(),
                    jobDescription.getId(),
                    aiClientService.modelName(),
                    LogSanitizer.sanitize(errorMessage));
            return failedResult;
        }
    }

    @Override
    public List<AiJobMatchResult> listByResume(Long userId, Long resumeId) {
        Resume resume = getOwnedResume(userId, resumeId);
        return aiJobMatchResultMapper.selectList(new LambdaQueryWrapper<AiJobMatchResult>()
                .eq(AiJobMatchResult::getResumeId, resume.getId())
                .orderByDesc(AiJobMatchResult::getUpdatedAt)
                .orderByDesc(AiJobMatchResult::getCreatedAt));
    }

    @Override
    public AiJobMatchResult getByResumeAndJobDescription(Long userId, Long resumeId, Long jobDescriptionId) {
        Resume resume = getOwnedResume(userId, resumeId);
        JobDescription jobDescription = getOwnedJobDescription(userId, jobDescriptionId);
        AiJobMatchResult matchResult = aiJobMatchResultMapper.selectOne(new LambdaQueryWrapper<AiJobMatchResult>()
                .eq(AiJobMatchResult::getResumeId, resume.getId())
                .eq(AiJobMatchResult::getJobDescriptionId, jobDescription.getId()));
        if (matchResult == null) {
            throw new BusinessException(404, "匹配分析结果不存在");
        }
        return matchResult;
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
            throw new BusinessException(400, "简历解析未成功，不能进行匹配分析");
        }
        if (parseResult.getStructuredJson() == null || parseResult.getStructuredJson().isBlank()) {
            throw new BusinessException(400, "简历结构化解析结果为空，不能进行匹配分析");
        }
        return parseResult;
    }

    private JobDescription getOwnedSuccessfulJobDescription(Long userId, Long jobDescriptionId) {
        JobDescription jobDescription = getOwnedJobDescription(userId, jobDescriptionId);
        if (!PARSE_STATUS_SUCCESS.equals(jobDescription.getParseStatus())) {
            throw new BusinessException(400, "目标岗位解析未成功，不能进行匹配分析");
        }
        if (jobDescription.getStructuredContent() == null || jobDescription.getStructuredContent().isBlank()) {
            throw new BusinessException(400, "目标岗位结构化解析结果为空，不能进行匹配分析");
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

    private AiJobMatchResult saveSuccess(
            Long resumeId,
            Long jobDescriptionId,
            String promptVersion,
            AiJobMatchResultDTO result) {
        try {
            AiJobMatchResult matchResult = getOrCreateResult(resumeId, jobDescriptionId);
            matchResult.setMatchStatus(MATCH_STATUS_SUCCESS);
            matchResult.setOverallScore(result.getOverallScore());
            matchResult.setStrongMatches(objectMapper.writeValueAsString(result.getStrongMatches()));
            matchResult.setWeakMatches(objectMapper.writeValueAsString(result.getWeakMatches()));
            matchResult.setMissingSkills(objectMapper.writeValueAsString(result.getMissingSkills()));
            matchResult.setWeakExperienceDescriptions(objectMapper.writeValueAsString(result.getWeakExperienceDescriptions()));
            matchResult.setEvidence(objectMapper.writeValueAsString(result.getEvidence()));
            matchResult.setRiskNotes(objectMapper.writeValueAsString(result.getRiskNotes()));
            matchResult.setModelName(aiClientService.modelName());
            matchResult.setPromptVersion(promptVersion);
            matchResult.setErrorMessage(null);
            save(matchResult);
            return matchResult;
        } catch (JsonProcessingException exception) {
            return saveFailed(resumeId, jobDescriptionId, promptVersion, "AI 匹配结果序列化失败");
        }
    }

    private AiJobMatchResult saveFailed(
            Long resumeId,
            Long jobDescriptionId,
            String promptVersion,
            String errorMessage) {
        AiJobMatchResult matchResult = getOrCreateResult(resumeId, jobDescriptionId);
        matchResult.setMatchStatus(MATCH_STATUS_FAILED);
        matchResult.setOverallScore(null);
        matchResult.setStrongMatches(null);
        matchResult.setWeakMatches(null);
        matchResult.setMissingSkills(null);
        matchResult.setWeakExperienceDescriptions(null);
        matchResult.setEvidence(null);
        matchResult.setRiskNotes(null);
        matchResult.setModelName(aiClientService.modelName());
        matchResult.setPromptVersion(promptVersion);
        matchResult.setErrorMessage(truncateErrorMessage(errorMessage));
        save(matchResult);
        return matchResult;
    }

    private AiJobMatchResult getOrCreateResult(Long resumeId, Long jobDescriptionId) {
        AiJobMatchResult matchResult = aiJobMatchResultMapper.selectOne(new LambdaQueryWrapper<AiJobMatchResult>()
                .eq(AiJobMatchResult::getResumeId, resumeId)
                .eq(AiJobMatchResult::getJobDescriptionId, jobDescriptionId));
        if (matchResult != null) {
            return matchResult;
        }

        matchResult = new AiJobMatchResult();
        matchResult.setResumeId(resumeId);
        matchResult.setJobDescriptionId(jobDescriptionId);
        matchResult.setCreatedAt(LocalDateTime.now());
        return matchResult;
    }

    private void save(AiJobMatchResult matchResult) {
        matchResult.setUpdatedAt(LocalDateTime.now());
        if (matchResult.getId() == null) {
            aiJobMatchResultMapper.insert(matchResult);
        } else {
            aiJobMatchResultMapper.updateById(matchResult);
        }
    }

    private String normalizeErrorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "匹配分析失败";
        }
        return exception.getMessage();
    }

    private String truncateErrorMessage(String errorMessage) {
        String sanitized = LogSanitizer.sanitize(errorMessage);
        if (sanitized == null || sanitized.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
