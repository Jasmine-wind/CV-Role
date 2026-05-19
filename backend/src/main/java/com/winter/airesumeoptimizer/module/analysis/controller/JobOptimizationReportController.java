package com.winter.airesumeoptimizer.module.analysis.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.analysis.service.JobOptimizationReportService;
import com.winter.airesumeoptimizer.module.analysis.vo.JobOptimizationReportVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumes")
@Validated
@Tag(name = "Job Optimization Report", description = "岗位优化报告接口")
@SecurityRequirement(name = "bearerAuth")
public class JobOptimizationReportController {

    private final JobOptimizationReportService jobOptimizationReportService;

    public JobOptimizationReportController(JobOptimizationReportService jobOptimizationReportService) {
        this.jobOptimizationReportService = jobOptimizationReportService;
    }

    @GetMapping("/{resumeId}/job-optimization-report")
    @Operation(summary = "查询岗位优化报告", description = "基于已有匹配分析、岗位优化建议和局部改写结果聚合岗位优化报告")
    public Result<JobOptimizationReportVO> report(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long resumeId,
            @RequestParam @Positive(message = "目标岗位 ID 必须大于 0") Long jobDescriptionId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(jobOptimizationReportService.getReport(
                authenticatedUser.getUserId(),
                resumeId,
                jobDescriptionId));
    }
}
