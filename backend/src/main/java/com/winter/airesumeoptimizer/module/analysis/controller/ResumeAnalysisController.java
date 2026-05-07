package com.winter.airesumeoptimizer.module.analysis.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.result.Result;
import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;
import com.winter.airesumeoptimizer.module.analysis.service.ResumeAnalysisService;
import com.winter.airesumeoptimizer.module.analysis.vo.ResumeAiAnalysisVO;
import com.winter.airesumeoptimizer.module.analysis.vo.ResumeAiAnalysisTriggerVO;
import com.winter.airesumeoptimizer.security.AuthenticatedUser;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumes")
public class ResumeAnalysisController {

    private final ResumeAnalysisService resumeAnalysisService;
    private final ObjectMapper objectMapper;

    public ResumeAnalysisController(
            ResumeAnalysisService resumeAnalysisService,
            ObjectMapper objectMapper) {
        this.resumeAnalysisService = resumeAnalysisService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/{id}/ai-analysis")
    public Result<ResumeAiAnalysisTriggerVO> analyze(
            @PathVariable Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        ResumeAiAnalysis analysis = resumeAnalysisService.analyze(authenticatedUser.getUserId(), id);
        String message = "FAILED".equals(analysis.getAnalysisStatus()) ? "AI 分析失败" : "AI 分析完成";
        return Result.success(message, toTriggerVO(analysis));
    }

    @GetMapping("/{id}/ai-analysis")
    public Result<ResumeAiAnalysisVO> analysis(
            @PathVariable Long id,
            Authentication authentication) {
        AuthenticatedUser authenticatedUser = (AuthenticatedUser) authentication.getPrincipal();
        ResumeAiAnalysis analysis = resumeAnalysisService.getAnalysis(authenticatedUser.getUserId(), id);
        return Result.success(toAnalysisVO(analysis));
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
}
