package com.winter.airesumeoptimizer.module.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionParseResultDTO;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionPromptDTO;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionOutputParser;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionParseService;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionPromptService;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobDescriptionParseServiceImpl implements JobDescriptionParseService {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionParseServiceImpl.class);

    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    private static final String PARSE_STATUS_FAILED = "FAILED";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final JobDescriptionMapper jobDescriptionMapper;
    private final JobDescriptionPromptService jobDescriptionPromptService;
    private final JobDescriptionOutputParser jobDescriptionOutputParser;
    private final AiClientService aiClientService;
    private final ObjectMapper objectMapper;

    public JobDescriptionParseServiceImpl(
            JobDescriptionMapper jobDescriptionMapper,
            JobDescriptionPromptService jobDescriptionPromptService,
            JobDescriptionOutputParser jobDescriptionOutputParser,
            AiClientService aiClientService,
            ObjectMapper objectMapper) {
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.jobDescriptionPromptService = jobDescriptionPromptService;
        this.jobDescriptionOutputParser = jobDescriptionOutputParser;
        this.aiClientService = aiClientService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public JobDescriptionVO parse(Long userId, Long jobDescriptionId) {
        JobDescription jobDescription = getOwnedJobDescription(userId, jobDescriptionId);
        JobDescriptionPromptDTO prompt = jobDescriptionPromptService.buildPrompt(jobDescription.getRawText());
        log.info("Job description AI parse started: userId={}, jobDescriptionId={}, model={}",
                userId,
                jobDescription.getId(),
                aiClientService.modelName());

        try {
            String aiOutput = aiClientService.complete(prompt.getPrompt());
            JobDescriptionParseResultDTO result = jobDescriptionOutputParser.parse(aiOutput);
            saveSuccess(jobDescription, prompt.getPromptVersion(), result);
            log.info("Job description AI parse succeeded: userId={}, jobDescriptionId={}, model={}",
                    userId,
                    jobDescription.getId(),
                    jobDescription.getModelName());
        } catch (RuntimeException exception) {
            String errorMessage = normalizeErrorMessage(exception);
            saveFailed(jobDescription, prompt.getPromptVersion(), errorMessage);
            log.warn("Job description AI parse failed: userId={}, jobDescriptionId={}, model={}, reason={}",
                    userId,
                    jobDescription.getId(),
                    aiClientService.modelName(),
                    LogSanitizer.sanitize(errorMessage));
        }

        return toVO(jobDescription);
    }

    private JobDescription getOwnedJobDescription(Long userId, Long jobDescriptionId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (jobDescriptionId == null) {
            throw new BusinessException(400, "岗位描述 ID 不能为空");
        }

        JobDescription jobDescription = jobDescriptionMapper.selectOne(new LambdaQueryWrapper<JobDescription>()
                .eq(JobDescription::getId, jobDescriptionId)
                .eq(JobDescription::getUserId, userId));
        if (jobDescription == null) {
            throw new BusinessException(404, "岗位描述不存在");
        }
        if (jobDescription.getRawText() == null || jobDescription.getRawText().isBlank()) {
            throw new BusinessException(400, "岗位描述原文不能为空");
        }
        return jobDescription;
    }

    private void saveSuccess(
            JobDescription jobDescription,
            String promptVersion,
            JobDescriptionParseResultDTO result) {
        try {
            jobDescription.setParseStatus(PARSE_STATUS_SUCCESS);
            jobDescription.setStructuredContent(objectMapper.writeValueAsString(result));
            jobDescription.setModelName(aiClientService.modelName());
            jobDescription.setPromptVersion(promptVersion);
            jobDescription.setErrorMessage(null);
            save(jobDescription);
        } catch (JsonProcessingException exception) {
            saveFailed(jobDescription, promptVersion, "岗位描述解析结果序列化失败");
        }
    }

    private void saveFailed(JobDescription jobDescription, String promptVersion, String errorMessage) {
        jobDescription.setParseStatus(PARSE_STATUS_FAILED);
        jobDescription.setStructuredContent(null);
        jobDescription.setModelName(aiClientService.modelName());
        jobDescription.setPromptVersion(promptVersion);
        jobDescription.setErrorMessage(truncateErrorMessage(errorMessage));
        save(jobDescription);
    }

    private void save(JobDescription jobDescription) {
        jobDescription.setUpdatedAt(LocalDateTime.now());
        jobDescriptionMapper.updateById(jobDescription);
    }

    private String normalizeErrorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "岗位描述 AI 解析失败";
        }
        return exception.getMessage();
    }

    private String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private JobDescriptionVO toVO(JobDescription jobDescription) {
        return JobDescriptionVO.builder()
                .id(jobDescription.getId())
                .title(jobDescription.getTitle())
                .rawText(jobDescription.getRawText())
                .parseStatus(jobDescription.getParseStatus())
                .structuredContent(jobDescription.getStructuredContent())
                .modelName(jobDescription.getModelName())
                .promptVersion(jobDescription.getPromptVersion())
                .errorMessage(jobDescription.getErrorMessage())
                .createdAt(jobDescription.getCreatedAt())
                .updatedAt(jobDescription.getUpdatedAt())
                .build();
    }
}
