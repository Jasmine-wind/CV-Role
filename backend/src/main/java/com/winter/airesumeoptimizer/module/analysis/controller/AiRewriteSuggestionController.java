package com.winter.airesumeoptimizer.module.analysis.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteAcceptStatusUpdateDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionService;
import com.winter.airesumeoptimizer.module.analysis.vo.AiRewriteSuggestionVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rewrite-suggestions")
@Validated
@Tag(name = "Analysis", description = "AI 局部改写建议接口")
@SecurityRequirement(name = "bearerAuth")
public class AiRewriteSuggestionController {

    private final AiRewriteSuggestionService aiRewriteSuggestionService;

    public AiRewriteSuggestionController(AiRewriteSuggestionService aiRewriteSuggestionService) {
        this.aiRewriteSuggestionService = aiRewriteSuggestionService;
    }

    @PatchMapping("/{rewriteId}/accept-status")
    @Operation(summary = "更新 AI 局部改写采纳状态", description = "记录用户对局部改写建议的采纳或拒绝决策，不修改原始简历")
    public Result<AiRewriteSuggestionVO> updateAcceptStatus(
            @PathVariable @Positive(message = "局部改写建议 ID 必须大于 0") Long rewriteId,
            @Valid @RequestBody AiRewriteAcceptStatusUpdateDTO request,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        AiRewriteSuggestion suggestion = aiRewriteSuggestionService.updateAcceptStatus(
                authenticatedUser.getUserId(),
                rewriteId,
                request.getAcceptStatus());
        return Result.success("局部改写采纳状态已更新", toAiRewriteSuggestionVO(suggestion));
    }

    private AiRewriteSuggestionVO toAiRewriteSuggestionVO(AiRewriteSuggestion suggestion) {
        return AiRewriteSuggestionVO.builder()
                .rewriteId(suggestion.getId())
                .resumeId(suggestion.getResumeId())
                .jobDescriptionId(suggestion.getJobDescriptionId())
                .aiJobMatchResultId(suggestion.getAiJobMatchResultId())
                .aiResumeSuggestionId(suggestion.getAiResumeSuggestionId())
                .rewriteType(suggestion.getRewriteType())
                .targetSection(suggestion.getTargetSection())
                .originalText(suggestion.getOriginalText())
                .rewrittenText(suggestion.getRewrittenText())
                .rewriteReason(suggestion.getRewriteReason())
                .caution(suggestion.getCaution())
                .acceptStatus(suggestion.getAcceptStatus())
                .rewriteStatus(suggestion.getRewriteStatus())
                .modelName(suggestion.getModelName())
                .promptVersion(suggestion.getPromptVersion())
                .errorMessage(suggestion.getErrorMessage())
                .createdAt(suggestion.getCreatedAt())
                .updatedAt(suggestion.getUpdatedAt())
                .build();
    }
}
