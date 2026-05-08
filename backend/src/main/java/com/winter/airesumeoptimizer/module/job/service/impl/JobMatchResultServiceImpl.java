package com.winter.airesumeoptimizer.module.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.job.dto.JobMatchCalculationResultDTO;
import com.winter.airesumeoptimizer.module.job.dto.JobMatchSuggestionDTO;
import com.winter.airesumeoptimizer.module.job.entity.Job;
import com.winter.airesumeoptimizer.module.job.entity.JobMatchResult;
import com.winter.airesumeoptimizer.module.job.mapper.JobMapper;
import com.winter.airesumeoptimizer.module.job.mapper.JobMatchResultMapper;
import com.winter.airesumeoptimizer.module.job.service.JobMatchResultService;
import com.winter.airesumeoptimizer.module.job.service.JobMatchService;
import com.winter.airesumeoptimizer.module.job.service.JobMatchSuggestionService;
import com.winter.airesumeoptimizer.module.job.vo.JobMatchResultVO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobMatchResultServiceImpl implements JobMatchResultService {

    private static final String JOB_STATUS_ENABLED = "ENABLED";
    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<JobMatchSuggestionDTO>> SUGGESTION_LIST_TYPE = new TypeReference<>() {
    };

    private final ResumeMapper resumeMapper;
    private final ResumeParseResultMapper resumeParseResultMapper;
    private final JobMapper jobMapper;
    private final JobMatchResultMapper jobMatchResultMapper;
    private final JobMatchService jobMatchService;
    private final JobMatchSuggestionService jobMatchSuggestionService;
    private final ObjectMapper objectMapper;

    public JobMatchResultServiceImpl(
            ResumeMapper resumeMapper,
            ResumeParseResultMapper resumeParseResultMapper,
            JobMapper jobMapper,
            JobMatchResultMapper jobMatchResultMapper,
            JobMatchService jobMatchService,
            JobMatchSuggestionService jobMatchSuggestionService,
            ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.resumeParseResultMapper = resumeParseResultMapper;
        this.jobMapper = jobMapper;
        this.jobMatchResultMapper = jobMatchResultMapper;
        this.jobMatchService = jobMatchService;
        this.jobMatchSuggestionService = jobMatchSuggestionService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public JobMatchResultVO match(Long userId, Long resumeId, Long jobId) {
        Resume resume = getOwnedResume(userId, resumeId);
        ResumeParseResult parseResult = getSuccessfulParseResult(resume.getId());
        ResumeStructuredContentDTO resumeContent = readResumeStructuredContent(parseResult.getStructuredJson());
        Job job = getEnabledJob(jobId);

        JobMatchCalculationResultDTO calculationResult = jobMatchService.calculateMatch(resumeContent, job);
        List<JobMatchSuggestionDTO> suggestions = jobMatchSuggestionService.generateSuggestions(calculationResult, job);
        JobMatchResult matchResult = saveMatchResult(resume.getId(), job.getId(), calculationResult, suggestions);
        return toVO(matchResult, job);
    }

    @Override
    public List<JobMatchResultVO> listByResume(Long userId, Long resumeId) {
        Resume resume = getOwnedResume(userId, resumeId);
        return jobMatchResultMapper.selectList(new LambdaQueryWrapper<JobMatchResult>()
                        .eq(JobMatchResult::getResumeId, resume.getId())
                        .orderByDesc(JobMatchResult::getUpdatedAt))
                .stream()
                .map(matchResult -> toVO(matchResult, getJobForResult(matchResult.getJobId())))
                .toList();
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
            throw new BusinessException(400, "简历解析未成功，不能进行岗位匹配");
        }
        if (parseResult.getStructuredJson() == null || parseResult.getStructuredJson().isBlank()) {
            throw new BusinessException(400, "简历结构化解析结果为空，不能进行岗位匹配");
        }
        return parseResult;
    }

    private ResumeStructuredContentDTO readResumeStructuredContent(String structuredJson) {
        try {
            return objectMapper.readValue(structuredJson, ResumeStructuredContentDTO.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历结构化解析结果格式不正确");
        }
    }

    private Job getEnabledJob(Long jobId) {
        if (jobId == null) {
            throw new BusinessException(400, "岗位 ID 不能为空");
        }

        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(404, "岗位不存在");
        }
        if (!JOB_STATUS_ENABLED.equals(job.getStatus())) {
            throw new BusinessException(404, "岗位不可用");
        }
        return job;
    }

    private Job getJobForResult(Long jobId) {
        Job job = jobMapper.selectById(jobId);
        if (job == null) {
            throw new BusinessException(500, "岗位匹配结果关联岗位不存在");
        }
        return job;
    }

    private JobMatchResult saveMatchResult(
            Long resumeId,
            Long jobId,
            JobMatchCalculationResultDTO calculationResult,
            List<JobMatchSuggestionDTO> suggestions) {
        try {
            JobMatchResult matchResult = getOrCreateMatchResult(resumeId, jobId);
            matchResult.setMatchScore(calculationResult.getMatchScore());
            matchResult.setMatchedItems(objectMapper.writeValueAsString(calculationResult.getMatchedItems()));
            matchResult.setMissingItems(objectMapper.writeValueAsString(calculationResult.getMissingItems()));
            matchResult.setMatchReason(calculationResult.getMatchReason());
            matchResult.setSuggestions(objectMapper.writeValueAsString(suggestions));
            matchResult.setUpdatedAt(LocalDateTime.now());

            if (matchResult.getId() == null) {
                jobMatchResultMapper.insert(matchResult);
            } else {
                jobMatchResultMapper.updateById(matchResult);
            }
            return matchResult;
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "岗位匹配结果序列化失败");
        }
    }

    private JobMatchResult getOrCreateMatchResult(Long resumeId, Long jobId) {
        JobMatchResult matchResult = jobMatchResultMapper.selectOne(new LambdaQueryWrapper<JobMatchResult>()
                .eq(JobMatchResult::getResumeId, resumeId)
                .eq(JobMatchResult::getJobId, jobId));
        if (matchResult != null) {
            return matchResult;
        }

        matchResult = new JobMatchResult();
        matchResult.setResumeId(resumeId);
        matchResult.setJobId(jobId);
        matchResult.setCreatedAt(LocalDateTime.now());
        return matchResult;
    }

    private JobMatchResultVO toVO(JobMatchResult matchResult, Job job) {
        return JobMatchResultVO.builder()
                .matchId(matchResult.getId())
                .resumeId(matchResult.getResumeId())
                .jobId(matchResult.getJobId())
                .jobTitle(job.getTitle())
                .companyName(job.getCompanyName())
                .matchScore(matchResult.getMatchScore())
                .matchedItems(readStringList(matchResult.getMatchedItems()))
                .missingItems(readStringList(matchResult.getMissingItems()))
                .matchReason(matchResult.getMatchReason())
                .suggestions(readSuggestions(matchResult.getSuggestions()))
                .updatedAt(matchResult.getUpdatedAt())
                .build();
    }

    private List<String> readStringList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "岗位匹配结果格式不正确");
        }
    }

    private List<JobMatchSuggestionDTO> readSuggestions(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, SUGGESTION_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "岗位匹配建议格式不正确");
        }
    }
}
