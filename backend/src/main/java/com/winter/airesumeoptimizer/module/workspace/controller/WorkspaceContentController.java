package com.winter.airesumeoptimizer.module.workspace.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceContentSaveRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.service.WorkspaceContentService;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentSaveResultVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspace")
@Validated
@Tag(name = "Workspace", description = "岗位定向优化工作区：以优化任务为唯一入口编辑岗位版本简历")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceContentController {

    private final WorkspaceContentService workspaceContentService;

    public WorkspaceContentController(WorkspaceContentService workspaceContentService) {
        this.workspaceContentService = workspaceContentService;
    }

    @GetMapping("/{optimizationTaskId}/content")
    @Operation(summary = "读取工作区内容", description = "按优化任务解析岗位版本，返回最后成功持久化的编辑文档与服务端内容版本号")
    public Result<WorkspaceContentVO> getContent(
            @PathVariable @Positive(message = "优化任务 ID 必须大于 0") Long optimizationTaskId,
            Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(workspaceContentService.getContent(user.getUserId(), optimizationTaskId));
    }

    @PutMapping("/{optimizationTaskId}/content")
    @Operation(summary = "保存工作区内容", description = "携带 expectedRevision 做乐观并发控制；版本不一致时返回冲突与服务端当前版本号")
    public Result<WorkspaceContentSaveResultVO> saveContent(
            @PathVariable @Positive(message = "优化任务 ID 必须大于 0") Long optimizationTaskId,
            @Valid @RequestBody WorkspaceContentSaveRequestDTO request,
            Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(
                workspaceContentService.saveContent(user.getUserId(), optimizationTaskId, request));
    }

    @PostMapping("/{optimizationTaskId}/restore-pre-optimization")
    @Operation(summary = "恢复本次优化前版本", description = "基于任务冻结的简历输入快照重新生成编辑文档并作为新的内容版本号写入")
    public Result<WorkspaceContentSaveResultVO> restorePreOptimization(
            @PathVariable @Positive(message = "优化任务 ID 必须大于 0") Long optimizationTaskId,
            @Valid @RequestBody WorkspaceRestoreRequest request,
            Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(workspaceContentService.restorePreOptimizationContent(
                user.getUserId(), optimizationTaskId, request.expectedRevision()));
    }

    public record WorkspaceRestoreRequest(
            @NotNull(message = "缺少内容版本号")
            @PositiveOrZero(message = "内容版本号不能为负数")
            Long expectedRevision) {
    }
}
