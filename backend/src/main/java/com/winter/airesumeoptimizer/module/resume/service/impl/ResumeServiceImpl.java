package com.winter.airesumeoptimizer.module.resume.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.common.logging.LogSanitizer;
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
import com.winter.airesumeoptimizer.module.job.entity.JobMatchResult;
import com.winter.airesumeoptimizer.module.job.mapper.JobMatchResultMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import com.winter.airesumeoptimizer.module.resume.config.ResumeParseProperties;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeAiStructuredParseResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseMetaDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseMode;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseOptionsDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeParseQualityResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeBlockDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeDisplayModelDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeIndexedLineDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSectionClassificationDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeSectionClassifyResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextCleanResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextQualityResultDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeTextSectionDTO;
import com.winter.airesumeoptimizer.module.resume.dto.SourceSectionConfidence;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAiSectionClassifier;
import com.winter.airesumeoptimizer.module.resume.service.ResumeAiStructuredParser;
import com.winter.airesumeoptimizer.module.resume.service.ResumeBlockBuilder;
import com.winter.airesumeoptimizer.module.resume.service.ResumeBlockReorderService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeDisplayModelService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeLineIndexer;
import com.winter.airesumeoptimizer.module.resume.service.ResumeParseQualityCheckService;
import com.winter.airesumeoptimizer.module.resume.service.ResumePointerPostProcessor;
import com.winter.airesumeoptimizer.module.resume.service.ResumeStructureParseService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeService;
import com.winter.airesumeoptimizer.module.resume.service.ResumeTextCleanService;
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
    private static final String AI_STATUS_DISABLED = "DISABLED";
    private static final double MEDIUM_SOURCE_AI_OVERRIDE_THRESHOLD = 0.85;
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
    private final ResumeAiAnalysisMapper resumeAiAnalysisMapper;
    private final JobMatchResultMapper jobMatchResultMapper;
    private final AiJobMatchResultMapper aiJobMatchResultMapper;
    private final AiResumeSuggestionMapper aiResumeSuggestionMapper;
    private final AiRewriteSuggestionMapper aiRewriteSuggestionMapper;
    private final ResumeEmbeddingMapper resumeEmbeddingMapper;
    private final FileStorageService fileStorageService;
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
    private final ResumeParseProperties resumeParseProperties;
    private final ObjectMapper objectMapper;
    private final long maxFileSize;
    private final boolean defaultAiStructuredParseEnabled;

    public ResumeServiceImpl(
            ResumeMapper resumeMapper,
            ResumeParseResultMapper resumeParseResultMapper,
            ResumeAiAnalysisMapper resumeAiAnalysisMapper,
            JobMatchResultMapper jobMatchResultMapper,
            AiJobMatchResultMapper aiJobMatchResultMapper,
            AiResumeSuggestionMapper aiResumeSuggestionMapper,
            AiRewriteSuggestionMapper aiRewriteSuggestionMapper,
            ResumeEmbeddingMapper resumeEmbeddingMapper,
            FileStorageService fileStorageService,
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
            ResumeParseProperties resumeParseProperties,
            ObjectMapper objectMapper,
            @Value("${app.resume.upload.max-file-size-bytes:10485760}") long maxFileSize,
            @Value("${app.resume.parse.ai-structured-parse-enabled:false}") boolean defaultAiStructuredParseEnabled) {
        this.resumeMapper = resumeMapper;
        this.resumeParseResultMapper = resumeParseResultMapper;
        this.resumeAiAnalysisMapper = resumeAiAnalysisMapper;
        this.jobMatchResultMapper = jobMatchResultMapper;
        this.aiJobMatchResultMapper = aiJobMatchResultMapper;
        this.aiResumeSuggestionMapper = aiResumeSuggestionMapper;
        this.aiRewriteSuggestionMapper = aiRewriteSuggestionMapper;
        this.resumeEmbeddingMapper = resumeEmbeddingMapper;
        this.fileStorageService = fileStorageService;
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
        this.resumeParseProperties = resumeParseProperties;
        this.objectMapper = objectMapper;
        this.maxFileSize = maxFileSize;
        this.defaultAiStructuredParseEnabled = defaultAiStructuredParseEnabled;
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
            fileStorageService.delete(storedFile.objectKey());
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
                .fileType(resume.getFileType())
                .fileSize(resume.getFileSize())
                .uploadStatus(resume.getUploadStatus())
                .createdAt(resume.getCreatedAt())
                .build();
    }

    @Override
    public List<ResumeListVO> listByUser(Long userId) {
        validateUserId(userId);

        return resumeMapper.selectList(new LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, userId)
                        .orderByDesc(Resume::getCreatedAt))
                .stream()
                .map(this::toListVO)
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
    public ResumeParseResultVO parse(Long userId, Long resumeId) {
        return parse(userId, resumeId, null);
    }

    @Override
    @Transactional
    public ResumeParseResultVO parse(Long userId, Long resumeId, ResumeParseOptionsDTO options) {
        long totalStartedAt = System.nanoTime();
        Resume resume = getOwnedResume(userId, resumeId);
        log.info("Resume parse started: userId={}, resumeId={}, fileType={}",
                userId,
                resume.getId(),
                resume.getFileType());

        String extractedText = null;
        ResumeTextQualityResultDTO qualityResult = null;
        ResumeTextCleanResultDTO cleanResult = null;
        ResumeParseQualityResultDTO parseQualityResult = null;
        long textExtractDurationMs = 0;
        long ruleParseDurationMs = 0;
        try {
            long textExtractStartedAt = System.nanoTime();
            extractedText = resumeTextExtractionService.extractText(resume.getObjectKey(), resume.getFileType());
            textExtractDurationMs = elapsedMs(textExtractStartedAt);
            qualityResult = resumeTextQualityCheckService.check(extractedText, resume.getFileType());
            if (qualityResult.failed()) {
                ResumeParseResult parseResult = saveParseResult(
                        resume.getId(),
                        PARSE_STATUS_FAILED,
                        extractedText,
                        null,
                        null,
                        null,
                        qualityResult.getMessage(),
                        qualityResult,
                        null);
                log.warn("Resume parse stopped by text quality: userId={}, resumeId={}, qualityStatus={}, issues={}",
                        userId,
                        resume.getId(),
                        qualityResult.getStatus(),
                        qualityResult.getIssues());
                return toParseResultVO(parseResult);
            }
            cleanResult = resumeTextCleanService.cleanAndSplitSections(extractedText);
            List<ResumeBlockDTO> blocks = resumeBlockReorderService.reorder(resumeBlockBuilder.build(cleanResult));
            ResumeParseMode parseMode = resolveParseMode(options);
            applyBlockParseContext(blocks, parseMode.name(), null);
            ResumeSectionClassifyResultDTO sectionClassifyResult = resumeAiSectionClassifier.classify(
                    resume.getId(),
                    blocks,
                    resolveSectionClassifyEnabled(options, parseMode));
            if (sectionClassifyResult == null) {
                sectionClassifyResult = ResumeSectionClassifyResultDTO.builder()
                        .aiEnabled(false)
                        .applied(false)
                        .aiInvoked(false)
                        .aiStatus(AI_STATUS_FALLBACK)
                        .fallbackOccurred(true)
                        .fallbackReason("AI 章节归类未返回结果")
                        .classifications(List.of())
                        .build();
            }
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
            Boolean structuredParseEnabled = resolveStructuredParseEnabled(options, parseMode, sectionClassifyResult);
            ResumeAiStructuredParseResultDTO structuredParseResult = resumeAiStructuredParser.parse(
                    blocks,
                    structuredContent,
                    List.of(),
                    structuredParseEnabled);
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
            if (structuredParseResult.shouldApply()) {
                structuredContent = structuredParseResult.getStructuredContent();
            }
            structuredContent.setParseMode(parseMode.name());
            log.info("Resume AI structured parse checked: userId={}, resumeId={}, enabled={}, applied={}, fallbackReason={}",
                    userId,
                    resume.getId(),
                    structuredParseResult.getAiEnabled(),
                    structuredParseResult.getApplied(),
                    LogSanitizer.sanitize(structuredParseResult.getFallbackReason()));
            applyAiParseMetadata(structuredContent, sectionClassifyResult, structuredParseResult);
            applyParseDurations(structuredContent, textExtractDurationMs, ruleParseDurationMs, elapsedMs(totalStartedAt));
            ResumeStructuredResultAssembler.enrich(structuredContent);
            List<ResumeIndexedLineDTO> indexedLines = resumeLineIndexer.index(structuredContent.getRawSections());
            structuredContent.setIndexedLines(indexedLines);
            resumePointerPostProcessor.attachSourceRefs(structuredContent, indexedLines);
            parseQualityResult = resumeParseQualityCheckService.check(structuredContent, cleanResult, qualityResult);
            mergeStructuredQualityWarnings(structuredContent, structuredParseResult.getQualityWarnings(), parseQualityResult.getWarnings());
            applyDisplayModels(resume.getId(), parseMode, structuredContent);
            String structuredJson = objectMapper.writeValueAsString(structuredContent);
            String parseStatus = parseQualityResult.failed() ? PARSE_STATUS_FAILED : PARSE_STATUS_SUCCESS;
            ResumeParseResult parseResult = saveParseResult(
                    resume.getId(),
                    parseStatus,
                    extractedText,
                    cleanResult.getCleanedText(),
                    serializeSections(cleanResult),
                    structuredJson,
                    parseQualityResult.failed() ? parseQualityResult.getMessage() : null,
                    qualityResult,
                    parseQualityResult);
            log.info("Resume parse finished: userId={}, resumeId={}, parseStatus={}, extractedTextLength={}, cleanedTextLength={}, sectionCount={}, textQualityStatus={}, parseQualityStatus={}, parseQualityWarnings={}",
                    userId,
                    resume.getId(),
                    parseStatus,
                    extractedText == null ? 0 : extractedText.length(),
                    cleanResult.getCleanedText() == null ? 0 : cleanResult.getCleanedText().length(),
                    cleanResult.getSections() == null ? 0 : cleanResult.getSections().size(),
                    qualityResult.getStatus(),
                    parseQualityResult.getStatus(),
                    parseQualityResult.getWarnings());
            return toParseResultVO(parseResult);
        } catch (JsonProcessingException exception) {
            ResumeParseResult parseResult = saveParseResult(
                    resume.getId(),
                    PARSE_STATUS_FAILED,
                    extractedText,
                    cleanResult == null ? null : cleanResult.getCleanedText(),
                    serializeSections(cleanResult),
                    null,
                    "结构化解析结果序列化失败",
                    qualityResult,
                    parseQualityResult);
            log.warn("Resume parse failed: userId={}, resumeId={}, reason={}",
                    userId,
                    resume.getId(),
                    LogSanitizer.sanitize("结构化解析结果序列化失败"),
                    exception);
            return toParseResultVO(parseResult);
        } catch (RuntimeException exception) {
            String errorMessage = normalizeErrorMessage(exception);
            ResumeParseResult parseResult = saveParseResult(
                    resume.getId(),
                    PARSE_STATUS_FAILED,
                    extractedText,
                    cleanResult == null ? null : cleanResult.getCleanedText(),
                    serializeSections(cleanResult),
                    null,
                    errorMessage,
                    qualityResult,
                    parseQualityResult);
            log.warn("Resume parse failed: userId={}, resumeId={}, reason={}",
                    userId,
                    resume.getId(),
                    LogSanitizer.sanitize(errorMessage),
                    exception);
            return toParseResultVO(parseResult);
        }
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
        Resume resume = getOwnedResume(userId, resumeId);
        deleteResumeChildren(resume.getId());
        resumeMapper.deleteById(resume.getId());
        fileStorageService.delete(resume.getObjectKey());
        log.info("Resume deleted: userId={}, resumeId={}", userId, resume.getId());
    }

    private void deleteResumeChildren(Long resumeId) {
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
        return resume;
    }

    private ResumeParseResult saveParseResult(
            Long resumeId,
            String parseStatus,
            String extractedText,
            String cleanedText,
            String sectionResult,
            String structuredJson,
            String errorMessage,
            ResumeTextQualityResultDTO qualityResult,
            ResumeParseQualityResultDTO parseQualityResult) {
        LocalDateTime now = LocalDateTime.now();
        ResumeParseResult parseResult = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, resumeId));

        if (parseResult == null) {
            parseResult = new ResumeParseResult();
            parseResult.setResumeId(resumeId);
            parseResult.setCreatedAt(now);
        }

        parseResult.setParseStatus(parseStatus);
        parseResult.setExtractedText(extractedText);
        parseResult.setCleanedText(cleanedText);
        parseResult.setSectionResult(sectionResult);
        parseResult.setStructuredJson(structuredJson);
        parseResult.setErrorMessage(truncateErrorMessage(errorMessage));
        parseResult.setTextQualityStatus(qualityResult == null ? null : qualityResult.getStatus());
        parseResult.setTextQualityIssues(serializeQualityIssues(qualityResult));
        parseResult.setTextQualityMessage(truncateErrorMessage(qualityResult == null ? null : qualityResult.getMessage()));
        parseResult.setParseQualityStatus(parseQualityResult == null ? null : parseQualityResult.getStatus());
        parseResult.setParseQualityWarnings(serializeParseQualityWarnings(parseQualityResult));
        parseResult.setParseQualityMessage(truncateErrorMessage(parseQualityResult == null ? null : parseQualityResult.getMessage()));
        parseResult.setParseQualityScore(parseQualityResult == null ? null : parseQualityResult.getScore());
        parseResult.setUpdatedAt(now);

        if (parseResult.getId() == null) {
            resumeParseResultMapper.insert(parseResult);
        } else {
            updateParseResultIncludingNulls(parseResult);
        }
        return parseResult;
    }

    private void updateParseResultIncludingNulls(ResumeParseResult parseResult) {
        resumeParseResultMapper.update(null, new LambdaUpdateWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getId, parseResult.getId())
                .set(ResumeParseResult::getParseStatus, parseResult.getParseStatus())
                .set(ResumeParseResult::getExtractedText, parseResult.getExtractedText())
                .set(ResumeParseResult::getCleanedText, parseResult.getCleanedText())
                .set(ResumeParseResult::getSectionResult, parseResult.getSectionResult())
                .set(ResumeParseResult::getStructuredJson, parseResult.getStructuredJson())
                .set(ResumeParseResult::getErrorMessage, parseResult.getErrorMessage())
                .set(ResumeParseResult::getTextQualityStatus, parseResult.getTextQualityStatus())
                .set(ResumeParseResult::getTextQualityIssues, parseResult.getTextQualityIssues())
                .set(ResumeParseResult::getTextQualityMessage, parseResult.getTextQualityMessage())
                .set(ResumeParseResult::getParseQualityStatus, parseResult.getParseQualityStatus())
                .set(ResumeParseResult::getParseQualityWarnings, parseResult.getParseQualityWarnings())
                .set(ResumeParseResult::getParseQualityMessage, parseResult.getParseQualityMessage())
                .set(ResumeParseResult::getParseQualityScore, parseResult.getParseQualityScore())
                .set(ResumeParseResult::getUpdatedAt, parseResult.getUpdatedAt()));
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

    private ResumeListVO toListVO(Resume resume) {
        return ResumeListVO.builder()
                .id(resume.getId())
                .originalFilename(resume.getOriginalFilename())
                .fileType(resume.getFileType())
                .fileSize(resume.getFileSize())
                .uploadStatus(resume.getUploadStatus())
                .createdAt(resume.getCreatedAt())
                .build();
    }

    private ResumeDetailVO toDetailVO(Resume resume) {
        return ResumeDetailVO.builder()
                .id(resume.getId())
                .originalFilename(resume.getOriginalFilename())
                .fileType(resume.getFileType())
                .fileSize(resume.getFileSize())
                .uploadStatus(resume.getUploadStatus())
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
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
                .parseQualityStatus(parseResult.getParseQualityStatus())
                .parseQualityWarnings(parseResult.getParseQualityWarnings())
                .parseQualityMessage(parseResult.getParseQualityMessage())
                .parseQualityScore(parseResult.getParseQualityScore())
                .updatedAt(parseResult.getUpdatedAt())
                .build();
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
                        .index(block.getIndex())
                        .originalIndex(block.getOriginalIndex())
                        .displayOrder(block.getDisplayOrder())
                        .text(block.getText())
                        .prevText(block.getPrevText())
                        .nextText(block.getNextText())
                        .sourceType(block.getSourceType())
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
                        .heading("AI 章节归类：" + entry.getKey())
                        .lines(entry.getValue().stream()
                                .map(ResumeBlockDTO::getText)
                                .toList())
                        .blocks(entry.getValue())
                        .build())
                .toList();
    }

    private String firstSection(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return "OTHERS";
    }

    private SectionDecision decideFinalSection(
            String sourceSection,
            SourceSectionConfidence sourceConfidence,
            ResumeSectionClassificationDTO classification) {
        String aiSection = classification == null ? null : normalizeSection(classification.getSection());
        double aiConfidence = classification == null || classification.getConfidence() == null
                ? 0
                : classification.getConfidence();
        if (sourceConfidence == SourceSectionConfidence.HIGH && !"OTHERS".equals(sourceSection)) {
            return new SectionDecision(sourceSection, "RULE_SOURCE_SECTION");
        }
        if (sourceConfidence == SourceSectionConfidence.MEDIUM
                && aiSection != null
                && !sourceSection.equals(aiSection)
                && aiConfidence >= MEDIUM_SOURCE_AI_OVERRIDE_THRESHOLD) {
            return new SectionDecision(aiSection, "AI_OVERRIDE");
        }
        if (sourceConfidence == SourceSectionConfidence.LOW && aiSection != null) {
            return new SectionDecision(aiSection, "AI_OVERRIDE");
        }
        return new SectionDecision(firstSection(sourceSection, aiSection), "RULE_FALLBACK");
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

    private void applyDisplayModels(Long resumeId, ResumeParseMode parseMode, ResumeStructuredContentDTO structuredContent) {
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
            ResumeDisplayModelDTO cachedAiDisplayModel = resumeDisplayModelService.getCachedAiDisplayModel(resumeId, structuredContent);
            structuredContent.setAiDisplayModel(cachedAiDisplayModel);
            structuredContent.setDisplayModel(cachedAiDisplayModel == null ? ruleDisplayModel : cachedAiDisplayModel);
        } catch (RuntimeException exception) {
            structuredContent.setDisplayModel(structuredContent.getRuleDisplayModel());
            addQualityWarning(structuredContent, "DISPLAY_MODEL_FAILED");
            log.warn("Resume display model skipped: resumeId={}, exceptionType={}, reason={}",
                    resumeId,
                    exception.getClass().getSimpleName(),
                    LogSanitizer.sanitize(normalizeErrorMessage(exception)));
        }
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
        }
        structuredContent.setParseMeta(buildParseMeta(structuredContent, sectionClassifyResult, structuredParseResult));
    }

    private ResumeParseMetaDTO buildParseMeta(
            ResumeStructuredContentDTO structuredContent,
            ResumeSectionClassifyResultDTO sectionClassifyResult,
            ResumeAiStructuredParseResultDTO structuredParseResult) {
        String aiStatus = resolveAiStatus(sectionClassifyResult, structuredParseResult);
        boolean aiUsed = AI_STATUS_USED.equals(aiStatus) || AI_STATUS_FALLBACK.equals(aiStatus);
        boolean fallbackOccurred = AI_STATUS_FALLBACK.equals(aiStatus);
        boolean cacheRelevant = AI_STATUS_USED.equals(aiStatus) || AI_STATUS_FALLBACK.equals(aiStatus);
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

    private Boolean resolveStructuredParseEnabled(
            ResumeParseOptionsDTO options,
            ResumeParseMode parseMode,
            ResumeSectionClassifyResultDTO sectionClassifyResult) {
        if (parseMode == ResumeParseMode.FAST) {
            return false;
        }
        boolean requested = options == null
                ? parseMode == ResumeParseMode.ACCURATE || defaultAiStructuredParseEnabled
                : options.getAiStructuredParseEnabled() == null
                        ? parseMode == ResumeParseMode.ACCURATE || defaultAiStructuredParseEnabled
                        : Boolean.TRUE.equals(options.getAiStructuredParseEnabled());
        if (!requested) {
            return false;
        }
        if (sectionClassifyResult == null || !sectionClassifyResult.shouldApply()) {
            return false;
        }
        return true;
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

    private Boolean resolveSectionClassifyEnabled(ResumeParseOptionsDTO options, ResumeParseMode parseMode) {
        if (parseMode == ResumeParseMode.FAST) {
            return false;
        }
        if (options != null && options.getAiSectionClassifyEnabled() != null) {
            return Boolean.TRUE.equals(options.getAiSectionClassifyEnabled());
        }
        if (parseMode == ResumeParseMode.ACCURATE) {
            return true;
        }
        return null;
    }

    private long elapsedMs(long startedAt) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
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
