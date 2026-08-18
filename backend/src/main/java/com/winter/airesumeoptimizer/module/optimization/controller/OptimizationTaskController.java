package com.winter.airesumeoptimizer.module.optimization.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.analysis.assembler.AnalysisVoAssembler;
import com.winter.airesumeoptimizer.module.analysis.service.JobAnalysisService;
import com.winter.airesumeoptimizer.module.analysis.vo.JobAnalysisStartVO;
import com.winter.airesumeoptimizer.module.optimization.service.OptimizationTaskService;
import com.winter.airesumeoptimizer.module.optimization.vo.OptimizationAnalysisResultVO;
import com.winter.airesumeoptimizer.module.optimization.vo.OptimizationTaskVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/optimization-tasks")
@Validated
@Tag(name = "Optimization Task", description = "岗位定向优化正式业务任务")
@SecurityRequirement(name = "bearerAuth")
public class OptimizationTaskController {

    private final OptimizationTaskService optimizationTaskService;
    private final JobAnalysisService jobAnalysisService;
    private final AnalysisVoAssembler analysisVoAssembler;

    public OptimizationTaskController(
            OptimizationTaskService optimizationTaskService,
            JobAnalysisService jobAnalysisService,
            AnalysisVoAssembler analysisVoAssembler) {
        this.optimizationTaskService = optimizationTaskService;
        this.jobAnalysisService = jobAnalysisService;
        this.analysisVoAssembler = analysisVoAssembler;
    }

    @GetMapping("/{optimizationTaskId}")
    @Operation(summary = "查看优化任务", description = "按当前用户读取任务、版本来源和执行快照元数据")
    public Result<OptimizationTaskVO> get(
            @PathVariable @Positive(message = "优化任务 ID 必须大于 0") Long optimizationTaskId,
            Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(optimizationTaskService.get(user.getUserId(), optimizationTaskId));
    }

    @PostMapping("/{optimizationTaskId}/retry")
    @Operation(summary = "重试优化任务", description = "复用正式任务中已保存的简历版本与目标岗位输入")
    public Result<JobAnalysisStartVO> retry(
            @PathVariable @Positive(message = "优化任务 ID 必须大于 0") Long optimizationTaskId,
            Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(
                "岗位分析已重新开始",
                jobAnalysisService.retry(user.getUserId(), optimizationTaskId));
    }

    @GetMapping("/{optimizationTaskId}/analysis-result")
    @Operation(summary = "查看岗位分析结果", description = "通过正式优化任务读取对应的兼容分析结果")
    public Result<OptimizationAnalysisResultVO> getAnalysisResult(
            @PathVariable @Positive(message = "优化任务 ID 必须大于 0") Long optimizationTaskId,
            Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        OptimizationTaskVO task = optimizationTaskService.get(user.getUserId(), optimizationTaskId);
        return Result.success(OptimizationAnalysisResultVO.builder()
                .optimizationTaskId(task.getOptimizationTaskId())
                .sourceResumeVersionId(task.getSourceResumeVersionId())
                .targetResumeVersionId(task.getTargetResumeVersionId())
                .jobTargetId(task.getJobTargetId())
                .status(task.getStatus())
                .jobTitle(task.getJobTitle())
                .resumeName(task.getResumeName())
                .analysis(analysisVoAssembler.toAiJobMatchResultVO(
                        optimizationTaskService.getAnalysisResult(user.getUserId(), optimizationTaskId)))
                .build());
    }
}
