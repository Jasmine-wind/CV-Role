package com.winter.airesumeoptimizer.module.history.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.history.service.HistoryService;
import com.winter.airesumeoptimizer.module.history.vo.HistoryDetailVO;
import com.winter.airesumeoptimizer.module.history.vo.HistoryPageVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
@Validated
@Tag(name = "History", description = "历史记录接口")
@SecurityRequirement(name = "bearerAuth")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    @Operation(summary = "历史记录列表", description = "分页查询当前用户的简历处理历史记录")
    public Result<HistoryPageVO> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于等于 1") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量必须大于等于 1")
            @Max(value = 50, message = "每页数量不能超过 50") Integer size,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(historyService.list(authenticatedUser.getUserId(), page, size));
    }

    @GetMapping("/{resumeId}")
    @Operation(summary = "历史记录详情", description = "查询指定简历的完整历史记录详情")
    public Result<HistoryDetailVO> detail(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long resumeId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(historyService.detail(authenticatedUser.getUserId(), resumeId));
    }
}
