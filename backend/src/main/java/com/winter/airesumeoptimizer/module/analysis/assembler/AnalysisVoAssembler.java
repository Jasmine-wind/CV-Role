package com.winter.airesumeoptimizer.module.analysis.assembler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchEvidenceDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchItemDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiJobMatchWeakExperienceDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionItemDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;
import com.winter.airesumeoptimizer.module.analysis.vo.AiJobMatchResultVO;
import com.winter.airesumeoptimizer.module.analysis.vo.AiJobMatchTriggerVO;
import com.winter.airesumeoptimizer.module.analysis.vo.AiResumeSuggestionTriggerVO;
import com.winter.airesumeoptimizer.module.analysis.vo.AiResumeSuggestionVO;
import com.winter.airesumeoptimizer.module.analysis.vo.AiRewriteSuggestionVO;
import com.winter.airesumeoptimizer.module.analysis.vo.ResumeAiAnalysisTriggerVO;
import com.winter.airesumeoptimizer.module.analysis.vo.ResumeAiAnalysisVO;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AnalysisVoAssembler {

    private final ObjectMapper objectMapper;

    public AnalysisVoAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ResumeAiAnalysisTriggerVO toResumeAiAnalysisTriggerVO(ResumeAiAnalysis analysis) {
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

    public ResumeAiAnalysisVO toResumeAiAnalysisVO(ResumeAiAnalysis analysis) {
        return ResumeAiAnalysisVO.builder()
                .resumeId(analysis.getResumeId())
                .analysisStatus(analysis.getAnalysisStatus())
                .score(analysis.getScore())
                .strengths(readTextList(analysis.getStrengths(), "简历诊断结果格式不正确"))
                .problems(readTextList(analysis.getProblems(), "简历诊断结果格式不正确"))
                .suggestionsSummary(readTextList(analysis.getSuggestionsSummary(), "简历诊断结果格式不正确"))
                .modelName(analysis.getModelName())
                .promptVersion(analysis.getPromptVersion())
                .errorMessage(analysis.getErrorMessage())
                .createdAt(analysis.getCreatedAt())
                .updatedAt(analysis.getUpdatedAt())
                .build();
    }

    public AiJobMatchTriggerVO toAiJobMatchTriggerVO(AiJobMatchResult matchResult) {
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

    public AiJobMatchResultVO toAiJobMatchResultVO(AiJobMatchResult matchResult) {
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
                .riskNotes(readTextList(matchResult.getRiskNotes(), "匹配分析结果格式不正确"))
                .modelName(matchResult.getModelName())
                .promptVersion(matchResult.getPromptVersion())
                .matchStatus(matchResult.getMatchStatus())
                .errorMessage(matchResult.getErrorMessage())
                .createdAt(matchResult.getCreatedAt())
                .updatedAt(matchResult.getUpdatedAt())
                .build();
    }

    public AiResumeSuggestionTriggerVO toAiResumeSuggestionTriggerVO(AiResumeSuggestion suggestion) {
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

    public AiResumeSuggestionVO toAiResumeSuggestionVO(AiResumeSuggestion suggestion) {
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

    public AiRewriteSuggestionVO toAiRewriteSuggestionVO(AiRewriteSuggestion suggestion) {
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

    private List<String> readTextList(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, errorMessage);
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
            throw new BusinessException(500, "匹配分析结果格式不正确");
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
            throw new BusinessException(500, "匹配分析结果格式不正确");
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
            throw new BusinessException(500, "匹配分析结果格式不正确");
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
