package com.winter.airesumeoptimizer.module.analysis.controller;

import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.analysis.assembler.AnalysisVoAssembler;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchRequestDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionRequestDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionRequestDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchService;
import com.winter.airesumeoptimizer.module.analysis.service.AiResumeSuggestionService;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionService;
import com.winter.airesumeoptimizer.module.analysis.service.ResumeAnalysisService;
import com.winter.airesumeoptimizer.module.analysis.vo.AiJobMatchResultVO;
import com.winter.airesumeoptimizer.module.analysis.vo.AiJobMatchTriggerVO;
import com.winter.airesumeoptimizer.module.analysis.vo.AiResumeSuggestionTriggerVO;
import com.winter.airesumeoptimizer.module.analysis.vo.AiResumeSuggestionVO;
import com.winter.airesumeoptimizer.module.analysis.vo.AiRewriteSuggestionVO;
import com.winter.airesumeoptimizer.module.analysis.vo.ResumeAiAnalysisVO;
import com.winter.airesumeoptimizer.module.analysis.vo.ResumeAiAnalysisTriggerVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumes")
@Validated
@Tag(name = "Analysis", description = "简历诊断、匹配分析、岗位优化建议和局部改写接口")
@SecurityRequirement(name = "bearerAuth")
public class ResumeAnalysisController {

    private final ResumeAnalysisService resumeAnalysisService;
    private final AiJobMatchService aiJobMatchService;
    private final AiResumeSuggestionService aiResumeSuggestionService;
    private final AiRewriteSuggestionService aiRewriteSuggestionService;
    private final AnalysisVoAssembler analysisVoAssembler;

    public ResumeAnalysisController(
            ResumeAnalysisService resumeAnalysisService,
            AiJobMatchService aiJobMatchService,
            AiResumeSuggestionService aiResumeSuggestionService,
            AiRewriteSuggestionService aiRewriteSuggestionService,
            AnalysisVoAssembler analysisVoAssembler) {
        this.resumeAnalysisService = resumeAnalysisService;
        this.aiJobMatchService = aiJobMatchService;
        this.aiResumeSuggestionService = aiResumeSuggestionService;
        this.aiRewriteSuggestionService = aiRewriteSuggestionService;
        this.analysisVoAssembler = analysisVoAssembler;
    }

    @PostMapping("/{id}/ai-analysis")
    @Operation(summary = "触发简历诊断", description = "基于简历解析结果调用 AI 诊断简历本身质量，不绑定目标岗位")
    public Result<ResumeAiAnalysisTriggerVO> analyze(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        ResumeAiAnalysis analysis = resumeAnalysisService.analyze(authenticatedUser.getUserId(), id);
        String message = "FAILED".equals(analysis.getAnalysisStatus()) ? "简历诊断失败" : "简历诊断完成";
        return Result.success(message, analysisVoAssembler.toResumeAiAnalysisTriggerVO(analysis));
    }

    @GetMapping("/{id}/ai-analysis")
    @Operation(summary = "查询简历诊断", description = "查询指定简历的诊断结果")
    public Result<ResumeAiAnalysisVO> analysis(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        ResumeAiAnalysis analysis = resumeAnalysisService.getAnalysis(authenticatedUser.getUserId(), id);
        return Result.success(analysisVoAssembler.toResumeAiAnalysisVO(analysis));
    }

    @PostMapping("/{id}/ai-job-matches")
    @Operation(summary = "触发匹配分析", description = "基于已解析简历和已解析目标岗位生成匹配分析结果")
    public Result<AiJobMatchTriggerVO> matchJobDescription(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            @Valid @RequestBody AiJobMatchRequestDTO request,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        AiJobMatchResult matchResult = aiJobMatchService.match(
                authenticatedUser.getUserId(),
                id,
                request.getJobDescriptionId());
        String message = "FAILED".equals(matchResult.getMatchStatus()) ? "匹配分析失败" : "匹配分析完成";
        return Result.success(message, analysisVoAssembler.toAiJobMatchTriggerVO(matchResult));
    }

    @PostMapping("/{id}/ai-suggestions")
    @Operation(summary = "触发岗位优化建议", description = "基于成功的匹配分析结果生成岗位优化建议，不直接改写简历正文")
    public Result<AiResumeSuggestionTriggerVO> suggestResumeOptimization(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            @Valid @RequestBody AiResumeSuggestionRequestDTO request,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        AiResumeSuggestion suggestion = aiResumeSuggestionService.generate(
                authenticatedUser.getUserId(),
                id,
                request.getJobDescriptionId(),
                request.getAiJobMatchResultId());
        String message = "FAILED".equals(suggestion.getSuggestionStatus()) ? "AI 优化建议生成失败" : "AI 优化建议生成完成";
        return Result.success(message, analysisVoAssembler.toAiResumeSuggestionTriggerVO(suggestion));
    }

    @GetMapping(value = "/{id}/ai-suggestions", params = "jobDescriptionId")
    @Operation(summary = "查询指定岗位优化建议", description = "按简历和目标岗位查询当前用户的岗位优化建议")
    public Result<AiResumeSuggestionVO> aiResumeSuggestionDetail(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            @RequestParam @Positive(message = "目标岗位 ID 必须大于 0") Long jobDescriptionId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        AiResumeSuggestion suggestion = aiResumeSuggestionService.getByResumeAndJobDescription(
                authenticatedUser.getUserId(),
                id,
                jobDescriptionId);
        return Result.success(analysisVoAssembler.toAiResumeSuggestionVO(suggestion));
    }

    @GetMapping(value = "/{id}/ai-suggestions", params = "aiJobMatchResultId")
    @Operation(summary = "查询指定岗位优化建议", description = "按简历和匹配分析结果查询当前用户的岗位优化建议")
    public Result<AiResumeSuggestionVO> aiResumeSuggestionByMatchResult(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            @RequestParam @Positive(message = "AI 匹配结果 ID 必须大于 0") Long aiJobMatchResultId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        AiResumeSuggestion suggestion = aiResumeSuggestionService.getByResumeAndMatchResult(
                authenticatedUser.getUserId(),
                id,
                aiJobMatchResultId);
        return Result.success(analysisVoAssembler.toAiResumeSuggestionVO(suggestion));
    }

    @GetMapping("/{id}/ai-suggestions")
    @Operation(summary = "查询岗位优化建议列表", description = "查询指定简历下当前用户的岗位优化建议列表")
    public Result<List<AiResumeSuggestionVO>> aiResumeSuggestionList(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(aiResumeSuggestionService.listByResume(authenticatedUser.getUserId(), id)
                .stream()
                .map(analysisVoAssembler::toAiResumeSuggestionVO)
                .toList());
    }

    @PostMapping("/{id}/rewrite-suggestions")
    @Operation(summary = "生成 AI 局部改写建议", description = "基于用户选择的简历片段生成局部改写建议")
    public Result<AiRewriteSuggestionVO> rewriteSuggestion(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            @Valid @RequestBody AiRewriteSuggestionRequestDTO request,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        AiRewriteSuggestion suggestion = aiRewriteSuggestionService.generate(
                authenticatedUser.getUserId(),
                id,
                request.getRewriteType(),
                request.getTargetSection(),
                request.getOriginalText(),
                request.getJobDescriptionId(),
                request.getAiJobMatchResultId(),
                request.getAiResumeSuggestionId());
        String message = "FAILED".equals(suggestion.getRewriteStatus()) ? "AI 局部改写生成失败" : "AI 局部改写生成完成";
        return Result.success(message, analysisVoAssembler.toAiRewriteSuggestionVO(suggestion));
    }

    @GetMapping("/{id}/rewrite-suggestions")
    @Operation(summary = "查询 AI 局部改写建议列表", description = "查询指定简历下当前用户的局部改写建议")
    public Result<List<AiRewriteSuggestionVO>> rewriteSuggestionList(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            @RequestParam(required = false) String rewriteType,
            @RequestParam(required = false) String acceptStatus,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(aiRewriteSuggestionService.listByResume(
                        authenticatedUser.getUserId(),
                        id,
                        rewriteType,
                        acceptStatus)
                .stream()
                .map(analysisVoAssembler::toAiRewriteSuggestionVO)
                .toList());
    }

    @GetMapping(value = "/{id}/ai-job-matches", params = "jobDescriptionId")
    @Operation(summary = "查询指定匹配分析结果", description = "按简历和目标岗位查询当前用户的匹配分析结果")
    public Result<AiJobMatchResultVO> aiJobMatchDetail(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            @RequestParam @Positive(message = "目标岗位 ID 必须大于 0") Long jobDescriptionId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        AiJobMatchResult matchResult = aiJobMatchService.getByResumeAndJobDescription(
                authenticatedUser.getUserId(),
                id,
                jobDescriptionId);
        return Result.success(analysisVoAssembler.toAiJobMatchResultVO(matchResult));
    }

    @GetMapping("/{id}/ai-job-matches")
    @Operation(summary = "查询匹配分析结果列表", description = "查询指定简历下当前用户的匹配分析结果")
    public Result<List<AiJobMatchResultVO>> aiJobMatchList(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(aiJobMatchService.listByResume(authenticatedUser.getUserId(), id)
                .stream()
                .map(analysisVoAssembler::toAiJobMatchResultVO)
                .toList());
    }
}
