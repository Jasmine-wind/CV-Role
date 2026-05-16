package com.winter.airesumeoptimizer.module.embedding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.AiClientException;
import com.winter.airesumeoptimizer.infra.ai.EmbeddingClientService;
import com.winter.airesumeoptimizer.module.embedding.entity.ResumeEmbedding;
import com.winter.airesumeoptimizer.module.embedding.mapper.ResumeEmbeddingMapper;
import com.winter.airesumeoptimizer.module.embedding.service.ResumeEmbeddingService;
import com.winter.airesumeoptimizer.module.embedding.service.TextChunkService;
import com.winter.airesumeoptimizer.module.embedding.vo.ResumeEmbeddingRecordVO;
import com.winter.airesumeoptimizer.module.embedding.vo.ResumeEmbeddingSummaryVO;
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
public class ResumeEmbeddingServiceImpl implements ResumeEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ResumeEmbeddingServiceImpl.class);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PARTIAL_SUCCESS = "PARTIAL_SUCCESS";
    private static final String STATUS_NOT_GENERATED = "NOT_GENERATED";
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final ResumeMapper resumeMapper;
    private final ResumeParseResultMapper resumeParseResultMapper;
    private final ResumeEmbeddingMapper resumeEmbeddingMapper;
    private final TextChunkService textChunkService;
    private final EmbeddingClientService embeddingClientService;

    public ResumeEmbeddingServiceImpl(
            ResumeMapper resumeMapper,
            ResumeParseResultMapper resumeParseResultMapper,
            ResumeEmbeddingMapper resumeEmbeddingMapper,
            TextChunkService textChunkService,
            EmbeddingClientService embeddingClientService) {
        this.resumeMapper = resumeMapper;
        this.resumeParseResultMapper = resumeParseResultMapper;
        this.resumeEmbeddingMapper = resumeEmbeddingMapper;
        this.textChunkService = textChunkService;
        this.embeddingClientService = embeddingClientService;
    }

    @Override
    @Transactional
    public ResumeEmbeddingSummaryVO generate(Long userId, Long resumeId) {
        Resume resume = getOwnedResume(userId, resumeId);
        ResumeParseResult parseResult = getSuccessParseResult(resume.getId());
        List<String> chunks = textChunkService.splitResumeText(
                parseResult.getStructuredJson(),
                parseResult.getExtractedText());
        if (chunks.isEmpty()) {
            throw new BusinessException(400, "简历解析文本为空，无法生成向量");
        }

        resumeEmbeddingMapper.deleteByResumeId(resume.getId());
        log.info("Resume embedding generation started: userId={}, resumeId={}, chunkCount={}",
                userId,
                resume.getId(),
                chunks.size());

        for (int index = 0; index < chunks.size(); index++) {
            ResumeEmbedding embeddingRecord = insertPendingRecord(resume, index, chunks.get(index));
            try {
                List<Double> embedding = embeddingClientService.embed(chunks.get(index));
                resumeEmbeddingMapper.updateSuccessEmbedding(
                        embeddingRecord.getId(),
                        toPgVectorLiteral(embedding),
                        embeddingClientService.modelName(),
                        embedding.size(),
                        LocalDateTime.now());
            } catch (AiClientException exception) {
                resumeEmbeddingMapper.updateFailedEmbedding(
                        embeddingRecord.getId(),
                        embeddingClientService.modelName(),
                        embeddingClientService.dimension(),
                        truncateErrorMessage(exception.getMessage()),
                        LocalDateTime.now());
                log.warn("Resume embedding chunk failed: userId={}, resumeId={}, chunkIndex={}, reason={}",
                        userId,
                        resume.getId(),
                        index,
                        exception.getMessage());
            }
        }

        ResumeEmbeddingSummaryVO summary = getSummary(userId, resume.getId());
        log.info("Resume embedding generation finished: userId={}, resumeId={}, status={}, totalChunks={}",
                userId,
                resume.getId(),
                summary.getEmbeddingStatus(),
                summary.getTotalChunks());
        return summary;
    }

    @Override
    public ResumeEmbeddingSummaryVO getSummary(Long userId, Long resumeId) {
        Resume resume = getOwnedResume(userId, resumeId);
        List<ResumeEmbedding> records = resumeEmbeddingMapper.selectList(new LambdaQueryWrapper<ResumeEmbedding>()
                .eq(ResumeEmbedding::getResumeId, resume.getId())
                .orderByAsc(ResumeEmbedding::getChunkIndex));
        return toSummary(resume.getId(), records);
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

    private ResumeParseResult getSuccessParseResult(Long resumeId) {
        ResumeParseResult parseResult = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resumeId));
        if (parseResult == null) {
            throw new BusinessException(400, "简历尚未解析，不能生成向量");
        }
        if (!STATUS_SUCCESS.equals(parseResult.getParseStatus())) {
            throw new BusinessException(400, "简历解析未成功，不能生成向量");
        }
        return parseResult;
    }

    private ResumeEmbedding insertPendingRecord(Resume resume, int chunkIndex, String chunkText) {
        LocalDateTime now = LocalDateTime.now();
        ResumeEmbedding record = new ResumeEmbedding();
        record.setResumeId(resume.getId());
        record.setUserId(resume.getUserId());
        record.setChunkIndex(chunkIndex);
        record.setChunkText(chunkText);
        record.setEmbeddingModel(embeddingClientService.modelName());
        record.setEmbeddingDimension(embeddingClientService.dimension());
        record.setEmbeddingStatus(STATUS_PENDING);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        resumeEmbeddingMapper.insert(record);
        return record;
    }

    private ResumeEmbeddingSummaryVO toSummary(Long resumeId, List<ResumeEmbedding> records) {
        int totalChunks = records.size();
        int successChunks = (int) records.stream()
                .filter(record -> STATUS_SUCCESS.equals(record.getEmbeddingStatus()))
                .count();
        int failedChunks = (int) records.stream()
                .filter(record -> STATUS_FAILED.equals(record.getEmbeddingStatus()))
                .count();

        return ResumeEmbeddingSummaryVO.builder()
                .resumeId(resumeId)
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

    private String resolveEmbeddingModel(List<ResumeEmbedding> records) {
        return records.stream()
                .map(ResumeEmbedding::getEmbeddingModel)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(embeddingClientService.modelName());
    }

    private Integer resolveEmbeddingDimension(List<ResumeEmbedding> records) {
        return records.stream()
                .map(ResumeEmbedding::getEmbeddingDimension)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(embeddingClientService.dimension());
    }

    private ResumeEmbeddingRecordVO toRecordVO(ResumeEmbedding record) {
        return ResumeEmbeddingRecordVO.builder()
                .id(record.getId())
                .resumeId(record.getResumeId())
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
