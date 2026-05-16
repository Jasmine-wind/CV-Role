package com.winter.airesumeoptimizer.module.embedding.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.embedding.service.ResumeEmbeddingService;
import com.winter.airesumeoptimizer.module.embedding.vo.ResumeEmbeddingSummaryVO;
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
@RequestMapping("/api/resumes")
@Validated
@Tag(name = "Embedding", description = "简历向量生成接口")
@SecurityRequirement(name = "bearerAuth")
public class ResumeEmbeddingController {

    private final ResumeEmbeddingService resumeEmbeddingService;

    public ResumeEmbeddingController(ResumeEmbeddingService resumeEmbeddingService) {
        this.resumeEmbeddingService = resumeEmbeddingService;
    }

    @PostMapping("/{resumeId}/embeddings")
    @Operation(summary = "生成简历向量", description = "基于已解析简历生成文本片段并调用 Embedding 服务生成向量")
    public Result<ResumeEmbeddingSummaryVO> generate(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long resumeId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success("简历向量生成完成",
                resumeEmbeddingService.generate(authenticatedUser.getUserId(), resumeId));
    }

    @GetMapping("/{resumeId}/embeddings")
    @Operation(summary = "查询简历向量状态", description = "查询指定简历的向量生成状态和文本片段")
    public Result<ResumeEmbeddingSummaryVO> detail(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long resumeId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(resumeEmbeddingService.getSummary(authenticatedUser.getUserId(), resumeId));
    }
}
