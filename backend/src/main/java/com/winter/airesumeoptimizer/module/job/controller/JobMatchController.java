package com.winter.airesumeoptimizer.module.job.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.job.dto.JobMatchRequestDTO;
import com.winter.airesumeoptimizer.module.job.service.JobMatchResultService;
import com.winter.airesumeoptimizer.module.job.vo.JobMatchResultVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumes/{resumeId}/job-matches")
@Validated
@Tag(name = "Job Match", description = "简历与岗位匹配接口")
@SecurityRequirement(name = "bearerAuth")
public class JobMatchController {

    private final JobMatchResultService jobMatchResultService;

    public JobMatchController(JobMatchResultService jobMatchResultService) {
        this.jobMatchResultService = jobMatchResultService;
    }

    @PostMapping
    @Operation(summary = "触发岗位匹配", description = "将指定简历与目标岗位进行匹配并生成建议")
    public Result<JobMatchResultVO> match(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long resumeId,
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
    @Operation(summary = "岗位匹配结果列表", description = "查询指定简历的岗位匹配结果")
    public Result<List<JobMatchResultVO>> list(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long resumeId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(jobMatchResultService.listByResume(authenticatedUser.getUserId(), resumeId));
    }
}
