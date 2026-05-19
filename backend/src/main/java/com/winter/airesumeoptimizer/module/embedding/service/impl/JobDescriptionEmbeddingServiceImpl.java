package com.winter.airesumeoptimizer.module.embedding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.AiClientException;
import com.winter.airesumeoptimizer.infra.embedding.EmbeddingClientService;
import com.winter.airesumeoptimizer.module.embedding.entity.JobDescriptionEmbedding;
import com.winter.airesumeoptimizer.module.embedding.mapper.JobDescriptionEmbeddingMapper;
import com.winter.airesumeoptimizer.module.embedding.service.JobDescriptionEmbeddingService;
import com.winter.airesumeoptimizer.module.embedding.service.TextChunkService;
import com.winter.airesumeoptimizer.module.embedding.vo.JobDescriptionEmbeddingRecordVO;
import com.winter.airesumeoptimizer.module.embedding.vo.JobDescriptionEmbeddingSummaryVO;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobDescriptionEmbeddingServiceImpl implements JobDescriptionEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionEmbeddingServiceImpl.class);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    private static final String STATUS_NOT_GENERATED = "NOT_GENERATED";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final JobDescriptionMapper jobDescriptionMapper;
    private final JobDescriptionEmbeddingMapper jobDescriptionEmbeddingMapper;
    private final TextChunkService textChunkService;
    private final EmbeddingClientService embeddingClientService;

    public JobDescriptionEmbeddingServiceImpl(
            JobDescriptionMapper jobDescriptionMapper,
            JobDescriptionEmbeddingMapper jobDescriptionEmbeddingMapper,
            TextChunkService textChunkService,
            EmbeddingClientService embeddingClientService) {
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.jobDescriptionEmbeddingMapper = jobDescriptionEmbeddingMapper;
        this.textChunkService = textChunkService;
        this.embeddingClientService = embeddingClientService;
    }

    @Override
    @Transactional
    public JobDescriptionEmbeddingSummaryVO generate(Long userId, Long jobDescriptionId) {
        JobDescription jobDescription = getOwnedJobDescription(userId, jobDescriptionId);
        validateParseSuccess(jobDescription);
        List<String> chunks = textChunkService.splitJobDescriptionText(
                jobDescription.getStructuredContent(),
                jobDescription.getRawText());
        if (chunks.isEmpty()) {
            throw new BusinessException(400, "目标岗位文本为空，无法生成向量");
        }

        jobDescriptionEmbeddingMapper.deleteByJobDescriptionId(jobDescription.getId());
        log.info("Job description embedding generation started: userId={}, jobDescriptionId={}, chunkCount={}",
                userId,
                jobDescription.getId(),
                chunks.size());

        for (int index = 0; index < chunks.size(); index++) {
            JobDescriptionEmbedding embeddingRecord = insertPendingRecord(jobDescription, index, chunks.get(index));
            try {
                List<Double> embedding = embeddingClientService.embed(chunks.get(index));
                jobDescriptionEmbeddingMapper.updateSuccessEmbedding(
                        embeddingRecord.getId(),
                        toPgVectorLiteral(embedding),
                        embeddingClientService.modelName(),
                        embedding.size(),
                        LocalDateTime.now());
            } catch (AiClientException exception) {
                jobDescriptionEmbeddingMapper.updateFailedEmbedding(
                        embeddingRecord.getId(),
                        embeddingClientService.modelName(),
                        embeddingClientService.dimension(),
                        truncateErrorMessage(exception.getMessage()),
                        LocalDateTime.now());
                log.warn("Job description embedding chunk failed: userId={}, jobDescriptionId={}, chunkIndex={}, reason={}",
                        userId,
                        jobDescription.getId(),
                        index,
                        exception.getMessage());
            }
        }

        JobDescriptionEmbeddingSummaryVO summary = getSummary(userId, jobDescription.getId());
        log.info("Job description embedding generation finished: userId={}, jobDescriptionId={}, status={}, totalChunks={}",
                userId,
                jobDescription.getId(),
                summary.getEmbeddingStatus(),
                summary.getTotalChunks());
        return summary;
    }

    @Override
    public JobDescriptionEmbeddingSummaryVO getSummary(Long userId, Long jobDescriptionId) {
        JobDescription jobDescription = getOwnedJobDescription(userId, jobDescriptionId);
        List<JobDescriptionEmbedding> records = jobDescriptionEmbeddingMapper.selectList(
                new LambdaQueryWrapper<JobDescriptionEmbedding>()
                        .eq(JobDescriptionEmbedding::getJobDescriptionId, jobDescription.getId())
                        .orderByAsc(JobDescriptionEmbedding::getChunkIndex));
        return toSummary(jobDescription.getId(), records);
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
        return jobDescription;
    }

    private void validateParseSuccess(JobDescription jobDescription) {
        if (!STATUS_SUCCESS.equals(jobDescription.getParseStatus())) {
            throw new BusinessException(400, "目标岗位解析未成功，不能生成向量");
        }
    }

    private JobDescriptionEmbedding insertPendingRecord(
            JobDescription jobDescription,
            int chunkIndex,
            String chunkText) {
        LocalDateTime now = LocalDateTime.now();
        JobDescriptionEmbedding record = new JobDescriptionEmbedding();
        record.setJobDescriptionId(jobDescription.getId());
        record.setUserId(jobDescription.getUserId());
        record.setChunkIndex(chunkIndex);
        record.setChunkText(chunkText);
        record.setEmbeddingModel(embeddingClientService.modelName());
        record.setEmbeddingDimension(embeddingClientService.dimension());
        record.setEmbeddingStatus(STATUS_PENDING);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        jobDescriptionEmbeddingMapper.insert(record);
        return record;
    }

    private JobDescriptionEmbeddingSummaryVO toSummary(Long jobDescriptionId, List<JobDescriptionEmbedding> records) {
        int totalChunks = records.size();
        int successChunks = (int) records.stream()
                .filter(record -> STATUS_SUCCESS.equals(record.getEmbeddingStatus()))
                .count();
        int failedChunks = (int) records.stream()
                .filter(record -> STATUS_FAILED.equals(record.getEmbeddingStatus()))
                .count();

        return JobDescriptionEmbeddingSummaryVO.builder()
                .jobDescriptionId(jobDescriptionId)
                .embeddingModel(resolveEmbeddingModel(records))
                .embeddingDimension(resolveEmbeddingDimension(records))
                .totalChunks(totalChunks)
                .successChunks(successChunks)
                .failedChunks(failedChunks)
                .embeddingStatus(resolveSummaryStatus(totalChunks, successChunks, failedChunks))
                .records(records.stream().map(this::toRecordVO).toList())
                .build();
    }

    private String resolveSummaryStatus(int totalChunks, int successChunks, int failedChunks) {
        if (totalChunks == 0) {
            return STATUS_NOT_GENERATED;
        }
        if (successChunks == totalChunks) {
            return STATUS_SUCCESS;
        }
        if (failedChunks == totalChunks) {
            return STATUS_FAILED;
        }
        return STATUS_PARTIAL_SUCCESS;
    }

    private String resolveEmbeddingModel(List<JobDescriptionEmbedding> records) {
        return records.stream()
                .map(JobDescriptionEmbedding::getEmbeddingModel)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(embeddingClientService.modelName());
    }

    private Integer resolveEmbeddingDimension(List<JobDescriptionEmbedding> records) {
        return records.stream()
                .map(JobDescriptionEmbedding::getEmbeddingDimension)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(embeddingClientService.dimension());
    }

    private JobDescriptionEmbeddingRecordVO toRecordVO(JobDescriptionEmbedding record) {
        return JobDescriptionEmbeddingRecordVO.builder()
                .id(record.getId())
                .jobDescriptionId(record.getJobDescriptionId())
                .chunkIndex(record.getChunkIndex())
                .chunkText(record.getChunkText())
                .embeddingModel(record.getEmbeddingModel())
                .embeddingDimension(record.getEmbeddingDimension())
                .embeddingStatus(record.getEmbeddingStatus())
                .errorMessage(record.getErrorMessage())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    private String toPgVectorLiteral(List<Double> embedding) {
        return "[" + embedding.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("") + "]";
    }

    private String truncateErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }
        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
