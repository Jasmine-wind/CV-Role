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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-descriptions")
@Validated
@Tag(name = "Target Job", description = "目标岗位提交、查询和解析接口")
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
    @Operation(summary = "新增目标岗位", description = "保存当前用户粘贴的真实目标岗位 JD，来源固定为 USER_INPUT")
    public Result<JobDescriptionVO> submit(
            @Valid @RequestBody JobDescriptionSubmitDTO request,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success("目标岗位提交成功",
                jobDescriptionService.submit(authenticatedUser.getUserId(), request));
    }

    @GetMapping
    @Operation(summary = "目标岗位列表", description = "查询当前用户提交过的目标岗位 JD")
    public Result<List<JobDescriptionVO>> list(Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(jobDescriptionService.listByUser(authenticatedUser.getUserId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "目标岗位详情", description = "查询当前用户提交的目标岗位 JD")
    public Result<JobDescriptionVO> detail(
            @PathVariable @Positive(message = "目标岗位 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(jobDescriptionService.getDetail(authenticatedUser.getUserId(), id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除目标岗位", description = "删除当前用户提交的目标岗位及关联 AI 匹配结果")
    public Result<Void> delete(
            @PathVariable @Positive(message = "目标岗位 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        jobDescriptionService.delete(authenticatedUser.getUserId(), id);
        return Result.success("目标岗位删除成功", null);
    }

    @PostMapping("/{id}/parse")
    @Operation(summary = "解析目标岗位", description = "触发当前用户目标岗位 JD 的 AI 结构化解析，不生成简历建议")
    public Result<JobDescriptionVO> parse(
            @PathVariable @Positive(message = "目标岗位 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        JobDescriptionVO result = jobDescriptionParseService.parse(authenticatedUser.getUserId(), id);
        String message = "FAILED".equals(result.getParseStatus()) ? "目标岗位解析失败" : "目标岗位解析完成";
        return Result.success(message, result);
    }
}
