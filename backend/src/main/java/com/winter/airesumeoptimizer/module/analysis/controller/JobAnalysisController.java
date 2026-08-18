package com.winter.airesumeoptimizer.module.analysis.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.analysis.dto.JobAnalysisStartRequestDTO;
import com.winter.airesumeoptimizer.module.analysis.service.JobAnalysisService;
import com.winter.airesumeoptimizer.module.analysis.vo.JobAnalysisStartVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-analyses")
@Validated
@Tag(name = "Job Analysis", description = "默认岗位分析主流程")
@SecurityRequirement(name = "bearerAuth")
public class JobAnalysisController {

    private final JobAnalysisService jobAnalysisService;

    public JobAnalysisController(JobAnalysisService jobAnalysisService) {
        this.jobAnalysisService = jobAnalysisService;
    }

    @PostMapping
    @Operation(summary = "开始岗位分析", description = "保存目标 JD，并在后台完成准备和岗位匹配分析")
    public Result<JobAnalysisStartVO> start(
            @Valid @RequestBody JobAnalysisStartRequestDTO request,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success("岗位分析已开始", jobAnalysisService.start(authenticatedUser.getUserId(), request));
    }

    @PostMapping("/{jobDescriptionId}/retry")
    @Operation(summary = "重试岗位分析", description = "复用已保存的目标 JD 和简历重新执行岗位分析")
    public Result<JobAnalysisStartVO> retry(
            @PathVariable @Positive(message = "目标岗位 ID 必须大于 0") Long jobDescriptionId,
            @RequestParam @Positive(message = "简历 ID 必须大于 0") Long resumeId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success("岗位分析已重新开始", jobAnalysisService.retry(
                authenticatedUser.getUserId(),
                resumeId,
                jobDescriptionId));
    }
}
