package com.winter.airesumeoptimizer.module.workspace.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceBulletSuggestRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.service.BulletRewriteService;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceBulletSuggestionVO;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspace")
@Validated
@Tag(name = "Workspace", description = "岗位定向优化工作区：以优化任务为唯一入口编辑岗位版本简历")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceSuggestController {

    private final BulletRewriteService bulletRewriteService;

    public WorkspaceSuggestController(BulletRewriteService bulletRewriteService) {
        this.bulletRewriteService = bulletRewriteService;
    }

    @PostMapping("/{optimizationTaskId}/bullet-suggestion")
    @Operation(summary = "请求单 Bullet 岗位定向改写建议",
            description = "只读生成建议：校验任务归属、正式证据分析、baseRevision 与原文哈希；"
                    + "建议只存在于当前会话，不落库，Apply 由前端草稿经既有自动保存完成")
    public Result<WorkspaceBulletSuggestionVO> suggestBulletRewrite(
            @PathVariable @Positive(message = "优化任务 ID 必须大于 0") Long optimizationTaskId,
            @Valid @RequestBody WorkspaceBulletSuggestRequestDTO request,
            Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(
                bulletRewriteService.suggestBulletRewrite(user.getUserId(), optimizationTaskId, request));
    }
}
