package com.winter.airesumeoptimizer.module.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.embedding.mapper.JobDescriptionEmbeddingMapper;
import com.winter.airesumeoptimizer.module.export.service.ExportArtifactCleanupService;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionSubmitDTO;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionService;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobDescriptionServiceImpl implements JobDescriptionService {

    private static final String PARSE_STATUS_PENDING = "PENDING";
    private static final String SOURCE_TYPE_USER_INPUT = "USER_INPUT";

    private final JobDescriptionMapper jobDescriptionMapper;
    private final AiJobMatchResultMapper aiJobMatchResultMapper;
    private final JobDescriptionEmbeddingMapper jobDescriptionEmbeddingMapper;
    private final ExportArtifactCleanupService exportArtifactCleanupService;

    public JobDescriptionServiceImpl(
            JobDescriptionMapper jobDescriptionMapper,
            AiJobMatchResultMapper aiJobMatchResultMapper,
            JobDescriptionEmbeddingMapper jobDescriptionEmbeddingMapper,
            ExportArtifactCleanupService exportArtifactCleanupService) {
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.aiJobMatchResultMapper = aiJobMatchResultMapper;
        this.jobDescriptionEmbeddingMapper = jobDescriptionEmbeddingMapper;
        this.exportArtifactCleanupService = exportArtifactCleanupService;
    }

    @Override
    @Transactional
    public JobDescriptionVO submit(Long userId, JobDescriptionSubmitDTO request) {
        validateUserId(userId);
        if (request == null) {
            throw new BusinessException(400, "目标岗位不能为空");
        }

        JobDescription jobDescription = new JobDescription();
        jobDescription.setUserId(userId);
        jobDescription.setTitle(request.getTitle().strip());
        jobDescription.setSourceType(SOURCE_TYPE_USER_INPUT);
        jobDescription.setRawText(request.getRawText().strip());
        jobDescription.setParseStatus(PARSE_STATUS_PENDING);
        LocalDateTime now = LocalDateTime.now();
        jobDescription.setCreatedAt(now);
        jobDescription.setUpdatedAt(now);

        int rows = jobDescriptionMapper.insert(jobDescription);
        if (rows != 1 || jobDescription.getId() == null) {
            throw new BusinessException(500, "目标岗位保存失败");
        }

        return toVO(jobDescription);
    }

    @Override
    public List<JobDescriptionVO> listByUser(Long userId) {
        validateUserId(userId);

        return jobDescriptionMapper.selectList(new LambdaQueryWrapper<JobDescription>()
                        .eq(JobDescription::getUserId, userId)
                        .orderByDesc(JobDescription::getUpdatedAt)
                        .orderByDesc(JobDescription::getCreatedAt))
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public JobDescriptionVO getDetail(Long userId, Long jobDescriptionId) {
        return toVO(getOwnedJobDescription(userId, jobDescriptionId));
    }

    @Override
    @Transactional
    public void delete(Long userId, Long jobDescriptionId) {
        JobDescription jobDescription = getOwnedJobDescription(userId, jobDescriptionId);
        // 必须先完成对象存储 + ExportArtifact 清理，随后才允许数据库父级联。
        exportArtifactCleanupService.deleteArtifactsForJobDescription(userId, jobDescription.getId());
        jobDescriptionEmbeddingMapper.deleteByJobDescriptionId(jobDescription.getId());
        aiJobMatchResultMapper.delete(new LambdaQueryWrapper<AiJobMatchResult>()
                .eq(AiJobMatchResult::getJobDescriptionId, jobDescription.getId()));
        jobDescriptionMapper.deleteById(jobDescription.getId());
    }

    private JobDescription getOwnedJobDescription(Long userId, Long jobDescriptionId) {
        validateUserId(userId);
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

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
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
