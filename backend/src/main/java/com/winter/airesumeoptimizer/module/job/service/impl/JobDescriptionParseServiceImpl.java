package com.winter.airesumeoptimizer.module.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.infra.ai.AiGatewayRequest;
import com.winter.airesumeoptimizer.infra.ai.AiCompletionResult;
import com.winter.airesumeoptimizer.infra.ai.AiGateway;
import com.winter.airesumeoptimizer.infra.ai.AiGatewayException;
import com.winter.airesumeoptimizer.infra.ai.AiGatewaySupport;
import com.winter.airesumeoptimizer.infra.ai.AiInvocationContext;
import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionParseResultDTO;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionPromptDTO;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionOutputParser;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionParseService;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionPromptService;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobDescriptionParseServiceImpl implements JobDescriptionParseService {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionParseServiceImpl.class);

    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    private static final String PARSE_STATUS_FAILED = "FAILED";
    private static final String SOURCE_TYPE_USER_INPUT = "USER_INPUT";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final JobDescriptionMapper jobDescriptionMapper;
    private final JobDescriptionPromptService jobDescriptionPromptService;
    private final JobDescriptionOutputParser jobDescriptionOutputParser;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    public JobDescriptionParseServiceImpl(
            JobDescriptionMapper jobDescriptionMapper,
            JobDescriptionPromptService jobDescriptionPromptService,
            JobDescriptionOutputParser jobDescriptionOutputParser,
            AiGateway aiGateway,
            ObjectMapper objectMapper) {
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.jobDescriptionPromptService = jobDescriptionPromptService;
        this.jobDescriptionOutputParser = jobDescriptionOutputParser;
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public JobDescriptionVO parse(Long userId, Long jobDescriptionId) {
        return parse(userId, jobDescriptionId, null, null);
    }

    @Override
    @Transactional
    public JobDescriptionVO parse(
            Long userId,
            Long jobDescriptionId,
            AiSelectionSnapshot selection) {
        return parse(userId, jobDescriptionId, selection, null);
    }

    @Override
    @Transactional
    public JobDescriptionVO parse(
            Long userId,
            Long jobDescriptionId,
            AiSelectionSnapshot selection,
            Long optimizationTaskId) {
        JobDescription jobDescription = getOwnedJobDescription(userId, jobDescriptionId);
        JobDescriptionPromptDTO prompt = jobDescriptionPromptService.buildPrompt(jobDescription.getRawText());
        String selectedModel = resolveModel(userId, selection);
        log.info("Job description AI parse started: userId={}, jobDescriptionId={}, model={}",
                userId,
                jobDescription.getId(),
                selectedModel);

        try {
            AiCompletionResult completion = AiGatewaySupport.complete(
                    aiGateway,
                    new AiInvocationContext(userId, optimizationTaskId, "JOB_DESCRIPTION_PARSE", selection),
                    new AiGatewayRequest(
                            "JOB_DESCRIPTION_PARSE",
                            prompt.getSystemPrompt() == null || prompt.getSystemPrompt().isBlank()
                                    ? "只遵循服务端岗位解析输出契约。"
                                    : prompt.getSystemPrompt(),
                            prompt.getUserPrompt() == null ? prompt.getPrompt() : prompt.getUserPrompt()));
            JobDescriptionParseResultDTO result = jobDescriptionOutputParser.parse(completion.text());
            if (result == null) {
                throw new BusinessException(502, "目标岗位解析结果不是合法 JSON");
            }
            saveSuccess(jobDescription, prompt.getPromptVersion(), result, completion.model());
            log.info("Job description AI parse succeeded: userId={}, jobDescriptionId={}, model={}",
                    userId,
                    jobDescription.getId(),
                    completion.model());
        } catch (RuntimeException exception) {
            if (selection != null && selection.isUserByok() && exception instanceof AiGatewayException) {
                throw exception;
            }
            String errorMessage = normalizeErrorMessage(exception);
            saveFailed(jobDescription, prompt.getPromptVersion(), errorMessage, selectedModel);
            log.warn("Job description AI parse failed: userId={}, jobDescriptionId={}, model={}, reason={}",
                    userId,
                    jobDescription.getId(),
                    selectedModel,
                    LogSanitizer.sanitize(errorMessage));
        }

        return toVO(jobDescription);
    }

    private JobDescription getOwnedJobDescription(Long userId, Long jobDescriptionId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (jobDescriptionId == null) {
            throw new BusinessException(400, "目标岗位 ID 不能为空");
        }

        JobDescription jobDescription = jobDescriptionMapper.selectOne(new LambdaQueryWrapper<JobDescription>()
                .eq(JobDescription::getId, jobDescriptionId)
                .eq(JobDescription::getUserId, userId));
        if (jobDescription == null) {
            throw new BusinessException(404, "目标岗位不存在");
        }
        if (jobDescription.getRawText() == null || jobDescription.getRawText().isBlank()) {
            throw new BusinessException(400, "目标岗位 JD 原文不能为空");
        }
        return jobDescription;
    }

    private void saveSuccess(
            JobDescription jobDescription,
            String promptVersion,
            JobDescriptionParseResultDTO result,
            String modelName) {
        try {
            if (result.getJobTitle() != null && !result.getJobTitle().isBlank()) {
                jobDescription.setTitle(truncateTitle(result.getJobTitle()));
            }
            jobDescription.setParseStatus(PARSE_STATUS_SUCCESS);
            jobDescription.setStructuredContent(objectMapper.writeValueAsString(result));
            jobDescription.setModelName(modelName);
            jobDescription.setPromptVersion(promptVersion);
            jobDescription.setErrorMessage(null);
            save(jobDescription);
        } catch (JsonProcessingException exception) {
            saveFailed(jobDescription, promptVersion, "目标岗位解析结果序列化失败", modelName);
        }
    }

    private void saveFailed(JobDescription jobDescription, String promptVersion, String errorMessage, String modelName) {
        jobDescription.setParseStatus(PARSE_STATUS_FAILED);
        jobDescription.setStructuredContent(null);
        jobDescription.setModelName(modelName);
        jobDescription.setPromptVersion(promptVersion);
        jobDescription.setErrorMessage(truncateErrorMessage(errorMessage));
        save(jobDescription);
    }

    private void save(JobDescription jobDescription) {
        jobDescription.setUpdatedAt(LocalDateTime.now());
        jobDescriptionMapper.updateById(jobDescription);
    }

    private String resolveModel(Long userId, AiSelectionSnapshot selection) {
        if (selection != null && !selection.model().isBlank()) {
            return selection.model();
        }
        try {
            String model = AiGatewaySupport.modelName(
                    aiGateway,
                    new AiInvocationContext(userId, null, "JOB_DESCRIPTION_PARSE_METADATA", selection));
            return model == null || model.isBlank() ? "unknown" : model;
        } catch (RuntimeException exception) {
            return "unknown";
        }
    }

    private String normalizeErrorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "目标岗位解析失败";
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

    private String truncateTitle(String title) {
        String normalized = title.strip();
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }

    private JobDescriptionVO toVO(JobDescription jobDescription) {
        return JobDescriptionVO.builder()
                .id(jobDescription.getId())
                .title(jobDescription.getTitle())
                .sourceType(resolveSourceType(jobDescription.getSourceType()))
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

    private String resolveSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            return SOURCE_TYPE_USER_INPUT;
        }
        return sourceType;
    }
}
