package com.winter.airesumeoptimizer.module.history.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.history.service.AiHistoryService;
import com.winter.airesumeoptimizer.module.history.vo.AiResultDetailVO;
import com.winter.airesumeoptimizer.module.history.vo.AiResultPageVO;
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
@RequestMapping("/api/ai-results")
@Validated
@Tag(name = "AI History", description = "AI 历史结果接口")
@SecurityRequirement(name = "bearerAuth")
public class AiHistoryController {

    private final AiHistoryService aiHistoryService;

    public AiHistoryController(AiHistoryService aiHistoryService) {
        this.aiHistoryService = aiHistoryService;
    }

    @GetMapping
    @Operation(summary = "AI 历史结果列表", description = "分页查询当前用户的 AI 历史结果")
    public Result<AiResultPageVO> list(
            @RequestParam(required = false) String resultType,
            @RequestParam(required = false) @Positive(message = "简历 ID 必须大于 0") Long resumeId,
            @RequestParam(required = false) @Positive(message = "目标岗位 ID 必须大于 0") Long jobDescriptionId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码必须大于等于 1") Integer page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量必须大于等于 1")
            @Max(value = 50, message = "每页数量不能超过 50") Integer size,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(aiHistoryService.list(
                authenticatedUser.getUserId(),
                resultType,
                resumeId,
                jobDescriptionId,
                status,
                page,
                size));
    }

    @GetMapping("/{resultType}/{recordId}")
    @Operation(summary = "AI 历史结果详情", description = "查询当前用户某一条 AI 历史结果详情")
    public Result<AiResultDetailVO> detail(
            @PathVariable String resultType,
            @PathVariable @Positive(message = "AI 结果记录 ID 必须大于 0") Long recordId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(aiHistoryService.detail(authenticatedUser.getUserId(), resultType, recordId));
    }
}
