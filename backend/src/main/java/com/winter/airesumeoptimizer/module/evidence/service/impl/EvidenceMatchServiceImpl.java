package com.winter.airesumeoptimizer.module.evidence.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.ai.AiClientService;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceMatchOutcomeDTO;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceQuoteDTO;
import com.winter.airesumeoptimizer.module.evidence.dto.EvidenceRequirementEvaluationDTO;
import com.winter.airesumeoptimizer.module.evidence.entity.EvidenceAnalysis;
import com.winter.airesumeoptimizer.module.evidence.entity.EvidenceRequirement;
import com.winter.airesumeoptimizer.module.evidence.entity.RequirementEvidence;
import com.winter.airesumeoptimizer.module.evidence.enums.EvidenceMatchLevel;
import com.winter.airesumeoptimizer.module.evidence.mapper.EvidenceAnalysisMapper;
import com.winter.airesumeoptimizer.module.evidence.mapper.EvidenceRequirementMapper;
import com.winter.airesumeoptimizer.module.evidence.mapper.RequirementEvidenceMapper;
import com.winter.airesumeoptimizer.module.evidence.service.EvidenceMatchService;
import com.winter.airesumeoptimizer.module.evidence.service.EvidenceMatchingStrategy;
import com.winter.airesumeoptimizer.module.evidence.vo.EvidenceAnalysisResultVO;
import com.winter.airesumeoptimizer.module.evidence.vo.EvidenceRequirementVO;
import com.winter.airesumeoptimizer.module.evidence.vo.RequirementEvidenceVO;
import com.winter.airesumeoptimizer.module.job.vo.JobDescriptionVO;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceMatchServiceImpl implements EvidenceMatchService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceMatchServiceImpl.class);

    private final OptimizationTaskMapper optimizationTaskMapper;
    private final EvidenceAnalysisMapper evidenceAnalysisMapper;
    private final EvidenceRequirementMapper evidenceRequirementMapper;
    private final RequirementEvidenceMapper requirementEvidenceMapper;
    private final EvidenceMatchingStrategy evidenceMatchingStrategy;
    private final AiClientService aiClientService;

    public EvidenceMatchServiceImpl(
            OptimizationTaskMapper optimizationTaskMapper,
            EvidenceAnalysisMapper evidenceAnalysisMapper,
            EvidenceRequirementMapper evidenceRequirementMapper,
            RequirementEvidenceMapper requirementEvidenceMapper,
            EvidenceMatchingStrategy evidenceMatchingStrategy,
            AiClientService aiClientService) {
        this.optimizationTaskMapper = optimizationTaskMapper;
        this.evidenceAnalysisMapper = evidenceAnalysisMapper;
        this.evidenceRequirementMapper = evidenceRequirementMapper;
        this.requirementEvidenceMapper = requirementEvidenceMapper;
        this.evidenceMatchingStrategy = evidenceMatchingStrategy;
        this.aiClientService = aiClientService;
    }

    @Override
    @Transactional
    public EvidenceAnalysis analyze(Long userId, Long optimizationTaskId, JobDescriptionVO parsedJob) {
        OptimizationTask task = getOwnedTask(userId, optimizationTaskId);
        String resumeSnapshot = task.getResumeInputSnapshot();
        if (resumeSnapshot == null || resumeSnapshot.isBlank()) {
            throw new BusinessException(500, "优化任务缺少简历输入快照");
        }
        String jobStructuredContent = parsedJob == null ? null : parsedJob.getStructuredContent();
        if (jobStructuredContent == null || jobStructuredContent.isBlank()) {
            throw new BusinessException(400, "目标岗位结构化解析结果为空");
        }

        log.info("Evidence match started: userId={}, optimizationTaskId={}, model={}",
                userId,
                optimizationTaskId,
                aiClientService.modelName());

        EvidenceMatchOutcomeDTO outcome = evidenceMatchingStrategy.match(jobStructuredContent, resumeSnapshot);
        deleteExistingAnalysis(userId, optimizationTaskId);
        EvidenceAnalysis analysis = saveAnalysis(userId, task, outcome);

        log.info("Evidence match succeeded: userId={}, optimizationTaskId={}, matched={}, expressionGap={}, noEvidence={}",
                userId,
                optimizationTaskId,
                analysis.getMatchedCount(),
                analysis.getExpressionGapCount(),
                analysis.getNoEvidenceCount());
        return analysis;
    }

    @Override
    public EvidenceAnalysisResultVO getResult(Long userId, Long optimizationTaskId) {
        getOwnedTask(userId, optimizationTaskId);
        EvidenceAnalysis analysis = evidenceAnalysisMapper.selectOne(new LambdaQueryWrapper<EvidenceAnalysis>()
                .eq(EvidenceAnalysis::getOptimizationTaskId, optimizationTaskId)
                .eq(EvidenceAnalysis::getUserId, userId));
        if (analysis == null) {
            return null;
        }

        List<EvidenceRequirement> requirements = evidenceRequirementMapper.selectList(
                new LambdaQueryWrapper<EvidenceRequirement>()
                        .eq(EvidenceRequirement::getEvidenceAnalysisId, analysis.getId())
                        .eq(EvidenceRequirement::getUserId, userId)
                        .orderByAsc(EvidenceRequirement::getDisplayOrder)
                        .orderByAsc(EvidenceRequirement::getId));
        Map<Long, List<RequirementEvidence>> evidencesByRequirement = loadEvidences(userId, requirements);
        return toResultVO(analysis, requirements, evidencesByRequirement);
    }

    private EvidenceAnalysis saveAnalysis(Long userId, OptimizationTask task, EvidenceMatchOutcomeDTO outcome) {
        LocalDateTime now = LocalDateTime.now();
        int matched = 0;
        int expressionGap = 0;
        int noEvidence = 0;
        for (EvidenceRequirementEvaluationDTO evaluation : outcome.getRequirements()) {
            if (evaluation.getMatchLevel() == EvidenceMatchLevel.MATCHED) {
                matched++;
            } else if (evaluation.getMatchLevel() == EvidenceMatchLevel.EXPRESSION_GAP) {
                expressionGap++;
            } else {
                noEvidence++;
            }
        }

        EvidenceAnalysis analysis = new EvidenceAnalysis();
        analysis.setUserId(userId);
        analysis.setOptimizationTaskId(task.getId());
        analysis.setMatchedCount(matched);
        analysis.setExpressionGapCount(expressionGap);
        analysis.setNoEvidenceCount(noEvidence);
        analysis.setModelName(aiClientService.modelName());
        analysis.setPromptVersion(evidenceMatchingStrategy.promptVersion());
        analysis.setCreatedAt(now);
        analysis.setUpdatedAt(now);
        if (evidenceAnalysisMapper.insert(analysis) != 1 || analysis.getId() == null) {
            throw new BusinessException(500, "岗位证据分析保存失败");
        }

        int displayOrder = 0;
        for (EvidenceRequirementEvaluationDTO evaluation : outcome.getRequirements()) {
            EvidenceRequirement requirement = new EvidenceRequirement();
            requirement.setUserId(userId);
            requirement.setEvidenceAnalysisId(analysis.getId());
            requirement.setRequirementText(evaluation.getRequirementText());
            requirement.setImportance(evaluation.getImportance().name());
            requirement.setMatchLevel(evaluation.getMatchLevel().name());
            requirement.setConclusion(nullIfBlank(evaluation.getConclusion()));
            requirement.setSuggestion(nullIfBlank(evaluation.getSuggestion()));
            requirement.setDisplayOrder(displayOrder++);
            requirement.setCreatedAt(now);
            if (evidenceRequirementMapper.insert(requirement) != 1 || requirement.getId() == null) {
                throw new BusinessException(500, "岗位证据分析保存失败");
            }

            for (EvidenceQuoteDTO quote : evaluation.getEvidences()) {
                RequirementEvidence evidence = new RequirementEvidence();
                evidence.setUserId(userId);
                evidence.setEvidenceRequirementId(requirement.getId());
                evidence.setSourceResumeVersionId(task.getSourceResumeVersionId());
                evidence.setSectionLabel(nullIfBlank(quote.getSectionLabel()));
                evidence.setEvidenceText(quote.getQuote());
                evidence.setExpressionStatus(quote.getExpressionStatus().name());
                evidence.setCreatedAt(now);
                if (requirementEvidenceMapper.insert(evidence) != 1 || evidence.getId() == null) {
                    throw new BusinessException(500, "岗位证据分析保存失败");
                }
            }
        }
        return analysis;
    }

    private void deleteExistingAnalysis(Long userId, Long optimizationTaskId) {
        EvidenceAnalysis existing = evidenceAnalysisMapper.selectOne(new LambdaQueryWrapper<EvidenceAnalysis>()
                .eq(EvidenceAnalysis::getOptimizationTaskId, optimizationTaskId)
                .eq(EvidenceAnalysis::getUserId, userId));
        if (existing != null) {
            // 要求与证据行通过数据库外键级联删除。
            evidenceAnalysisMapper.deleteById(existing.getId());
        }
    }

    private Map<Long, List<RequirementEvidence>> loadEvidences(
            Long userId,
            List<EvidenceRequirement> requirements) {
        Map<Long, List<RequirementEvidence>> grouped = new LinkedHashMap<>();
        if (requirements.isEmpty()) {
            return grouped;
        }
        List<Long> requirementIds = requirements.stream().map(EvidenceRequirement::getId).toList();
        List<RequirementEvidence> evidences = requirementEvidenceMapper.selectList(
                new LambdaQueryWrapper<RequirementEvidence>()
                        .in(RequirementEvidence::getEvidenceRequirementId, requirementIds)
                        .eq(RequirementEvidence::getUserId, userId)
                        .orderByAsc(RequirementEvidence::getId));
        for (RequirementEvidence evidence : evidences) {
            grouped.computeIfAbsent(evidence.getEvidenceRequirementId(), key -> new ArrayList<>()).add(evidence);
        }
        return grouped;
    }

    private EvidenceAnalysisResultVO toResultVO(
            EvidenceAnalysis analysis,
            List<EvidenceRequirement> requirements,
            Map<Long, List<RequirementEvidence>> evidencesByRequirement) {
        List<EvidenceRequirementVO> requirementVOs = requirements.stream()
                .map(requirement -> EvidenceRequirementVO.builder()
                        .evidenceRequirementId(requirement.getId())
                        .requirementText(requirement.getRequirementText())
                        .importance(requirement.getImportance())
                        .matchLevel(requirement.getMatchLevel())
                        .conclusion(requirement.getConclusion())
                        .suggestion(requirement.getSuggestion())
                        .evidences(evidencesByRequirement
                                .getOrDefault(requirement.getId(), List.of())
                                .stream()
                                .map(evidence -> RequirementEvidenceVO.builder()
                                        .requirementEvidenceId(evidence.getId())
                                        .sectionLabel(evidence.getSectionLabel())
                                        .evidenceText(evidence.getEvidenceText())
                                        .expressionStatus(evidence.getExpressionStatus())
                                        .build())
                                .toList())
                        .build())
                .toList();
        return EvidenceAnalysisResultVO.builder()
                .evidenceAnalysisId(analysis.getId())
                .matchedCount(analysis.getMatchedCount())
                .expressionGapCount(analysis.getExpressionGapCount())
                .noEvidenceCount(analysis.getNoEvidenceCount())
                .requirements(requirementVOs)
                .build();
    }

    private OptimizationTask getOwnedTask(Long userId, Long optimizationTaskId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (optimizationTaskId == null || optimizationTaskId <= 0) {
            throw new BusinessException(400, "优化任务 ID 必须大于 0");
        }
        OptimizationTask task = optimizationTaskMapper.selectOne(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getId, optimizationTaskId)
                .eq(OptimizationTask::getUserId, userId));
        if (task == null) {
            throw new BusinessException(404, "优化任务不存在");
        }
        return task;
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
