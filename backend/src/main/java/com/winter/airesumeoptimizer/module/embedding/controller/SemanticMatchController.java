package com.winter.airesumeoptimizer.module.embedding.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.embedding.service.SemanticMatchService;
import com.winter.airesumeoptimizer.module.embedding.vo.SemanticMatchResultVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumes")
@Validated
@Tag(name = "Embedding", description = "语义相似度查询接口")
@SecurityRequirement(name = "bearerAuth")
public class SemanticMatchController {

    private final SemanticMatchService semanticMatchService;

    public SemanticMatchController(SemanticMatchService semanticMatchService) {
        this.semanticMatchService = semanticMatchService;
    }

    @GetMapping("/{resumeId}/semantic-matches")
    @Operation(summary = "查询简历与岗位描述语义相似片段", description = "基于已生成向量返回 Top-K 语义相似片段")
    public Result<SemanticMatchResultVO> match(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long resumeId,
            @RequestParam @Positive(message = "岗位描述 ID 必须大于 0") Long jobDescriptionId,
            @RequestParam(required = false) @Positive(message = "topK 必须大于 0") Integer topK,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(semanticMatchService.match(
                authenticatedUser.getUserId(),
                resumeId,
                jobDescriptionId,
                topK));
    }
}
