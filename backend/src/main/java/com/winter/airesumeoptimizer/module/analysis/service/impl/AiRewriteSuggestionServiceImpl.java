package com.winter.airesumeoptimizer.module.analysis.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.infra.ai.AiGatewayRequest;
import com.winter.airesumeoptimizer.infra.ai.AiCompletionResult;
import com.winter.airesumeoptimizer.infra.ai.AiGateway;
import com.winter.airesumeoptimizer.infra.ai.AiGatewayException;
import com.winter.airesumeoptimizer.infra.ai.AiGatewaySupport;
import com.winter.airesumeoptimizer.infra.ai.AiInvocationContext;
import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionPromptDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiRewriteSuggestionResultDTO;
import com.winter.airesumeoptimizer.module.analysis.dto.AiResumeSuggestionItemDTO;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiResumeSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiRewriteSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionOutputParser;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionPromptService;
import com.winter.airesumeoptimizer.module.analysis.service.AiRewriteSuggestionService;
import com.winter.airesumeoptimizer.module.analysis.vo.RecommendedRewriteSectionVO;
import com.winter.airesumeoptimizer.module.analysis.vo.RewriteContextVO;
import com.winter.airesumeoptimizer.module.analysis.vo.RewriteSourceRefVO;
import com.winter.airesumeoptimizer.module.job.entity.JobDescription;
import com.winter.airesumeoptimizer.module.job.mapper.JobDescriptionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiRewriteSuggestionServiceImpl implements AiRewriteSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(AiRewriteSuggestionServiceImpl.class);

    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    private static final String MATCH_STATUS_SUCCESS = "SUCCESS";
    private static final String SUGGESTION_STATUS_SUCCESS = "SUCCESS";
    private static final String REWRITE_STATUS_SUCCESS = "SUCCESS";
    private static final String REWRITE_STATUS_FAILED = "FAILED";
    private static final String ACCEPT_STATUS_PENDING = "PENDING";
    private static final int MAX_ORIGINAL_TEXT_LENGTH = 3000;
    private static final int MAX_TARGET_SECTION_LENGTH = 100;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;
    private static final int MAX_CONTEXT_SECTION_LENGTH = 1200;
    private static final int MAX_CONTEXT_SECTIONS = 5;
    private static final Set<String> ALLOWED_REWRITE_TYPES = Set.of(
            "PROJECT",
            "SKILL",
            "INTERNSHIP",
            "SUMMARY",
            "EDUCATION",
            "OTHER");
    private static final Set<String> ALLOWED_ACCEPT_STATUSES = Set.of("PENDING", "ACCEPTED", "REJECTED");

    private final ResumeMapper resumeMapper;
    private final JobDescriptionMapper jobDescriptionMapper;
    private final AiJobMatchResultMapper aiJobMatchResultMapper;
    private final AiResumeSuggestionMapper aiResumeSuggestionMapper;
    private final AiRewriteSuggestionMapper aiRewriteSuggestionMapper;
    private final ResumeParseResultMapper resumeParseResultMapper;
    private final AiRewriteSuggestionPromptService aiRewriteSuggestionPromptService;
    private final AiRewriteSuggestionOutputParser aiRewriteSuggestionOutputParser;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;

    public AiRewriteSuggestionServiceImpl(
            ResumeMapper resumeMapper,
            JobDescriptionMapper jobDescriptionMapper,
            AiJobMatchResultMapper aiJobMatchResultMapper,
            AiResumeSuggestionMapper aiResumeSuggestionMapper,
            AiRewriteSuggestionMapper aiRewriteSuggestionMapper,
            ResumeParseResultMapper resumeParseResultMapper,
            AiRewriteSuggestionPromptService aiRewriteSuggestionPromptService,
            AiRewriteSuggestionOutputParser aiRewriteSuggestionOutputParser,
            AiGateway aiGateway,
            ObjectMapper objectMapper) {
        this.resumeMapper = resumeMapper;
        this.jobDescriptionMapper = jobDescriptionMapper;
        this.aiJobMatchResultMapper = aiJobMatchResultMapper;
        this.aiResumeSuggestionMapper = aiResumeSuggestionMapper;
        this.aiRewriteSuggestionMapper = aiRewriteSuggestionMapper;
        this.resumeParseResultMapper = resumeParseResultMapper;
        this.aiRewriteSuggestionPromptService = aiRewriteSuggestionPromptService;
        this.aiRewriteSuggestionOutputParser = aiRewriteSuggestionOutputParser;
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public AiRewriteSuggestion generate(
            Long userId,
            Long resumeId,
            String rewriteType,
            String targetSection,
            String originalText,
            Long jobDescriptionId,
            Long aiJobMatchResultId,
            Long aiResumeSuggestionId,
            String rewriteGoal,
            List<String> jobKeywords,
            String tone,
            Integer lengthLimit) {
        AiSelectionSnapshot selection = AiGatewaySupport.selectionForNewTask(
                aiGateway,
                userId,
                "LEGACY_REWRITE_SUGGESTION");
        Resume resume = getOwnedResume(userId, resumeId);
        String normalizedRewriteType = normalizeRewriteType(rewriteType);
        String normalizedTargetSection = normalizeTargetSection(targetSection);
        String normalizedOriginalText = normalizeOriginalText(originalText);
        JobDescription jobDescription = getOptionalOwnedSuccessfulJobDescription(userId, jobDescriptionId);
        AiJobMatchResult matchResult = getOptionalSuccessfulMatchResult(resume.getId(), jobDescriptionId, aiJobMatchResultId);
        AiResumeSuggestion resumeSuggestion = getOptionalSuccessfulResumeSuggestion(
                resume.getId(),
                aiJobMatchResultId,
                aiResumeSuggestionId);

        AiRewriteSuggestion rewriteSuggestion = createPendingRewriteSuggestion(
                resume.getId(),
                jobDescription == null ? null : jobDescription.getId(),
                matchResult == null ? null : matchResult.getId(),
                resumeSuggestion == null ? null : resumeSuggestion.getId(),
                normalizedRewriteType,
                normalizedTargetSection,
                normalizedOriginalText);

        AiRewriteSuggestionPromptDTO prompt = aiRewriteSuggestionPromptService.buildPrompt(
                normalizedOriginalText,
                normalizedRewriteType,
                normalizedTargetSection,
                jobDescription == null ? null : jobDescription.getStructuredContent(),
                matchResult == null ? null : buildMatchResultPromptInput(matchResult),
                resumeSuggestion == null ? null : resumeSuggestion.getSuggestions(),
                normalizeOptionalText(rewriteGoal, 200),
                normalizeJobKeywords(jobKeywords),
                normalizeOptionalText(tone, 40),
                lengthLimit);

        log.info("AI rewrite suggestion started: userId={}, resumeId={}, jobDescriptionId={}, aiJobMatchResultId={}, aiResumeSuggestionId={}, model={}",
                userId,
                resume.getId(),
                rewriteSuggestion.getJobDescriptionId(),
                rewriteSuggestion.getAiJobMatchResultId(),
                rewriteSuggestion.getAiResumeSuggestionId(),
                selection.model());

        try {
            AiCompletionResult completion = AiGatewaySupport.complete(
                    aiGateway,
                    new AiInvocationContext(userId, null, "LEGACY_REWRITE_SUGGESTION", selection),
                    new AiGatewayRequest(
                            "LEGACY_REWRITE_SUGGESTION",
                            prompt.getSystemPrompt() == null || prompt.getSystemPrompt().isBlank()
                                    ? "只遵循服务端改写输出契约，不得新增事实。"
                                    : prompt.getSystemPrompt(),
                            prompt.getUserPrompt() == null ? prompt.getPrompt() : prompt.getUserPrompt()));
            AiRewriteSuggestionResultDTO result = aiRewriteSuggestionOutputParser.parse(completion.text());
            applySuccess(rewriteSuggestion, prompt.getPromptVersion(), result, completion.model());
        } catch (RuntimeException exception) {
            if (selection.isUserByok() && exception instanceof AiGatewayException) {
                throw exception;
            }
            String errorMessage = normalizeErrorMessage(exception);
            applyFailed(rewriteSuggestion, prompt.getPromptVersion(), errorMessage, selection.model());
            log.warn("AI rewrite suggestion failed: userId={}, resumeId={}, jobDescriptionId={}, aiJobMatchResultId={}, aiResumeSuggestionId={}, model={}, reason={}",
                    userId,
                    resume.getId(),
                    rewriteSuggestion.getJobDescriptionId(),
                    rewriteSuggestion.getAiJobMatchResultId(),
                    rewriteSuggestion.getAiResumeSuggestionId(),
                    selection.model(),
                    LogSanitizer.sanitize(errorMessage));
        }
        save(rewriteSuggestion);
        if (REWRITE_STATUS_SUCCESS.equals(rewriteSuggestion.getRewriteStatus())) {
            log.info("AI rewrite suggestion succeeded: userId={}, resumeId={}, rewriteId={}, model={}",
                    userId,
                    resume.getId(),
                    rewriteSuggestion.getId(),
                    rewriteSuggestion.getModelName());
        }
        return rewriteSuggestion;
    }

    public AiRewriteSuggestion generate(
            Long userId,
            Long resumeId,
            String rewriteType,
            String targetSection,
            String originalText,
            Long jobDescriptionId,
            Long aiJobMatchResultId,
            Long aiResumeSuggestionId) {
        return generate(
                userId,
                resumeId,
                rewriteType,
                targetSection,
                originalText,
                jobDescriptionId,
                aiJobMatchResultId,
                aiResumeSuggestionId,
                null,
                List.of(),
                null,
                null);
    }

    @Override
    public List<AiRewriteSuggestion> listByResume(Long userId, Long resumeId, String rewriteType, String acceptStatus) {
        Resume resume = getOwnedResume(userId, resumeId);
        LambdaQueryWrapper<AiRewriteSuggestion> query = new LambdaQueryWrapper<AiRewriteSuggestion>()
                .eq(AiRewriteSuggestion::getResumeId, resume.getId());
        if (rewriteType != null && !rewriteType.isBlank()) {
            query.eq(AiRewriteSuggestion::getRewriteType, normalizeRewriteType(rewriteType));
        }
        if (acceptStatus != null && !acceptStatus.isBlank()) {
            query.eq(AiRewriteSuggestion::getAcceptStatus, normalizeAcceptStatus(acceptStatus));
        }
        return aiRewriteSuggestionMapper.selectList(query
                .orderByDesc(AiRewriteSuggestion::getUpdatedAt)
                .orderByDesc(AiRewriteSuggestion::getCreatedAt));
    }

    @Override
    @Transactional
    public AiRewriteSuggestion updateAcceptStatus(Long userId, Long rewriteId, String acceptStatus) {
        if (rewriteId == null) {
            throw new BusinessException(400, "局部改写建议 ID 不能为空");
        }
        AiRewriteSuggestion suggestion = aiRewriteSuggestionMapper.selectById(rewriteId);
        if (suggestion == null) {
            throw new BusinessException(404, "AI 局部改写建议不存在");
        }
        getOwnedResume(userId, suggestion.getResumeId());
        suggestion.setAcceptStatus(normalizeAcceptDecisionStatus(acceptStatus));
        suggestion.setUpdatedAt(LocalDateTime.now());
        aiRewriteSuggestionMapper.updateById(suggestion);
        return suggestion;
    }

    @Override
    public RewriteContextVO getRewriteContext(Long userId, Long aiResumeSuggestionId, Integer suggestionIndex) {
        if (aiResumeSuggestionId == null) {
            throw new BusinessException(400, "AI 优化建议 ID 不能为空");
        }
        AiResumeSuggestion suggestion = aiResumeSuggestionMapper.selectById(aiResumeSuggestionId);
        if (suggestion == null) {
            throw new BusinessException(404, "AI 优化建议结果不存在");
        }
        if (!SUGGESTION_STATUS_SUCCESS.equals(suggestion.getSuggestionStatus())) {
            throw new BusinessException(400, "AI 优化建议未成功，不能准备局部改写上下文");
        }

        Resume resume = getOwnedResume(userId, suggestion.getResumeId());
        JobDescription jobDescription = getOptionalOwnedSuccessfulJobDescription(userId, suggestion.getJobDescriptionId());
        AiJobMatchResult matchResult = getOptionalSuccessfulMatchResult(
                resume.getId(),
                suggestion.getJobDescriptionId(),
                suggestion.getAiJobMatchResultId());
        List<AiResumeSuggestionItemDTO> suggestionItems = readSuggestionItems(suggestion.getSuggestions());
        int selectedIndex = normalizeSuggestionIndex(suggestionItems, suggestionIndex);
        AiResumeSuggestionItemDTO selectedItem = suggestionItems.isEmpty() ? null : suggestionItems.get(selectedIndex);
        List<String> jobKeywords = extractJobKeywords(jobDescription);
        List<RecommendedRewriteSectionVO> recommendedSections = buildRecommendedSections(resume.getId(), selectedItem, jobKeywords);
        List<String> rewriteGoals = buildRewriteGoals(selectedItem, jobKeywords);

        return RewriteContextVO.builder()
                .suggestionId(suggestion.getId())
                .suggestionIndex(suggestionItems.isEmpty() ? null : selectedIndex)
                .resumeId(suggestion.getResumeId())
                .jobDescriptionId(suggestion.getJobDescriptionId())
                .matchId(matchResult == null ? suggestion.getAiJobMatchResultId() : matchResult.getId())
                .suggestionTitle(buildSuggestionTitle(selectedItem))
                .suggestionText(selectedItem == null ? null : selectedItem.getSuggestion())
                .suggestionReason(buildSuggestionReason(selectedItem))
                .recommendedSections(recommendedSections)
                .jobKeywords(jobKeywords)
                .rewriteGoals(rewriteGoals)
                .defaultRewriteGoal(rewriteGoals.isEmpty() ? "提升表达清晰度并保持事实真实" : rewriteGoals.get(0))
                .tones(List.of("简洁专业", "偏技术", "适合实习简历", "适合社招简历"))
                .build();
    }

    private Resume getOwnedResume(Long userId, Long resumeId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (resumeId == null) {
            throw new BusinessException(400, "简历 ID 不能为空");
        }

        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId));
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        return resume;
    }

    private JobDescription getOptionalOwnedSuccessfulJobDescription(Long userId, Long jobDescriptionId) {
        if (jobDescriptionId == null) {
            return null;
        }
        JobDescription jobDescription = jobDescriptionMapper.selectOne(new LambdaQueryWrapper<JobDescription>()
                .eq(JobDescription::getId, jobDescriptionId)
                .eq(JobDescription::getUserId, userId));
        if (jobDescription == null) {
            throw new BusinessException(404, "目标岗位不存在");
        }
        if (!PARSE_STATUS_SUCCESS.equals(jobDescription.getParseStatus())) {
            throw new BusinessException(400, "目标岗位解析未成功，不能用于局部改写");
        }
        if (jobDescription.getStructuredContent() == null || jobDescription.getStructuredContent().isBlank()) {
            throw new BusinessException(400, "目标岗位结构化解析结果为空，不能用于局部改写");
        }
        return jobDescription;
    }

    private AiJobMatchResult getOptionalSuccessfulMatchResult(
            Long resumeId,
            Long jobDescriptionId,
            Long aiJobMatchResultId) {
        if (aiJobMatchResultId == null) {
            return null;
        }
        LambdaQueryWrapper<AiJobMatchResult> query = new LambdaQueryWrapper<AiJobMatchResult>()
                .eq(AiJobMatchResult::getId, aiJobMatchResultId)
                .eq(AiJobMatchResult::getResumeId, resumeId);
        if (jobDescriptionId != null) {
            query.eq(AiJobMatchResult::getJobDescriptionId, jobDescriptionId);
        }
        AiJobMatchResult matchResult = aiJobMatchResultMapper.selectOne(query);
        if (matchResult == null) {
            throw new BusinessException(404, "匹配分析结果不存在");
        }
        if (!MATCH_STATUS_SUCCESS.equals(matchResult.getMatchStatus())) {
            throw new BusinessException(400, "匹配分析未成功，不能用于局部改写");
        }
        return matchResult;
    }

    private AiResumeSuggestion getOptionalSuccessfulResumeSuggestion(
            Long resumeId,
            Long aiJobMatchResultId,
            Long aiResumeSuggestionId) {
        if (aiResumeSuggestionId == null) {
            return null;
        }
        LambdaQueryWrapper<AiResumeSuggestion> query = new LambdaQueryWrapper<AiResumeSuggestion>()
                .eq(AiResumeSuggestion::getId, aiResumeSuggestionId)
                .eq(AiResumeSuggestion::getResumeId, resumeId);
        if (aiJobMatchResultId != null) {
            query.eq(AiResumeSuggestion::getAiJobMatchResultId, aiJobMatchResultId);
        }
        AiResumeSuggestion suggestion = aiResumeSuggestionMapper.selectOne(query);
        if (suggestion == null) {
            throw new BusinessException(404, "AI 优化建议结果不存在");
        }
        if (!SUGGESTION_STATUS_SUCCESS.equals(suggestion.getSuggestionStatus())) {
            throw new BusinessException(400, "AI 优化建议未成功，不能用于局部改写");
        }
        return suggestion;
    }

    private List<AiResumeSuggestionItemDTO> readSuggestionItems(String value) {
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

    private int normalizeSuggestionIndex(List<AiResumeSuggestionItemDTO> suggestions, Integer suggestionIndex) {
        if (suggestions.isEmpty()) {
            return 0;
        }
        if (suggestionIndex != null) {
            if (suggestionIndex < 0 || suggestionIndex >= suggestions.size()) {
                throw new BusinessException(400, "AI 优化建议条目索引不合法");
            }
            return suggestionIndex;
        }
        for (int index = 0; index < suggestions.size(); index++) {
            if ("HIGH".equalsIgnoreCase(suggestions.get(index).getPriority())) {
                return index;
            }
        }
        return 0;
    }

    private List<String> extractJobKeywords(JobDescription jobDescription) {
        if (jobDescription == null || jobDescription.getStructuredContent() == null
                || jobDescription.getStructuredContent().isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        try {
            JsonNode root = objectMapper.readTree(jobDescription.getStructuredContent());
            addTextArray(keywords, root.path("keywords"));
            addTextArray(keywords, root.path("requiredSkills"));
            addTextArray(keywords, root.path("bonusSkills"));
            addTextArray(keywords, root.path("experienceSignals"));
        } catch (JsonProcessingException exception) {
            return List.of();
        }
        return keywords.stream()
                .filter(keyword -> keyword.length() <= 40)
                .limit(20)
                .toList();
    }

    private List<String> buildRewriteGoals(AiResumeSuggestionItemDTO selectedItem, List<String> jobKeywords) {
        LinkedHashSet<String> goals = new LinkedHashSet<>();
        String keywordHint = jobKeywords.isEmpty() ? "" : "（如 " + String.join("、", jobKeywords.stream().limit(3).toList()) + "）";
        String type = selectedItem == null ? "" : safeText(selectedItem.getType()).toUpperCase(Locale.ROOT);

        if ("SKILL_GAP".equals(type)) {
            goals.add("补充与岗位关键词相关的真实技能证据" + keywordHint);
        } else if ("PROJECT_DESCRIPTION".equals(type)) {
            goals.add("突出项目中的岗位相关技术动作" + keywordHint);
        } else if ("EXPERIENCE_WEAKNESS".equals(type)) {
            goals.add("增强经历描述中的职责、动作和结果表达");
        }

        goals.add("突出岗位关键词");
        goals.add("补充技术细节");
        goals.add("增强项目成果表达");
        goals.add("压缩表达并提升专业度");
        return goals.stream().limit(5).toList();
    }

    private List<RecommendedRewriteSectionVO> buildRecommendedSections(
            Long resumeId,
            AiResumeSuggestionItemDTO selectedItem,
            List<String> jobKeywords) {
        ResumeParseResult parseResult = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resumeId)
                .eq(ResumeParseResult::getParseStatus, PARSE_STATUS_SUCCESS)
                .orderByDesc(ResumeParseResult::getUpdatedAt)
                .orderByDesc(ResumeParseResult::getCreatedAt)
                .last("LIMIT 1"));
        if (parseResult == null) {
            return List.of();
        }

        List<RewriteCandidate> candidates = new ArrayList<>();
        collectStructuredCandidates(candidates, parseResult.getStructuredJson(), selectedItem, jobKeywords);
        if (candidates.isEmpty()) {
            collectTextCandidates(candidates, parseResult.getCleanedText(), selectedItem, jobKeywords);
        }

        return candidates.stream()
                .sorted(Comparator.comparing(RewriteCandidate::confidence).reversed())
                .limit(MAX_CONTEXT_SECTIONS)
                .map(RewriteCandidate::toVO)
                .toList();
    }

    private void collectStructuredCandidates(
            List<RewriteCandidate> candidates,
            String structuredJson,
            AiResumeSuggestionItemDTO selectedItem,
            List<String> jobKeywords) {
        if (structuredJson == null || structuredJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(structuredJson);
            JsonNode structuredData = root.path("structuredData");
            if (structuredData.isObject()) {
                collectProjectCandidates(candidates, structuredData.path("projects"), selectedItem, jobKeywords);
                collectExperienceCandidates(candidates, structuredData.path("experiences"), selectedItem, jobKeywords);
                collectSkillCandidates(candidates, structuredData.path("skills"), selectedItem, jobKeywords);
                collectSummaryCandidate(candidates, structuredData.path("summary"), structuredData.path("summarySourceRef"), selectedItem, jobKeywords);
                collectTextArrayCandidates(candidates, "EDUCATION", "教育经历", structuredData.path("education"), selectedItem, jobKeywords);
                collectTextArrayCandidates(candidates, "OTHERS", "其他经历", structuredData.path("others"), selectedItem, jobKeywords);
            }

            JsonNode displayModel = root.path("displayModel");
            if (displayModel.isObject()) {
                collectDisplayProjectCandidates(candidates, displayModel.path("projectCards"), selectedItem, jobKeywords);
                collectDisplayExperienceCandidates(candidates, displayModel.path("workExperienceCards"), "WORK_EXPERIENCES", selectedItem, jobKeywords);
                collectDisplayExperienceCandidates(candidates, displayModel.path("internshipCards"), "INTERNSHIP", selectedItem, jobKeywords);
                collectDisplayExperienceCandidates(candidates, displayModel.path("campusExperienceCards"), "CAMPUS_EXPERIENCES", selectedItem, jobKeywords);
                collectSummaryCandidate(candidates, displayModel.path("summaryCard").path("content"),
                        displayModel.path("summaryCard").path("sourceRef"), selectedItem, jobKeywords);
            }
        } catch (JsonProcessingException exception) {
            log.warn("Failed to parse resume structuredJson for rewrite context: reason={}",
                    LogSanitizer.sanitize(exception.getMessage()));
        }
    }

    private void collectProjectCandidates(
            List<RewriteCandidate> candidates,
            JsonNode projects,
            AiResumeSuggestionItemDTO selectedItem,
            List<String> jobKeywords) {
        if (!projects.isArray()) {
            return;
        }
        for (JsonNode project : projects) {
            String title = firstNonBlank(project.path("name").asText(null), project.path("title").asText(null), "项目经历");
            String sourceText = joinText(
                    title,
                    project.path("description").asText(null),
                    project.path("role").asText(null),
                    project.path("environment").asText(null),
                    arrayText(project.path("techStack")),
                    arrayText(project.path("responsibilities")),
                    arrayText(project.path("evidence")));
            addCandidate(candidates, "PROJECTS", title, sourceText, project.path("sourceRef"), selectedItem, jobKeywords);
        }
    }

    private void collectExperienceCandidates(
            List<RewriteCandidate> candidates,
            JsonNode experiences,
            AiResumeSuggestionItemDTO selectedItem,
            List<String> jobKeywords) {
        if (!experiences.isArray()) {
            return;
        }
        for (JsonNode experience : experiences) {
            String type = safeText(experience.path("type").asText(null)).toUpperCase(Locale.ROOT);
            String sectionType = switch (type) {
                case "INTERNSHIP" -> "INTERNSHIP";
                case "CAMPUS", "PRACTICE", "VOLUNTEER" -> "CAMPUS_EXPERIENCES";
                default -> "WORK_EXPERIENCES";
            };
            String title = firstNonBlank(
                    joinText(experience.path("organization").asText(null), experience.path("role").asText(null)),
                    experience.path("sourceTitle").asText(null),
                    "经历描述");
            String sourceText = joinText(
                    title,
                    joinText(experience.path("startDate").asText(null), experience.path("endDate").asText(null)),
                    experience.path("description").asText(null),
                    arrayText(experience.path("bullets")),
                    arrayText(experience.path("evidence")));
            addCandidate(candidates, sectionType, title, sourceText, experience.path("sourceRef"), selectedItem, jobKeywords);
        }
    }

    private void collectSkillCandidates(
            List<RewriteCandidate> candidates,
            JsonNode skills,
            AiResumeSuggestionItemDTO selectedItem,
            List<String> jobKeywords) {
        if (!skills.isObject()) {
            return;
        }
        String sourceText = joinText(arrayText(skills.path("keywords")), flattenGroups(skills.path("groups")));
        addCandidate(candidates, "SKILLS", "技能标签", sourceText, null, selectedItem, jobKeywords);
    }

    private void collectDisplayProjectCandidates(
            List<RewriteCandidate> candidates,
            JsonNode projectCards,
            AiResumeSuggestionItemDTO selectedItem,
            List<String> jobKeywords) {
        if (!projectCards.isArray()) {
            return;
        }
        for (JsonNode project : projectCards) {
            String title = firstNonBlank(project.path("name").asText(null), "项目经历");
            String sourceText = joinText(
                    title,
                    project.path("summary").asText(null),
                    arrayText(project.path("techStack")),
                    arrayText(project.path("responsibilities")));
            addCandidate(candidates, "PROJECTS", title, sourceText, project.path("sourceRef"), selectedItem, jobKeywords);
        }
    }

    private void collectDisplayExperienceCandidates(
            List<RewriteCandidate> candidates,
            JsonNode cards,
            String sectionType,
            AiResumeSuggestionItemDTO selectedItem,
            List<String> jobKeywords) {
        if (!cards.isArray()) {
            return;
        }
        for (JsonNode card : cards) {
            String title = firstNonBlank(joinText(card.path("company").asText(null), card.path("position").asText(null)), "经历描述");
            String sourceText = joinText(title, card.path("timeRange").asText(null), card.path("summary").asText(null),
                    arrayText(card.path("responsibilities")));
            addCandidate(candidates, sectionType, title, sourceText, card.path("sourceRef"), selectedItem, jobKeywords);
        }
    }

    private void collectSummaryCandidate(
            List<RewriteCandidate> candidates,
            JsonNode summary,
            JsonNode sourceRef,
            AiResumeSuggestionItemDTO selectedItem,
            List<String> jobKeywords) {
        if (summary == null || summary.isMissingNode() || summary.asText("").isBlank()) {
            return;
        }
        addCandidate(candidates, "SUMMARY", "个人总结", summary.asText(), sourceRef, selectedItem, jobKeywords);
    }

    private void collectTextArrayCandidates(
            List<RewriteCandidate> candidates,
            String sectionType,
            String sectionTitle,
            JsonNode values,
            AiResumeSuggestionItemDTO selectedItem,
            List<String> jobKeywords) {
        if (!values.isArray()) {
            return;
        }
        String sourceText = arrayText(values);
        addCandidate(candidates, sectionType, sectionTitle, sourceText, null, selectedItem, jobKeywords);
    }

    private void collectTextCandidates(
            List<RewriteCandidate> candidates,
            String cleanedText,
            AiResumeSuggestionItemDTO selectedItem,
            List<String> jobKeywords) {
        if (cleanedText == null || cleanedText.isBlank()) {
            return;
        }
        String[] chunks = cleanedText.split("\\n\\s*\\n");
        int index = 1;
        for (String chunk : chunks) {
            String normalized = normalizeWhitespace(chunk);
            if (normalized.length() < 20) {
                continue;
            }
            addCandidate(candidates, "OTHERS", "候选片段 " + index, normalized, null, selectedItem, jobKeywords);
            index++;
            if (index > MAX_CONTEXT_SECTIONS) {
                break;
            }
        }
    }

    private void addCandidate(
            List<RewriteCandidate> candidates,
            String sectionType,
            String sectionTitle,
            String sourceText,
            JsonNode sourceRefNode,
            AiResumeSuggestionItemDTO selectedItem,
            List<String> jobKeywords) {
        String normalizedSourceText = truncateContextText(normalizeWhitespace(sourceText));
        if (normalizedSourceText.isBlank()) {
            return;
        }
        RewriteSourceRefVO sourceRef = buildSourceRef(sourceRefNode);
        List<String> matchedKeywords = matchedKeywords(normalizedSourceText, jobKeywords);
        double confidence = scoreCandidate(sectionType, normalizedSourceText, selectedItem, matchedKeywords, sourceRef);
        candidates.add(new RewriteCandidate(
                sectionType,
                sectionTitle,
                normalizedSourceText,
                buildCandidateReason(sectionType, selectedItem, matchedKeywords),
                confidence,
                matchedKeywords,
                sourceRef));
    }

    private double scoreCandidate(
            String sectionType,
            String sourceText,
            AiResumeSuggestionItemDTO selectedItem,
            List<String> matchedKeywords,
            RewriteSourceRefVO sourceRef) {
        double score = 0.45;
        String targetSection = selectedItem == null ? "" : safeText(selectedItem.getTargetSection());
        String type = selectedItem == null ? "" : safeText(selectedItem.getType()).toUpperCase(Locale.ROOT);
        String section = safeText(sectionType);

        if (targetSection.contains("项目") && "PROJECTS".equals(section)) {
            score += 0.22;
        } else if ((targetSection.contains("技能") || "SKILL_GAP".equals(type)) && "SKILLS".equals(section)) {
            score += 0.22;
        } else if ((targetSection.contains("经历") || "EXPERIENCE_WEAKNESS".equals(type))
                && Set.of("WORK_EXPERIENCES", "INTERNSHIP", "CAMPUS_EXPERIENCES").contains(section)) {
            score += 0.22;
        } else if (targetSection.contains("总结") && "SUMMARY".equals(section)) {
            score += 0.22;
        }

        score += Math.min(0.24, matchedKeywords.size() * 0.06);
        if (sourceRef != null) {
            score += 0.04;
        }
        if (sourceText.length() >= 60) {
            score += 0.05;
        }
        return BigDecimal.valueOf(Math.min(score, 0.98))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private String buildCandidateReason(
            String sectionType,
            AiResumeSuggestionItemDTO selectedItem,
            List<String> matchedKeywords) {
        if (!matchedKeywords.isEmpty()) {
            return "命中岗位关键词：" + String.join("、", matchedKeywords);
        }
        if (selectedItem != null && selectedItem.getTargetSection() != null && !selectedItem.getTargetSection().isBlank()) {
            return "与当前建议的目标部分“" + selectedItem.getTargetSection().strip() + "”接近，可作为候选片段";
        }
        return "该片段来自简历的" + sectionType + "部分，可作为局部改写候选";
    }

    private RewriteSourceRefVO buildSourceRef(JsonNode sourceRefNode) {
        if (sourceRefNode == null || !sourceRefNode.isObject()) {
            return null;
        }
        Integer startLine = integerOrNull(sourceRefNode.path("startLine"));
        Integer endLine = integerOrNull(sourceRefNode.path("endLine"));
        String text = sourceRefNode.path("text").asText(null);
        if (startLine == null && endLine == null && (text == null || text.isBlank())) {
            return null;
        }
        return RewriteSourceRefVO.builder()
                .startLine(startLine)
                .endLine(endLine)
                .text(text == null || text.isBlank() ? null : truncateContextText(text.strip()))
                .build();
    }

    private Integer integerOrNull(JsonNode node) {
        return node != null && node.isNumber() ? node.asInt() : null;
    }

    private List<String> matchedKeywords(String sourceText, List<String> jobKeywords) {
        if (sourceText == null || sourceText.isBlank() || jobKeywords == null || jobKeywords.isEmpty()) {
            return List.of();
        }
        String lowerSourceText = sourceText.toLowerCase(Locale.ROOT);
        return jobKeywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::strip)
                .filter(keyword -> lowerSourceText.contains(keyword.toLowerCase(Locale.ROOT)))
                .distinct()
                .limit(8)
                .toList();
    }

    private void addTextArray(LinkedHashSet<String> target, JsonNode values) {
        if (!values.isArray()) {
            return;
        }
        values.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                target.add(value.asText().strip());
            }
        });
    }

    private String arrayText(JsonNode values) {
        if (!values.isArray()) {
            return "";
        }
        return String.join("；", StreamSupport.stream(values.spliterator(), false)
                .filter(value -> value.isValueNode() && !value.asText().isBlank())
                .map(value -> value.asText().strip())
                .toList());
    }

    private String flattenGroups(JsonNode groups) {
        if (!groups.isObject()) {
            return "";
        }
        List<String> values = new ArrayList<>();
        groups.fields().forEachRemaining(entry -> {
            String groupText = arrayText(entry.getValue());
            if (!groupText.isBlank()) {
                values.add(entry.getKey() + "：" + groupText);
            }
        });
        return String.join("；", values);
    }

    private String buildSuggestionTitle(AiResumeSuggestionItemDTO item) {
        if (item == null) {
            return "岗位优化建议";
        }
        return firstNonBlank(item.getTargetSection(), resolveSuggestionTypeText(item.getType()), "岗位优化建议");
    }

    private String buildSuggestionReason(AiResumeSuggestionItemDTO item) {
        if (item == null) {
            return null;
        }
        String evidence = item.getEvidence() == null || item.getEvidence().isEmpty()
                ? ""
                : "依据：" + String.join("；", item.getEvidence());
        return joinText(item.getIssue(), evidence);
    }

    private String resolveSuggestionTypeText(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        return switch (type.strip().toUpperCase(Locale.ROOT)) {
            case "SKILL_GAP" -> "技能缺口";
            case "EXPERIENCE_WEAKNESS" -> "经历表达不足";
            case "PROJECT_DESCRIPTION" -> "项目描述优化";
            case "HIGHLIGHT_STRENGTH" -> "优势突出";
            case "STRUCTURE" -> "结构优化";
            default -> "综合建议";
        };
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private String truncateContextText(String value) {
        if (value == null || value.length() <= MAX_CONTEXT_SECTION_LENGTH) {
            return value == null ? "" : value;
        }
        return value.substring(0, MAX_CONTEXT_SECTION_LENGTH) + "\n[候选片段过长，已截断]";
    }

    private String joinText(String... values) {
        List<String> parts = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                parts.add(value.strip());
            }
        }
        return String.join("\n", parts);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }

    private String safeText(String value) {
        return value == null ? "" : value.strip();
    }

    private String normalizeRewriteType(String rewriteType) {
        if (rewriteType == null || rewriteType.isBlank()) {
            throw new BusinessException(400, "改写对象类型不能为空");
        }
        String normalized = rewriteType.strip().toUpperCase();
        if (!ALLOWED_REWRITE_TYPES.contains(normalized)) {
            throw new BusinessException(400, "改写对象类型不合法");
        }
        return normalized;
    }

    private String normalizeTargetSection(String targetSection) {
        if (targetSection == null || targetSection.isBlank()) {
            throw new BusinessException(400, "目标简历部分不能为空");
        }
        String normalized = targetSection.strip();
        if (normalized.length() > MAX_TARGET_SECTION_LENGTH) {
            throw new BusinessException(400, "目标简历部分过长");
        }
        return normalized;
    }

    private String normalizeAcceptStatus(String acceptStatus) {
        String normalized = acceptStatus.strip().toUpperCase();
        if (!ALLOWED_ACCEPT_STATUSES.contains(normalized)) {
            throw new BusinessException(400, "采纳状态不合法");
        }
        return normalized;
    }

    private String normalizeAcceptDecisionStatus(String acceptStatus) {
        String normalized = normalizeAcceptStatus(acceptStatus);
        if (ACCEPT_STATUS_PENDING.equals(normalized)) {
            throw new BusinessException(400, "采纳状态只能为 ACCEPTED 或 REJECTED");
        }
        return normalized;
    }

    private String normalizeOriginalText(String originalText) {
        if (originalText == null || originalText.isBlank()) {
            throw new BusinessException(400, "原文片段不能为空");
        }
        String normalized = originalText.strip();
        if (normalized.length() > MAX_ORIGINAL_TEXT_LENGTH) {
            throw new BusinessException(400, "原文片段过长");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            return normalized.substring(0, maxLength);
        }
        return normalized;
    }

    private List<String> normalizeJobKeywords(List<String> jobKeywords) {
        if (jobKeywords == null || jobKeywords.isEmpty()) {
            return List.of();
        }
        return jobKeywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank())
                .map(String::strip)
                .filter(keyword -> keyword.length() <= 40)
                .distinct()
                .limit(20)
                .toList();
    }

    private AiRewriteSuggestion createPendingRewriteSuggestion(
            Long resumeId,
            Long jobDescriptionId,
            Long aiJobMatchResultId,
            Long aiResumeSuggestionId,
            String rewriteType,
            String targetSection,
            String originalText) {
        AiRewriteSuggestion suggestion = new AiRewriteSuggestion();
        suggestion.setResumeId(resumeId);
        suggestion.setJobDescriptionId(jobDescriptionId);
        suggestion.setAiJobMatchResultId(aiJobMatchResultId);
        suggestion.setAiResumeSuggestionId(aiResumeSuggestionId);
        suggestion.setRewriteType(rewriteType);
        suggestion.setTargetSection(targetSection);
        suggestion.setOriginalText(originalText);
        suggestion.setAcceptStatus(ACCEPT_STATUS_PENDING);
        suggestion.setCreatedAt(LocalDateTime.now());
        return suggestion;
    }

    private String buildMatchResultPromptInput(AiJobMatchResult matchResult) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("overallScore", matchResult.getOverallScore());
        root.set("strongMatches", readJsonArrayOrText(matchResult.getStrongMatches()));
        root.set("weakMatches", readJsonArrayOrText(matchResult.getWeakMatches()));
        root.set("missingSkills", readJsonArrayOrText(matchResult.getMissingSkills()));
        root.set("weakExperienceDescriptions", readJsonArrayOrText(matchResult.getWeakExperienceDescriptions()));
        root.set("evidence", readJsonArrayOrText(matchResult.getEvidence()));
        root.set("riskNotes", readJsonArrayOrText(matchResult.getRiskNotes()));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "AI 匹配结果组装失败");
        }
    }

    private JsonNode readJsonArrayOrText(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createArrayNode();
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node.isArray()) {
                return node;
            }
        } catch (JsonProcessingException ignored) {
            // Stored AI match fields should be JSON arrays. If legacy data is not JSON, keep the text as context input.
        }
        return objectMapper.getNodeFactory().textNode(value);
    }

    private void applySuccess(
            AiRewriteSuggestion rewriteSuggestion,
            String promptVersion,
            AiRewriteSuggestionResultDTO result,
            String modelName) {
        rewriteSuggestion.setRewriteStatus(REWRITE_STATUS_SUCCESS);
        rewriteSuggestion.setRewrittenText(result.getRewrittenText());
        rewriteSuggestion.setRewriteReason(result.getRewriteReason());
        rewriteSuggestion.setCaution(buildCaution(result));
        rewriteSuggestion.setModelName(modelName);
        rewriteSuggestion.setPromptVersion(promptVersion);
        rewriteSuggestion.setErrorMessage(null);
    }

    private String buildCaution(AiRewriteSuggestionResultDTO result) {
        String caution = result.getCaution();
        if (!Boolean.TRUE.equals(result.getNeedUserSupplement()) || result.getSupplementQuestions().isEmpty()) {
            return caution;
        }
        return caution + "\n需要用户补充：" + String.join("；", result.getSupplementQuestions());
    }

    private void applyFailed(
            AiRewriteSuggestion rewriteSuggestion,
            String promptVersion,
            String errorMessage,
            String modelName) {
        rewriteSuggestion.setRewriteStatus(REWRITE_STATUS_FAILED);
        rewriteSuggestion.setRewrittenText(null);
        rewriteSuggestion.setRewriteReason(null);
        rewriteSuggestion.setCaution(null);
        rewriteSuggestion.setModelName(modelName);
        rewriteSuggestion.setPromptVersion(promptVersion);
        rewriteSuggestion.setErrorMessage(truncateErrorMessage(errorMessage));
    }

    private void save(AiRewriteSuggestion rewriteSuggestion) {
        rewriteSuggestion.setUpdatedAt(LocalDateTime.now());
        aiRewriteSuggestionMapper.insert(rewriteSuggestion);
    }

    private String normalizeErrorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "AI 局部改写生成失败";
        }
        return exception.getMessage();
    }

    private String truncateErrorMessage(String errorMessage) {
        String sanitized = LogSanitizer.sanitize(errorMessage);
        if (sanitized == null || sanitized.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private record RewriteCandidate(
            String sectionType,
            String sectionTitle,
            String sourceText,
            String reason,
            Double confidence,
            List<String> matchedKeywords,
            RewriteSourceRefVO sourceRef) {

        private RecommendedRewriteSectionVO toVO() {
            return RecommendedRewriteSectionVO.builder()
                    .sectionType(sectionType)
                    .sectionTitle(sectionTitle)
                    .sourceText(sourceText)
                    .reason(reason)
                    .confidence(confidence)
                    .matchedKeywords(matchedKeywords)
                    .sourceRef(sourceRef)
                    .build();
        }
    }
}
