package com.winter.airesumeoptimizer.module.embedding.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.embedding.dto.SemanticMatchRow;
import com.winter.airesumeoptimizer.module.embedding.entity.JobDescriptionEmbedding;
import com.winter.airesumeoptimizer.module.embedding.entity.ResumeEmbedding;
import com.winter.airesumeoptimizer.module.embedding.mapper.JobDescriptionEmbeddingMapper;
import com.winter.airesumeoptimizer.module.embedding.mapper.ResumeEmbeddingMapper;
import com.winter.airesumeoptimizer.module.embedding.service.SemanticMatchService;
import com.winter.airesumeoptimizer.module.embedding.vo.SemanticMatchItemVO;
import com.winter.airesumeoptimizer.module.embedding.vo.SemanticMatchResultVO;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SemanticMatchServiceImpl implements SemanticMatchService {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final int DEFAULT_TOP_K = 5;
    private static final int MIN_TOP_K = 1;
    private static final int MAX_TOP_K = 20;

    private final ResumeMapper resumeMapper;
    private final JobDescriptionMapper jobDescriptionMapper;
    private final ResumeEmbeddingMapper resumeEmbeddingMapper;
    private final JobDescriptionEmbeddingMapper jobDescriptionEmbeddingMapper;

    public SemanticMatchServiceImpl(
            ResumeMapper resumeMapper,
            JobDescriptionMapper jobDescriptionMapper,
            ResumeEmbeddingMapper resumeEmbeddingMapper,
            JobDescriptionEmbeddingMapper jobDescriptionEmbeddingMapper) {
        this.resumeMapper = resumeMapper;
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.resumeEmbeddingMapper = resumeEmbeddingMapper;
        this.jobDescriptionEmbeddingMapper = jobDescriptionEmbeddingMapper;
    }

    @Override
    public SemanticMatchResultVO match(Long userId, Long resumeId, Long jobDescriptionId, Integer topK) {
        Resume resume = getOwnedResume(userId, resumeId);
        JobDescription jobDescription = getOwnedJobDescription(userId, jobDescriptionId);
        List<ResumeEmbedding> resumeEmbeddings = getSuccessfulResumeEmbeddings(resume.getId());
        List<JobDescriptionEmbedding> jobDescriptionEmbeddings =
                getSuccessfulJobDescriptionEmbeddings(jobDescription.getId());
        int dimension = resolveAndValidateDimension(resumeEmbeddings, jobDescriptionEmbeddings);
        String model = resolveEmbeddingModel(resumeEmbeddings, jobDescriptionEmbeddings);
        int resolvedTopK = normalizeTopK(topK);

        List<SemanticMatchItemVO> matches = resumeEmbeddingMapper.selectTopSemanticMatches(
                        userId,
                        resume.getId(),
                        jobDescription.getId(),
                        resolvedTopK)
                .stream()
                .map(this::toItemVO)
                .toList();

        return SemanticMatchResultVO.builder()
                .resumeId(resume.getId())
                .jobDescriptionId(jobDescription.getId())
                .embeddingModel(model)
                .embeddingDimension(dimension)
                .topK(resolvedTopK)
                .overallSimilarity(resolveOverallSimilarity(matches))
                .matches(matches)
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

    private List<ResumeEmbedding> getSuccessfulResumeEmbeddings(Long resumeId) {
        List<ResumeEmbedding> records = resumeEmbeddingMapper.selectList(new LambdaQueryWrapper<ResumeEmbedding>()
                .eq(ResumeEmbedding::getResumeId, resumeId)
                .eq(ResumeEmbedding::getEmbeddingStatus, STATUS_SUCCESS)
                .isNotNull(ResumeEmbedding::getEmbedding)
                .orderByAsc(ResumeEmbedding::getChunkIndex));
        if (records.isEmpty()) {
            throw new BusinessException(400, "简历向量尚未生成，不能进行语义相似度查询");
        }
        return records;
    }

    private List<JobDescriptionEmbedding> getSuccessfulJobDescriptionEmbeddings(Long jobDescriptionId) {
        List<JobDescriptionEmbedding> records = jobDescriptionEmbeddingMapper.selectList(
                new LambdaQueryWrapper<JobDescriptionEmbedding>()
                        .eq(JobDescriptionEmbedding::getJobDescriptionId, jobDescriptionId)
                        .eq(JobDescriptionEmbedding::getEmbeddingStatus, STATUS_SUCCESS)
                        .isNotNull(JobDescriptionEmbedding::getEmbedding)
                        .orderByAsc(JobDescriptionEmbedding::getChunkIndex));
        if (records.isEmpty()) {
            throw new BusinessException(400, "岗位描述向量尚未生成，不能进行语义相似度查询");
        }
        return records;
    }

    private int resolveAndValidateDimension(
            List<ResumeEmbedding> resumeEmbeddings,
            List<JobDescriptionEmbedding> jobDescriptionEmbeddings) {
        Integer resumeDimension = resolveResumeDimension(resumeEmbeddings);
        Integer jobDescriptionDimension = resolveJobDescriptionDimension(jobDescriptionEmbeddings);
        if (resumeDimension == null || jobDescriptionDimension == null) {
            throw new BusinessException(400, "向量维度缺失，不能进行语义相似度查询");
        }
        if (!resumeDimension.equals(jobDescriptionDimension)) {
            throw new BusinessException(400, "简历向量和岗位描述向量维度不一致，不能进行语义相似度查询");
        }
        return resumeDimension;
    }

    private Integer resolveResumeDimension(List<ResumeEmbedding> records) {
        return records.stream()
                .map(ResumeEmbedding::getEmbeddingDimension)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(null);
    }

    private Integer resolveJobDescriptionDimension(List<JobDescriptionEmbedding> records) {
        return records.stream()
                .map(JobDescriptionEmbedding::getEmbeddingDimension)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(null);
    }

    private String resolveEmbeddingModel(
            List<ResumeEmbedding> resumeEmbeddings,
            List<JobDescriptionEmbedding> jobDescriptionEmbeddings) {
        return resumeEmbeddings.stream()
                .map(ResumeEmbedding::getEmbeddingModel)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> jobDescriptionEmbeddings.stream()
                        .map(JobDescriptionEmbedding::getEmbeddingModel)
                        .filter(value -> value != null && !value.isBlank())
                        .findFirst()
                        .orElse(null));
    }

    private int normalizeTopK(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        return Math.max(MIN_TOP_K, Math.min(MAX_TOP_K, topK));
    }

    private SemanticMatchItemVO toItemVO(SemanticMatchRow row) {
        return SemanticMatchItemVO.builder()
                .resumeEmbeddingId(row.getResumeEmbeddingId())
                .resumeChunkIndex(row.getResumeChunkIndex())
                .resumeChunkText(row.getResumeChunkText())
                .jobDescriptionEmbeddingId(row.getJobDescriptionEmbeddingId())
                .jobDescriptionChunkIndex(row.getJobDescriptionChunkIndex())
                .jobDescriptionChunkText(row.getJobDescriptionChunkText())
                .similarityScore(row.getSimilarityScore())
                .build();
    }

    private Double resolveOverallSimilarity(List<SemanticMatchItemVO> matches) {
        return matches.stream()
                .map(SemanticMatchItemVO::getSimilarityScore)
                .filter(score -> score != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .stream()
                .boxed()
                .findFirst()
                .orElse(null);
    }
}
