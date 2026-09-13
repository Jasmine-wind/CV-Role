package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.winter.airesumeoptimizer.infra.ai.AiGatewayRequest;
import com.winter.airesumeoptimizer.infra.ai.AiCompletionResult;
import com.winter.airesumeoptimizer.infra.ai.AiGateway;
import com.winter.airesumeoptimizer.infra.ai.AiGatewayException;
import com.winter.airesumeoptimizer.infra.ai.AiGatewaySupport;
import com.winter.airesumeoptimizer.infra.ai.AiInvocationContext;
import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.resume.config.ResumeParseProperties;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAiStructuredParseResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceBlockRole;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredParsePromptDTO;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAiRepairCoordinator;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAiStructuredParser;
import com.winter.airesumeoptimizer.module.resume.service.ResumeParseValidator;
import com.winter.airesumeoptimizer.module.resume.service.ResumeStructuredParsePromptService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ResumeAiStructuredParserImpl implements ResumeAiStructuredParser {

    private static final Logger log = LoggerFactory.getLogger(ResumeAiStructuredParserImpl.class);
    private static final String AI_STATUS_USED = "USED";
    private static final String AI_STATUS_SKIPPED = "SKIPPED";
    private static final String AI_STATUS_FALLBACK = "FALLBACK";
    private static final String AI_STATUS_DISABLED = "DISABLED";
    private static final long DURABLE_REPAIR_WAIT_MILLIS = 5000L;
    private static final long DURABLE_REPAIR_POLL_MILLIS = 25L;

    private final ResumeParseProperties properties;
    private final ResumeStructuredParsePromptService promptService;
    private final ResumeParseValidator resumeParseValidator;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;
    private final ResumeAiRepairCoordinator repairCoordinator;
    /** Cache stores immutable serialized snapshots, never mutable DTO instances. */
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();
    /** One in-process repair dispatch may own a scope; unrelated scopes remain independent. */
    private final ConcurrentMap<String, CompletableFuture<RepairOutcome>> inFlightRepairs =
            new ConcurrentHashMap<>();
    private static final int MAX_CACHE_ENTRIES = 128;

    /** Compatibility constructor for pre-coordination unit doubles. */
    public ResumeAiStructuredParserImpl(
            ResumeParseProperties properties,
            ResumeStructuredParsePromptService promptService,
            ResumeParseValidator resumeParseValidator,
            AiGateway aiGateway,
            ObjectMapper objectMapper) {
        this(properties, promptService, resumeParseValidator, aiGateway, objectMapper, null);
    }

    @Autowired
    public ResumeAiStructuredParserImpl(
            ResumeParseProperties properties,
            ResumeStructuredParsePromptService promptService,
            ResumeParseValidator resumeParseValidator,
            AiGateway aiGateway,
            ObjectMapper objectMapper,
            ResumeAiRepairCoordinator repairCoordinator) {
        this.properties = properties;
        this.promptService = promptService;
        this.resumeParseValidator = resumeParseValidator;
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
        this.repairCoordinator = repairCoordinator;
    }

    @Override
    public ResumeAiStructuredParseResultDTO parse(
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings) {
        return parse(blocks, ruleStructuredContent, qualityWarnings, null);
    }

    @Override
    public ResumeAiStructuredParseResultDTO parse(
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride) {
        return parse(null, blocks, ruleStructuredContent, qualityWarnings, enabledOverride, null);
    }

    @Override
    public ResumeAiStructuredParseResultDTO parse(
            Long userId,
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride,
            AiSelectionSnapshot selection) {
        return parseInternal(userId, null, blocks, ruleStructuredContent, qualityWarnings, enabledOverride, selection, false);
    }

    @Override
    public ResumeAiStructuredParseResultDTO parse(
            Long userId,
            Long resumeId,
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride,
            AiSelectionSnapshot selection) {
        return parseInternal(userId, resumeId, blocks, ruleStructuredContent, qualityWarnings, enabledOverride, selection, false);
    }

    @Override
    public ResumeAiStructuredParseResultDTO parseReferenceOnly(
            Long userId,
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride,
            AiSelectionSnapshot selection) {
        return parseInternal(userId, null, blocks, ruleStructuredContent, qualityWarnings, enabledOverride, selection, true);
    }

    @Override
    public ResumeAiStructuredParseResultDTO parseReferenceOnly(
            Long userId,
            Long resumeId,
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride,
            AiSelectionSnapshot selection) {
        return parseInternal(userId, resumeId, blocks, ruleStructuredContent, qualityWarnings, enabledOverride, selection, true);
    }

    private ResumeAiStructuredParseResultDTO parseInternal(
            Long userId,
            Long resumeId,
            List<ResumeBlockDTO> blocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            Boolean enabledOverride,
            AiSelectionSnapshot selection,
            boolean referenceOnly) {
        long startedAt = System.nanoTime();
        boolean enabled = enabledOverride == null ? properties.aiStructuredParseEnabled() : Boolean.TRUE.equals(enabledOverride);
        if (!enabled) {
            return disabled("AI_STRUCTURED_PARSE_DISABLED", ruleStructuredContent, qualityWarnings, startedAt, referenceOnly);
        }
        // The historical reference-only overload does not carry a resume ID. Keep it usable for
        // compatibility; its cache scope still includes the authenticated user, complete source
        // occurrence snapshot, and BYOK selection, so a text-only global cache key is never used.
        if (blocks == null || blocks.isEmpty()) {
            return skipped(referenceOnly, "NO_STRUCTURED_PARSE_BLOCKS", ruleStructuredContent, qualityWarnings, startedAt, referenceOnly);
        }
        List<ResumeBlockDTO> aiBlocks = blocks.stream()
                .filter(referenceOnly ? this::needsReferenceOnlyParse : this::needsAiStructuredParse)
                .toList();
        if (aiBlocks.isEmpty()) {
            return skipped(referenceOnly, "STABLE_FIELDS_RULE_CONFIRMED", ruleStructuredContent, qualityWarnings, startedAt, referenceOnly);
        }
        if (!referenceOnly) {
            // The ordinary parser is retained as a compatibility seam only. Deterministic rules
            // are canonical, so this path must never build a prompt, read a cache, or dispatch AI.
            return skipped(false, "AI_STRUCTURED_PARSE_RULES_CANONICAL", ruleStructuredContent,
                    qualityWarnings, startedAt, false);
        }
        if (aiBlocks.size() > properties.aiMaxBlocks()) {
            return skipped(true, "AI_BLOCK_LIMIT_EXCEEDED", ruleStructuredContent, qualityWarnings, startedAt, true);
        }
        if (userId == null || userId <= 0) {
            return skipped(true, "AI_CONFIGURATION_REQUIRED", ruleStructuredContent, qualityWarnings, startedAt, true);
        }
        if (selection != null && !selection.isUserByok()) {
            return skipped(true, "AI_BYOK_REQUIRED", ruleStructuredContent, qualityWarnings, startedAt, true);
        }
        if (selection == null) {
            try {
                // Do not use AiGatewaySupport.selectionForNewTask here: its legacy compatibility
                // fallback intentionally creates SYSTEM_DEFAULT snapshots, which are forbidden
                // for a new reference repair.
                selection = aiGateway == null ? null : aiGateway.selectionForNewTask(userId);
            } catch (AiGatewayException exception) {
                return skipped(true, "AI_CONFIGURATION_REQUIRED", ruleStructuredContent, qualityWarnings, startedAt, true);
            }
            if (selection == null) {
                return skipped(true, "AI_CONFIGURATION_REQUIRED", ruleStructuredContent, qualityWarnings, startedAt, true);
            }
        }
        if (!selection.isUserByok()) {
            return skipped(true, "AI_BYOK_REQUIRED", ruleStructuredContent, qualityWarnings, startedAt, true);
        }

        List<String> warnings = new ArrayList<>(qualityWarnings == null ? List.of() : qualityWarnings);
        try {
            ResumeStructuredParsePromptDTO prompt = promptService.buildPrompt(aiBlocks, ruleStructuredContent, warnings);
            // Key the complete source snapshot, not just the blocks sent to the model. A locked
            // or otherwise filtered block can still change the source-backed reference result.
            String cacheKey = buildCacheKey(
                    userId, resumeId, blocks, prompt, ruleStructuredContent, selection, referenceOnly);
            String cachedPayload = cache.get(cacheKey);
            ResumeStructuredContentDTO cached = readCachedContent(cachedPayload);
            if (cached != null && isReferenceContentSourceBacked(cached, blocks, ruleStructuredContent)) {
                return referenceResult(ruleStructuredContent, cached, true, cacheKey, startedAt,
                        cached.getQualityWarnings());
            }
            if (cachedPayload != null) {
                // A malformed or no-longer-source-backed entry must not poison this scope.
                cache.remove(cacheKey, cachedPayload);
            }
            return getOrStartReferenceRepair(
                    userId,
                    resumeId,
                    blocks,
                    aiBlocks,
                    ruleStructuredContent,
                    prompt,
                    warnings,
                    selection,
                    cacheKey,
                    startedAt);
        } catch (RuntimeException exception) {
            log.warn("Resume AI structured reference repair fallback: exceptionType={}", exception.getClass().getSimpleName());
            return aiFallback("AI 结构化补全失败",
                    ruleStructuredContent, qualityWarnings, startedAt, true, false);
        }
    }

    private ResumeAiStructuredParseResultDTO getOrStartReferenceRepair(
            Long userId,
            Long resumeId,
            List<ResumeBlockDTO> sourceBlocks,
            List<ResumeBlockDTO> aiBlocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            ResumeStructuredParsePromptDTO prompt,
            List<String> qualityWarnings,
            AiSelectionSnapshot selection,
            String cacheKey,
            long startedAt) {
        CompletableFuture<RepairOutcome> candidate = new CompletableFuture<>();
        CompletableFuture<RepairOutcome> existing = inFlightRepairs.putIfAbsent(cacheKey, candidate);
        if (existing != null) {
            try {
                // Only callers for this exact source/user/resume/provider scope wait here. No
                // unrelated resume can hold a global parser monitor while its provider call runs.
                return repairOutcomeResult(
                        existing.join(), ruleStructuredContent, sourceBlocks, cacheKey, startedAt, true,
                        qualityWarnings);
            } catch (CompletionException exception) {
                log.warn("Resume AI structured reference repair coordination failed: exceptionType={}",
                        exception.getClass().getSimpleName());
                return aiFallback("AI 结构化补全失败", ruleStructuredContent, qualityWarnings,
                        startedAt, true, false);
            }
        }

        String durableKey = durableRepairKey(cacheKey);
        ResumeAiRepairCoordinator.RepairReservation durableReservation = null;
        try {
            RepairOutcome outcome;
            if (repairCoordinator == null) {
                // Old unit doubles do not have a database seam. Their process-local single-flight
                // map still prevents duplicate calls, while production always uses the durable
                // reservation below.
                outcome = executeReferenceRepair(
                        userId, resumeId, sourceBlocks, aiBlocks, ruleStructuredContent, prompt,
                        qualityWarnings, selection, cacheKey);
            } else {
                try {
                    durableReservation = repairCoordinator.reserve(userId, resumeId, durableKey);
                } catch (RuntimeException exception) {
                    // A missing coordination decision is not permission to call the Provider. A
                    // database outage therefore fails closed rather than weakening at-most-once.
                    log.warn("Resume AI structured repair reservation failed: exceptionType={}",
                            exception.getClass().getSimpleName());
                    outcome = RepairOutcome.failure("AI 结构化补全协调失败", false);
                }
                if (durableReservation != null && durableReservation.state()
                        != ResumeAiRepairCoordinator.State.OWNER) {
                    outcome = durableRepairOutcome(userId, durableKey, cacheKey, durableReservation);
                } else if (durableReservation != null) {
                    outcome = executeReferenceRepair(
                            userId, resumeId, sourceBlocks, aiBlocks, ruleStructuredContent, prompt,
                            qualityWarnings, selection, cacheKey);
                    publishDurableOutcome(userId, durableKey, durableReservation, outcome);
                } else {
                    outcome = RepairOutcome.failure("AI 结构化补全协调失败", false);
                }
            }
            candidate.complete(outcome);
            return repairOutcomeResult(
                    outcome, ruleStructuredContent, sourceBlocks, cacheKey, startedAt,
                    durableReservation != null && durableReservation.state()
                            != ResumeAiRepairCoordinator.State.OWNER,
                    qualityWarnings);
        } finally {
            // Complete even if an unexpected unchecked failure escapes the guarded provider path;
            // waiters must never start a second call merely because the owner failed to publish.
            if (!candidate.isDone()) {
                candidate.complete(RepairOutcome.failure("AI 结构化补全失败", false));
            }
            inFlightRepairs.remove(cacheKey, candidate);
        }
    }

    private RepairOutcome durableRepairOutcome(
            Long userId,
            String durableKey,
            String cacheKey,
            ResumeAiRepairCoordinator.RepairReservation reservation) {
        if (reservation.state() == ResumeAiRepairCoordinator.State.SUCCEEDED) {
            String payload = reservation.resultJson();
            if (payload == null || payload.isBlank()) {
                return RepairOutcome.failure("AI 结构化补全缓存结果无效", true);
            }
            putCachedPayload(cacheKey, payload);
            return RepairOutcome.success(payload);
        }
        if (isDurableFailure(reservation.state())) {
            return RepairOutcome.failure(
                    reservation.failureReason() == null || reservation.failureReason().isBlank()
                            ? "AI 结构化补全失败" : reservation.failureReason(),
                    reservation.providerDispatched());
        }
        if (reservation.state() != ResumeAiRepairCoordinator.State.IN_PROGRESS) {
            return RepairOutcome.failure("AI 结构化补全协调失败", false);
        }

        long deadline = System.nanoTime()
                + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(DURABLE_REPAIR_WAIT_MILLIS);
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(DURABLE_REPAIR_POLL_MILLIS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return RepairOutcome.failure("AI 结构化补全等待被中断", false);
            }
            try {
                ResumeAiRepairCoordinator.RepairReservation current =
                        repairCoordinator.find(userId, durableKey);
                if (current == null) {
                    return RepairOutcome.failure("AI 结构化补全协调失败", false);
                }
                if (current.state() == ResumeAiRepairCoordinator.State.SUCCEEDED) {
                    if (current.resultJson() == null || current.resultJson().isBlank()) {
                        return RepairOutcome.failure("AI 结构化补全缓存结果无效", true);
                    }
                    putCachedPayload(cacheKey, current.resultJson());
                    return RepairOutcome.success(current.resultJson());
                }
                if (isDurableFailure(current.state())) {
                    return RepairOutcome.failure(
                            current.failureReason() == null || current.failureReason().isBlank()
                                    ? "AI 结构化补全失败" : current.failureReason(),
                            current.providerDispatched());
                }
            } catch (RuntimeException exception) {
                log.warn("Resume AI structured repair result lookup failed: exceptionType={}",
                        exception.getClass().getSimpleName());
                return RepairOutcome.failure("AI 结构化补全协调失败", false);
            }
        }
        return RepairOutcome.failure("AI 结构化补全正在处理中", false);
    }

    private boolean isDurableFailure(ResumeAiRepairCoordinator.State state) {
        return state == ResumeAiRepairCoordinator.State.FAILED_NO_DISPATCH
                || state == ResumeAiRepairCoordinator.State.FAILED_AFTER_DISPATCH;
    }

    private void publishDurableOutcome(
            Long userId,
            String durableKey,
            ResumeAiRepairCoordinator.RepairReservation reservation,
            RepairOutcome outcome) {
        try {
            if (outcome != null && outcome.payload() != null) {
                repairCoordinator.complete(
                        userId,
                        durableKey,
                        reservation.ownerToken(),
                        outcome.payload(),
                        outcome.providerDispatched() ? 1 : 0);
            } else {
                repairCoordinator.fail(
                        userId,
                        durableKey,
                        reservation.ownerToken(),
                        outcome == null ? "AI 结构化补全失败" : outcome.failureReason(),
                        outcome != null && outcome.providerDispatched() ? 1 : 0);
            }
        } catch (RuntimeException exception) {
            // The claim remains terminally owned if publication fails. In particular, never
            // start another Provider call just because result persistence was unavailable.
            log.warn("Resume AI structured repair result publication failed: exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    private String durableRepairKey(String cacheKey) {
        return sha256("resume-ai-repair-v1:" + cacheKey);
    }

    private RepairOutcome executeReferenceRepair(
            Long userId,
            Long resumeId,
            List<ResumeBlockDTO> sourceBlocks,
            List<ResumeBlockDTO> aiBlocks,
            ResumeStructuredContentDTO ruleStructuredContent,
            ResumeStructuredParsePromptDTO prompt,
            List<String> qualityWarnings,
            AiSelectionSnapshot selection,
            String cacheKey) {
        List<String> warnings = new ArrayList<>(qualityWarnings == null ? List.of() : qualityWarnings);
        boolean providerCallAttempted = false;
        try {
            String trustedPolicy = prompt.getSystemPrompt() == null || prompt.getSystemPrompt().isBlank()
                    ? "只遵循服务端简历结构化输出契约。"
                    : prompt.getSystemPrompt();
            String untrustedData = prompt.getUserPrompt() == null || prompt.getUserPrompt().isBlank()
                    ? prompt.getPrompt()
                    : prompt.getUserPrompt();
            providerCallAttempted = true;
            AiCompletionResult completion = AiGatewaySupport.complete(
                    aiGateway,
                    new AiInvocationContext(userId, null, "RESUME_STRUCTURED_PARSE", selection),
                    new AiGatewayRequest("RESUME_STRUCTURED_PARSE", trustedPolicy, untrustedData));
            ResumeStructuredContentDTO aiContent = readAiStructuredContent(completion.text());
            if (aiContent.getQualityWarnings() != null) {
                warnings.addAll(aiContent.getQualityWarnings());
            }
            // This is deliberately a reference-only projection. It never replaces the rule
            // result and is never treated as canonical content by this parser.
            ResumeStructuredContentDTO reference = filterReferenceContent(
                    aiContent, ruleStructuredContent, sourceBlocks, warnings);
            if (!hasReferenceFacts(reference)) {
                // An accepted provider response with no source-backed field is not a useful
                // reference candidate. Do not cache an empty success that would mask later
                // repairs for this exact source snapshot.
                return RepairOutcome.failure(
                        "AI 结构化补全未找到可验证的原文内容",
                        providerDispatched(providerCallAttempted, null));
            }
            String payload = serializeCachedContent(reference);
            putCachedPayload(cacheKey, payload);
            return RepairOutcome.success(payload);
        } catch (JsonProcessingException exception) {
            // Jackson diagnostics can include model output; do not retain or return them.
            log.warn("Resume AI structured reference repair fallback: exceptionType={}", exception.getClass().getSimpleName());
            return RepairOutcome.failure(
                    "AI 结构化补全 JSON 解析失败", providerDispatched(providerCallAttempted, exception));
        } catch (RuntimeException exception) {
            log.warn("Resume AI structured reference repair fallback: exceptionType={}", exception.getClass().getSimpleName());
            return RepairOutcome.failure(
                    "AI 结构化补全失败", providerDispatched(providerCallAttempted, exception));
        }
    }

    private boolean providerDispatched(boolean providerCallAttempted, Exception exception) {
        return providerCallAttempted
                && (!(exception instanceof AiGatewayException gatewayException)
                || gatewayException.getProviderDispatchCount() > 0);
    }

    private boolean hasReferenceFacts(ResumeStructuredContentDTO content) {
        if (content == null) {
            return false;
        }
        return hasText(content.getName())
                || hasText(content.getPhone())
                || hasText(content.getEmail())
                || hasText(content.getJobIntention())
                || hasText(content.getHighestEducation())
                || content.getBasicInfo() != null && content.getBasicInfo().entrySet().stream()
                .anyMatch(entry -> hasText(entry.getValue()))
                || hasText(content.getSummary())
                || hasTextIn(content.getEducation())
                || hasTextIn(content.getSkills())
                || hasTextIn(content.getProjects())
                || hasTextIn(content.getWorkExperiences())
                || hasTextIn(content.getInternships())
                || hasTextIn(content.getCampusExperiences())
                || hasTextIn(content.getAwards())
                || hasTextIn(content.getCertificates())
                || hasTextIn(content.getOthers());
    }

    private boolean hasTextIn(List<String> values) {
        return values != null && values.stream().anyMatch(this::hasText);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ResumeAiStructuredParseResultDTO repairOutcomeResult(
            RepairOutcome outcome,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<ResumeBlockDTO> sourceBlocks,
            String cacheKey,
            long startedAt,
            boolean cacheHit,
            List<String> qualityWarnings) {
        if (outcome != null && outcome.payload() != null) {
            ResumeStructuredContentDTO reference = readCachedContent(outcome.payload());
            if (reference != null && isReferenceContentSourceBacked(reference, sourceBlocks, ruleStructuredContent)) {
                return referenceResult(ruleStructuredContent, reference, cacheHit, cacheKey, startedAt,
                        reference.getQualityWarnings());
            }
            cache.remove(cacheKey, outcome.payload());
            return aiFallback("AI 结构化补全缓存结果无效", ruleStructuredContent, qualityWarnings,
                    startedAt, true, !cacheHit && outcome.providerDispatched());
        }
        return aiFallback(
                outcome == null || outcome.failureReason() == null
                        ? "AI 结构化补全失败" : outcome.failureReason(),
                ruleStructuredContent,
                qualityWarnings,
                startedAt,
                true,
                !cacheHit && outcome != null && outcome.providerDispatched());
    }

    private record RepairOutcome(String payload, String failureReason, boolean providerDispatched) {

        private static RepairOutcome success(String payload) {
            return new RepairOutcome(payload, null, true);
        }

        private static RepairOutcome failure(String reason, boolean providerDispatched) {
            return new RepairOutcome(null, reason, providerDispatched);
        }
    }

    private boolean needsAiStructuredParse(ResumeBlockDTO block) {
        if (block == null || block.getText() == null || block.getText().isBlank()) {
            return false;
        }
        String section = block.getSourceSection();
        return !Boolean.TRUE.equals(block.getSectionLocked())
                || section == null
                || section.isBlank()
                || "GENERAL".equals(section)
                || "OTHERS".equals(section);
    }

    private boolean needsReferenceOnlyParse(ResumeBlockDTO block) {
        return block != null
                && block.getText() != null
                && !block.getText().isBlank()
                && block.getRole() != ResumeSourceBlockRole.SECTION_HEADING;
    }

    private ResumeAiStructuredParseResultDTO disabled(
            String reasonCode,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            long startedAt,
            boolean referenceOnly) {
        if (ruleStructuredContent != null && ruleStructuredContent.getQualityWarnings() == null && qualityWarnings != null) {
            ruleStructuredContent.setQualityWarnings(qualityWarnings);
        }
        return ResumeAiStructuredParseResultDTO.builder()
                .aiEnabled(false)
                .applied(false)
                .aiInvoked(false)
                .aiStatus(AI_STATUS_DISABLED)
                .referenceOnly(referenceOnly)
                .skippedReason(reasonCode)
                .fallbackOccurred(false)
                .durationMs(elapsedMs(startedAt))
                .cacheHit(false)
                .structuredContent(ruleStructuredContent)
                .qualityWarnings(qualityWarnings == null ? List.of() : qualityWarnings)
                .build();
    }

    private ResumeAiStructuredParseResultDTO skipped(
            boolean enabled,
            String reasonCode,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            long startedAt,
            boolean referenceOnly) {
        if (ruleStructuredContent != null && ruleStructuredContent.getQualityWarnings() == null && qualityWarnings != null) {
            ruleStructuredContent.setQualityWarnings(qualityWarnings);
        }
        return ResumeAiStructuredParseResultDTO.builder()
                .aiEnabled(enabled)
                .applied(false)
                .aiInvoked(false)
                .aiStatus(AI_STATUS_SKIPPED)
                .referenceOnly(referenceOnly)
                .skippedReason(reasonCode)
                .fallbackOccurred(false)
                .durationMs(elapsedMs(startedAt))
                .cacheHit(false)
                .structuredContent(ruleStructuredContent)
                .qualityWarnings(qualityWarnings == null ? List.of() : qualityWarnings)
                .build();
    }

    private ResumeAiStructuredParseResultDTO aiFallback(
            String reason,
            ResumeStructuredContentDTO ruleStructuredContent,
            List<String> qualityWarnings,
            long startedAt,
            boolean referenceOnly,
            boolean aiInvoked) {
        if (ruleStructuredContent != null && ruleStructuredContent.getQualityWarnings() == null && qualityWarnings != null) {
            ruleStructuredContent.setQualityWarnings(qualityWarnings);
        }
        return ResumeAiStructuredParseResultDTO.builder()
                .aiEnabled(true)
                .applied(false)
                .aiInvoked(aiInvoked)
                .aiStatus(AI_STATUS_FALLBACK)
                .fallbackOccurred(true)
                .fallbackReason(reason)
                .durationMs(elapsedMs(startedAt))
                .cacheHit(false)
                .structuredContent(ruleStructuredContent)
                .referenceOnly(referenceOnly)
                .qualityWarnings(qualityWarnings == null ? List.of() : qualityWarnings)
                .build();
    }

    private ResumeAiStructuredParseResultDTO referenceResult(
            ResumeStructuredContentDTO ruleStructuredContent,
            ResumeStructuredContentDTO reference,
            boolean cacheHit,
            String cacheKey,
            long startedAt,
            List<String> qualityWarnings) {
        return ResumeAiStructuredParseResultDTO.builder()
                .aiEnabled(true)
                .applied(false)
                .aiInvoked(!cacheHit)
                .aiStatus("REFERENCE_ONLY")
                .fallbackOccurred(false)
                .durationMs(elapsedMs(startedAt))
                .cacheHit(cacheHit)
                .cacheKey(cacheKey)
                .structuredContent(ruleStructuredContent)
                .referenceContent(reference)
                .referenceOnly(true)
                .referenceConfidence(0.35d)
                .qualityWarnings(qualityWarnings == null ? List.of() : List.copyOf(qualityWarnings))
                .build();
    }

    private ResumeStructuredContentDTO filterReferenceContent(
            ResumeStructuredContentDTO ai,
            ResumeStructuredContentDTO rule,
            List<ResumeBlockDTO> sourceBlocks,
            List<String> warnings) {
        List<ResumeSourceEvidenceMatcher.Occurrence> occurrences = referenceOccurrences(sourceBlocks, rule);
        List<String> safeWarnings = warnings == null ? new ArrayList<>() : warnings;
        safeWarnings.add("AI_REFERENCE_ONLY");
        Map<String, String> basicInfo = new LinkedHashMap<>();
        if (ai != null && ai.getBasicInfo() != null) {
            for (Map.Entry<String, String> entry : ai.getBasicInfo().entrySet()) {
                // resumeType is derived classification, not a source fact.
                if (!"resumeType".equalsIgnoreCase(entry.getKey())
                        && ResumeSourceEvidenceMatcher.isClaimSupported(
                        entry.getValue(), occurrences, Set.of())) {
                    basicInfo.put(entry.getKey(), entry.getValue().strip());
                }
            }
        }
        ResumeStructuredContentDTO reference = ResumeStructuredContentDTO.builder()
                .name(sourceBackedValue(aiValue(ai, "name", ai == null ? null : ai.getName()), occurrences, Set.of()))
                .phone(sourceBackedValue(aiValue(ai, "phone", ai == null ? null : ai.getPhone()), occurrences, Set.of()))
                .email(sourceBackedValue(aiValue(ai, "email", ai == null ? null : ai.getEmail()), occurrences, Set.of()))
                .basicInfo(basicInfo)
                .jobIntention(sourceBackedValue(aiValue(ai, "jobIntention", ai == null ? null : ai.getJobIntention()), occurrences, Set.of()))
                .highestEducation(sourceBackedValue(aiValue(ai, "degree", ai == null ? null : ai.getHighestEducation()), occurrences, Set.of("EDUCATION")))
                .resumeType(null)
                .education(sourceBackedList(ai == null ? null : ai.getEducation(), occurrences, Set.of("EDUCATION")))
                .skills(sourceBackedList(ai == null ? null : ai.getSkills(), occurrences, Set.of("SKILLS")))
                .projects(sourceBackedList(ai == null ? null : ai.getProjects(), occurrences, Set.of("PROJECTS")))
                .workExperiences(sourceBackedList(ai == null ? null : ai.getWorkExperiences(), occurrences, Set.of("WORK_EXPERIENCES")))
                .internships(sourceBackedList(ai == null ? null : ai.getInternships(), occurrences, Set.of("INTERNSHIPS")))
                .campusExperiences(sourceBackedList(ai == null ? null : ai.getCampusExperiences(), occurrences, Set.of("CAMPUS_EXPERIENCES")))
                .awards(sourceBackedList(ai == null ? null : ai.getAwards(), occurrences, Set.of("AWARDS")))
                .certificates(sourceBackedList(ai == null ? null : ai.getCertificates(), occurrences, Set.of("CERTIFICATES")))
                .summary(sourceBackedValue(ai == null ? null : ai.getSummary(), occurrences, Set.of("SUMMARY")))
                .others(sourceBackedList(ai == null ? null : ai.getOthers(), occurrences, Set.of("OTHERS", "GENERAL")))
                .parseMode(rule == null ? null : rule.getParseMode())
                .qualityWarnings(List.copyOf(new LinkedHashSet<>(safeWarnings)))
                .build();
        return reference;
    }

    private List<ResumeSourceEvidenceMatcher.Occurrence> referenceOccurrences(
            List<ResumeBlockDTO> sourceBlocks, ResumeStructuredContentDTO rule) {
        List<ResumeSourceEvidenceMatcher.Occurrence> occurrences =
                new ArrayList<>(ResumeSourceEvidenceMatcher.index(sourceBlocks));
        boolean hasConcreteIds = occurrences.stream().anyMatch(occurrence -> !occurrence.syntheticId());
        if (!hasConcreteIds && rule != null && rule.getRawText() != null) {
            int index = 0;
            for (String line : rule.getRawText().lines().toList()) {
                if (!ResumeSourceEvidenceMatcher.hasText(line)) {
                    continue;
                }
                ResumeBlockDTO fallback = ResumeBlockDTO.builder()
                        .id("rule-source-" + index)
                        .index(index)
                        .originalIndex(index)
                        .text(line.strip())
                        .sourceSection("GENERAL")
                        .build();
                occurrences.addAll(ResumeSourceEvidenceMatcher.index(List.of(fallback)));
                index++;
            }
        }
        return List.copyOf(occurrences);
    }

    private List<String> sourceBackedList(
            List<String> values,
            List<ResumeSourceEvidenceMatcher.Occurrence> occurrences,
            Set<String> sections) {
        List<String> result = new ArrayList<>();
        for (String value : values == null ? List.<String>of() : values) {
            String safe = sourceBackedValue(value, occurrences, sections);
            if (safe != null && !result.contains(safe)) {
                result.add(safe);
            }
        }
        return List.copyOf(result);
    }

    private String aiValue(ResumeStructuredContentDTO ai, String key, String directValue) {
        if (directValue != null && !directValue.isBlank()) {
            return directValue;
        }
        return ai == null || ai.getBasicInfo() == null ? null : ai.getBasicInfo().get(key);
    }

    private String sourceBackedValue(
            String value,
            List<ResumeSourceEvidenceMatcher.Occurrence> occurrences,
            Set<String> sections) {
        return ResumeSourceEvidenceMatcher.isClaimSupported(value, occurrences, sections)
                ? value.strip() : null;
    }

    private ResumeStructuredContentDTO readCachedContent(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, ResumeStructuredContentDTO.class);
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    /**
     * Revalidate the serialized candidate as well as its key. This protects a hit if a caller
     * mutates/reuses a source DTO or if a future key-version change accidentally omits a field.
     */
    private boolean isReferenceContentSourceBacked(
            ResumeStructuredContentDTO cached,
            List<ResumeBlockDTO> sourceBlocks,
            ResumeStructuredContentDTO ruleStructuredContent) {
        if (cached == null) {
            return false;
        }
        ResumeStructuredContentDTO filtered = filterReferenceContent(
                cached, ruleStructuredContent, sourceBlocks, new ArrayList<>());
        return sameReferenceFacts(cached, filtered);
    }

    private boolean sameReferenceFacts(ResumeStructuredContentDTO left, ResumeStructuredContentDTO right) {
        return left != null
                && right != null
                && Objects.equals(left.getName(), right.getName())
                && Objects.equals(left.getPhone(), right.getPhone())
                && Objects.equals(left.getEmail(), right.getEmail())
                && Objects.equals(left.getBasicInfo(), right.getBasicInfo())
                && Objects.equals(left.getJobIntention(), right.getJobIntention())
                && Objects.equals(left.getHighestEducation(), right.getHighestEducation())
                && Objects.equals(left.getEducation(), right.getEducation())
                && Objects.equals(left.getSkills(), right.getSkills())
                && Objects.equals(left.getProjects(), right.getProjects())
                && Objects.equals(left.getWorkExperiences(), right.getWorkExperiences())
                && Objects.equals(left.getInternships(), right.getInternships())
                && Objects.equals(left.getCampusExperiences(), right.getCampusExperiences())
                && Objects.equals(left.getAwards(), right.getAwards())
                && Objects.equals(left.getCertificates(), right.getCertificates())
                && Objects.equals(left.getSummary(), right.getSummary())
                && Objects.equals(left.getOthers(), right.getOthers());
    }

    private String serializeCachedContent(ResumeStructuredContentDTO content) throws JsonProcessingException {
        return objectMapper.writeValueAsString(content);
    }

    private void putCachedPayload(String cacheKey, String payload) {
        if (cacheKey == null || payload == null || payload.isBlank()) {
            return;
        }
        cache.put(cacheKey, payload);
        // The cache is a bounded optimization, not a source of truth. Concurrent eviction is
        // intentionally approximate so it never serializes unrelated provider calls.
        if (cache.size() > MAX_CACHE_ENTRIES) {
            cache.keySet().stream()
                    .filter(key -> !key.equals(cacheKey))
                    .findFirst()
                    .ifPresent(cache::remove);
        }
    }

    private String buildCacheKey(
            Long userId,
            Long resumeId,
            List<ResumeBlockDTO> blocks,
            ResumeStructuredParsePromptDTO prompt,
            ResumeStructuredContentDTO ruleStructuredContent,
            AiSelectionSnapshot selection,
            boolean referenceOnly) {
        // A cache entry is valid only for the explicit USER_BYOK selection that authorized the
        // reference request. Never derive a cache identity from the gateway's current/default
        // model, which could turn a historical/system configuration into a new call.
        String selectionIdentity = selection.cacheIdentity(userId);
        String modelIdentity = nullToUnknown(selection.model());
        return "structured"
                + ":userId=" + nullToUnknown(userId)
                + ":resumeId=" + nullToUnknown(resumeId)
                + ":ruleContentHash=" + hashStructuredContent(ruleStructuredContent)
                + ":cleanedTextHash=" + hashCleanedText(blocks)
                + ":promptVersion=" + nullToUnknown(prompt == null ? null : prompt.getPromptVersion())
                + ":promptHash=" + hashPrompt(prompt)
                + ":selection=" + selectionIdentity
                + ":modelName=" + modelIdentity
                + ":parserVersion=" + ResumeParseVersions.PARSER_VERSION
                + ":parseMode=" + nullToUnknown(ruleStructuredContent == null ? null : ruleStructuredContent.getParseMode())
                + ":blockBuilderVersion=" + ResumeParseVersions.BLOCK_BUILDER_VERSION
                + ":sectionRuleVersion=" + ResumeParseVersions.SECTION_RULE_VERSION
                + ":referenceOnly=" + referenceOnly
                + ":blockContextHash=" + hashBlocks(blocks);
    }

    private String hashStructuredContent(ResumeStructuredContentDTO content) {
        return hashJson(content);
    }

    private String hashPrompt(ResumeStructuredParsePromptDTO prompt) {
        return hashJson(prompt);
    }

    private String hashJson(Object value) {
        try {
            return sha256(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            // A key that collapses unrelated source snapshots is worse than a repair fallback.
            throw new IllegalStateException("AI 结构化补全缓存快照序列化失败");
        }
    }

    private String hashCleanedText(List<ResumeBlockDTO> blocks) {
        StringBuilder builder = new StringBuilder();
        for (ResumeBlockDTO block : blocks == null ? List.<ResumeBlockDTO>of() : blocks) {
            builder.append(nullToUnknown(block == null ? null : block.getText())).append('\n');
        }
        return sha256(builder.toString());
    }

    /**
     * Hash the complete source-block DTO list. In particular this includes every occurrence ID,
     * logical/source block ID, page, section, and layout coordinate rather than only rendered text.
     * Keeping the full serialized snapshot also makes newly added provenance fields cache-breaking
     * by default instead of silently widening an old cache scope.
     */
    private String hashBlocks(List<ResumeBlockDTO> blocks) {
        return hashJson(blocks == null ? List.of() : blocks);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private String nullToUnknown(Object value) {
        if (value == null) {
            return "unknown";
        }
        String text = String.valueOf(value);
        return text.isBlank() ? "unknown" : text;
    }

    private long elapsedMs(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private ResumeStructuredContentDTO readAiStructuredContent(String aiOutput) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(extractJsonObject(aiOutput));
        JsonNode contentNode = unwrapStructuredContent(root);
        JsonNode normalized = normalizeStructuredContent(contentNode);
        return objectMapper.treeToValue(normalized, ResumeStructuredContentDTO.class);
    }

    private JsonNode unwrapStructuredContent(JsonNode root) {
        for (String fieldName : List.of("structuredResult", "structuredContent", "result", "data")) {
            JsonNode node = root.path(fieldName);
            if (node.isObject()) {
                return node;
            }
        }
        return root;
    }

    private JsonNode normalizeStructuredContent(JsonNode contentNode) throws JsonProcessingException {
        ObjectNode normalized = objectMapper.createObjectNode();
        if (contentNode == null || !contentNode.isObject()) {
            return normalized;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = contentNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode value = field.getValue();
            if ("basicInfo".equals(fieldName) && value.isObject()) {
                normalized.set(fieldName, normalizeStringMap(value));
            } else if (isStringArrayField(fieldName)) {
                normalized.set(fieldName, normalizeStringArray(value));
            } else if (isStringField(fieldName)) {
                putStringValue(normalized, fieldName, value);
            } else {
                normalized.set(fieldName, value);
            }
        }
        return normalized;
    }

    private ObjectNode normalizeStringMap(JsonNode value) throws JsonProcessingException {
        ObjectNode result = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String text = toText(field.getValue());
            if (!text.isBlank()) {
                result.put(field.getKey(), text);
            }
        }
        return result;
    }

    private ArrayNode normalizeStringArray(JsonNode value) throws JsonProcessingException {
        ArrayNode result = objectMapper.createArrayNode();
        if (value == null || value.isNull()) {
            return result;
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                String text = toText(item);
                if (!text.isBlank()) {
                    result.add(text);
                }
            }
            return result;
        }
        String text = toText(value);
        if (!text.isBlank()) {
            result.add(text);
        }
        return result;
    }

    private void putStringValue(ObjectNode target, String fieldName, JsonNode value) throws JsonProcessingException {
        String text = toText(value);
        if (text.isBlank()) {
            target.putNull(fieldName);
        } else {
            target.put(fieldName, text);
        }
    }

    private String toText(JsonNode value) throws JsonProcessingException {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isTextual()) {
            return value.asText().strip();
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return objectMapper.writeValueAsString(value);
    }

    private boolean isStringArrayField(String fieldName) {
        return List.of(
                "education",
                "skills",
                "workExperiences",
                "internships",
                "projects",
                "campusExperiences",
                "awards",
                "certificates",
                "others",
                "qualityWarnings")
                .contains(fieldName);
    }

    private boolean isStringField(String fieldName) {
        return List.of(
                "name",
                "phone",
                "email",
                "jobIntention",
                "highestEducation",
                "resumeType",
                "summary",
                "aiSectionClassifyFallbackReason",
                "aiStructuredParseFallbackReason")
                .contains(fieldName);
    }


    private String extractJsonObject(String value) throws JsonProcessingException {
        if (value == null) {
            return "";
        }
        String stripped = value.strip();
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceFirst("(?i)^```(?:json)?\\s*", "");
            stripped = stripped.replaceFirst("\\s*```$", "");
        }
        stripped = stripped.strip();
        if (stripped.startsWith("{") && stripped.endsWith("}")) {
            return stripped;
        }

        int start = stripped.indexOf('{');
        if (start < 0) {
            throw new JsonProcessingException("AI 输出中未找到 JSON 对象") {
            };
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = start; index < stripped.length(); index++) {
            char current = stripped.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = inString;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return stripped.substring(start, index + 1);
                }
            }
        }

        throw new JsonProcessingException("AI 输出中的 JSON 对象不完整") {
        };
    }
}
