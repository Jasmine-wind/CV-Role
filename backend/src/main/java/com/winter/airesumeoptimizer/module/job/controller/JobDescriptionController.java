package com.winter.airesumeoptimizer.module.job.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.job.dto.JobDescriptionSubmitDTO;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionParseService;
import com.winter.airesumeoptimizer.module.job.service.JobDescriptionService;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
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
@RequestMapping("/api/job-descriptions")
@Validated
@Tag(name = "JobDescription", description = "岗位描述提交和查询接口")
@SecurityRequirement(name = "bearerAuth")
public class JobDescriptionController {

    private final JobDescriptionService jobDescriptionService;
    private final JobDescriptionParseService jobDescriptionParseService;

    public JobDescriptionController(
            JobDescriptionService jobDescriptionService,
            JobDescriptionParseService jobDescriptionParseService) {
        this.jobDescriptionService = jobDescriptionService;
        this.jobDescriptionParseService = jobDescriptionParseService;
    }

    @PostMapping
    @Operation(summary = "提交岗位描述", description = "保存当前用户提交的岗位描述原文")
    public Result<JobDescriptionVO> submit(
            @Valid @RequestBody JobDescriptionSubmitDTO request,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success("岗位描述提交成功",
                jobDescriptionService.submit(authenticatedUser.getUserId(), request));
    }

    @GetMapping
    @Operation(summary = "岗位描述列表", description = "查询当前用户提交过的岗位描述")
    public Result<List<JobDescriptionVO>> list(Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(jobDescriptionService.listByUser(authenticatedUser.getUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "岗位描述详情", description = "查询当前用户提交的岗位描述")
    public Result<JobDescriptionVO> detail(
            @PathVariable @Positive(message = "岗位描述 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(jobDescriptionService.getDetail(authenticatedUser.getUserId(), id));
    }

    @PostMapping("/{id}/parse")
    @Operation(summary = "解析岗位描述", description = "触发当前用户岗位描述的 AI 结构化解析")
    public Result<JobDescriptionVO> parse(
            @PathVariable @Positive(message = "岗位描述 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        JobDescriptionVO result = jobDescriptionParseService.parse(authenticatedUser.getUserId(), id);
        String message = "FAILED".equals(result.getParseStatus()) ? "岗位描述解析失败" : "岗位描述解析完成";
        return Result.success(message, result);
    }
}
