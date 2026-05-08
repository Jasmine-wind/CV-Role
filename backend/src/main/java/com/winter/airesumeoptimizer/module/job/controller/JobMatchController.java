package com.winter.airesumeoptimizer.module.job.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.job.dto.JobMatchRequestDTO;
import com.winter.airesumeoptimizer.module.job.service.JobMatchResultService;
import com.winter.airesumeoptimizer.module.job.vo.JobMatchResultVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumes/{resumeId}/job-matches")
public class JobMatchController {

    private final JobMatchResultService jobMatchResultService;

    public JobMatchController(JobMatchResultService jobMatchResultService) {
        this.jobMatchResultService = jobMatchResultService;
    }

    @PostMapping
    public Result<JobMatchResultVO> match(
            @PathVariable Long resumeId,
            @Valid @RequestBody JobMatchRequestDTO requestDTO,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        JobMatchResultVO result = jobMatchResultService.match(
                authenticatedUser.getUserId(),
                resumeId,
                requestDTO.getJobId());
        return Result.success("岗位匹配完成", result);
    }

    @GetMapping
    public Result<List<JobMatchResultVO>> list(
            @PathVariable Long resumeId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(jobMatchResultService.listByResume(authenticatedUser.getUserId(), resumeId));
    }
}
