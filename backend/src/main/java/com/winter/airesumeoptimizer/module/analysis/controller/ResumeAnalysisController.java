package com.winter.airesumeoptimizer.module.analysis.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchRequestDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchEvidenceDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchItemDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchWeakExperienceDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionItemDTO;
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
@Tag(name = "Analysis", description = "AI 简历分析接口")
@SecurityRequirement(name = "bearerAuth")
public class ResumeAnalysisController {

    private final ResumeAnalysisService resumeAnalysisService;
    private final AiJobMatchService aiJobMatchService;
    private final AiResumeSuggestionService aiResumeSuggestionService;
    private final AiRewriteSuggestionService aiRewriteSuggestionService;
    private final ObjectMapper objectMapper;

    public ResumeAnalysisController(
            ResumeAnalysisService resumeAnalysisService,
            AiJobMatchService aiJobMatchService,
            AiResumeSuggestionService aiResumeSuggestionService,
            AiRewriteSuggestionService aiRewriteSuggestionService,
            ObjectMapper objectMapper) {
        this.resumeAnalysisService = resumeAnalysisService;
        this.aiJobMatchService = aiJobMatchService;
        this.aiResumeSuggestionService = aiResumeSuggestionService;
        this.aiRewriteSuggestionService = aiRewriteSuggestionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{id}/ai-analysis")
    @Operation(summary = "触发 AI 分析", description = "基于简历解析结果调用 AI 生成分析报告")
    public Result<ResumeAiAnalysisTriggerVO> analyze(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        ResumeAiAnalysis analysis = resumeAnalysisService.analyze(authenticatedUser.getUserId(), id);
        String message = "FAILED".equals(analysis.getAnalysisStatus()) ? "AI 分析失败" : "AI 分析完成";
        return Result.success(message, toTriggerVO(analysis));
    }

    @GetMapping("/{id}/ai-analysis")
    @Operation(summary = "查询 AI 分析", description = "查询指定简历的 AI 分析结果")
    public Result<ResumeAiAnalysisVO> analysis(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        ResumeAiAnalysis analysis = resumeAnalysisService.getAnalysis(authenticatedUser.getUserId(), id);
        return Result.success(toAnalysisVO(analysis));
    }

    @PostMapping("/{id}/ai-job-matches")
    @Operation(summary = "触发 AI 岗位匹配", description = "基于已解析简历和已解析岗位描述生成 AI 匹配结果")
    public Result<AiJobMatchTriggerVO> matchJobDescription(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            @Valid @RequestBody AiJobMatchRequestDTO request,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        AiJobMatchResult matchResult = aiJobMatchService.match(
                authenticatedUser.getUserId(),
                id,
                request.getJobDescriptionId());
        String message = "FAILED".equals(matchResult.getMatchStatus()) ? "AI 岗位匹配失败" : "AI 岗位匹配完成";
        return Result.success(message, toAiJobMatchTriggerVO(matchResult));
    }

    @PostMapping("/{id}/ai-suggestions")
    @Operation(summary = "触发 AI 简历优化建议", description = "基于成功的 AI 岗位匹配结果生成简历优化建议")
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
        return Result.success(message, toAiResumeSuggestionTriggerVO(suggestion));
    }

    @GetMapping(value = "/{id}/ai-suggestions", params = "jobDescriptionId")
    @Operation(summary = "查询指定 AI 简历优化建议", description = "按简历和岗位描述查询当前用户的 AI 优化建议")
    public Result<AiResumeSuggestionVO> aiResumeSuggestionDetail(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            @RequestParam @Positive(message = "岗位描述 ID 必须大于 0") Long jobDescriptionId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        AiResumeSuggestion suggestion = aiResumeSuggestionService.getByResumeAndJobDescription(
                authenticatedUser.getUserId(),
                id,
                jobDescriptionId);
        return Result.success(toAiResumeSuggestionVO(suggestion));
    }

    @GetMapping(value = "/{id}/ai-suggestions", params = "aiJobMatchResultId")
    @Operation(summary = "查询指定 AI 简历优化建议", description = "按简历和 AI 匹配结果查询当前用户的 AI 优化建议")
    public Result<AiResumeSuggestionVO> aiResumeSuggestionByMatchResult(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            @RequestParam @Positive(message = "AI 匹配结果 ID 必须大于 0") Long aiJobMatchResultId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        AiResumeSuggestion suggestion = aiResumeSuggestionService.getByResumeAndMatchResult(
                authenticatedUser.getUserId(),
                id,
                aiJobMatchResultId);
        return Result.success(toAiResumeSuggestionVO(suggestion));
    }

    @GetMapping("/{id}/ai-suggestions")
    @Operation(summary = "查询 AI 简历优化建议列表", description = "查询指定简历下当前用户的 AI 优化建议列表")
    public Result<List<AiResumeSuggestionVO>> aiResumeSuggestionList(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(aiResumeSuggestionService.listByResume(authenticatedUser.getUserId(), id)
                .stream()
                .map(this::toAiResumeSuggestionVO)
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
        return Result.success(message, toAiRewriteSuggestionVO(suggestion));
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
                .map(this::toAiRewriteSuggestionVO)
                .toList());
    }

    @GetMapping(value = "/{id}/ai-job-matches", params = "jobDescriptionId")
    @Operation(summary = "查询指定 AI 岗位匹配结果", description = "按简历和岗位描述查询当前用户的 AI 匹配结果")
    public Result<AiJobMatchResultVO> aiJobMatchDetail(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            @RequestParam @Positive(message = "岗位描述 ID 必须大于 0") Long jobDescriptionId,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        AiJobMatchResult matchResult = aiJobMatchService.getByResumeAndJobDescription(
                authenticatedUser.getUserId(),
                id,
                jobDescriptionId);
        return Result.success(toAiJobMatchResultVO(matchResult));
    }

    @GetMapping("/{id}/ai-job-matches")
    @Operation(summary = "查询 AI 岗位匹配结果列表", description = "查询指定简历下当前用户的 AI 岗位匹配结果")
    public Result<List<AiJobMatchResultVO>> aiJobMatchList(
            @PathVariable @Positive(message = "简历 ID 必须大于 0") Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        return Result.success(aiJobMatchService.listByResume(authenticatedUser.getUserId(), id)
                .stream()
                .map(this::toAiJobMatchResultVO)
                .toList());
    }

    private ResumeAiAnalysisTriggerVO toTriggerVO(ResumeAiAnalysis analysis) {
        return ResumeAiAnalysisTriggerVO.builder()
                .resumeId(analysis.getResumeId())
                .analysisStatus(analysis.getAnalysisStatus())
                .score(analysis.getScore())
                .modelName(analysis.getModelName())
                .promptVersion(analysis.getPromptVersion())
                .errorMessage(analysis.getErrorMessage())
                .updatedAt(analysis.getUpdatedAt())
                .build();
    }

    private AiJobMatchTriggerVO toAiJobMatchTriggerVO(AiJobMatchResult matchResult) {
        return AiJobMatchTriggerVO.builder()
                .matchId(matchResult.getId())
                .resumeId(matchResult.getResumeId())
                .jobDescriptionId(matchResult.getJobDescriptionId())
                .overallScore(matchResult.getOverallScore())
                .matchStatus(matchResult.getMatchStatus())
                .modelName(matchResult.getModelName())
                .promptVersion(matchResult.getPromptVersion())
                .errorMessage(matchResult.getErrorMessage())
                .updatedAt(matchResult.getUpdatedAt())
                .build();
    }

    private AiResumeSuggestionTriggerVO toAiResumeSuggestionTriggerVO(AiResumeSuggestion suggestion) {
        return AiResumeSuggestionTriggerVO.builder()
                .suggestionId(suggestion.getId())
                .resumeId(suggestion.getResumeId())
                .jobDescriptionId(suggestion.getJobDescriptionId())
                .aiJobMatchResultId(suggestion.getAiJobMatchResultId())
                .suggestionStatus(suggestion.getSuggestionStatus())
                .suggestionCount(readSuggestionCount(suggestion.getSuggestions()))
                .errorMessage(suggestion.getErrorMessage())
                .updatedAt(suggestion.getUpdatedAt())
                .build();
    }

    private AiResumeSuggestionVO toAiResumeSuggestionVO(AiResumeSuggestion suggestion) {
        return AiResumeSuggestionVO.builder()
                .suggestionId(suggestion.getId())
                .resumeId(suggestion.getResumeId())
                .jobDescriptionId(suggestion.getJobDescriptionId())
                .aiJobMatchResultId(suggestion.getAiJobMatchResultId())
                .suggestionStatus(suggestion.getSuggestionStatus())
                .suggestions(readSuggestionList(suggestion.getSuggestions()))
                .modelName(suggestion.getModelName())
                .promptVersion(suggestion.getPromptVersion())
                .errorMessage(suggestion.getErrorMessage())
                .createdAt(suggestion.getCreatedAt())
                .updatedAt(suggestion.getUpdatedAt())
                .build();
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

    private AiJobMatchResultVO toAiJobMatchResultVO(AiJobMatchResult matchResult) {
        return AiJobMatchResultVO.builder()
                .matchId(matchResult.getId())
                .resumeId(matchResult.getResumeId())
                .jobDescriptionId(matchResult.getJobDescriptionId())
                .overallScore(matchResult.getOverallScore())
                .strongMatches(readMatchItemList(matchResult.getStrongMatches()))
                .weakMatches(readMatchItemList(matchResult.getWeakMatches()))
                .missingSkills(readMatchItemList(matchResult.getMissingSkills()))
                .weakExperienceDescriptions(readWeakExperienceList(matchResult.getWeakExperienceDescriptions()))
                .evidence(readEvidenceList(matchResult.getEvidence()))
                .riskNotes(readAiJobMatchTextList(matchResult.getRiskNotes()))
                .modelName(matchResult.getModelName())
                .promptVersion(matchResult.getPromptVersion())
                .matchStatus(matchResult.getMatchStatus())
                .errorMessage(matchResult.getErrorMessage())
                .createdAt(matchResult.getCreatedAt())
                .updatedAt(matchResult.getUpdatedAt())
                .build();
    }

    private ResumeAiAnalysisVO toAnalysisVO(ResumeAiAnalysis analysis) {
        return ResumeAiAnalysisVO.builder()
                .resumeId(analysis.getResumeId())
                .analysisStatus(analysis.getAnalysisStatus())
                .score(analysis.getScore())
                .strengths(readTextList(analysis.getStrengths()))
                .problems(readTextList(analysis.getProblems()))
                .suggestionsSummary(readTextList(analysis.getSuggestionsSummary()))
                .modelName(analysis.getModelName())
                .promptVersion(analysis.getPromptVersion())
                .errorMessage(analysis.getErrorMessage())
                .createdAt(analysis.getCreatedAt())
                .updatedAt(analysis.getUpdatedAt())
                .build();
    }

    private List<String> readTextList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "AI 分析结果格式不正确");
        }
    }

    private List<String> readAiJobMatchTextList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "AI 岗位匹配结果格式不正确");
        }
    }

    private List<AiJobMatchItemDTO> readMatchItemList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<AiJobMatchItemDTO>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "AI 岗位匹配结果格式不正确");
        }
    }

    private List<AiJobMatchWeakExperienceDTO> readWeakExperienceList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<AiJobMatchWeakExperienceDTO>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "AI 岗位匹配结果格式不正确");
        }
    }

    private List<AiJobMatchEvidenceDTO> readEvidenceList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<AiJobMatchEvidenceDTO>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "AI 岗位匹配结果格式不正确");
        }
    }

    private Integer readSuggestionCount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<AiResumeSuggestionItemDTO>>() {
            }).size();
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "AI 优化建议结果格式不正确");
        }
    }

    private List<AiResumeSuggestionItemDTO> readSuggestionList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<AiResumeSuggestionItemDTO>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "AI 优化建议结果格式不正确");
        }
    }
}
