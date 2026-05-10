package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.module.analysis.dto.ResumeAnalysisPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.ResumeAnalysisResultDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;
import com.winter.airesumeoptimizer.module.analysis.mapper.ResumeAiAnalysisMapper;
import com.winter.airesumeoptimizer.module.analysis.service.ResumeAnalysisOutputParser;
import com.winter.airesumeoptimizer.module.analysis.service.ResumeAnalysisPromptService;
import com.winter.airesumeoptimizer.module.analysis.service.ResumeAnalysisService;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeAnalysisServiceImpl implements ResumeAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalysisServiceImpl.class);

    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    private static final String ANALYSIS_STATUS_SUCCESS = "SUCCESS";
    private static final String ANALYSIS_STATUS_FAILED = "FAILED";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final ResumeMapper resumeMapper;
    private final ResumeParseResultMapper resumeParseResultMapper;
    private final ResumeAiAnalysisMapper resumeAiAnalysisMapper;
    private final ResumeAnalysisPromptService resumeAnalysisPromptService;
    private final ResumeAnalysisOutputParser resumeAnalysisOutputParser;
    private final AiClientService aiClientService;
    private final ObjectMapper objectMapper;

    public ResumeAnalysisServiceImpl(
            ResumeMapper resumeMapper,
            ResumeParseResultMapper resumeParseResultMapper,
            ResumeAiAnalysisMapper resumeAiAnalysisMapper,
            ResumeAnalysisPromptService resumeAnalysisPromptService,
            ResumeAnalysisOutputParser resumeAnalysisOutputParser,
            AiClientService aiClientService,
            ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.resumeParseResultMapper = resumeParseResultMapper;
        this.resumeAiAnalysisMapper = resumeAiAnalysisMapper;
        this.resumeAnalysisPromptService = resumeAnalysisPromptService;
        this.resumeAnalysisOutputParser = resumeAnalysisOutputParser;
        this.aiClientService = aiClientService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ResumeAiAnalysis analyze(Long userId, Long resumeId) {
        Resume resume = getOwnedResume(userId, resumeId);
        ResumeParseResult parseResult = getSuccessfulParseResult(resume.getId());
        ResumeAnalysisPromptDTO prompt = resumeAnalysisPromptService.buildPrompt(
                parseResult.getExtractedText(),
                parseResult.getStructuredJson());
        log.info("Resume AI analysis started: userId={}, resumeId={}, model={}",
                userId,
                resume.getId(),
                aiClientService.modelName());

        try {
            String aiOutput = aiClientService.complete(prompt.getPrompt());
            ResumeAnalysisResultDTO result = resumeAnalysisOutputParser.parse(aiOutput);
            ResumeAiAnalysis analysis = saveSuccessAnalysis(resume.getId(), prompt.getPromptVersion(), result);
            log.info("Resume AI analysis succeeded: userId={}, resumeId={}, score={}, model={}",
                    userId,
                    resume.getId(),
                    analysis.getScore(),
                    analysis.getModelName());
            return analysis;
        } catch (RuntimeException exception) {
            String errorMessage = normalizeErrorMessage(exception);
            ResumeAiAnalysis analysis = saveFailedAnalysis(
                    resume.getId(),
                    prompt.getPromptVersion(),
                    errorMessage);
            log.warn("Resume AI analysis failed: userId={}, resumeId={}, model={}, reason={}",
                    userId,
                    resume.getId(),
                    aiClientService.modelName(),
                    LogSanitizer.sanitize(errorMessage));
            return analysis;
        }
    }

    @Override
    public ResumeAiAnalysis getAnalysis(Long userId, Long resumeId) {
        Resume resume = getOwnedResume(userId, resumeId);
        ResumeAiAnalysis analysis = resumeAiAnalysisMapper.selectOne(new LambdaQueryWrapper<ResumeAiAnalysis>()
                .eq(ResumeAiAnalysis::getResumeId, resume.getId()));
        if (analysis == null) {
            throw new BusinessException(404, "简历尚未进行 AI 分析");
        }
        return analysis;
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

    private ResumeParseResult getSuccessfulParseResult(Long resumeId) {
        ResumeParseResult parseResult = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resumeId));
        if (parseResult == null) {
            throw new BusinessException(400, "请先完成简历解析");
        }
        if (!PARSE_STATUS_SUCCESS.equals(parseResult.getParseStatus())) {
            throw new BusinessException(400, "简历解析未成功，不能进行 AI 分析");
        }
        if (parseResult.getExtractedText() == null || parseResult.getExtractedText().isBlank()) {
            throw new BusinessException(400, "简历解析文本为空，不能进行 AI 分析");
        }
        return parseResult;
    }

    private ResumeAiAnalysis saveSuccessAnalysis(
            Long resumeId,
            String promptVersion,
            ResumeAnalysisResultDTO result) {
        try {
            ResumeAiAnalysis analysis = getOrCreateAnalysis(resumeId);
            analysis.setAnalysisStatus(ANALYSIS_STATUS_SUCCESS);
            analysis.setScore(result.getScore());
            analysis.setStrengths(objectMapper.writeValueAsString(result.getStrengths()));
            analysis.setProblems(objectMapper.writeValueAsString(result.getProblems()));
            analysis.setSuggestionsSummary(objectMapper.writeValueAsString(result.getSuggestionsSummary()));
            analysis.setModelName(aiClientService.modelName());
            analysis.setPromptVersion(promptVersion);
            analysis.setErrorMessage(null);
            saveAnalysis(analysis);
            return analysis;
        } catch (JsonProcessingException exception) {
            return saveFailedAnalysis(resumeId, promptVersion, "AI 分析结果序列化失败");
        }
    }

    private ResumeAiAnalysis saveFailedAnalysis(Long resumeId, String promptVersion, String errorMessage) {
        ResumeAiAnalysis analysis = getOrCreateAnalysis(resumeId);
        analysis.setAnalysisStatus(ANALYSIS_STATUS_FAILED);
        analysis.setScore(null);
        analysis.setStrengths(null);
        analysis.setProblems(null);
        analysis.setSuggestionsSummary(null);
        analysis.setModelName(aiClientService.modelName());
        analysis.setPromptVersion(promptVersion);
        analysis.setErrorMessage(truncateErrorMessage(errorMessage));
        saveAnalysis(analysis);
        return analysis;
    }

    private ResumeAiAnalysis getOrCreateAnalysis(Long resumeId) {
        ResumeAiAnalysis analysis = resumeAiAnalysisMapper.selectOne(new LambdaQueryWrapper<ResumeAiAnalysis>()
                .eq(ResumeAiAnalysis::getResumeId, resumeId));
        if (analysis != null) {
            return analysis;
        }

        analysis = new ResumeAiAnalysis();
        analysis.setResumeId(resumeId);
        analysis.setCreatedAt(LocalDateTime.now());
        return analysis;
    }

    private void saveAnalysis(ResumeAiAnalysis analysis) {
        analysis.setUpdatedAt(LocalDateTime.now());
        if (analysis.getId() == null) {
            resumeAiAnalysisMapper.insert(analysis);
        } else {
            resumeAiAnalysisMapper.updateById(analysis);
        }
    }

    private String normalizeErrorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "AI 简历分析失败";
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
