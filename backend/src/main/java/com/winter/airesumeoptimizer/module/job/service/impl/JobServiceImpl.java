package com.winter.airesumeoptimizer.module.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.job.entity.Job;
import com.winter.airesumeoptimizer.module.job.mapper.JobMapper;
import com.winter.airesumeoptimizer.module.job.service.JobService;
import com.winter.airesumeoptimizer.module.job.vo.JobDetailVO;
import com.winter.airesumeoptimizer.module.job.vo.JobListVO;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class JobServiceImpl implements JobService {

    private static final String STATUS_ENABLED = "ENABLED";
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final JobMapper jobMapper;
    private final ObjectMapper objectMapper;

    public JobServiceImpl(JobMapper jobMapper, ObjectMapper objectMapper) {
        this.jobMapper = jobMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<JobListVO> listEnabledJobs() {
        return jobMapper.selectList(new LambdaQueryWrapper<Job>()
                        .eq(Job::getStatus, STATUS_ENABLED)
                        .orderByDesc(Job::getCreatedAt))
                .stream()
                .map(this::toListVO)
                .toList();
    }

    @Override
    public JobDetailVO getEnabledJobDetail(Long jobId) {
        if (jobId == null) {
            throw new BusinessException(400, "岗位 ID 不能为空");
        }

        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        if (!STATUS_ENABLED.equals(job.getStatus())) {
            throw new BusinessException(404, "岗位不可用");
        }

        return toDetailVO(job);
    }

    private JobListVO toListVO(Job job) {
        return JobListVO.builder()
                .id(job.getId())
                .title(job.getTitle())
                .companyName(job.getCompanyName())
                .jobCategory(job.getJobCategory())
                .location(job.getLocation())
                .requiredSkills(parseRequiredSkills(job.getRequiredSkills()))
                .status(job.getStatus())
                .build();
    }

    private JobDetailVO toDetailVO(Job job) {
        return JobDetailVO.builder()
                .id(job.getId())
                .title(job.getTitle())
                .companyName(job.getCompanyName())
                .jobCategory(job.getJobCategory())
                .location(job.getLocation())
                .description(job.getDescription())
                .requirements(job.getRequirements())
                .requiredSkills(parseRequiredSkills(job.getRequiredSkills()))
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private List<String> parseRequiredSkills(String requiredSkills) {
        if (requiredSkills == null || requiredSkills.isBlank()) {
            return List.of();
        }

        String normalized = requiredSkills.trim();
        if (normalized.startsWith("[")) {
            try {
                return objectMapper.readValue(normalized, STRING_LIST_TYPE);
            } catch (JsonProcessingException exception) {
                throw new BusinessException(500, "岗位技能关键词格式不正确");
            }
        }

        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(skill -> !skill.isEmpty())
                .toList();
    }
}
