package com.winter.airesumeoptimizer.module.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
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

    private final JobDescriptionMapper jobDescriptionMapper;

    public JobDescriptionServiceImpl(JobDescriptionMapper jobDescriptionMapper) {
        this.jobDescriptionMapper = jobDescriptionMapper;
    }

    @Override
    @Transactional
    public JobDescriptionVO submit(Long userId, JobDescriptionSubmitDTO request) {
        validateUserId(userId);
        if (request == null) {
            throw new BusinessException(400, "岗位描述不能为空");
        }

        JobDescription jobDescription = new JobDescription();
        jobDescription.setUserId(userId);
        jobDescription.setTitle(request.getTitle().strip());
        jobDescription.setRawText(request.getRawText().strip());
        jobDescription.setParseStatus(PARSE_STATUS_PENDING);
        LocalDateTime now = LocalDateTime.now();
        jobDescription.setCreatedAt(now);
        jobDescription.setUpdatedAt(now);

        int rows = jobDescriptionMapper.insert(jobDescription);
        if (rows != 1 || jobDescription.getId() == null) {
            throw new BusinessException(500, "岗位描述保存失败");
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
        validateUserId(userId);
        if (jobDescriptionId == null) {
            throw new BusinessException(400, "岗位描述 ID 不能为空");
        }

        JobDescription jobDescription = jobDescriptionMapper.selectOne(new LambdaQueryWrapper<JobDescription>()
                .eq(JobDescription::getId, jobDescriptionId)
                .eq(JobDescription::getUserId, userId));
        if (jobDescription == null) {
            throw new BusinessException(404, "岗位描述不存在");
        }

        return toVO(jobDescription);
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
