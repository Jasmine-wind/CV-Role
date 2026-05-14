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
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;
import com.winter.airesumeoptimizer.module.analysis.service.AiJobMatchService;
import com.winter.airesumeoptimizer.module.analysis.service.ResumeAnalysisService;
import com.winter.airesumeoptimizer.module.analysis.vo.AiJobMatchResultVO;
import com.winter.airesumeoptimizer.module.analysis.vo.AiJobMatchTriggerVO;
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
    private final ObjectMapper objectMapper;

    public ResumeAnalysisController(
            ResumeAnalysisService resumeAnalysisService,
            AiJobMatchService aiJobMatchService,
            ObjectMapper objectMapper) {
        this.resumeAnalysisService = resumeAnalysisService;
        this.aiJobMatchService = aiJobMatchService;
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
}
