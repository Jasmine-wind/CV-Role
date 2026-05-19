package com.winter.airesumeoptimizer.module.task.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.task.service.AsyncTaskService;
import com.winter.airesumeoptimizer.module.task.vo.AsyncTaskVO;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
@Validated
@Tag(name = "Async Task", description = "异步任务状态查询接口")
@SecurityRequirement(name = "bearerAuth")
public class AsyncTaskController {

    private final AsyncTaskService asyncTaskService;

    public AsyncTaskController(AsyncTaskService asyncTaskService) {
        this.asyncTaskService = asyncTaskService;
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "查询异步任务状态", description = "查询当前用户自己的异步任务状态")
    public Result<AsyncTaskVO> detail(
            @PathVariable @Positive(message = "任务 ID 必须大于 0") Long taskId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(asyncTaskService.getTask(taskId, authenticatedUser.getUserId()));
    }
}
