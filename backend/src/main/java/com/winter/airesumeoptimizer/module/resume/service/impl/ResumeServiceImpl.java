package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
import com.winter.airesumeoptimizer.infra.ai.AiGatewayException;
import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.infra.storage.StoreFileCommand;
import com.winter.airesumeoptimizer.infra.storage.StoredFile;
import com.winter.airesumeoptimizer.module.analysis.entity.AiJobMatchResult;
import com.winter.airesumeoptimizer.module.analysis.entity.AiResumeSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.AiRewriteSuggestion;
import com.winter.airesumeoptimizer.module.analysis.entity.ResumeAiAnalysis;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiJobMatchResultMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiResumeSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.AiRewriteSuggestionMapper;
import com.winter.airesumeoptimizer.module.analysis.mapper.ResumeAiAnalysisMapper;
import com.winter.airesumeoptimizer.module.embedding.mapper.ResumeEmbeddingMapper;
import com.winter.airesumeoptimizer.module.evidence.entity.RequirementEvidence;
import com.winter.airesumeoptimizer.module.evidence.mapper.RequirementEvidenceMapper;
import com.winter.airesumeoptimizer.module.export.service.ExportArtifactCleanupService;
import com.winter.airesumeoptimizer.module.job.entity.JobMatchResult;
import com.winter.airesumeoptimizer.module.job.mapper.JobMatchResultMapper;
import com.winter.airesumeoptimizer.module.optimization.entity.JobTarget;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.JobTargetMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import com.winter.airesumeoptimizer.module.resume.config.ResumeParseProperties;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAiStructuredParseResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeDisplayNameUpdateRequestDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseMetaDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseMode;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseOptionsDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseQualityResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeDisplayModelDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAchievementDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeExperienceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeProjectDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillEvidenceDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSkillSetDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSourceRefDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredDataDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSectionClassificationDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSectionClassifyResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructureHealthEvaluation;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextQualityResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeUnresolvedItemDTO;
import com.winter.airesumeoptimizer.module.resume.dto.SourceSectionConfidence;
import com.winter.airesumeoptimizer.module.resume.enums.ResumeQualityStatus;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAiSectionClassifier;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAiStructuredParser;
import com.winter.airesumeoptimizer.module.resume.service.ResumeBlockBuilder;
import com.winter.airesumeoptimizer.module.resume.service.ResumeBlockReorderService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeCanonicalDocumentService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeDisplayModelService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeDocumentQualityValidator;
import com.winter.airesumeoptimizer.module.resume.service.ResumeLineIndexer;
import com.winter.airesumeoptimizer.module.resume.service.ResumeLayoutAwareTextCleanService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeLayoutAwareTextExtractionService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeParseClaimService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeParseQualityCheckService;
import com.winter.airesumeoptimizer.module.resume.service.ResumePointerPostProcessor;
import com.winter.airesumeoptimizer.module.resume.service.ResumeStructureHealthEvaluator;
import com.winter.airesumeoptimizer.module.resume.service.ResumeStructureParseService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextCleanService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextExtractionResult;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextExtractionService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextQualityCheckService;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeDetailVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeListVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeParseResultVO;
import com.winter.airesumeoptimizer.module.resume.vo.ResumeUploadVO;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ResumeServiceImpl implements ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeServiceImpl.class);

    private static final String UPLOAD_STATUS_UPLOADED = "UPLOADED";
    private static final String STORAGE_BIZ_TYPE_RESUMES = "resumes";
    private static final String PARSE_STATUS_SUCCESS = "SUCCESS";
    private static final String PARSE_STATUS_FAILED = "FAILED";
    private static final String AI_STATUS_USED = "USED";
    private static final String AI_STATUS_SKIPPED = "SKIPPED";
    private static final String AI_STATUS_FALLBACK = "FALLBACK";
    private static final String AI_STATUS_REFERENCE_ONLY = "REFERENCE_ONLY";
    private static final String AI_STATUS_DISABLED = "DISABLED";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx");
    private static final String OCTET_STREAM_CONTENT_TYPE = "application/octet-stream";
    private static final Set<String> PDF_CONTENT_TYPES = Set.of("application/pdf", "application/x-pdf");
    private static final Set<String> DOC_CONTENT_TYPES = Set.of(
            "application/msword",
            "application/vnd.ms-word",
            "application/x-msword",
            "application/wps-office.doc");
    private static final Set<String> DOCX_CONTENT_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/zip",
            "application/x-zip-compressed",
            "application/wps-office.docx");
    private static final byte[] PDF_SIGNATURE = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D};
    private static final byte[] DOC_SIGNATURE = new byte[]{
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private final ResumeMapper resumeMapper;
    private final ResumeParseResultMapper resumeParseResultMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final ResumeAiAnalysisMapper resumeAiAnalysisMapper;
    /** Optional setter-injected cleanup seams keep pure unit constructors source-compatible. */
    private OptimizationTaskMapper optimizationTaskMapper;
    private RequirementEvidenceMapper requirementEvidenceMapper;
    private JobTargetMapper jobTargetMapper;
    private final JobMatchResultMapper jobMatchResultMapper;
    private final AiJobMatchResultMapper aiJobMatchResultMapper;
    private final AiResumeSuggestionMapper aiResumeSuggestionMapper;
    private final AiRewriteSuggestionMapper aiRewriteSuggestionMapper;
    private final ResumeEmbeddingMapper resumeEmbeddingMapper;
    private final FileStorageService fileStorageService;
    private final ExportArtifactCleanupService exportArtifactCleanupService;
    private final ResumeTextExtractionService resumeTextExtractionService;
    private final ResumeTextQualityCheckService resumeTextQualityCheckService;
    private final ResumeTextCleanService resumeTextCleanService;
    private final ResumeBlockBuilder resumeBlockBuilder;
    private final ResumeBlockReorderService resumeBlockReorderService;
    private final ResumeAiSectionClassifier resumeAiSectionClassifier;
    private final ResumeAiStructuredParser resumeAiStructuredParser;
    private final ResumeStructureParseService resumeStructureParseService;
    private final ResumeParseQualityCheckService resumeParseQualityCheckService;
    private final ResumeDisplayModelService resumeDisplayModelService;
    private final ResumeLineIndexer resumeLineIndexer;
    private final ResumePointerPostProcessor resumePointerPostProcessor;
    private final ResumeCanonicalDocumentService resumeCanonicalDocumentService;
    private final ResumeDocumentQualityValidator resumeDocumentQualityValidator;
    private final ResumeParseProperties resumeParseProperties;
    private final ObjectMapper objectMapper;
    private final long maxFileSize;
    private final boolean defaultAiStructuredParseEnabled;
    private final ResumeStructureHealthEvaluator structureHealthEvaluator;
    /** Injected by Spring so existing pure unit constructors remain source-compatible. */
    private ResumeParseClaimService resumeParseClaimService;

    public ResumeServiceImpl(
            ResumeMapper resumeMapper,
            ResumeParseResultMapper resumeParseResultMapper,
            ResumeVersionMapper resumeVersionMapper,
            ResumeAiAnalysisMapper resumeAiAnalysisMapper,
            JobMatchResultMapper jobMatchResultMapper,
            AiJobMatchResultMapper aiJobMatchResultMapper,
            AiResumeSuggestionMapper aiResumeSuggestionMapper,
            AiRewriteSuggestionMapper aiRewriteSuggestionMapper,
            ResumeEmbeddingMapper resumeEmbeddingMapper,
            FileStorageService fileStorageService,
            ExportArtifactCleanupService exportArtifactCleanupService,
            ResumeTextExtractionService resumeTextExtractionService,
            ResumeTextQualityCheckService resumeTextQualityCheckService,
            ResumeTextCleanService resumeTextCleanService,
            ResumeBlockBuilder resumeBlockBuilder,
            ResumeBlockReorderService resumeBlockReorderService,
            ResumeAiSectionClassifier resumeAiSectionClassifier,
            ResumeAiStructuredParser resumeAiStructuredParser,
            ResumeStructureParseService resumeStructureParseService,
            ResumeParseQualityCheckService resumeParseQualityCheckService,
            ResumeDisplayModelService resumeDisplayModelService,
            ResumeLineIndexer resumeLineIndexer,
            ResumePointerPostProcessor resumePointerPostProcessor,
            ResumeCanonicalDocumentService resumeCanonicalDocumentService,
            ResumeDocumentQualityValidator resumeDocumentQualityValidator,
            ResumeStructureHealthEvaluator structureHealthEvaluator,
            ResumeParseProperties resumeParseProperties,
            ObjectMapper objectMapper,
            @Value("${app.resume.upload.max-file-size-bytes:10485760}") long maxFileSize,
            @Value("${app.resume.parse.ai-structured-parse-enabled:false}") boolean defaultAiStructuredParseEnabled) {
        this.resumeMapper = resumeMapper;
        this.resumeParseResultMapper = resumeParseResultMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.resumeAiAnalysisMapper = resumeAiAnalysisMapper;
        this.jobMatchResultMapper = jobMatchResultMapper;
        this.aiJobMatchResultMapper = aiJobMatchResultMapper;
        this.aiResumeSuggestionMapper = aiResumeSuggestionMapper;
        this.aiRewriteSuggestionMapper = aiRewriteSuggestionMapper;
        this.resumeEmbeddingMapper = resumeEmbeddingMapper;
        this.fileStorageService = fileStorageService;
        this.exportArtifactCleanupService = exportArtifactCleanupService;
        this.resumeTextExtractionService = resumeTextExtractionService;
        this.resumeTextQualityCheckService = resumeTextQualityCheckService;
        this.resumeTextCleanService = resumeTextCleanService;
        this.resumeBlockBuilder = resumeBlockBuilder;
        this.resumeBlockReorderService = resumeBlockReorderService;
        this.resumeAiSectionClassifier = resumeAiSectionClassifier;
        this.resumeAiStructuredParser = resumeAiStructuredParser;
        this.resumeStructureParseService = resumeStructureParseService;
        this.resumeParseQualityCheckService = resumeParseQualityCheckService;
        this.resumeDisplayModelService = resumeDisplayModelService;
        this.resumeLineIndexer = resumeLineIndexer;
        this.resumePointerPostProcessor = resumePointerPostProcessor;
        this.resumeCanonicalDocumentService = resumeCanonicalDocumentService;
        this.resumeDocumentQualityValidator = resumeDocumentQualityValidator;
        this.structureHealthEvaluator = structureHealthEvaluator;
        this.resumeParseProperties = resumeParseProperties;
        this.objectMapper = objectMapper;
        this.maxFileSize = maxFileSize;
        this.defaultAiStructuredParseEnabled = defaultAiStructuredParseEnabled;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setResumeParseClaimService(ResumeParseClaimService resumeParseClaimService) {
        this.resumeParseClaimService = resumeParseClaimService;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setFormalDeletionDependencies(
            OptimizationTaskMapper optimizationTaskMapper,
            RequirementEvidenceMapper requirementEvidenceMapper) {
        this.optimizationTaskMapper = optimizationTaskMapper;
        this.requirementEvidenceMapper = requirementEvidenceMapper;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setJobTargetMapper(JobTargetMapper jobTargetMapper) {
        this.jobTargetMapper = jobTargetMapper;
    }

    @Override
    @Transactional
    public ResumeUploadVO upload(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
        validateFile(file);

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null
                ? "resume"
                : file.getOriginalFilename());
        String fileType = extractFileType(originalFilename);
        log.info("Resume upload started: userId={}, fileType={}, fileSize={}", userId, fileType, file.getSize());
        StoredFile storedFile = storeResumeFile(userId, file, originalFilename);

        Resume resume = buildResume(userId, storedFile, fileType);
        try {
            int rows = resumeMapper.insert(resume);
            if (rows != 1 || resume.getId() == null) {
                log.warn("Resume metadata save failed: userId={}, storageType={}", userId, storedFile.storageType());
                throw new BusinessException(500, "简历元数据保存失败");
            }
        } catch (RuntimeException exception) {
            try {
                fileStorageService.delete(storedFile.objectKey());
            } catch (RuntimeException cleanupException) {
                // Preserve the metadata failure as the user-facing error; the object key is
                // deliberately omitted from logs because it may contain private upload data.
                log.error("简历元数据保存失败，补偿删除对象也失败: userId={}", userId, cleanupException);
            }
            throw exception;
        }

        log.info("Resume uploaded: userId={}, resumeId={}, fileType={}, fileSize={}, storageType={}",
                userId,
                resume.getId(),
                resume.getFileType(),
                resume.getFileSize(),
                resume.getStorageType());
        return ResumeUploadVO.builder()
                .id(resume.getId())
                .originalFilename(resume.getOriginalFilename())
                .displayName(resume.getDisplayName())
                .fileType(resume.getFileType())
                .fileSize(resume.getFileSize())
                .uploadStatus(resume.getUploadStatus())
                .createdAt(resume.getCreatedAt())
                .build();
    }

    @Override
    public List<ResumeListVO> listByUser(Long userId) {
        validateUserId(userId);

        List<Resume> resumes = resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getUserId, userId)
                .orderByDesc(Resume::getCreatedAt));
        if (resumes.isEmpty()) {
            return List.of();
        }

        Map<Long, ResumeParseResult> parseResults = new LinkedHashMap<>();
        resumeParseResultMapper.selectList(new LambdaQueryWrapper<ResumeParseResult>()
                        .in(ResumeParseResult::getResumeId, resumes.stream().map(Resume::getId).toList()))
                .forEach(result -> parseResults.put(result.getResumeId(), result));
        return resumes.stream()
                .map(resume -> toListVO(resume, parseResults.get(resume.getId())))
                .toList();
    }

    @Override
    public ResumeDetailVO getDetail(Long userId, Long resumeId) {
        validateUserId(userId);
        if (resumeId == null) {
            throw new BusinessException(400, "简历 ID 不能为空");
        }

        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId));
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }

        return toDetailVO(resume);
    }

    @Override
    @Transactional
    public ResumeDetailVO updateDisplayName(
            Long userId, Long resumeId, ResumeDisplayNameUpdateRequestDTO request) {
        Resume resume = getOwnedResume(userId, resumeId);
        if (request == null || request.getDisplayName() == null || request.getDisplayName().isBlank()) {
            throw new BusinessException(400, "简历名称不能为空");
        }
        String displayName = request.getDisplayName().strip();
        if (displayName.length() > 255) {
            throw new BusinessException(400, "简历名称不能超过 255 个字符");
        }
        int rows = resumeMapper.update(null, new LambdaUpdateWrapper<Resume>()
                .eq(Resume::getId, resume.getId())
                .eq(Resume::getUserId, userId)
                .set(Resume::getDisplayName, displayName)
                .set(Resume::getUpdatedAt, LocalDateTime.now()));
        if (rows != 1) {
            throw new BusinessException(409, "简历名称更新失败，请重试");
        }
        return getDetail(userId, resume.getId());
    }

    @Override
    @Transactional
    public ResumeParseResultVO parse(Long userId, Long resumeId) {
        return parse(userId, resumeId, (ResumeParseOptionsDTO) null);
    }

    @Override
    @Transactional
    public ResumeParseResultVO parse(Long userId, Long resumeId, ResumeParseOptionsDTO options) {
        return parseInternal(userId, resumeId, options, null);
    }

    @Override
    @Transactional
    public ResumeParseResultVO parseWithSelection(Long userId, Long resumeId, AiSelectionSnapshot selection) {
        return parseInternal(userId, resumeId, null, selection);
    }

    /**
     * Parse execution is intentionally not guarded by a JVM-wide monitor. A durable
     * resume-scoped generation/token claim is acquired before parsing and every final write is
     * compare-and-set against that claim, so a second application instance cannot publish an
     * older result after a newer attempt has won.
     */
    private ResumeParseResultVO parseInternal(
            Long userId,
            Long resumeId,
            ResumeParseOptionsDTO options,
            AiSelectionSnapshot selection) {
        long totalStartedAt = System.nanoTime();
        Resume resume = getOwnedResume(userId, resumeId);
        ParseClaim parseClaim = acquireParseClaim(resume.getId());
        log.info("Resume parse started: userId={}, resumeId={}, generation={}",
                userId,
                resume.getId(),
                parseClaim.generation());
        // The durable claim transaction already resets quality to PENDING and commits before
        // parsing starts. Keep the old in-process update only for pure unit-test doubles that do
        // not inject the coordination service.
        if (resumeParseClaimService == null) {
            LambdaUpdateWrapper<ResumeParseResult> pendingUpdate = new LambdaUpdateWrapper<ResumeParseResult>()
                    .eq(ResumeParseResult::getResumeId, resume.getId())
                    .eq(ResumeParseResult::getParseGeneration, parseClaim.generation())
                    .eq(ResumeParseResult::getParseToken, parseClaim.token())
                    .set(ResumeParseResult::getQualityStatus, ResumeQualityStatus.QUALITY_PENDING);
            if (resumeParseResultMapper.update(null, pendingUpdate) != 1) {
                throw new BusinessException(409, "简历解析状态已被更新，请重试");
            }
        }

        String extractedText = null;
        ResumeTextExtractionResult extractionResult = null;
        ResumeTextQualityResultDTO qualityResult = null;
        ResumeTextCleanResultDTO cleanResult = null;
        ResumeParseQualityResultDTO parseQualityResult = null;
        long textExtractDurationMs = 0;
        long ruleParseDurationMs = 0;
        try {
            long textExtractStartedAt = System.nanoTime();
            extractionResult = extractWithMetadata(resume.getObjectKey(), resume.getFileType());
            extractionResult = chooseDeterministicCandidate(extractionResult);
            extractedText = extractionResult.text();
            textExtractDurationMs = elapsedMs(textExtractStartedAt);
            qualityResult = checkTextQuality(extractedText, resume.getFileType(), extractionResult);
            if (qualityResult.failed()) {
                ResumeParseResult parseResult = saveParseResult(
                        userId,
                        resume.getId(),
                        parseClaim,
                        PARSE_STATUS_FAILED,
                        extractedText,
                        extractionResult,
                        null,
                        null,
                        null,
                        qualityResult.getMessage(),
                        qualityResult,
                        null,
                        CanonicalQualitySnapshot.failed());
                log.warn("Resume parse stopped by text quality: userId={}, resumeId={}, qualityStatus={}, issues={}",
                        userId,
                        resume.getId(),
                        qualityResult.getStatus(),
                        qualityResult.getIssues());
                return toParseResultVO(parseResult);
            }
            cleanResult = cleanWithMetadata(extractedText, extractionResult.sourceBlocks());
            cleanResult.setExtractionCandidateType(extractionResult.candidateType());
            List<ResumeBlockDTO> blocks = resumeBlockReorderService.reorder(resumeBlockBuilder.build(cleanResult));
            List<ResumeBlockDTO> sourceHealthBlocks = cleanResult.getSourceBlocks() == null
                    || cleanResult.getSourceBlocks().isEmpty()
                    ? blocks : cleanResult.getSourceBlocks();
            ResumeParseMode parseMode = resolveParseMode(options);
            applyBlockParseContext(blocks, parseMode.name(), null);
            // Section ownership is established by the deterministic cleaner/block builder. Do
            // not dispatch the legacy classifier: it can make an AI response authoritative over
            // source provenance and its batching can exceed the parse call budget.
            ResumeSectionClassifyResultDTO sectionClassifyResult = rulesOnlySectionClassifyResult(
                    options, parseMode);
            applySectionClassifyResult(cleanResult, blocks, sectionClassifyResult);
            log.info("Resume AI section classify checked: userId={}, resumeId={}, enabled={}, applied={}, fallbackReason={}",
                    userId,
                    resume.getId(),
                    sectionClassifyResult.getAiEnabled(),
                    sectionClassifyResult.getApplied(),
                    LogSanitizer.sanitize(sectionClassifyResult.getFallbackReason()));
            long ruleParseStartedAt = System.nanoTime();
            ResumeStructuredContentDTO structuredContent = resumeStructureParseService.parse(cleanResult.getCleanedText(), cleanResult.getSections());
            structuredContent.setParseMode(parseMode.name());
            applyBlockParseContext(blocks, parseMode.name(), structuredContent.getResumeType());
            ruleParseDurationMs = elapsedMs(ruleParseStartedAt);
            boolean structuredParseRequested = isStructuredParseRequested(options, parseMode);
            ResumeStructureHealthEvaluation preAiStructureHealth = structureHealthEvaluator.evaluate(
                    structuredContent, sourceHealthBlocks, List.of(), extractionResult.candidateType());
            boolean hasRecoveryEvidence = (blocks != null && !blocks.isEmpty())
                    || StringUtils.hasText(structuredContent.getRawText())
                    || (structuredContent.getRawSections() != null && !structuredContent.getRawSections().isEmpty());
            boolean referenceRepairRequested = structuredParseRequested
                    && hasRecoveryEvidence
                    && preAiStructureHealth.requiresReferenceRepair();
            ResumeAiStructuredParseResultDTO structuredParseResult;
            if (referenceRepairRequested) {
                structuredParseResult = resumeAiStructuredParser.parseReferenceOnly(
                        userId,
                        resume.getId(),
                        blocks,
                        structuredContent,
                        List.of(),
                        true,
                        selection);
                if (structuredParseResult == null) {
                    structuredParseResult = skippedStructuredRepair(
                            "AI_REFERENCE_REPAIR_UNAVAILABLE", structuredContent);
                }
            } else {
                // AI structured content is advisory-only. Do not dispatch the normal parser on a
                // healthy candidate and do not allow an AI result to replace deterministic rules.
                structuredParseResult = skippedStructuredRepair(
                        "AI_REPAIR_NOT_REQUIRED_OR_NOT_ALLOWED", structuredContent);
            }
            if (structuredParseResult == null) {
                structuredParseResult = ResumeAiStructuredParseResultDTO.builder()
                        .aiEnabled(false)
                        .applied(false)
                        .aiInvoked(false)
                        .aiStatus(AI_STATUS_FALLBACK)
                        .fallbackOccurred(true)
                        .fallbackReason("AI 结构化补全未返回结果")
                        .structuredContent(structuredContent)
                        .qualityWarnings(List.of())
                        .build();
            }
            if (referenceRepairRequested) {
                enforceReferenceOnly(structuredParseResult, structuredContent, sourceHealthBlocks);
            } else {
                // A custom implementation must not turn the non-reference path into an implicit
                // AI apply seam. Keep the deterministic rule object as the only canonical input.
                structuredParseResult.setApplied(false);
                structuredParseResult.setStructuredContent(structuredContent);
            }
            structuredContent.setParseMode(parseMode.name());
            log.info("Resume AI structured parse checked: userId={}, resumeId={}, enabled={}, applied={}, fallbackReason={}",
                    userId,
                    resume.getId(),
                    structuredParseResult.getAiEnabled(),
                    structuredParseResult.getApplied(),
                    LogSanitizer.sanitize(structuredParseResult.getFallbackReason()));
            applyAiParseMetadata(structuredContent, sectionClassifyResult, structuredParseResult);
            applyExtractionMetadata(structuredContent, extractionResult);
            applyParseDurations(structuredContent, textExtractDurationMs, ruleParseDurationMs, elapsedMs(totalStartedAt));
            ResumeStructuredResultAssembler.enrich(structuredContent);
            ResumeStructureHealthEvaluation structureHealth = structureHealthEvaluator.evaluate(
                    structuredContent, sourceHealthBlocks, List.of(), extractionResult.candidateType());
            List<ResumeIndexedLineDTO> indexedLines = resumeLineIndexer.index(structuredContent.getRawSections());
            structuredContent.setIndexedLines(indexedLines);
            resumePointerPostProcessor.attachSourceRefs(structuredContent, indexedLines);
            // Re-evaluate after provenance attachment and deterministic enrichment. Health is a
            // delivery gate, not debug metadata from an earlier intermediate representation.
            structureHealth = structureHealthEvaluator.evaluate(
                    structuredContent, sourceHealthBlocks, List.of(), extractionResult.candidateType());
            applyStructureHealthMetadata(structuredContent, structureHealth);
            parseQualityResult = resumeParseQualityCheckService.check(structuredContent, cleanResult, qualityResult);
            mergeStructuredQualityWarnings(structuredContent, structuredParseResult.getQualityWarnings(), parseQualityResult.getWarnings());
            applyDisplayModels(userId, resume.getId(), parseMode, structuredContent, selection);
            String structuredJson = objectMapper.writeValueAsString(structuredContent);
            // Slice A：候选解析 → canonical 交付文档 + 未决候选项，由确定性验证裁决质量状态。
            CanonicalQualitySnapshot canonicalSnapshot = buildCanonicalSnapshot(structuredContent, structureHealth);
            String parseStatus = parseQualityResult.failed() ? PARSE_STATUS_FAILED : PARSE_STATUS_SUCCESS;
            if (parseQualityResult.failed()) {
                canonicalSnapshot = CanonicalQualitySnapshot.failed();
            }
            ResumeParseResult parseResult = saveParseResult(
                    userId,
                    resume.getId(),
                    parseClaim,
                    parseStatus,
                    extractedText,
                    extractionResult,
                    cleanResult.getCleanedText(),
                    serializeSections(cleanResult),
                    structuredJson,
                    parseQualityResult.failed() ? parseQualityResult.getMessage() : null,
                    qualityResult,
                    parseQualityResult,
                    canonicalSnapshot);
            log.info("Resume parse finished: userId={}, resumeId={}, parseStatus={}, extractedTextLength={}, cleanedTextLength={}, sectionCount={}, textQualityStatus={}, parseQualityStatus={}, parseQualityWarnings={}, qualityStatus={}",
                    userId,
                    resume.getId(),
                    parseStatus,
                    extractedText == null ? 0 : extractedText.length(),
                    cleanResult.getCleanedText() == null ? 0 : cleanResult.getCleanedText().length(),
                    cleanResult.getSections() == null ? 0 : cleanResult.getSections().size(),
                    qualityResult.getStatus(),
                    parseQualityResult.getStatus(),
                    parseQualityResult.getWarnings(),
                    canonicalSnapshot.qualityStatus());
            return toParseResultVO(parseResult);
        } catch (StaleParseAttemptException exception) {
            log.info("Discarding stale resume parse result: userId={}, resumeId={}", userId, resume.getId());
            return getCurrentParseResult(resume.getId());
        } catch (ParseSourceRollbackException exception) {
            // The transaction must roll back if the just-created SOURCE could not be removed.
            // Do not let the generic parse-failure handler overwrite the current attempt.
            throw exception;
        } catch (JsonProcessingException exception) {
            try {
                ResumeParseResult parseResult = saveParseResult(
                        userId,
                        resume.getId(),
                        parseClaim,
                        PARSE_STATUS_FAILED,
                        extractedText,
                        extractionResult,
                        cleanResult == null ? null : cleanResult.getCleanedText(),
                        serializeSections(cleanResult),
                        null,
                        "结构化解析结果序列化失败",
                        qualityResult,
                        parseQualityResult,
                        CanonicalQualitySnapshot.failed());
                log.warn("Resume parse failed: userId={}, resumeId={}, reason={}",
                        userId,
                        resume.getId(),
                        LogSanitizer.sanitize("结构化解析结果序列化失败"),
                        exception);
                return toParseResultVO(parseResult);
            } catch (StaleParseAttemptException staleException) {
                log.info("Discarding stale parse failure: userId={}, resumeId={}", userId, resume.getId());
                return getCurrentParseResult(resume.getId());
            }
        } catch (RuntimeException exception) {
            if (selection != null && selection.isUserByok() && exception instanceof AiGatewayException) {
                throw exception;
            }
            String errorMessage = normalizeErrorMessage(exception);
            try {
                ResumeParseResult parseResult = saveParseResult(
                        userId,
                        resume.getId(),
                        parseClaim,
                        PARSE_STATUS_FAILED,
                        extractedText,
                        extractionResult,
                        cleanResult == null ? null : cleanResult.getCleanedText(),
                        serializeSections(cleanResult),
                        null,
                        errorMessage,
                        qualityResult,
                        parseQualityResult,
                        CanonicalQualitySnapshot.failed());
                log.warn("Resume parse failed: userId={}, resumeId={}, reason={}",
                        userId,
                        resume.getId(),
                        LogSanitizer.sanitize(errorMessage),
                        exception);
                return toParseResultVO(parseResult);
            } catch (StaleParseAttemptException staleException) {
                log.info("Discarding stale parse failure: userId={}, resumeId={}", userId, resume.getId());
                return getCurrentParseResult(resume.getId());
            }
        }
    }

    private ResumeTextExtractionResult chooseDeterministicCandidate(ResumeTextExtractionResult extracted) {
        if (extracted == null || extracted.candidates() == null || extracted.candidates().size() < 2) {
            return extracted;
        }
        List<DeterministicCandidateEvaluation> evaluations = extracted.candidates().stream()
                .map(this::evaluateDeterministicCandidate)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (evaluations.isEmpty()) {
            return extracted;
        }
        // LEGACY is the stable baseline. POSITION_SORTED and LAYOUT_LITE are optional recovery
        // candidates and may win only after the complete deterministic path proves a real gain.
        DeterministicCandidateEvaluation selected = evaluations.stream()
                .filter(evaluation -> "LEGACY".equalsIgnoreCase(evaluation.type()))
                .findFirst()
                .orElseGet(() -> evaluations.stream()
                        .filter(evaluation -> evaluation.type().equalsIgnoreCase(extracted.candidateType()))
                        .findFirst()
                        .orElse(evaluations.get(0)));
        for (DeterministicCandidateEvaluation candidate : evaluations) {
            if (candidate == selected || !candidate.hardPass()) {
                continue;
            }
            if (!selected.hardPass()
                    || candidate.health().improvedOver(selected.health(), 8)) {
                selected = candidate;
            }
        }
        if (!selected.hardPass()) {
            // Retain the extractor's stable choice when every candidate is structurally
            // uncertain. It will be delivered through the normal health gate, never promoted by
            // a text-only score.
            return extracted;
        }
        log.info("Deterministic extraction candidate selected after full validation: type={}, healthScore={}, sourceCoverage={}",
                selected.type(), selected.health().healthScore(), selected.health().sourceCoverage());
        return new ResumeTextExtractionResult(
                selected.text(),
                selected.type(),
                selected.sourceBlocks(),
                extracted.legacyScore(),
                extracted.positionScore(),
                extracted.layoutLiteScore(),
                extracted.candidates(),
                extracted.pageCount(),
                extracted.imageContentPresent(),
                extracted.pageCountKnown());
    }

    private DeterministicCandidateEvaluation evaluateDeterministicCandidate(
            ResumeTextExtractionResult.Candidate candidate) {
        if (candidate == null || candidate.text() == null || candidate.text().isBlank()) {
            return null;
        }
        try {
            ResumeTextCleanResultDTO candidateClean = cleanWithMetadata(candidate.text(), candidate.sourceBlocks());
            ResumeStructuredContentDTO candidateContent = resumeStructureParseService.parse(
                    candidateClean.getCleanedText(), candidateClean.getSections());
            if (candidateContent == null) {
                return new DeterministicCandidateEvaluation(
                        candidate.text(), candidate.candidateType(), candidate.sourceBlocks(),
                        new ResumeStructureHealthEvaluation(0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0,
                                false, List.of("STRUCTURE_PARSE_EMPTY"), candidate.candidateType()), false);
            }
            ResumeStructuredResultAssembler.enrich(candidateContent);
            List<ResumeBlockDTO> sourceBlocks = candidateClean.getSourceBlocks() == null
                    || candidateClean.getSourceBlocks().isEmpty()
                    ? candidate.sourceBlocks() : candidateClean.getSourceBlocks();
            ResumeStructureHealthEvaluation health = structureHealthEvaluator.evaluate(
                    candidateContent, sourceBlocks, List.of(), candidate.candidateType());
            ResumeCanonicalDocumentService.BuildResult buildResult = resumeCanonicalDocumentService.build(candidateContent);
            ResumeDocumentQualityValidator.ValidationResult validation = resumeDocumentQualityValidator.validate(
                    buildResult.document(), buildResult.unresolvedItems());
            boolean hardPass = health.hardInvariantPass()
                    && ResumeQualityStatus.QUALITY_READY.equals(validation.qualityStatus());
            return new DeterministicCandidateEvaluation(
                    candidate.text(), candidate.candidateType(), candidate.sourceBlocks(), health, hardPass);
        } catch (RuntimeException exception) {
            log.warn("Deterministic extraction candidate rejected: type={}, exceptionType={}",
                    candidate.candidateType(), exception.getClass().getSimpleName());
            return null;
        }
    }

    private record DeterministicCandidateEvaluation(
            String text,
            String type,
            List<ResumeBlockDTO> sourceBlocks,
            ResumeStructureHealthEvaluation health,
            boolean hardPass) {
    }

    private ResumeTextQualityResultDTO checkTextQuality(
            String extractedText,
            String fileType,
            ResumeTextExtractionResult extractionResult) {
        if (resumeTextQualityCheckService instanceof ResumeTextQualityCheckServiceImpl metadataAware) {
            return metadataAware.check(
                    extractedText,
                    fileType,
                    extractionResult == null ? null : extractionResult.imageContentPresent());
        }
        // Keep old test doubles and external implementations on their established seam until
        // they opt into extraction metadata.
        return resumeTextQualityCheckService.check(extractedText, fileType);
    }

    private ResumeTextExtractionResult extractWithMetadata(String objectKey, String fileType) {
        if (resumeTextExtractionService instanceof ResumeLayoutAwareTextExtractionService layoutAware) {
            ResumeTextExtractionResult result = layoutAware.extractWithMetadata(objectKey, fileType);
            if (result != null) {
                return result;
            }
        }
        String text = resumeTextExtractionService.extractText(objectKey, fileType);
        return new ResumeTextExtractionResult(text, "LEGACY", List.of(), -1, -1, -1, List.of(), 0);
    }

    private ResumeTextCleanResultDTO cleanWithMetadata(String extractedText, List<ResumeBlockDTO> sourceBlocks) {
        if (sourceBlocks != null && !sourceBlocks.isEmpty()
                && resumeTextCleanService instanceof ResumeLayoutAwareTextCleanService layoutAware) {
            ResumeTextCleanResultDTO result = layoutAware.cleanAndSplitSections(extractedText, sourceBlocks);
            if (result != null) {
                return result;
            }
        }
        return resumeTextCleanService.cleanAndSplitSections(extractedText);
    }

    @Override
    @Transactional
    public void lockForAsyncTaskSubmission(Long userId, Long resumeId) {
        getOwnedResumeForUpdate(userId, resumeId);
    }

    @Override
    public ResumeParseResultVO getParseResult(Long userId, Long resumeId) {
        Resume resume = getOwnedResume(userId, resumeId);
        ResumeParseResult parseResult = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resume.getId()));
        if (parseResult == null) {
            throw new BusinessException(404, "简历尚未解析");
        }
        return toParseResultVO(parseResult);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long resumeId) {
        Resume resume = getOwnedResumeForUpdate(userId, resumeId);
        // 先将派生导出物标记为 DELETE_PENDING 并删除对象；元数据保留到本事务成功级联，
        // 这样后续数据库失败/回滚时仍可重试，而不会留下外部对象。
        exportArtifactCleanupService.deleteArtifactsForResume(userId, resume.getId());
        Set<Long> jobTargetIds = deleteResumeChildren(userId, resume.getId());
        int resumeRows = resumeMapper.delete(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resume.getId())
                .eq(Resume::getUserId, userId));
        if (resumeRows != 1) {
            throw new BusinessException(404, "简历不存在");
        }
        deleteOrphanJobTargets(userId, jobTargetIds);
        try {
            fileStorageService.delete(resume.getObjectKey());
        } catch (RuntimeException exception) {
            // The database transaction rolls back, so the retained Resume row remains the
            // retry anchor when object storage is temporarily unavailable.
            throw new BusinessException(500, "简历文件删除失败，简历未删除，请重试");
        }
        log.info("Resume deleted: userId={}, resumeId={}", userId, resume.getId());
    }

    private Set<Long> deleteResumeChildren(Long userId, Long resumeId) {
        Set<Long> jobTargetIds = deleteFormalResumeChildren(userId, resumeId);
        resumeEmbeddingMapper.deleteByResumeId(resumeId);
        aiRewriteSuggestionMapper.delete(new LambdaQueryWrapper<AiRewriteSuggestion>()
                .eq(AiRewriteSuggestion::getResumeId, resumeId));
        aiResumeSuggestionMapper.delete(new LambdaQueryWrapper<AiResumeSuggestion>()
                .eq(AiResumeSuggestion::getResumeId, resumeId));
        aiJobMatchResultMapper.delete(new LambdaQueryWrapper<AiJobMatchResult>()
                .eq(AiJobMatchResult::getResumeId, resumeId));
        jobMatchResultMapper.delete(new LambdaQueryWrapper<JobMatchResult>()
                .eq(JobMatchResult::getResumeId, resumeId));
        resumeAiAnalysisMapper.delete(new LambdaQueryWrapper<ResumeAiAnalysis>()
                .eq(ResumeAiAnalysis::getResumeId, resumeId));
        resumeParseResultMapper.delete(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resumeId));
        return jobTargetIds;
    }

    private Set<Long> deleteFormalResumeChildren(Long userId, Long resumeId) {
        if (optimizationTaskMapper == null || requirementEvidenceMapper == null) {
            return Set.of();
        }
        List<ResumeVersion> versions = resumeVersionMapper.selectList(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getUserId, userId)
                .eq(ResumeVersion::getResumeId, resumeId));
        Set<Long> jobTargetIds = new java.util.LinkedHashSet<>();
        versions.stream()
                .filter(version -> version != null && version.getJobTargetId() != null)
                .map(ResumeVersion::getJobTargetId)
                .forEach(jobTargetIds::add);
        List<Long> versionIds = versions.stream()
                .filter(version -> version != null && version.getId() != null)
                .map(ResumeVersion::getId)
                .toList();
        if (versionIds.isEmpty()) {
            return jobTargetIds;
        }
        // Requirement evidence points directly at SOURCE. Remove it before the restrictive
        // source FK is reached; task deletion then cascades its analysis/requirement rows.
        if (requirementEvidenceMapper != null) {
            requirementEvidenceMapper.delete(new LambdaQueryWrapper<RequirementEvidence>()
                    .eq(RequirementEvidence::getUserId, userId)
                    .in(RequirementEvidence::getSourceResumeVersionId, versionIds));
        }
        if (optimizationTaskMapper != null) {
            List<OptimizationTask> tasks = optimizationTaskMapper.selectList(new LambdaQueryWrapper<OptimizationTask>()
                    .eq(OptimizationTask::getUserId, userId)
                    .and(wrapper -> wrapper
                            .in(OptimizationTask::getSourceResumeVersionId, versionIds)
                            .or()
                            .in(OptimizationTask::getTargetResumeVersionId, versionIds)));
            tasks.stream()
                    .filter(task -> task != null && task.getJobTargetId() != null)
                    .map(OptimizationTask::getJobTargetId)
                    .forEach(jobTargetIds::add);
            optimizationTaskMapper.delete(new LambdaQueryWrapper<OptimizationTask>()
                    .eq(OptimizationTask::getUserId, userId)
                    .and(wrapper -> wrapper
                            .in(OptimizationTask::getSourceResumeVersionId, versionIds)
                            .or()
                            .in(OptimizationTask::getTargetResumeVersionId, versionIds)));
        }
        return jobTargetIds;
    }

    private void deleteOrphanJobTargets(Long userId, Set<Long> candidateIds) {
        if (jobTargetMapper == null || optimizationTaskMapper == null
                || userId == null || candidateIds == null || candidateIds.isEmpty()) {
            return;
        }
        for (Long jobTargetId : candidateIds) {
            if (jobTargetId == null) {
                continue;
            }
            long remainingTasks = optimizationTaskMapper.selectCount(new LambdaQueryWrapper<OptimizationTask>()
                    .eq(OptimizationTask::getUserId, userId)
                    .eq(OptimizationTask::getJobTargetId, jobTargetId));
            long remainingVersions = resumeVersionMapper.selectCount(new LambdaQueryWrapper<ResumeVersion>()
                    .eq(ResumeVersion::getUserId, userId)
                    .eq(ResumeVersion::getJobTargetId, jobTargetId));
            if (remainingTasks == 0L && remainingVersions == 0L) {
                jobTargetMapper.delete(new LambdaQueryWrapper<JobTarget>()
                        .eq(JobTarget::getId, jobTargetId)
                        .eq(JobTarget::getUserId, userId));
            }
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的简历文件");
        }
        if (file.getSize() > maxFileSize) {
            throw new BusinessException(400, "简历文件大小不能超过 " + maxFileSize + " 字节");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null
                ? "resume"
                : file.getOriginalFilename());
        String fileType = extractFileType(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(fileType)) {
            throw new BusinessException(400, "仅支持 PDF、DOC、DOCX 简历文件");
        }

        if (!isAllowedContentType(fileType, file.getContentType())) {
            throw new BusinessException(400, "文件类型与扩展名不匹配");
        }
        if (!hasSupportedFileSignature(fileType, file)) {
            throw new BusinessException(400, "文件内容与扩展名不匹配");
        }
    }

    private Resume getOwnedResume(Long userId, Long resumeId) {
        return findOwnedResume(userId, resumeId, false);
    }

    private Resume getOwnedResumeForUpdate(Long userId, Long resumeId) {
        return findOwnedResume(userId, resumeId, true);
    }

    private Resume findOwnedResume(Long userId, Long resumeId, boolean forUpdate) {
        validateUserId(userId);
        if (resumeId == null) {
            throw new BusinessException(400, "简历 ID 不能为空");
        }

        LambdaQueryWrapper<Resume> query = new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId);
        if (forUpdate) {
            query.last("FOR UPDATE");
        }
        Resume resume = resumeMapper.selectOne(query);
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        return resume;
    }

    private ParseClaim acquireParseClaim(Long resumeId) {
        if (resumeParseClaimService != null) {
            ResumeParseClaimService.ResumeParseClaim claim = resumeParseClaimService.acquire(resumeId);
            return new ParseClaim(claim.generation(), claim.token());
        }
        LocalDateTime now = LocalDateTime.now();
        int ensured = resumeParseResultMapper.ensureParseRow(resumeId, now);
        if (ensured < 0) {
            throw new BusinessException(500, "简历解析状态初始化失败");
        }

        String token = UUID.randomUUID().toString();
        Long generation = resumeParseResultMapper.claimParseGeneration(resumeId, token, now);
        if (generation == null || generation < 1L) {
            // A missing durable claim is not a compatibility case: continuing would re-enable
            // unconditional parse-result/SOURCE writes and let a stale worker publish data.
            throw new BusinessException(409, "简历解析状态已被更新，请重试");
        }
        return new ParseClaim(generation, token);
    }

    private ResumeParseResult saveParseResult(
            Long userId,
            Long resumeId,
            ParseClaim parseClaim,
            String parseStatus,
            String extractedText,
            ResumeTextExtractionResult extractionMetadata,
            String cleanedText,
            String sectionResult,
            String structuredJson,
            String errorMessage,
            ResumeTextQualityResultDTO qualityResult,
            ResumeParseQualityResultDTO parseQualityResult,
            CanonicalQualitySnapshot canonicalSnapshot) {
        LocalDateTime now = LocalDateTime.now();
        ResumeParseResult parseResult = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resumeId));

        if (parseResult == null) {
            // A successful claim always created this row in PostgreSQL. Keeping an in-memory
            // representation here lets the final mapper CAS be the only write path and makes a
            // missing row fail closed instead of falling back to an unconditional insert.
            parseResult = new ResumeParseResult();
            parseResult.setResumeId(resumeId);
            parseResult.setCreatedAt(now);
        }
        parseResult.setUserId(userId);
        parseResult.setParseGeneration(parseClaim.generation());
        parseResult.setParseToken(parseClaim.token());
        parseResult.setParseStatus(parseStatus);
        parseResult.setExtractedText(extractedText);
        parseResult.setCleanedText(cleanedText);
        parseResult.setSectionResult(sectionResult);
        parseResult.setStructuredJson(structuredJson);
        parseResult.setErrorMessage(truncateErrorMessage(errorMessage));
        parseResult.setTextQualityStatus(qualityResult == null ? null : qualityResult.getStatus());
        parseResult.setTextQualityIssues(serializeQualityIssues(qualityResult));
        parseResult.setTextQualityMessage(truncateErrorMessage(qualityResult == null ? null : qualityResult.getMessage()));
        parseResult.setExtractionPageCount(extractionMetadata == null
                ? null : Math.max(0, extractionMetadata.pageCount()));
        parseResult.setExtractionPageCountKnown(extractionMetadata == null
                ? null : extractionMetadata.pageCountKnown());
        parseResult.setExtractionImageContentPresent(extractionMetadata == null
                ? null : extractionMetadata.imageContentPresent());
        parseResult.setParseQualityStatus(parseQualityResult == null ? null : parseQualityResult.getStatus());
        parseResult.setParseQualityWarnings(serializeParseQualityWarnings(parseQualityResult));
        parseResult.setParseQualityMessage(truncateErrorMessage(parseQualityResult == null ? null : parseQualityResult.getMessage()));
        parseResult.setParseQualityScore(parseQualityResult == null ? null : parseQualityResult.getScore());
        CanonicalQualitySnapshot snapshot = canonicalSnapshot == null
                ? CanonicalQualitySnapshot.failed()
                : canonicalSnapshot;
        parseResult.setQualityStatus(snapshot.qualityStatus());
        parseResult.setQualityIssues(snapshot.qualityIssuesJson());
        parseResult.setUnresolvedItems(snapshot.unresolvedItemsJson());

        // Check immediately before materialization as well as at the final update. This keeps a
        // stale attempt from creating a committed SOURCE in the normal race and the final CAS
        // handles a claim that changes between this check and the SOURCE insert.
        assertCurrentParseClaim(resumeId, parseClaim, now);
        Long sourceVersionId = persistCanonicalSourceVersion(userId, resumeId, snapshot, parseClaim, now);
        parseResult.setCanonicalSourceVersionId(sourceVersionId);
        parseResult.setUpdatedAt(now);

        int rows = updateParseResultIncludingNulls(parseResult, parseClaim);
        if (rows != 1) {
            if (sourceVersionId != null) {
                rollbackOrphanSource(sourceVersionId);
            }
            throw new StaleParseAttemptException(resumeId);
        }
        // Terminal rows retain the generation for diagnostics, but no longer expose an active
        // claim token. A late continuation from the completed worker must fail the same CAS as a
        // worker superseded by a newer generation.
        parseResult.setParseToken(null);
        return parseResult;
    }

    private void assertCurrentParseClaim(Long resumeId, ParseClaim parseClaim, LocalDateTime now) {
        int rows = resumeParseResultMapper.touchIfCurrent(
                resumeId,
                parseClaim.generation(),
                parseClaim.token(),
                now);
        if (rows != 1) {
            throw new StaleParseAttemptException(resumeId);
        }
    }

    private int updateParseResultIncludingNulls(ResumeParseResult parseResult, ParseClaim parseClaim) {
        return resumeParseResultMapper.updateIfCurrent(
                parseResult,
                parseResult.getResumeId(),
                parseClaim.generation(),
                parseClaim.token());
    }

    private void rollbackOrphanSource(Long sourceVersionId) {
        int rows = resumeVersionMapper.deleteById(sourceVersionId);
        if (rows != 1) {
            throw new ParseSourceRollbackException(sourceVersionId);
        }
    }

    private boolean isAllowedContentType(String fileType, String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        String normalizedContentType = contentType
                .split(";", 2)[0]
                .strip()
                .toLowerCase(Locale.ROOT);
        if (normalizedContentType.isBlank() || OCTET_STREAM_CONTENT_TYPE.equals(normalizedContentType)) {
            return true;
        }
        return switch (fileType) {
            case "pdf" -> PDF_CONTENT_TYPES.contains(normalizedContentType);
            case "doc" -> DOC_CONTENT_TYPES.contains(normalizedContentType);
            case "docx" -> DOCX_CONTENT_TYPES.contains(normalizedContentType);
            default -> false;
        };
    }

    private boolean hasSupportedFileSignature(String fileType, MultipartFile file) {
        return switch (fileType) {
            case "pdf" -> startsWith(file, PDF_SIGNATURE);
            case "doc" -> startsWith(file, DOC_SIGNATURE);
            case "docx" -> isDocxZip(file);
            default -> false;
        };
    }

    private boolean startsWith(MultipartFile file, byte[] signature) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(signature.length);
            if (header.length < signature.length) {
                return false;
            }
            for (int index = 0; index < signature.length; index++) {
                if (header[index] != signature[index]) {
                    return false;
                }
            }
            return true;
        } catch (IOException exception) {
            throw new BusinessException(500, "简历文件读取失败");
        }
    }

    private boolean isDocxZip(MultipartFile file) {
        boolean hasContentTypes = false;
        boolean hasDocumentXml = false;
        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String entryName = entry.getName();
                if ("[Content_Types].xml".equals(entryName)) {
                    hasContentTypes = true;
                } else if ("word/document.xml".equals(entryName)) {
                    hasDocumentXml = true;
                }
                if (hasContentTypes && hasDocumentXml) {
                    return true;
                }
            }
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    private Resume buildResume(Long userId, StoredFile storedFile, String fileType) {
        LocalDateTime now = LocalDateTime.now();
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setOriginalFilename(storedFile.originalFilename());
        resume.setDisplayName(defaultDisplayName(storedFile.originalFilename()));
        resume.setFileType(fileType.toUpperCase(Locale.ROOT));
        resume.setFileSize(storedFile.size());
        resume.setObjectKey(storedFile.storageKey());
        resume.setStorageType(storedFile.storageType());
        resume.setUploadStatus(UPLOAD_STATUS_UPLOADED);
        resume.setCreatedAt(now);
        resume.setUpdatedAt(now);
        return resume;
    }

    private StoredFile storeResumeFile(Long userId, MultipartFile file, String originalFilename) {
        try (InputStream inputStream = file.getInputStream()) {
            return fileStorageService.store(new StoreFileCommand(
                    userId,
                    originalFilename,
                    file.getContentType(),
                    file.getSize(),
                    inputStream,
                    STORAGE_BIZ_TYPE_RESUMES));
        } catch (IOException exception) {
            throw new BusinessException(500, "简历文件读取失败");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
    }

    private Long persistCanonicalSourceVersion(
            Long userId,
            Long resumeId,
            CanonicalQualitySnapshot snapshot,
            ParseClaim parseClaim,
            LocalDateTime now) {
        // Keep the materialization gate defensive as well as the snapshot builder gate. A future
        // caller must not turn an inconsistent quality snapshot into a READY SOURCE by relying
        // on the status string alone.
        if (snapshot == null
                || snapshot.canonicalDocumentJson() == null
                || snapshot.canonicalDocumentJson().isBlank()
                || (!ResumeQualityStatus.QUALITY_READY.equals(snapshot.qualityStatus())
                && !ResumeQualityStatus.QUALITY_NEEDS_REVIEW.equals(snapshot.qualityStatus()))
                || (ResumeQualityStatus.QUALITY_READY.equals(snapshot.qualityStatus())
                && (!snapshot.hardInvariantPass() || !hasNoUnresolvedItems(snapshot.unresolvedItemsJson())))) {
            return null;
        }
        ResumeVersion source = new ResumeVersion();
        source.setUserId(userId);
        source.setResumeId(resumeId);
        source.setVersionType("SOURCE");
        source.setSourceType("PARSED_UPLOAD");
        // A NEEDS_REVIEW document is canonical and source-backed, but it is not yet a
        // deliverable snapshot. Keep it PENDING so task/export gates cannot consume it.
        source.setContentStatus(ResumeQualityStatus.QUALITY_READY.equals(snapshot.qualityStatus())
                ? "READY" : "PENDING");
        source.setStructuredContent(snapshot.canonicalDocumentJson());
        source.setContentRevision(0L);
        source.setCreatedAt(now);
        source.setUpdatedAt(now);
        int rows = resumeVersionMapper.insertIfCurrentParseClaim(
                source, resumeId, parseClaim.generation(), parseClaim.token());
        if (rows == 0) {
            // The insert predicate is the materialization CAS. A zero-row result means the
            // worker lost the claim; do not enter the generic failure path or write a pointer.
            throw new StaleParseAttemptException(resumeId);
        }
        if (rows != 1 || source.getId() == null) {
            if (source.getId() != null) {
                rollbackOrphanSource(source.getId());
            }
            throw new BusinessException(500, "简历 canonical 内容保存失败");
        }
        return source.getId();
    }

    private ResumeListVO toListVO(Resume resume, ResumeParseResult parseResult) {
        return ResumeListVO.builder()
                .id(resume.getId())
                .originalFilename(resume.getOriginalFilename())
                .displayName(resume.getDisplayName())
                .fileType(resume.getFileType())
                .fileSize(resume.getFileSize())
                .uploadStatus(resume.getUploadStatus())
                .parseStatus(parseResult == null ? "PENDING" : parseResult.getParseStatus())
                .qualityStatus(parseResult == null ? null : parseResult.getQualityStatus())
                .canonicalReady(parseResult != null
                        && ResumeQualityStatus.QUALITY_READY.equals(parseResult.getQualityStatus())
                        && parseResult.getCanonicalSourceVersionId() != null)
                .parseErrorMessage(parseResult == null ? null : parseResult.getErrorMessage())
                .createdAt(resume.getCreatedAt())
                .build();
    }

    private ResumeDetailVO toDetailVO(Resume resume) {
        return ResumeDetailVO.builder()
                .id(resume.getId())
                .originalFilename(resume.getOriginalFilename())
                .displayName(resume.getDisplayName())
                .fileType(resume.getFileType())
                .fileSize(resume.getFileSize())
                .uploadStatus(resume.getUploadStatus())
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }

    private String defaultDisplayName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) return "未命名简历";
        int extensionStart = originalFilename.lastIndexOf('.');
        if (extensionStart > 0) return originalFilename.substring(0, extensionStart);
        return originalFilename;
    }

    private ResumeParseResultVO toParseResultVO(ResumeParseResult parseResult) {
        return ResumeParseResultVO.builder()
                .resumeId(parseResult.getResumeId())
                .parseStatus(parseResult.getParseStatus())
                .extractedText(parseResult.getExtractedText())
                .cleanedText(parseResult.getCleanedText())
                .sectionResult(parseResult.getSectionResult())
                .structuredJson(parseResult.getStructuredJson())
                .errorMessage(parseResult.getErrorMessage())
                .textQualityStatus(parseResult.getTextQualityStatus())
                .textQualityIssues(parseResult.getTextQualityIssues())
                .textQualityMessage(parseResult.getTextQualityMessage())
                .pageCount(parseResult.getExtractionPageCount())
                .pageCountKnown(parseResult.getExtractionPageCountKnown())
                .imageContentPresent(parseResult.getExtractionImageContentPresent())
                .parseQualityStatus(parseResult.getParseQualityStatus())
                .parseQualityWarnings(parseResult.getParseQualityWarnings())
                .parseQualityMessage(parseResult.getParseQualityMessage())
                .parseQualityScore(parseResult.getParseQualityScore())
                .qualityStatus(parseResult.getQualityStatus())
                .qualityIssues(parseResult.getQualityIssues())
                .unresolvedItems(parseResult.getUnresolvedItems())
                .canonicalDocument(readCanonicalDocument(parseResult))
                .updatedAt(parseResult.getUpdatedAt())
                .build();
    }

    private ResumeParseResultVO getCurrentParseResult(Long resumeId) {
        ResumeParseResult current = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resumeId));
        if (current == null) {
            throw new BusinessException(409, "简历解析结果已被更新，请重试");
        }
        return toParseResultVO(current);
    }

    private String readCanonicalDocument(ResumeParseResult parseResult) {
        Long sourceVersionId = parseResult == null ? null : parseResult.getCanonicalSourceVersionId();
        if (sourceVersionId == null) {
            return null;
        }
        ResumeVersion source = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, sourceVersionId)
                .eq(ResumeVersion::getResumeId, parseResult.getResumeId())
                .eq(ResumeVersion::getVersionType, "SOURCE")
                .isNull(ResumeVersion::getSourceVersionId)
                .isNull(ResumeVersion::getJobTargetId));
        return source == null ? null : source.getStructuredContent();
    }

    private String serializeQualityIssues(ResumeTextQualityResultDTO qualityResult) {
        if (qualityResult == null || qualityResult.getIssues() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(qualityResult.getIssues());
        } catch (JsonProcessingException exception) {
            return "[\"QUALITY_ISSUES_SERIALIZE_FAILED\"]";
        }
    }

    private String serializeSections(ResumeTextCleanResultDTO cleanResult) {
        if (cleanResult == null || cleanResult.getSections() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(cleanResult.getSections());
        } catch (JsonProcessingException exception) {
            return "[{\"sectionType\":\"SECTION_RESULT_SERIALIZE_FAILED\",\"heading\":\"章节识别结果序列化失败\",\"lines\":[]}]";
        }
    }

    private ResumeSectionClassifyResultDTO rulesOnlySectionClassifyResult(
            ResumeParseOptionsDTO options,
            ResumeParseMode parseMode) {
        boolean requested = parseMode != ResumeParseMode.FAST
                && (options == null || !Boolean.FALSE.equals(options.getAiSectionClassifyEnabled()));
        return ResumeSectionClassifyResultDTO.builder()
                .aiEnabled(false)
                .applied(false)
                .aiInvoked(false)
                .aiStatus(requested ? AI_STATUS_SKIPPED : AI_STATUS_DISABLED)
                .skippedReason(requested
                        ? "AI_SECTION_CLASSIFY_RULES_CANONICAL"
                        : "AI_SECTION_CLASSIFY_DISABLED")
                .fallbackOccurred(false)
                .durationMs(0L)
                .cacheHit(false)
                .classifications(List.of())
                .build();
    }

    private void applySectionClassifyResult(
            ResumeTextCleanResultDTO cleanResult,
            List<ResumeBlockDTO> blocks,
            ResumeSectionClassifyResultDTO classifyResult) {
        if (cleanResult == null || classifyResult == null) {
            return;
        }
        cleanResult.setAiSectionClassifyEnabled(classifyResult.getAiEnabled());
        cleanResult.setAiSectionClassifyApplied(classifyResult.getApplied());
        cleanResult.setAiSectionClassifyFallbackReason(classifyResult.getFallbackReason());
        cleanResult.setAiSectionClassifyDurationMs(classifyResult.getDurationMs());
        cleanResult.setAiSectionClassifyCacheHit(classifyResult.getCacheHit());
        cleanResult.setAiSectionClassifyCacheKey(classifyResult.getCacheKey());
        if (!classifyResult.shouldApply()) {
            return;
        }
        List<String> conflictWarnings = new ArrayList<>();
        cleanResult.setSections(buildSectionsFromClassifications(blocks, classifyResult.getClassifications(), conflictWarnings));
        cleanResult.setSectionConflictWarnings(conflictWarnings);
    }

    private List<ResumeTextSectionDTO> buildSectionsFromClassifications(
            List<ResumeBlockDTO> blocks,
            List<ResumeSectionClassificationDTO> classifications,
            List<String> conflictWarnings) {
        Map<Integer, ResumeSectionClassificationDTO> classificationByIndex = new LinkedHashMap<>();
        if (classifications != null) {
            for (ResumeSectionClassificationDTO classification : classifications) {
                if (classification != null && classification.getIndex() != null) {
                    classificationByIndex.put(classification.getIndex(), classification);
                }
            }
        }

        List<ResumeBlockDTO> classifiedBlocks = new ArrayList<>();
        if (blocks != null) {
            for (ResumeBlockDTO block : blocks) {
                if (block == null || block.getText() == null || block.getText().isBlank()) {
                    continue;
                }
                ResumeSectionClassificationDTO classification = classificationByIndex.get(block.getIndex());
                String sourceSection = normalizeSection(block.getSourceSection());
                String aiSection = classification == null ? null : normalizeSection(classification.getSection());
                SourceSectionConfidence sourceConfidence = SourceSectionConfidence.from(block.getSourceSectionConfidence());
                SectionDecision decision = decideFinalSection(sourceSection, sourceConfidence, classification);
                boolean sectionLocked = sourceConfidence == SourceSectionConfidence.HIGH && !"OTHERS".equals(sourceSection);
                if (aiSection != null && !sourceSection.equals(aiSection)) {
                    conflictWarnings.add("AI_SECTION_CONFLICT:"
                            + decision.finalSectionSource()
                            + ":"
                            + block.getIndex()
                            + ":"
                            + sourceSection
                            + ">"
                            + aiSection);
                }
                classifiedBlocks.add(ResumeBlockDTO.builder()
                        .id(block.getId())
                        .index(block.getIndex())
                        .originalIndex(block.getOriginalIndex())
                        .displayOrder(block.getDisplayOrder())
                        .text(block.getText())
                        .page(block.getPage())
                        .x(block.getX())
                        .y(block.getY())
                        .width(block.getWidth())
                        .height(block.getHeight())
                        .fontSize(block.getFontSize())
                        .fontName(block.getFontName())
                        .boldHint(block.getBoldHint())
                        .indent(block.getIndent())
                        .bulletHint(block.getBulletHint())
                        .role(block.getRole())
                        .sourceBlockIds(block.getSourceBlockIds())
                        .sourceOccurrenceIds(block.getSourceOccurrenceIds())
                        .prevText(block.getPrevText())
                        .nextText(block.getNextText())
                        .sourceType(block.getSourceType())
                        .iconType(block.getIconType())
                        .sourceSection(decision.finalSection())
                        .ruleSection(block.getRuleSection())
                        .ruleConfidence(block.getRuleConfidence())
                        .sourceSectionConfidence(sourceConfidence.name())
                        .lockedLevel(sourceConfidence.name())
                        .resumeTypeHint(block.getResumeTypeHint())
                        .parseMode(block.getParseMode())
                        .finalSectionSource(decision.finalSectionSource())
                        .sectionLocked(sectionLocked)
                        .build());
            }
        }

        Map<String, List<ResumeBlockDTO>> blocksBySection = new LinkedHashMap<>();
        for (ResumeBlockDTO block : resumeBlockReorderService.reorder(classifiedBlocks)) {
            blocksBySection.computeIfAbsent(block.getSourceSection(), ignored -> new ArrayList<>()).add(block);
        }

        return blocksBySection.entrySet().stream()
                .map(entry -> ResumeTextSectionDTO.builder()
                        .sectionType(entry.getKey())
                        .heading("规则章节：" + entry.getKey())
                        .lines(entry.getValue().stream()
                                .map(ResumeBlockDTO::getText)
                                .toList())
                        .blocks(entry.getValue())
                        .build())
                .toList();
    }

    private SectionDecision decideFinalSection(
            String sourceSection,
            SourceSectionConfidence sourceConfidence,
            ResumeSectionClassificationDTO classification) {
        // AI classification is diagnostic only. Even a high-confidence response cannot move a
        // source occurrence between rule sections; otherwise the later structured parse would
        // silently treat an advisory label as provenance.
        String finalSection = sourceSection == null || sourceSection.isBlank()
                ? "OTHERS"
                : sourceSection;
        return new SectionDecision(finalSection, "RULE_SOURCE_SECTION");
    }

    private String normalizeSection(String section) {
        if (section == null || section.isBlank() || "GENERAL".equals(section)) {
            return "OTHERS";
        }
        return section;
    }

    private record SectionDecision(String finalSection, String finalSectionSource) {
    }

    private void mergeStructuredQualityWarnings(
            ResumeStructuredContentDTO structuredContent,
            List<String> aiWarnings,
            List<String> parseWarnings) {
        if (structuredContent == null) {
            return;
        }
        List<String> warnings = new ArrayList<>();
        if (aiWarnings != null) {
            warnings.addAll(aiWarnings);
        }
        if (parseWarnings != null) {
            warnings.addAll(parseWarnings);
        }
        structuredContent.setQualityWarnings(warnings.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList());
    }

    private void applyDisplayModels(
            Long userId,
            Long resumeId,
            ResumeParseMode parseMode,
            ResumeStructuredContentDTO structuredContent,
            AiSelectionSnapshot selection) {
        if (structuredContent == null) {
            return;
        }
        try {
            ResumeDisplayModelDTO ruleDisplayModel = resumeDisplayModelService.buildRuleDisplayModel(resumeId, structuredContent);
            structuredContent.setRuleDisplayModel(ruleDisplayModel);
            if (parseMode == ResumeParseMode.FAST) {
                structuredContent.setAiDisplayModel(null);
                structuredContent.setDisplayModel(ruleDisplayModel);
                return;
            }
            // The canonical parse path is deterministic. An AI display projection is not a
            // source-backed fact and must not be persisted as the user-visible primary model.
            structuredContent.setAiDisplayModel(null);
            structuredContent.setDisplayModel(ruleDisplayModel);
        } catch (RuntimeException exception) {
            structuredContent.setDisplayModel(structuredContent.getRuleDisplayModel());
            addQualityWarning(structuredContent, "DISPLAY_MODEL_FAILED");
            log.warn("Resume display model skipped: resumeId={}, exceptionType={}, reason={}",
                    resumeId,
                    exception.getClass().getSimpleName(),
                    LogSanitizer.sanitize(normalizeErrorMessage(exception)));
        }
    }

    private void rejectAiCandidate(
            ResumeAiStructuredParseResultDTO result,
            ResumeStructuredContentDTO ruleContent,
            String reason,
            ResumeStructureHealthEvaluation candidateHealth) {
        result.setApplied(false);
        result.setAiStatus(AI_STATUS_FALLBACK);
        result.setFallbackOccurred(true);
        result.setFallbackReason(reason);
        result.setStructuredContent(ruleContent);
        List<String> warnings = new ArrayList<>(result.getQualityWarnings() == null
                ? List.of() : result.getQualityWarnings());
        warnings.add("AI_STRUCTURE_HEALTH_GATE_REJECTED");
        if (candidateHealth != null && !candidateHealth.hardInvariantViolations().isEmpty()) {
            warnings.addAll(candidateHealth.hardInvariantViolations());
        }
        result.setQualityWarnings(warnings.stream().distinct().toList());
    }

    private void enforceReferenceOnly(
            ResumeAiStructuredParseResultDTO result,
            ResumeStructuredContentDTO ruleContent,
            List<ResumeBlockDTO> sourceBlocks) {
        if (result == null) {
            return;
        }
        result.setApplied(false);
        result.setReferenceOnly(true);
        result.setStructuredContent(ruleContent);
        boolean fallback = AI_STATUS_FALLBACK.equals(status(result))
                || Boolean.TRUE.equals(result.getFallbackOccurred());
        if (!AI_STATUS_SKIPPED.equals(status(result)) && !fallback) {
            result.setAiStatus(AI_STATUS_REFERENCE_ONLY);
        }
        Double confidence = result.getReferenceConfidence();
        if (confidence == null || !Double.isFinite(confidence) || confidence <= 0.0d || confidence > 0.5d) {
            result.setReferenceConfidence(0.35d);
        }
        boolean skippedWithoutCandidate = result.getReferenceContent() == null
                && (AI_STATUS_SKIPPED.equals(status(result)) || !Boolean.TRUE.equals(result.getAiInvoked()));
        // A provider/configuration failure is already a safe rule fallback. Only a non-fallback
        // candidate needs provenance validation; never rewrite the original failure into a
        // misleading provenance error merely because no candidate was returned.
        boolean candidateSafe = result.getReferenceContent() == null
                ? skippedWithoutCandidate || fallback
                : !fallback && referenceCandidateIsSafe(
                result.getReferenceContent(), ruleContent, sourceBlocks);
        if (!candidateSafe) {
            result.setReferenceContent(null);
            result.setAiStatus(AI_STATUS_FALLBACK);
            result.setFallbackOccurred(true);
            result.setFallbackReason("AI_REFERENCE_PROVENANCE_INVALID");
        }
        List<String> warnings = new ArrayList<>(result.getQualityWarnings() == null
                ? List.of() : result.getQualityWarnings());
        warnings.add("AI_REFERENCE_ONLY");
        result.setQualityWarnings(warnings.stream().distinct().toList());
    }

    private boolean referenceCandidateIsSafe(
            ResumeStructuredContentDTO candidate,
            ResumeStructuredContentDTO ruleContent,
            List<ResumeBlockDTO> sourceBlocks) {
        if (candidate == null || sourceBlocks == null || sourceBlocks.isEmpty()) {
            return false;
        }
        // The advisory object must not smuggle a second source/display document through the
        // reference channel. The rule result remains the sole canonical source.
        if (candidate.getRawText() != null || candidate.getRawSections() != null
                || candidate.getSections() != null || candidate.getIndexedLines() != null
                || candidate.getDebug() != null || candidate.getParseMeta() != null
                || candidate.getDisplayModel() != null || candidate.getAiDisplayModel() != null
                || candidate.getRuleDisplayModel() != null) {
            return false;
        }
        if (ruleContent != null && candidate.getParseMode() != null
                && !java.util.Objects.equals(candidate.getParseMode(), ruleContent.getParseMode())) {
            return false;
        }
        List<ResumeSourceEvidenceMatcher.Occurrence> occurrences =
                ResumeSourceEvidenceMatcher.index(sourceBlocks);
        if (occurrences.isEmpty() || occurrences.stream().anyMatch(ResumeSourceEvidenceMatcher.Occurrence::syntheticId)) {
            return false;
        }
        int claims = 0;
        int validation = validateReferenceScalar(candidate.getName(), occurrences, Set.of());
        if (validation < 0) return false;
        claims += validation;
        validation = validateReferenceScalar(candidate.getPhone(), occurrences, Set.of());
        if (validation < 0) return false;
        claims += validation;
        validation = validateReferenceScalar(candidate.getEmail(), occurrences, Set.of());
        if (validation < 0) return false;
        claims += validation;
        validation = validateReferenceScalar(candidate.getJobIntention(), occurrences, Set.of());
        if (validation < 0) return false;
        claims += validation;
        validation = validateReferenceScalar(candidate.getHighestEducation(), occurrences, Set.of("EDUCATION"));
        if (validation < 0) return false;
        claims += validation;
        validation = validateReferenceScalar(candidate.getSummary(), occurrences, Set.of("SUMMARY"));
        if (validation < 0) return false;
        claims += validation;
        if (candidate.getBasicInfo() != null) {
            for (Map.Entry<String, String> entry : candidate.getBasicInfo().entrySet()) {
                if ("resumeType".equalsIgnoreCase(entry.getKey())) {
                    continue;
                }
                validation = validateReferenceScalar(entry.getValue(), occurrences, Set.of());
                if (validation < 0) return false;
                claims += validation;
            }
        }
        validation = validateReferenceList(candidate.getEducation(), occurrences, Set.of("EDUCATION"));
        if (validation < 0) return false;
        claims += validation;
        validation = validateReferenceList(candidate.getSkills(), occurrences, Set.of("SKILLS"));
        if (validation < 0) return false;
        claims += validation;
        validation = validateReferenceList(candidate.getProjects(), occurrences, Set.of("PROJECTS"));
        if (validation < 0) return false;
        claims += validation;
        validation = validateReferenceList(candidate.getWorkExperiences(), occurrences, Set.of("WORK_EXPERIENCES"));
        if (validation < 0) return false;
        claims += validation;
        validation = validateReferenceList(candidate.getInternships(), occurrences, Set.of("INTERNSHIPS"));
        if (validation < 0) return false;
        claims += validation;
        validation = validateReferenceList(candidate.getCampusExperiences(), occurrences, Set.of("CAMPUS_EXPERIENCES"));
        if (validation < 0) return false;
        claims += validation;
        validation = validateReferenceList(candidate.getAwards(), occurrences, Set.of("AWARDS"));
        if (validation < 0) return false;
        claims += validation;
        validation = validateReferenceList(candidate.getCertificates(), occurrences, Set.of("CERTIFICATES"));
        if (validation < 0) return false;
        claims += validation;
        validation = validateReferenceList(candidate.getOthers(), occurrences, Set.of("OTHERS", "GENERAL"));
        if (validation < 0) return false;
        claims += validation;
        if (candidate.getStructuredData() != null) {
            validation = validateStructuredData(candidate.getStructuredData(), occurrences);
            if (validation < 0) return false;
            claims += validation;
        }
        return claims > 0;
    }

    private int validateReferenceScalar(
            String value,
            List<ResumeSourceEvidenceMatcher.Occurrence> occurrences,
            Set<String> sections) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return ResumeSourceEvidenceMatcher.isClaimSupported(value, occurrences, sections) ? 1 : -1000;
    }

    private int validateReferenceList(
            List<String> values,
            List<ResumeSourceEvidenceMatcher.Occurrence> occurrences,
            Set<String> sections) {
        int claims = 0;
        for (String value : values == null ? List.<String>of() : values) {
            int result = validateReferenceScalar(value, occurrences, sections);
            if (result < 0) {
                return result;
            }
            claims += result;
        }
        return claims;
    }

    private int validateStructuredData(
            ResumeStructuredDataDTO data,
            List<ResumeSourceEvidenceMatcher.Occurrence> occurrences) {
        int claims = 0;
        int result = validateReferenceList(data.getEducation(), occurrences, Set.of("EDUCATION"));
        if (result < 0 || !validateReferenceRefs(data.getEducationSourceRefs(), occurrences, Set.of("EDUCATION"))
                || data.getEducation() != null && !data.getEducation().isEmpty() && (data.getEducationSourceRefs() == null
                || data.getEducationSourceRefs().size() < data.getEducation().size())) return -1000;
        claims += result;
        ResumeSkillSetDTO skills = data.getSkills();
        if (skills != null) {
            result = validateReferenceList(skills.getKeywords(), occurrences, Set.of("SKILLS"));
            if (result < 0 || !validateSkillSet(skills, occurrences)) return -1000;
            claims += result;
        }
        for (ResumeExperienceDTO experience : data.getExperiences() == null
                ? List.<ResumeExperienceDTO>of() : data.getExperiences()) {
            if (experience == null) continue;
            if (!validConfidence(experience.getConfidence())
                    || !validEntryReference(experience.getSourceRef(), occurrences, Set.of("WORK_EXPERIENCES", "INTERNSHIPS", "CAMPUS_EXPERIENCES"))) return -1000;
            if (!entryValuesSupported(experience.getSourceRef(), occurrences,
                    experience.getOrganization(), experience.getRole(), experience.getStartDate(),
                    experience.getEndDate(), experience.getDescription())
                    || !valuesSupportedByReference(experience.getSourceRef(), occurrences, experience.getBullets())
                    || !valuesSupportedByReference(experience.getSourceRef(), occurrences, experience.getEvidence())) return -1000;
            claims++;
        }
        for (ResumeProjectDTO project : data.getProjects() == null
                ? List.<ResumeProjectDTO>of() : data.getProjects()) {
            if (project == null) continue;
            if (!validConfidence(project.getConfidence())
                    || !validEntryReference(project.getSourceRef(), occurrences, Set.of("PROJECTS", "WORK_EXPERIENCES", "INTERNSHIPS"))) return -1000;
            if (!entryValuesSupportedByReference(project.getSourceRef(), occurrences,
                    project.getName(), project.getDescription(), project.getRole(), project.getMentor(),
                    project.getTimeRange(), project.getEnvironment(), project.getStartDate(), project.getEndDate())
                    || !valuesSupportedByReference(project.getSourceRef(), occurrences, project.getTechStack())
                    || !valuesSupportedByReference(project.getSourceRef(), occurrences, project.getResponsibilities())
                    || !valuesSupportedByReference(project.getSourceRef(), occurrences, project.getEvidence())) return -1000;
            claims++;
        }
        for (ResumeAchievementDTO achievement : data.getAchievements() == null
                ? List.<ResumeAchievementDTO>of() : data.getAchievements()) {
            if (achievement == null) continue;
            if (!validConfidence(achievement.getConfidence())
                    || !validEntryReference(achievement.getSourceRef(), occurrences, Set.of("AWARDS", "CAMPUS_EXPERIENCES"))) return -1000;
            if (!entryValuesSupportedByReference(achievement.getSourceRef(), occurrences,
                    achievement.getTitle(), achievement.getLevel(), achievement.getCompetition(),
                    achievement.getRanking(), achievement.getTimeRange(), achievement.getDate())
                    || !valuesSupportedByReference(achievement.getSourceRef(), occurrences, achievement.getEvidence())) return -1000;
            claims++;
        }
        result = validateReferenceList(data.getCertificates(), occurrences, Set.of("CERTIFICATES"));
        if (result < 0) return -1000;
        claims += result;
        if (data.getSummary() != null && (data.getSummarySourceRef() == null
                || !ResumeSourceEvidenceMatcher.referenceSupportsValue(data.getSummary(), data.getSummarySourceRef(), occurrences, Set.of("SUMMARY")))) {
            return -1000;
        }
        if (data.getSummary() != null) claims++;
        result = validateReferenceList(data.getOthers(), occurrences, Set.of("OTHERS", "GENERAL"));
        return result < 0 ? -1000 : claims + result;
    }

    private boolean validateReferenceRefs(
            List<ResumeSourceRefDTO> references,
            List<ResumeSourceEvidenceMatcher.Occurrence> occurrences,
            Set<String> sections) {
        for (ResumeSourceRefDTO reference : references == null ? List.<ResumeSourceRefDTO>of() : references) {
            if (!ResumeSourceEvidenceMatcher.isValidReference(reference, occurrences, sections)) {
                return false;
            }
        }
        return true;
    }

    private boolean validateSkillSet(ResumeSkillSetDTO skills, List<ResumeSourceEvidenceMatcher.Occurrence> occurrences) {
        Set<String> keywords = new java.util.LinkedHashSet<>(skills.getKeywords() == null ? List.of() : skills.getKeywords());
        if (skills.getGroups() != null && skills.getGroups().values().stream()
                .flatMap(List::stream).anyMatch(value -> !keywords.contains(value))) return false;
        for (String description : skills.getDescriptions() == null ? List.<String>of() : skills.getDescriptions()) {
            if (!ResumeSourceEvidenceMatcher.isClaimSupported(description, occurrences, Set.of("SKILLS"))) return false;
        }
        for (ResumeSkillEvidenceDTO evidence : skills.getEvidence() == null
                ? List.<ResumeSkillEvidenceDTO>of() : skills.getEvidence()) {
            if (evidence == null || !ResumeSourceEvidenceMatcher.isValidReference(
                    evidence.getSourceRef(), occurrences, Set.of("SKILLS"))) return false;
            if (!entryValuesSupportedByReference(evidence.getSourceRef(), occurrences,
                    evidence.getSkill(), evidence.getSourceText(), evidence.getDescription())
                    || !valuesSupportedByReference(evidence.getSourceRef(), occurrences, evidence.getKeywords())) return false;
        }
        return true;
    }

    private boolean validEntryReference(
            ResumeSourceRefDTO reference,
            List<ResumeSourceEvidenceMatcher.Occurrence> occurrences,
            Set<String> sections) {
        return ResumeSourceEvidenceMatcher.isValidReference(reference, occurrences, sections);
    }

    private boolean entryValuesSupported(
            ResumeSourceRefDTO reference,
            List<ResumeSourceEvidenceMatcher.Occurrence> occurrences,
            String... values) {
        return entryValuesSupportedByReference(reference, occurrences, values);
    }

    private boolean entryValuesSupportedByReference(
            ResumeSourceRefDTO reference,
            List<ResumeSourceEvidenceMatcher.Occurrence> occurrences,
            String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()
                    && !ResumeSourceEvidenceMatcher.referenceSupportsValue(value, reference, occurrences, Set.of())) {
                return false;
            }
        }
        return true;
    }

    private boolean valuesSupportedByReference(
            ResumeSourceRefDTO reference,
            List<ResumeSourceEvidenceMatcher.Occurrence> occurrences,
            List<String> values) {
        for (String value : values == null ? List.<String>of() : values) {
            if (value != null && !value.isBlank()
                    && !ResumeSourceEvidenceMatcher.referenceSupportsValue(value, reference, occurrences, Set.of())) {
                return false;
            }
        }
        return true;
    }

    private boolean validConfidence(Double confidence) {
        return confidence == null || Double.isFinite(confidence) && confidence >= 0.0d && confidence <= 1.0d;
    }

    private ResumeAiStructuredParseResultDTO skippedStructuredRepair(
            String reason,
            ResumeStructuredContentDTO structuredContent) {
        return ResumeAiStructuredParseResultDTO.builder()
                .aiEnabled(false)
                .applied(false)
                .aiInvoked(false)
                .aiStatus(AI_STATUS_SKIPPED)
                .skippedReason(reason)
                .fallbackOccurred(false)
                .structuredContent(structuredContent)
                .qualityWarnings(List.of("AI_REFERENCE_REPAIR_SKIPPED"))
                .build();
    }

    private void applyStructureHealthMetadata(
            ResumeStructuredContentDTO structuredContent,
            ResumeStructureHealthEvaluation health) {
        if (structuredContent == null || health == null) {
            return;
        }
        Map<String, Object> debug = structuredContent.getDebug() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(structuredContent.getDebug());
        debug.put("structureHealthScore", health.healthScore());
        debug.put("sourceCoverage", health.sourceCoverage());
        debug.put("orphanContentCount", health.orphanContentCount());
        debug.put("duplicateSourceCount", health.duplicateSourceCount());
        debug.put("entryBoundaryViolations", health.entryBoundaryViolations());
        debug.put("unresolvedGeneralRatio", health.unresolvedGeneralRatio());
        debug.put("fragmentedLineCount", health.fragmentedLineCount());
        debug.put("suspiciousEntryCollapse", health.suspiciousEntryCollapse());
        debug.put("sectionConsistency", health.sectionConsistency());
        debug.put("selectedCandidate", health.candidateType());
        structuredContent.setDebug(debug);
        log.info("Resume structure health evaluated: candidateType={}, score={}, sourceCoverage={}, orphanCount={}, duplicateCount={}, boundaryViolations={}",
                health.candidateType(),
                health.healthScore(),
                health.sourceCoverage(),
                health.orphanContentCount(),
                health.duplicateSourceCount(),
                health.entryBoundaryViolations());
    }

    private void addQualityWarning(ResumeStructuredContentDTO structuredContent, String warning) {
        if (structuredContent == null || !StringUtils.hasText(warning)) {
            return;
        }
        List<String> warnings = new ArrayList<>(structuredContent.getQualityWarnings() == null
                ? List.of()
                : structuredContent.getQualityWarnings());
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
        structuredContent.setQualityWarnings(warnings);
    }

    private void applyAiParseMetadata(
            ResumeStructuredContentDTO structuredContent,
            ResumeSectionClassifyResultDTO sectionClassifyResult,
            ResumeAiStructuredParseResultDTO structuredParseResult) {
        if (structuredContent == null) {
            return;
        }
        structuredContent.setParserVersion(ResumeParseVersions.PARSER_VERSION);
        if (sectionClassifyResult != null) {
            structuredContent.setAiSectionClassifyEnabled(sectionClassifyResult.getAiEnabled());
            structuredContent.setAiSectionClassifyApplied(sectionClassifyResult.getApplied());
            structuredContent.setAiSectionClassifyFallbackReason(sectionClassifyResult.getFallbackReason());
            structuredContent.setAiSectionClassifyDurationMs(sectionClassifyResult.getDurationMs());
            structuredContent.setAiSectionClassifyCacheHit(sectionClassifyResult.getCacheHit());
            structuredContent.setAiSectionClassifyCacheKey(sectionClassifyResult.getCacheKey());
        }
        if (structuredParseResult != null) {
            structuredContent.setAiStructuredParseEnabled(structuredParseResult.getAiEnabled());
            structuredContent.setAiStructuredParseApplied(structuredParseResult.getApplied());
            structuredContent.setAiStructuredParseFallbackReason(structuredParseResult.getFallbackReason());
            structuredContent.setAiStructuredParseDurationMs(structuredParseResult.getDurationMs());
            structuredContent.setAiStructuredParseCacheHit(structuredParseResult.getCacheHit());
            structuredContent.setAiStructuredParseCacheKey(structuredParseResult.getCacheKey());
            structuredContent.setAiStructuredParseReference(
                    Boolean.TRUE.equals(structuredParseResult.getReferenceOnly())
                            ? structuredParseResult.getReferenceContent() : null);
            structuredContent.setAiStructuredParseReferenceConfidence(
                    Boolean.TRUE.equals(structuredParseResult.getReferenceOnly())
                            ? structuredParseResult.getReferenceConfidence() : null);
        }
        structuredContent.setParseMeta(buildParseMeta(structuredContent, sectionClassifyResult, structuredParseResult));
    }

    private ResumeParseMetaDTO buildParseMeta(
            ResumeStructuredContentDTO structuredContent,
            ResumeSectionClassifyResultDTO sectionClassifyResult,
            ResumeAiStructuredParseResultDTO structuredParseResult) {
        String aiStatus = resolveAiStatus(sectionClassifyResult, structuredParseResult);
        // Reference candidates and failed attempts are advisory/diagnostic, not canonical AI use.
        // Only an actually accepted normal AI result would be USED; the current parse path never
        // accepts one, but keeping this derived from status prevents fallback from masquerading as
        // a committed fact.
        boolean aiUsed = AI_STATUS_USED.equals(aiStatus);
        boolean fallbackOccurred = AI_STATUS_FALLBACK.equals(aiStatus);
        boolean cacheRelevant = AI_STATUS_USED.equals(aiStatus)
                || AI_STATUS_FALLBACK.equals(aiStatus)
                || AI_STATUS_REFERENCE_ONLY.equals(aiStatus);
        boolean cacheHit = cacheRelevant && (Boolean.TRUE.equals(sectionClassifyResult == null ? null : sectionClassifyResult.getCacheHit())
                || Boolean.TRUE.equals(structuredParseResult == null ? null : structuredParseResult.getCacheHit()));
        String cacheKey = firstNotBlank(
                sectionClassifyResult == null ? null : sectionClassifyResult.getCacheKey(),
                structuredParseResult == null ? null : structuredParseResult.getCacheKey());
        return ResumeParseMetaDTO.builder()
                .parseMode(structuredContent.getParseMode())
                .parserVersion(ResumeParseVersions.PARSER_VERSION)
                .aiStatus(aiStatus)
                .aiUsed(aiUsed)
                .aiSkippedReason(AI_STATUS_SKIPPED.equals(aiStatus)
                        ? firstNotBlank(skippedReason(sectionClassifyResult), skippedReason(structuredParseResult))
                        : null)
                .aiFallbackOccurred(fallbackOccurred)
                .aiFallbackReason(fallbackOccurred
                        ? joinReasons(fallbackReason(sectionClassifyResult), fallbackReason(structuredParseResult))
                        : null)
                .aiCacheHit(cacheHit)
                .aiCacheKeyDigest(cacheRelevant ? digest(cacheKey) : "")
                .aiSectionClassifyDurationMs(sectionClassifyResult == null ? null : sectionClassifyResult.getDurationMs())
                .aiStructuredParseDurationMs(structuredParseResult == null ? null : structuredParseResult.getDurationMs())
                .build();
    }

    private String resolveAiStatus(
            ResumeSectionClassifyResultDTO sectionClassifyResult,
            ResumeAiStructuredParseResultDTO structuredParseResult) {
        if (Boolean.TRUE.equals(structuredParseResult == null ? null : structuredParseResult.getReferenceOnly())
                && (AI_STATUS_FALLBACK.equals(status(structuredParseResult))
                || Boolean.TRUE.equals(structuredParseResult.getFallbackOccurred()))) {
            return AI_STATUS_FALLBACK;
        }
        if (Boolean.TRUE.equals(structuredParseResult == null ? null : structuredParseResult.getReferenceOnly())) {
            return AI_STATUS_SKIPPED.equals(status(structuredParseResult))
                    ? AI_STATUS_SKIPPED : AI_STATUS_REFERENCE_ONLY;
        }
        if (AI_STATUS_REFERENCE_ONLY.equals(status(structuredParseResult))) {
            return AI_STATUS_REFERENCE_ONLY;
        }
        if (AI_STATUS_FALLBACK.equals(status(sectionClassifyResult)) || AI_STATUS_FALLBACK.equals(status(structuredParseResult))
                || Boolean.TRUE.equals(sectionClassifyResult == null ? null : sectionClassifyResult.getFallbackOccurred())
                || Boolean.TRUE.equals(structuredParseResult == null ? null : structuredParseResult.getFallbackOccurred())
                || fallbackLike(sectionClassifyResult == null ? null : sectionClassifyResult.getFallbackReason())
                || fallbackLike(structuredParseResult == null ? null : structuredParseResult.getFallbackReason())) {
            return AI_STATUS_FALLBACK;
        }
        if (AI_STATUS_USED.equals(status(sectionClassifyResult)) || AI_STATUS_USED.equals(status(structuredParseResult))
                || Boolean.TRUE.equals(sectionClassifyResult == null ? null : sectionClassifyResult.getApplied())
                || Boolean.TRUE.equals(structuredParseResult == null ? null : structuredParseResult.getApplied())) {
            return AI_STATUS_USED;
        }
        if (AI_STATUS_SKIPPED.equals(status(sectionClassifyResult)) || AI_STATUS_SKIPPED.equals(status(structuredParseResult))) {
            return AI_STATUS_SKIPPED;
        }
        return AI_STATUS_DISABLED;
    }

    private boolean fallbackLike(String reason) {
        return StringUtils.hasText(reason)
                && reason.matches(".*(?:失败|JSON|超时|timeout|未返回|结果为空|校验).*");
    }

    private String status(ResumeSectionClassifyResultDTO result) {
        return result == null ? null : result.getAiStatus();
    }

    private String status(ResumeAiStructuredParseResultDTO result) {
        return result == null ? null : result.getAiStatus();
    }

    private String skippedReason(ResumeSectionClassifyResultDTO result) {
        return result == null ? null : result.getSkippedReason();
    }

    private String skippedReason(ResumeAiStructuredParseResultDTO result) {
        return result == null ? null : result.getSkippedReason();
    }

    private String fallbackReason(ResumeSectionClassifyResultDTO result) {
        if (result == null
                || (!Boolean.TRUE.equals(result.getFallbackOccurred())
                && !AI_STATUS_FALLBACK.equals(result.getAiStatus())
                && !fallbackLike(result.getFallbackReason()))) {
            return null;
        }
        return result.getFallbackReason();
    }

    private String fallbackReason(ResumeAiStructuredParseResultDTO result) {
        if (result == null
                || (!Boolean.TRUE.equals(result.getFallbackOccurred())
                && !AI_STATUS_FALLBACK.equals(result.getAiStatus())
                && !fallbackLike(result.getFallbackReason()))) {
            return null;
        }
        return result.getFallbackReason();
    }

    private String joinReasons(String first, String second) {
        List<String> reasons = new ArrayList<>();
        if (StringUtils.hasText(first)) {
            reasons.add(first);
        }
        if (StringUtils.hasText(second) && !reasons.contains(second)) {
            reasons.add(second);
        }
        return reasons.isEmpty() ? null : String.join("；", reasons);
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private void applyExtractionMetadata(
            ResumeStructuredContentDTO structuredContent,
            ResumeTextExtractionResult extractionResult) {
        if (structuredContent == null || extractionResult == null) {
            return;
        }
        ResumeParseMetaDTO parseMeta = structuredContent.getParseMeta();
        if (parseMeta == null) {
            parseMeta = ResumeParseMetaDTO.builder().build();
            structuredContent.setParseMeta(parseMeta);
        }
        parseMeta.setPageCount(Math.max(0, extractionResult.pageCount()));
        parseMeta.setPageCountKnown(extractionResult.pageCountKnown());
    }

    private void applyParseDurations(
            ResumeStructuredContentDTO structuredContent,
            long textExtractDurationMs,
            long ruleParseDurationMs,
            long totalParseDurationMs) {
        if (structuredContent == null) {
            return;
        }
        structuredContent.setTextExtractDurationMs(textExtractDurationMs);
        structuredContent.setRuleParseDurationMs(ruleParseDurationMs);
        structuredContent.setTotalParseDurationMs(totalParseDurationMs);
        ResumeParseMetaDTO parseMeta = structuredContent.getParseMeta();
        if (parseMeta == null) {
            parseMeta = ResumeParseMetaDTO.builder().build();
            structuredContent.setParseMeta(parseMeta);
        }
        parseMeta.setParseMode(structuredContent.getParseMode());
        parseMeta.setParserVersion(ResumeParseVersions.PARSER_VERSION);
        parseMeta.setRuleParseDurationMs(ruleParseDurationMs);
        parseMeta.setTotalParseDurationMs(totalParseDurationMs);
    }

    private String digest(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String hex = HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, Math.min(16, hex.length()));
        } catch (NoSuchAlgorithmException exception) {
            return "";
        }
    }

    private boolean isStructuredParseRequested(ResumeParseOptionsDTO options, ResumeParseMode parseMode) {
        if (parseMode == ResumeParseMode.FAST) {
            return false;
        }
        return options == null
                ? parseMode == ResumeParseMode.ACCURATE || defaultAiStructuredParseEnabled
                : options.getAiStructuredParseEnabled() == null
                        ? parseMode == ResumeParseMode.ACCURATE || defaultAiStructuredParseEnabled
                        : Boolean.TRUE.equals(options.getAiStructuredParseEnabled());
    }

    private void applyBlockParseContext(List<ResumeBlockDTO> blocks, String parseMode, String resumeTypeHint) {
        if (blocks == null) {
            return;
        }
        for (ResumeBlockDTO block : blocks) {
            if (block == null) {
                continue;
            }
            block.setParseMode(parseMode);
            if (StringUtils.hasText(resumeTypeHint)) {
                block.setResumeTypeHint(resumeTypeHint);
            }
        }
    }

    private ResumeParseMode resolveParseMode(ResumeParseOptionsDTO options) {
        if (options != null && StringUtils.hasText(options.getParseMode())) {
            return ResumeParseMode.from(options.getParseMode());
        }
        return ResumeParseMode.from(resumeParseProperties == null ? null : resumeParseProperties.getMode());
    }

    private long elapsedMs(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private record ParseClaim(Long generation, String token) {
    }

    private static final class StaleParseAttemptException extends RuntimeException {
        private StaleParseAttemptException(Long resumeId) {
            super("stale parse attempt for resume " + resumeId);
        }
    }

    private static final class ParseSourceRollbackException extends RuntimeException {
        private ParseSourceRollbackException(Long sourceVersionId) {
            super("could not roll back orphan SOURCE version " + sourceVersionId);
        }
    }

    /** 解析完成时的 canonical 交付结果；文档随后唯一物化到 SOURCE structured_content。 */
    private record CanonicalQualitySnapshot(
            String qualityStatus,
            String qualityIssuesJson,
            String unresolvedItemsJson,
            String canonicalDocumentJson,
            boolean hardInvariantPass) {

        static CanonicalQualitySnapshot failed() {
            return new CanonicalQualitySnapshot(ResumeQualityStatus.QUALITY_FAILED, null, null, null, false);
        }

        static CanonicalQualitySnapshot needsReview(List<String> issues) {
            try {
                return new CanonicalQualitySnapshot(
                        ResumeQualityStatus.QUALITY_NEEDS_REVIEW,
                        new ObjectMapper().writeValueAsString(issues == null ? List.of() : issues),
                        null,
                        null,
                        false);
            } catch (JsonProcessingException exception) {
                return failed();
            }
        }
    }

    private CanonicalQualitySnapshot buildCanonicalSnapshot(
            ResumeStructuredContentDTO structuredContent,
            ResumeStructureHealthEvaluation structureHealth) {
        // A source-backed draft is useful even when it is not yet deliverable. In particular,
        // NEEDS_REVIEW must expose the exact deterministic document that the review endpoint can
        // edit; keeping it only in structured_json made every candidate without READY quality
        // impossible to resolve. A missing raw source is still a hard provenance boundary.
        if (!hasSourceMaterial(structuredContent)) {
            return CanonicalQualitySnapshot.failed();
        }
        ResumeCanonicalDocumentService.BuildResult buildResult =
                resumeCanonicalDocumentService.build(structuredContent);
        if (buildResult == null) {
            List<String> issues = structureHealth == null
                    ? List.of("STRUCTURE_HEALTH_UNAVAILABLE", "CANONICAL_BUILD_UNAVAILABLE")
                    : structureHealth.hardInvariantViolations().stream()
                            .map(value -> "STRUCTURE_HEALTH:" + value)
                            .toList();
            return CanonicalQualitySnapshot.needsReview(issues);
        }
        List<ResumeUnresolvedItemDTO> unresolvedItems =
                buildResult.unresolvedItems() == null ? List.of() : buildResult.unresolvedItems();
        ResumeDocumentQualityValidator.ValidationResult validation =
                resumeDocumentQualityValidator.validate(buildResult.document(), unresolvedItems);
        try {
            boolean structureHealthPass = structureHealth != null && structureHealth.hardInvariantPass();
            String qualityStatus = validation.qualityStatus();
            boolean documentPresent = buildResult.document() != null;
            boolean noUnresolvedItems = unresolvedItems.isEmpty();
            boolean ready = structureHealthPass
                    && ResumeQualityStatus.QUALITY_READY.equals(qualityStatus)
                    && documentPresent
                    && noUnresolvedItems;
            // The validator contract already derives READY from unresolvedItems. Keep this
            // normalization here so a custom/legacy validator cannot create a READY row without
            // an exhaustive review sidecar and a materializable document. Health failures remain
            // reviewable drafts, but can never become a confirmed SOURCE.
            if (!structureHealthPass
                    || (ResumeQualityStatus.QUALITY_READY.equals(qualityStatus)
                    && (!documentPresent || !noUnresolvedItems))) {
                qualityStatus = ResumeQualityStatus.QUALITY_NEEDS_REVIEW;
            }
            List<Object> issues = new ArrayList<>();
            if (structureHealth == null) {
                issues.add("STRUCTURE_HEALTH_UNAVAILABLE");
            } else {
                structureHealth.hardInvariantViolations().stream()
                        .map(value -> "STRUCTURE_HEALTH:" + value)
                        .forEach(issues::add);
            }
            if (validation.issues() != null) {
                issues.addAll(validation.issues());
            }
            // PENDING is deliberate here: the bytes are canonical and source-backed, but the
            // delivery gate still requires explicit review before any task/export can consume
            // them. A FAILED quality result remains non-materializable.
            boolean materializableDraft = documentPresent
                    && (ResumeQualityStatus.QUALITY_READY.equals(qualityStatus)
                    || ResumeQualityStatus.QUALITY_NEEDS_REVIEW.equals(qualityStatus));
            return new CanonicalQualitySnapshot(
                    qualityStatus,
                    objectMapper.writeValueAsString(issues),
                    objectMapper.writeValueAsString(unresolvedItems),
                    materializableDraft ? objectMapper.writeValueAsString(buildResult.document()) : null,
                    structureHealthPass);
        } catch (JsonProcessingException exception) {
            log.warn("Canonical document snapshot serialize failed, fail closed to NEEDS_REVIEW");
            return CanonicalQualitySnapshot.failed();
        }
    }

    private boolean hasSourceMaterial(ResumeStructuredContentDTO content) {
        if (content == null) {
            return false;
        }
        if (content.getRawText() != null && !content.getRawText().isBlank()) {
            return true;
        }
        if (content.getIndexedLines() != null && content.getIndexedLines().stream()
                .anyMatch(line -> line != null && line.getText() != null && !line.getText().isBlank())) {
            return true;
        }
        if (content.getRawSections() != null && content.getRawSections().stream()
                .filter(section -> section != null && section.getBlocks() != null)
                .flatMap(section -> section.getBlocks().stream())
                .anyMatch(block -> block != null && block.getText() != null && !block.getText().isBlank())) {
            return true;
        }
        // A historical text-section row is evidence only when its occurrence identity was
        // retained explicitly. Display IDs alone are not a provenance boundary.
        return content.getSections() != null && content.getSections().stream()
                .filter(section -> section != null && section.getBlocks() != null)
                .flatMap(section -> section.getBlocks().stream())
                .anyMatch(block -> block != null && block.getText() != null && !block.getText().isBlank()
                        && block.getSourceOccurrenceIds() != null
                        && block.getSourceOccurrenceIds().stream().anyMatch(this::hasUsableOccurrenceId));
    }

    private boolean hasUsableOccurrenceId(String value) {
        return value != null && !value.isBlank()
                && !"null".equalsIgnoreCase(value.strip())
                && !"undefined".equalsIgnoreCase(value.strip());
    }

    private boolean hasNoUnresolvedItems(String unresolvedItemsJson) {
        if (unresolvedItemsJson == null || unresolvedItemsJson.isBlank()) {
            return false;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(unresolvedItemsJson);
            return root != null && root.isArray() && root.isEmpty();
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    private String serializeParseQualityWarnings(ResumeParseQualityResultDTO parseQualityResult) {
        if (parseQualityResult == null || parseQualityResult.getWarnings() == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(parseQualityResult.getWarnings());
        } catch (JsonProcessingException exception) {
            return "[\"PARSE_QUALITY_WARNINGS_SERIALIZE_FAILED\"]";
        }
    }

    private String normalizeErrorMessage(RuntimeException exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "简历解析失败";
        }
        return exception.getMessage();
    }

    private String truncateErrorMessage(String errorMessage) {
        String sanitized = LogSanitizer.sanitize(errorMessage);
        if (sanitized == null || sanitized.length() <= 1000) {
            return sanitized;
        }
        return sanitized.substring(0, 1000);
    }

    private String extractFileType(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(400, "文件名不能为空");
        }

        int index = filename.lastIndexOf('.');
        if (index < 0 || index == filename.length() - 1) {
            throw new BusinessException(400, "文件缺少扩展名");
        }

        return filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }
}
