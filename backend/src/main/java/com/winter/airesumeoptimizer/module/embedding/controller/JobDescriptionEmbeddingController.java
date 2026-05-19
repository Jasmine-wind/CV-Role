package com.winter.airesumeoptimizer.module.embedding.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.embedding.service.JobDescriptionEmbeddingService;
import com.winter.airesumeoptimizer.module.embedding.vo.JobDescriptionEmbeddingSummaryVO;
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
@RequestMapping("/api/job-descriptions")
@Validated
@Tag(name = "Embedding", description = "目标岗位向量生成接口")
@SecurityRequirement(name = "bearerAuth")
public class JobDescriptionEmbeddingController {

    private final JobDescriptionEmbeddingService jobDescriptionEmbeddingService;

    public JobDescriptionEmbeddingController(JobDescriptionEmbeddingService jobDescriptionEmbeddingService) {
        this.jobDescriptionEmbeddingService = jobDescriptionEmbeddingService;
    }

    @PostMapping("/{jobDescriptionId}/embeddings")
    @Operation(summary = "生成目标岗位向量", description = "基于已解析目标岗位生成文本片段并调用 Embedding 服务生成向量")
    public Result<JobDescriptionEmbeddingSummaryVO> generate(
            @PathVariable @Positive(message = "目标岗位 ID 必须大于 0") Long jobDescriptionId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success("目标岗位向量生成完成",
                jobDescriptionEmbeddingService.generate(authenticatedUser.getUserId(), jobDescriptionId));
    }

    @GetMapping("/{jobDescriptionId}/embeddings")
    @Operation(summary = "查询目标岗位向量状态", description = "查询指定目标岗位的向量生成状态和文本片段")
    public Result<JobDescriptionEmbeddingSummaryVO> detail(
            @PathVariable @Positive(message = "目标岗位 ID 必须大于 0") Long jobDescriptionId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(jobDescriptionEmbeddingService.getSummary(authenticatedUser.getUserId(), jobDescriptionId));
    }
}
