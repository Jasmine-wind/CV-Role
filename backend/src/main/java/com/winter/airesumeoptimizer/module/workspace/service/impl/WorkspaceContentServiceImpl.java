package com.winter.airesumeoptimizer.module.workspace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.winter.airesumeoptimizer.common.exception.BusinessException;
import com.winter.airesumeoptimizer.infra.storage.FileStorageService;
import com.winter.airesumeoptimizer.module.optimization.entity.JobTarget;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.JobTargetMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.OptimizationTaskMapper;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.resume.entity.Resume;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeMapper;
import com.winter.airesumeoptimizer.module.resume.service.ResumeCanonicalDocumentService;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBulletDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentEntryDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceContentSaveRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceSourceOmissionRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceSourceRestoreRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.enums.WorkspaceSourceMappingStatus;
import com.winter.airesumeoptimizer.module.workspace.enums.WorkspaceSourceRestoreScope;
import com.winter.airesumeoptimizer.module.workspace.service.ResumeDocumentConverter;
import com.winter.airesumeoptimizer.module.workspace.service.WorkspaceContentService;
import com.winter.airesumeoptimizer.module.workspace.service.WorkspaceSourceReferenceAssembler;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentSaveResultVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourcePdfVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceSourceReferenceVO.SourceBlock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceContentServiceImpl implements WorkspaceContentService {

    private static final String TASK_STATUS_SUCCESS = "SUCCESS";
    private static final String VERSION_SOURCE = "SOURCE";
    private static final String VERSION_TARGETED = "TARGETED";
    private static final String CONTENT_READY = "READY";
    private static final long PRISTINE_REVISION = 0L;

    private final OptimizationTaskMapper optimizationTaskMapper;
    private final ResumeVersionMapper resumeVersionMapper;
    private final JobTargetMapper jobTargetMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeDocumentConverter resumeDocumentConverter;
    private final ResumeCanonicalDocumentService resumeCanonicalDocumentService;
    private final WorkspaceSourceReferenceAssembler sourceReferenceAssembler;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public WorkspaceContentServiceImpl(
            OptimizationTaskMapper optimizationTaskMapper,
            ResumeVersionMapper resumeVersionMapper,
            JobTargetMapper jobTargetMapper,
            ResumeMapper resumeMapper,
            ResumeDocumentConverter resumeDocumentConverter,
            ResumeCanonicalDocumentService resumeCanonicalDocumentService,
            WorkspaceSourceReferenceAssembler sourceReferenceAssembler,
            FileStorageService fileStorageService,
            ObjectMapper objectMapper) {
        this.optimizationTaskMapper = optimizationTaskMapper;
        this.resumeVersionMapper = resumeVersionMapper;
        this.jobTargetMapper = jobTargetMapper;
        this.resumeMapper = resumeMapper;
        this.resumeDocumentConverter = resumeDocumentConverter;
        this.resumeCanonicalDocumentService = resumeCanonicalDocumentService;
        this.sourceReferenceAssembler = sourceReferenceAssembler;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    @Override
    public WorkspaceContentVO getContent(Long userId, Long optimizationTaskId) {
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        ResumeVersion target = context.target();
        long revision = revisionOf(target);

        ResumeDocumentDTO document;
        if (revision > PRISTINE_REVISION) {
            document = readPersistedDocument(target);
        } else {
            // revision 0：TARGET 仍是分析时冻结内容的原始副本，按冻结快照确定性地生成编辑文档。
            document = documentFromFrozenSnapshot(resolveFrozenSnapshot(context));
        }
        return WorkspaceContentVO.builder()
                .optimizationTaskId(context.task().getId())
                .revision(revision)
                .document(document)
                .build();
    }

    @Override
    public WorkspaceSourceReferenceVO getSourceReference(Long userId, Long optimizationTaskId) {
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        long revision = revisionOf(context.target());
        ResumeDocumentDTO source = documentFromFrozenSnapshot(resolveFrozenSnapshot(context));
        ResumeDocumentDTO target = revision > PRISTINE_REVISION
                ? readPersistedDocument(context.target()) : source;
        Resume resume = context.resume();
        return sourceReferenceAssembler.assemble(
                context.task().getId(), context.source().getId(), context.target().getId(), revision,
                resume.getOriginalFilename(), isPdf(resume), source, target);
    }

    @Override
    public WorkspaceSourcePdfVO getSourcePdf(Long userId, Long optimizationTaskId) {
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        Resume resume = context.resume();
        if (!isPdf(resume)) {
            throw new BusinessException(409, "原始文件不是 PDF，请使用原文视图核对");
        }
        if (resume.getObjectKey() == null || resume.getObjectKey().isBlank()) {
            throw new BusinessException(404, "原始文件不存在");
        }
        byte[] bytes;
        try {
            bytes = fileStorageService.loadAsBytes(resume.getObjectKey());
        } catch (RuntimeException exception) {
            throw new BusinessException(500, "原始文件读取失败，请稍后重试");
        }
        if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                || bytes[3] != 'F' || bytes[4] != '-') {
            throw new BusinessException(500, "原始 PDF 文件格式不正确");
        }
        return new WorkspaceSourcePdfVO(bytes, resume.getOriginalFilename());
    }

    @Override
    public WorkspaceContentVO getPersistedContentForRender(Long userId, Long optimizationTaskId) {
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        ResumeVersion target = context.target();
        long revision = revisionOf(target);
        if (revision == PRISTINE_REVISION) {
            throw new BusinessException(409, "请先保存当前简历内容，再进行预览或导出");
        }
        return WorkspaceContentVO.builder()
                .optimizationTaskId(context.task().getId())
                .revision(revision)
                .document(readPersistedDocument(target))
                .build();
    }

    @Override
    @Transactional
    public WorkspaceContentSaveResultVO saveContent(
            Long userId, Long optimizationTaskId, WorkspaceContentSaveRequestDTO request) {
        if (request == null || request.getExpectedRevision() == null) {
            throw new BusinessException(400, "缺少内容版本号");
        }
        validateExpectedRevision(request.getExpectedRevision());
        if (request.getDocument() == null) {
            throw new BusinessException(400, "简历内容不能为空");
        }
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        long currentRevision = revisionOf(context.target());
        if (currentRevision != request.getExpectedRevision()) {
            return conflictResult(currentRevision);
        }
        ResumeDocumentDTO frozen = documentFromFrozenSnapshot(resolveFrozenSnapshot(context));
        ResumeDocumentDTO current = currentRevision == PRISTINE_REVISION
                ? frozen : readPersistedDocument(context.target());
        ResumeDocumentDTO normalized = resumeDocumentConverter.normalizeWorkspaceSave(
                request.getDocument(), current, frozen);
        canonicalizeConfirmedOmissions(frozen, normalized);
        return writeTargetContent(context, normalized, request.getExpectedRevision());
    }

    @Override
    @Transactional
    public WorkspaceContentSaveResultVO confirmSourceOmissions(
            Long userId, Long optimizationTaskId, WorkspaceSourceOmissionRequestDTO request) {
        return mutateSourceOmissions(userId, optimizationTaskId, request, true);
    }

    @Override
    @Transactional
    public WorkspaceContentSaveResultVO unconfirmSourceOmissions(
            Long userId, Long optimizationTaskId, WorkspaceSourceOmissionRequestDTO request) {
        return mutateSourceOmissions(userId, optimizationTaskId, request, false);
    }

    @Override
    @Transactional
    public WorkspaceContentSaveResultVO restoreSourceContent(
            Long userId, Long optimizationTaskId, WorkspaceSourceRestoreRequestDTO request) {
        if (request == null || request.expectedRevision() == null) {
            throw new BusinessException(400, "缺少内容版本号");
        }
        validateExpectedRevision(request.expectedRevision());
        List<String> requested = validateRequestedOccurrenceIds(request.sourceOccurrenceIds());
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        long currentRevision = revisionOf(context.target());
        if (currentRevision != request.expectedRevision()) {
            return conflictResult(currentRevision);
        }
        ResumeDocumentDTO frozen = documentFromFrozenSnapshot(resolveFrozenSnapshot(context));
        // A restore only ever deep-copies frozen nodes; the candidate starts from the persisted
        // TARGET and never from request-supplied content.
        ResumeDocumentDTO current = currentRevision == PRISTINE_REVISION
                ? copyNode(frozen, ResumeDocumentDTO.class) : readPersistedDocument(context.target());

        WorkspaceSourceReferenceVO before = assembleFidelity(context, currentRevision, frozen, current);
        if (hasFidelityIssue(before, "SOURCE_MANIFEST_INVALID")
                || hasFidelityIssue(before, "SOURCE_MANIFEST_UNAVAILABLE")) {
            throw new BusinessException(400, "原文校验数据异常，无法在本任务内自动恢复");
        }
        RestoreBoundary boundary = resolveRestoreBoundary(requested, before.sourceBlocks());
        applyRestore(current, frozen, boundary);

        // Restore and confirmed omission are mutually exclusive for the same boundary: a restored
        // occurrence can no longer stay "intentionally omitted".
        LinkedHashSet<String> remaining = canonicalConfirmedOmissionIds(
                before.sourceBlocks(), canonicalCandidateOccurrenceIds(current.getConfirmedSourceOmissionIds()));
        remaining.removeAll(boundary.occurrenceClosure());
        Set<String> candidateIds = canonicalCandidateOccurrenceIds(List.copyOf(remaining));
        current.setConfirmedSourceOmissionIds(orderedFrozenIds(frozen, candidateIds));

        WorkspaceSourceReferenceVO after = assembleFidelity(context, currentRevision, frozen, current);
        LinkedHashSet<String> finalIds = canonicalConfirmedOmissionIds(
                after.sourceBlocks(), canonicalCandidateOccurrenceIds(current.getConfirmedSourceOmissionIds()));
        if (!finalIds.equals(canonicalCandidateOccurrenceIds(current.getConfirmedSourceOmissionIds()))) {
            current.setConfirmedSourceOmissionIds(orderedFrozenIds(frozen, finalIds));
            after = assembleFidelity(context, currentRevision, frozen, current);
        }
        // 与保存路径一致：写库前再做一次结构归一化校验；重复 ID、超限或形状问题全部 fail closed。
        resumeDocumentConverter.normalize(current);
        validateRestoreOutcome(before, after, boundary);
        return writeTargetContent(context, current, request.expectedRevision());
    }

    @Override
    @Transactional
    public WorkspaceContentSaveResultVO restorePreOptimizationContent(
            Long userId, Long optimizationTaskId, Long expectedRevision) {
        if (expectedRevision == null) {
            throw new BusinessException(400, "缺少内容版本号");
        }
        validateExpectedRevision(expectedRevision);
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        // 恢复只读取任务冻结快照重新生成文档；SOURCE、快照与证据分析不被回写。
        ResumeDocumentDTO restored = documentFromFrozenSnapshot(resolveFrozenSnapshot(context));
        restored.setConfirmedSourceOmissionIds(List.of());
        return writeTargetContent(context, restored, expectedRevision);
    }

    private WorkspaceContentSaveResultVO mutateSourceOmissions(
            Long userId,
            Long optimizationTaskId,
            WorkspaceSourceOmissionRequestDTO request,
            boolean confirm) {
        if (request == null || request.expectedRevision() == null) {
            throw new BusinessException(400, "缺少内容版本号");
        }
        validateExpectedRevision(request.expectedRevision());
        List<String> requested = validateRequestedOccurrenceIds(request.sourceOccurrenceIds());
        EditableTaskContext context = resolveEditableTarget(userId, optimizationTaskId);
        long currentRevision = revisionOf(context.target());
        if (currentRevision != request.expectedRevision()) {
            return conflictResult(currentRevision);
        }
        ResumeDocumentDTO frozen = documentFromFrozenSnapshot(resolveFrozenSnapshot(context));
        ResumeDocumentDTO current = currentRevision == PRISTINE_REVISION
                ? frozen : readPersistedDocument(context.target());
        Set<String> frozenIds = new LinkedHashSet<>(safeStrings(frozen.getSourceOccurrenceIds()));
        if (confirm && !frozenIds.containsAll(requested)) {
            throw new BusinessException(400, "来源 occurrence 不属于当前冻结简历");
        }

        // Inspect the persisted state before normalizing it. In particular, a malformed partial alias
        // group must remain removable even though the assembler correctly refuses to call it confirmed.
        Set<String> persistedIds = canonicalCandidateOccurrenceIds(current.getConfirmedSourceOmissionIds());
        Set<String> persistedMentions = mentionedOccurrenceIds(current.getConfirmedSourceOmissionIds());
        WorkspaceSourceReferenceVO fidelity = sourceReferenceAssembler.assemble(
                context.task().getId(), context.source().getId(), context.target().getId(), currentRevision,
                context.resume().getOriginalFilename(), isPdf(context.resume()), frozen, current);
        if (hasFidelityIssue(fidelity, "SOURCE_MANIFEST_INVALID")
                || hasFidelityIssue(fidelity, "SOURCE_MANIFEST_UNAVAILABLE")) {
            throw new BusinessException(400, "冻结原文清单不允许确认省略");
        }

        // Existing decisions have no authority merely because they were persisted. Rebuild only complete,
        // currently eligible alias/boundary groups; this drops unknown, duplicate, mapped, ambiguous,
        // ineligible, and unrelated partial stale state without promoting a partial alias to its closure.
        LinkedHashSet<String> next = canonicalConfirmedOmissionIds(fidelity.sourceBlocks(), persistedIds);
        LinkedHashSet<SourceBlock> boundaryBlocks = new LinkedHashSet<>();
        for (String requestedId : requested) {
            if (!frozenIds.contains(requestedId)) {
                if (confirm || !persistedMentions.contains(requestedId)) {
                    throw new BusinessException(400, "来源 occurrence 不属于当前冻结简历");
                }
                // An exact task-local stale value can authorize only its own CAS-backed removal.
                continue;
            }
            SourceBlock requestedBlock = resolveRequestedBlock(requestedId, fidelity.sourceBlocks());
            if (confirm && !isConfirmableOmission(requestedBlock)) {
                throw new BusinessException(400, "来源 occurrence 当前不能确认为省略");
            }
            List<SourceBlock> requestedBoundary = omissionBoundary(requestedBlock, fidelity.sourceBlocks());
            if (!confirm && requestedBoundary.stream()
                    .flatMap(block -> block.occurrenceIds().stream())
                    .noneMatch(persistedMentions::contains)) {
                throw new BusinessException(400, "来源 occurrence 当前未确认省略");
            }
            boundaryBlocks.addAll(requestedBoundary);
        }
        if (confirm && boundaryBlocks.stream().anyMatch(block -> !isConfirmableOmission(block))) {
            throw new BusinessException(400, "来源边界未完整省略，当前不能确认");
        }

        // SourceBlock.occurrenceIds is the complete physical alias closure. Applying the enclosing
        // Project/Bullet boundary after block resolution makes either operation authoritative and atomic.
        LinkedHashSet<String> authoritativeIds = boundaryBlocks.stream()
                .flatMap(block -> block.occurrenceIds().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (confirm) next.addAll(authoritativeIds);
        else next.removeAll(authoritativeIds);
        current.setConfirmedSourceOmissionIds(orderedFrozenIds(frozen, next));

        // A malformed confirmation list may be repaired, but no mutation may persist a confirmation state
        // that the assembler still considers invalid. This keeps confirm fail closed when rebuilding is unsafe.
        WorkspaceSourceReferenceVO rebuilt = sourceReferenceAssembler.assemble(
                context.task().getId(), context.source().getId(), context.target().getId(), currentRevision,
                context.resume().getOriginalFilename(), isPdf(context.resume()), frozen, current);
        Set<String> rebuiltIds = canonicalCandidateOccurrenceIds(current.getConfirmedSourceOmissionIds());
        if (hasFidelityIssue(rebuilt, "CONFIRMED_OMISSION_INVALID")
                || !canonicalConfirmedOmissionIds(rebuilt.sourceBlocks(), rebuiltIds).equals(rebuiltIds)) {
            throw new BusinessException(400, "已确认省略状态无法安全规范化");
        }
        return writeTargetContent(context, current, request.expectedRevision());
    }

    private List<String> validateRequestedOccurrenceIds(List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 500) {
            throw new BusinessException(400, "来源 occurrence ID 不能为空或数量超出上限");
        }
        List<String> normalized = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank() || !id.equals(id.strip()) || !unique.add(id)) {
                throw new BusinessException(400, "来源 occurrence ID 无效或重复");
            }
            normalized.add(id);
        }
        return normalized;
    }

    private void canonicalizeConfirmedOmissions(ResumeDocumentDTO frozen, ResumeDocumentDTO target) {
        WorkspaceSourceReferenceVO fidelity = sourceReferenceAssembler.assemble(
                null, null, null, 0L, null, false, frozen, target);
        Set<String> existing = canonicalCandidateOccurrenceIds(target.getConfirmedSourceOmissionIds());
        Set<String> canonical = canonicalConfirmedOmissionIds(fidelity.sourceBlocks(), existing);
        target.setConfirmedSourceOmissionIds(orderedFrozenIds(frozen, canonical));
    }

    private LinkedHashSet<String> canonicalConfirmedOmissionIds(
            List<SourceBlock> sourceBlocks, Set<String> existing) {
        LinkedHashSet<String> canonical = new LinkedHashSet<>();
        for (SourceBlock block : sourceBlocks) {
            if (!isConfirmableOmission(block)) continue;
            List<SourceBlock> boundary = omissionBoundary(block, sourceBlocks);
            boolean complete = boundary.stream().allMatch(candidate -> isConfirmableOmission(candidate)
                    && existing.containsAll(candidate.occurrenceIds()));
            if (complete) {
                boundary.stream().flatMap(candidate -> candidate.occurrenceIds().stream())
                        .forEach(canonical::add);
            }
        }
        return canonical;
    }

    private SourceBlock resolveRequestedBlock(String requestedId, List<SourceBlock> sourceBlocks) {
        List<SourceBlock> matches = sourceBlocks.stream()
                .filter(candidate -> candidate.occurrenceIds().contains(requestedId))
                .toList();
        if (matches.size() != 1) {
            throw new BusinessException(400, "来源 occurrence 不属于当前冻结简历");
        }
        return matches.get(0);
    }

    /**
     * Return only exact, unique persisted IDs that may retain authority. Malformed values are
     * never trimmed or deduplicated into a valid confirmation; every repeated ID is removed.
     */
    private Set<String> canonicalCandidateOccurrenceIds(List<String> ids) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        LinkedHashSet<String> duplicates = new LinkedHashSet<>();
        for (String id : safeStrings(ids)) {
            if (id == null || id.isBlank() || !id.equals(id.strip())) continue;
            if (!candidates.add(id)) duplicates.add(id);
        }
        candidates.removeAll(duplicates);
        return candidates;
    }

    /** A malformed stored value may authorize only its own removal, never confirmation. */
    private Set<String> mentionedOccurrenceIds(List<String> ids) {
        LinkedHashSet<String> mentions = new LinkedHashSet<>();
        for (String id : safeStrings(ids)) {
            if (id != null && !id.isBlank()) mentions.add(id.strip());
        }
        return mentions;
    }

    private List<String> orderedFrozenIds(ResumeDocumentDTO frozen, Set<String> ids) {
        return safeStrings(frozen.getSourceOccurrenceIds()).stream()
                .filter(ids::contains)
                .distinct()
                .toList();
    }

    private boolean hasFidelityIssue(WorkspaceSourceReferenceVO fidelity, String code) {
        return fidelity.fidelityIssues().stream().anyMatch(issue -> code.equals(issue.code()));
    }

    private boolean isConfirmableOmission(SourceBlock block) {
        return block.status()
                == com.winter.airesumeoptimizer.module.workspace.enums.WorkspaceSourceMappingStatus.UNMAPPED
                && block.omissionEligible();
    }

    /** Resolve omission units only from authenticated frozen-owner metadata returned by the server assembler. */
    private List<SourceBlock> omissionBoundary(SourceBlock anchor, List<SourceBlock> allBlocks) {
        if ("PROJECT".equalsIgnoreCase(anchor.sourceSectionKind())
                && hasBoundaryId(anchor.sourceSectionId()) && hasBoundaryId(anchor.sourceEntryId())) {
            return allBlocks.stream().filter(candidate ->
                    "PROJECT".equalsIgnoreCase(candidate.sourceSectionKind())
                            && anchor.sourceSectionId().equals(candidate.sourceSectionId())
                            && anchor.sourceEntryId().equals(candidate.sourceEntryId()))
                    .toList();
        }
        if ("BULLET".equals(anchor.sourceNodeType())
                && hasBoundaryId(anchor.sourceSectionId()) && hasBoundaryId(anchor.sourceEntryId())
                && hasBoundaryId(anchor.sourceBulletId())) {
            return allBlocks.stream().filter(candidate ->
                    "BULLET".equals(candidate.sourceNodeType())
                            && anchor.sourceSectionId().equals(candidate.sourceSectionId())
                            && anchor.sourceEntryId().equals(candidate.sourceEntryId())
                            && anchor.sourceBulletId().equals(candidate.sourceBulletId()))
                    .toList();
        }
        return List.of(anchor);
    }

    private boolean hasBoundaryId(String value) {
        return value != null && !value.isBlank();
    }

    private List<String> safeStrings(List<String> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 服务端从任务解析 SOURCE / TARGET 并校验完整版本链，调用方不能指定可写版本。
     */
    private EditableTaskContext resolveEditableTarget(Long userId, Long optimizationTaskId) {
        validateUserId(userId);
        if (optimizationTaskId == null || optimizationTaskId <= 0) {
            throw new BusinessException(400, "优化任务 ID 必须大于 0");
        }
        OptimizationTask task = optimizationTaskMapper.selectOne(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getId, optimizationTaskId)
                .eq(OptimizationTask::getUserId, userId));
        if (task == null) {
            throw new BusinessException(404, "优化任务不存在");
        }
        if (!TASK_STATUS_SUCCESS.equals(task.getStatus())) {
            throw new BusinessException(409, "岗位分析尚未完成，暂不能编辑简历内容");
        }

        ResumeVersion source = getOwnedVersion(userId, task.getSourceResumeVersionId());
        ResumeVersion target = getOwnedVersion(userId, task.getTargetResumeVersionId());
        JobTarget jobTarget = getOwnedJobTarget(userId, task.getJobTargetId());
        Resume resume = getOwnedResume(userId, source.getResumeId());

        if (!VERSION_SOURCE.equals(source.getVersionType())
                || source.getSourceVersionId() != null
                || source.getJobTargetId() != null
                || !CONTENT_READY.equals(source.getContentStatus())
                || source.getStructuredContent() == null
                || source.getStructuredContent().isBlank()) {
            throw new BusinessException(500, "优化任务的简历版本关系不一致");
        }
        if (!VERSION_TARGETED.equals(target.getVersionType())
                || !task.getSourceResumeVersionId().equals(target.getSourceVersionId())
                || !task.getJobTargetId().equals(target.getJobTargetId())
                || !source.getResumeId().equals(target.getResumeId())
                || !resume.getId().equals(target.getResumeId())
                || !jobTarget.getId().equals(target.getJobTargetId())
                || !CONTENT_READY.equals(target.getContentStatus())
                || target.getStructuredContent() == null
                || target.getStructuredContent().isBlank()) {
            throw new BusinessException(500, "优化任务的简历版本关系不一致");
        }
        long sourceRevision = revisionOf(source);
        long targetRevision = revisionOf(target);
        String snapshot = task.getResumeInputSnapshot();
        if (sourceRevision != PRISTINE_REVISION
                || (snapshot != null && !snapshot.isBlank() && !snapshot.equals(source.getStructuredContent()))
                || (targetRevision == PRISTINE_REVISION
                        && !target.getStructuredContent().equals(source.getStructuredContent()))) {
            throw new BusinessException(500, "优化任务的冻结简历内容不一致");
        }
        Long targetUseCount = optimizationTaskMapper.selectCount(new LambdaQueryWrapper<OptimizationTask>()
                .eq(OptimizationTask::getTargetResumeVersionId, target.getId()));
        if (targetUseCount == null || targetUseCount != 1L) {
            throw new BusinessException(500, "岗位版本被多个优化任务引用，不能安全编辑");
        }
        return new EditableTaskContext(task, source, target, resume);
    }

    private ResumeVersion getOwnedVersion(Long userId, Long versionId) {
        if (versionId == null) {
            throw new BusinessException(500, "优化任务的简历版本关系不一致");
        }
        ResumeVersion version = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, versionId)
                .eq(ResumeVersion::getUserId, userId));
        if (version == null) {
            throw new BusinessException(404, "简历版本不存在");
        }
        return version;
    }

    private JobTarget getOwnedJobTarget(Long userId, Long jobTargetId) {
        if (jobTargetId == null) {
            throw new BusinessException(500, "优化任务的岗位关系不一致");
        }
        JobTarget target = jobTargetMapper.selectOne(new LambdaQueryWrapper<JobTarget>()
                .eq(JobTarget::getId, jobTargetId)
                .eq(JobTarget::getUserId, userId));
        if (target == null) {
            throw new BusinessException(404, "目标岗位不存在");
        }
        return target;
    }

    private Resume getOwnedResume(Long userId, Long resumeId) {
        if (resumeId == null) {
            throw new BusinessException(500, "优化任务的简历关系不一致");
        }
        Resume resume = resumeMapper.selectOne(new LambdaQueryWrapper<Resume>()
                .eq(Resume::getId, resumeId)
                .eq(Resume::getUserId, userId));
        if (resume == null) {
            throw new BusinessException(404, "简历不存在");
        }
        return resume;
    }

    /**
     * 仅当 expectedRevision 与服务端当前 revision 一致时原子写入并递增；
     * 条件更新保证同 revision 的并发保存只有一个成功。
     */
    private WorkspaceContentSaveResultVO conflictResult(long currentRevision) {
        return WorkspaceContentSaveResultVO.builder()
                .saved(false)
                .conflict(true)
                .revision(currentRevision)
                .build();
    }

    private WorkspaceContentSaveResultVO writeTargetContent(
            EditableTaskContext context, ResumeDocumentDTO document, long expectedRevision) {
        ResumeVersion target = context.target();
        String serialized = serialize(document);
        LocalDateTime now = LocalDateTime.now();
        int rows = resumeVersionMapper.update(null, new UpdateWrapper<ResumeVersion>()
                .eq("id", target.getId())
                .eq("user_id", target.getUserId())
                .eq("version_type", VERSION_TARGETED)
                .eq("source_version_id", context.source().getId())
                .eq("job_target_id", context.task().getJobTargetId())
                .eq("content_status", CONTENT_READY)
                .eq("content_revision", expectedRevision)
                .set("structured_content", serialized)
                .set("content_revision", expectedRevision + 1)
                .set("updated_at", now));
        if (rows == 1) {
            return WorkspaceContentSaveResultVO.builder()
                    .saved(true)
                    .conflict(false)
                    .revision(expectedRevision + 1)
                    .document(document)
                    .build();
        }

        ResumeVersion current = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, target.getId())
                .eq(ResumeVersion::getUserId, target.getUserId()));
        if (current == null) {
            throw new BusinessException(404, "简历版本不存在");
        }
        return conflictResult(revisionOf(current));
    }

    private WorkspaceSourceReferenceVO assembleFidelity(
            EditableTaskContext context, long revision, ResumeDocumentDTO frozen, ResumeDocumentDTO current) {
        return sourceReferenceAssembler.assemble(
                context.task().getId(), context.source().getId(), context.target().getId(), revision,
                context.resume().getOriginalFilename(), isPdf(context.resume()), frozen, current);
    }

    /**
     * The request only names frozen occurrences. The restore unit, its boundary and its closure
     * are re-resolved server-side from the authoritative SourceBlock verdicts of this revision.
     */
    private RestoreBoundary resolveRestoreBoundary(
            List<String> requested, List<SourceBlock> sourceBlocks) {
        List<SourceBlock> requestedBlocks = new ArrayList<>();
        for (String requestedId : requested) {
            SourceBlock block = resolveRequestedBlock(requestedId, sourceBlocks);
            if (block.restoreScope() == WorkspaceSourceRestoreScope.NONE || !block.restoreEligible()) {
                throw new BusinessException(400, restoreRejectionMessage(block));
            }
            if (!requestedBlocks.contains(block)) {
                requestedBlocks.add(block);
            }
        }
        RestoreBoundary boundary = RestoreBoundary.of(requestedBlocks.get(0));
        for (SourceBlock block : requestedBlocks) {
            if (!boundary.matches(block)) {
                throw new BusinessException(400, "一次只能恢复一个来源边界，请分开处理");
            }
        }
        LinkedHashSet<String> closure = new LinkedHashSet<>();
        for (SourceBlock block : sourceBlocks) {
            if (boundary.matches(block)) {
                closure.addAll(block.occurrenceIds());
            }
        }
        if (closure.isEmpty()) {
            throw new BusinessException(400, "当前来源边界不存在");
        }
        return boundary.withClosure(closure);
    }

    private String restoreRejectionMessage(SourceBlock block) {
        String reason = block.restoreBlockedReason();
        if ("BOUNDARY_HAS_OTHER_MAPPINGS".equals(reason)) {
            return "当前来源边界存在其它映射，无法安全恢复";
        }
        if ("PARENT_SECTION_MISSING".equals(reason)) {
            return "原章节已不存在，无法安全局部恢复，请恢复优化前版本或重新创建本次优化";
        }
        if ("PARENT_LINEAGE_MISMATCH".equals(reason)) {
            return "来源归属关系不一致，无法安全恢复";
        }
        if ("ENTRY_ALREADY_PRESENT".equals(reason) || "TARGET_VALUE_CONFLICT".equals(reason)) {
            return "当前简历中已存在对应内容，无法安全恢复";
        }
        return "该来源内容当前无法安全自动恢复";
    }

    private void applyRestore(ResumeDocumentDTO target, ResumeDocumentDTO frozen, RestoreBoundary boundary) {
        switch (boundary.scope()) {
            case BULLET -> restoreBullet(target, frozen, boundary);
            case ENTRY, PROJECT_ENTRY -> restoreEntry(target, frozen, boundary);
            case CONTACT -> restoreContact(target, frozen, boundary);
            default -> throw new BusinessException(400, "该来源内容当前无法安全自动恢复");
        }
    }

    private void restoreBullet(ResumeDocumentDTO target, ResumeDocumentDTO frozen, RestoreBoundary boundary) {
        ResumeDocumentEntryDTO frozenEntry = entryById(sectionById(frozen, boundary.sectionId()), boundary.entryId());
        ResumeDocumentBulletDTO frozenBullet = null;
        for (ResumeDocumentBulletDTO bullet : safeList(frozenEntry == null ? null : frozenEntry.getBullets())) {
            if (bullet != null && Objects.equals(boundary.bulletId(), bullet.getId())) {
                frozenBullet = bullet;
                break;
            }
        }
        ResumeDocumentEntryDTO targetEntry = entryById(sectionById(target, boundary.sectionId()), boundary.entryId());
        if (frozenBullet == null || targetEntry == null) {
            throw new BusinessException(400, "恢复目标已不存在，请刷新后重试");
        }
        // Deterministic SOURCE sibling order: before the nearest surviving frozen successor,
        // otherwise after the nearest surviving frozen predecessor, otherwise append.
        List<ResumeDocumentBulletDTO> frozenBullets = safeList(frozenEntry.getBullets());
        List<ResumeDocumentBulletDTO> targetBullets = new ArrayList<>(safeList(targetEntry.getBullets()));
        int frozenIndex = indexOfId(frozenBullets, boundary.bulletId(), ResumeDocumentBulletDTO::getId);
        int insertion = insertionIndexByOrder(frozenBullets, targetBullets, frozenIndex, ResumeDocumentBulletDTO::getId);
        targetBullets.add(insertion, copyNode(frozenBullet, ResumeDocumentBulletDTO.class));
        targetEntry.setBullets(targetBullets);
    }

    private void restoreEntry(ResumeDocumentDTO target, ResumeDocumentDTO frozen, RestoreBoundary boundary) {
        ResumeDocumentSectionDTO frozenSection = sectionById(frozen, boundary.sectionId());
        ResumeDocumentEntryDTO frozenEntry = entryById(frozenSection, boundary.entryId());
        ResumeDocumentSectionDTO targetSection = sectionById(target, boundary.sectionId());
        if (frozenSection == null || frozenEntry == null || targetSection == null) {
            throw new BusinessException(400, "恢复目标已不存在，请刷新后重试");
        }
        List<ResumeDocumentEntryDTO> frozenEntries = safeList(frozenSection.getEntries());
        List<ResumeDocumentEntryDTO> targetEntries = new ArrayList<>(safeList(targetSection.getEntries()));
        int frozenIndex = indexOfId(frozenEntries, boundary.entryId(), ResumeDocumentEntryDTO::getId);
        int insertion = insertionIndexByOrder(frozenEntries, targetEntries, frozenIndex, ResumeDocumentEntryDTO::getId);
        targetEntries.add(insertion, copyNode(frozenEntry, ResumeDocumentEntryDTO.class));
        targetSection.setEntries(targetEntries);
    }

    private void restoreContact(ResumeDocumentDTO target, ResumeDocumentDTO frozen, RestoreBoundary boundary) {
        ResumeDocumentBasicsDTO frozenBasics = frozen.getBasics();
        ResumeDocumentBasicsDTO targetBasics = target.getBasics();
        if (frozenBasics == null || targetBasics == null) {
            throw new BusinessException(400, "恢复目标已不存在，请刷新后重试");
        }
        ResumeDocumentContactDTO frozenContact = null;
        for (ResumeDocumentContactDTO contact : safeList(frozenBasics.getContacts())) {
            if (contact == null
                    || contactOccurrenceIds(contact).stream().noneMatch(boundary.occurrenceClosure()::contains)) {
                continue;
            }
            if (frozenContact != null) {
                throw new BusinessException(400, "该联系方式对应的原文不唯一，无法安全恢复");
            }
            frozenContact = contact;
        }
        if (frozenContact == null || !hasText(frozenContact.getId())) {
            throw new BusinessException(400, "恢复目标已不存在，请刷新后重试");
        }
        List<ResumeDocumentContactDTO> frozenContacts = safeList(frozenBasics.getContacts());
        List<ResumeDocumentContactDTO> targetContacts = new ArrayList<>(safeList(targetBasics.getContacts()));
        int frozenIndex = indexOfId(frozenContacts, frozenContact.getId(), ResumeDocumentContactDTO::getId);
        int insertion = insertionIndexByOrder(frozenContacts, targetContacts, frozenIndex, ResumeDocumentContactDTO::getId);
        targetContacts.add(insertion, copyNode(frozenContact, ResumeDocumentContactDTO.class));
        targetBasics.setContacts(targetContacts);
    }

    private List<String> contactOccurrenceIds(ResumeDocumentContactDTO contact) {
        if (contact.getSourceOccurrenceIds() != null && !contact.getSourceOccurrenceIds().isEmpty()) {
            return contact.getSourceOccurrenceIds();
        }
        return contact.getSourceRef() == null || contact.getSourceRef().getSourceOccurrenceIds() == null
                ? List.of() : contact.getSourceRef().getSourceOccurrenceIds();
    }

    private ResumeDocumentSectionDTO sectionById(ResumeDocumentDTO document, String sectionId) {
        if (sectionId == null) return null;
        for (ResumeDocumentSectionDTO section : safeList(document.getSections())) {
            if (section != null && sectionId.equals(section.getId())) return section;
        }
        return null;
    }

    private ResumeDocumentEntryDTO entryById(ResumeDocumentSectionDTO section, String entryId) {
        if (section == null || entryId == null) return null;
        for (ResumeDocumentEntryDTO entry : safeList(section.getEntries())) {
            if (entry != null && entryId.equals(entry.getId())) return entry;
        }
        return null;
    }

    private <T> int indexOfId(List<T> nodes, String id, Function<T, String> idOf) {
        if (id == null) return -1;
        for (int index = 0; index < nodes.size(); index++) {
            if (id.equals(idOf.apply(nodes.get(index)))) return index;
        }
        return -1;
    }

    private <T> int insertionIndexByOrder(
            List<T> frozenOrder, List<T> targetOrder, int frozenIndex, Function<T, String> idOf) {
        for (int index = frozenIndex + 1; index < frozenOrder.size(); index++) {
            int position = indexOfId(targetOrder, idOf.apply(frozenOrder.get(index)), idOf);
            if (position >= 0) return position;
        }
        for (int index = frozenIndex - 1; index >= 0; index--) {
            int position = indexOfId(targetOrder, idOf.apply(frozenOrder.get(index)), idOf);
            if (position >= 0) return position + 1;
        }
        return targetOrder.size();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * The restore must really clear the blocker it was invoked for. A restore that still leaves
     * the boundary unmapped, introduces ambiguity/duplicate mappings, or corrupts the omission
     * state is rejected before any write instead of producing a worse provenance state.
     */
    private void validateRestoreOutcome(
            WorkspaceSourceReferenceVO before, WorkspaceSourceReferenceVO after, RestoreBoundary boundary) {
        if (hasFidelityIssue(after, "SOURCE_MANIFEST_INVALID")
                || hasFidelityIssue(after, "SOURCE_MANIFEST_UNAVAILABLE")
                || hasFidelityIssue(after, "CONFIRMED_OMISSION_INVALID")) {
            throw new BusinessException(400, "恢复后原文校验状态异常，本次恢复未生效");
        }
        if (countFidelityIssues(after, "AMBIGUOUS_MAPPING") > countFidelityIssues(before, "AMBIGUOUS_MAPPING")
                || countFidelityIssues(after, "DUPLICATE_MAPPING") > countFidelityIssues(before, "DUPLICATE_MAPPING")) {
            throw new BusinessException(400, "恢复会产生歧义或重复映射，本次恢复未生效");
        }
        for (var issue : after.fidelityIssues()) {
            boolean guarded = "AMBIGUOUS_MAPPING".equals(issue.code())
                    || "DUPLICATE_MAPPING".equals(issue.code())
                    || "SOURCE_CONTENT_UNMAPPED".equals(issue.code());
            if (guarded && issue.sourceOccurrenceIds().stream().anyMatch(boundary.occurrenceClosure()::contains)) {
                throw new BusinessException(400, "恢复未真正解除该原文的结构问题，本次恢复未生效");
            }
        }
        boolean restored = after.sourceBlocks().stream()
                .filter(block -> block.occurrenceIds().stream().anyMatch(boundary.occurrenceClosure()::contains))
                .noneMatch(block -> block.status() == WorkspaceSourceMappingStatus.UNMAPPED);
        if (!restored) {
            throw new BusinessException(400, "恢复未生效，请刷新后重试");
        }
    }

    private long countFidelityIssues(WorkspaceSourceReferenceVO fidelity, String code) {
        return fidelity.fidelityIssues().stream().filter(issue -> code.equals(issue.code())).count();
    }

    private <T> T copyNode(T node, Class<T> type) {
        try {
            return objectMapper.treeToValue(objectMapper.valueToTree(node), type);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException(500, "恢复原文失败，请稍后重试");
        }
    }

    private ResumeDocumentDTO readPersistedDocument(ResumeVersion target) {
        String content = target.getStructuredContent();
        if (content == null || content.isBlank()) {
            throw new BusinessException(500, "简历内容格式不正确");
        }
        // V1 语义内容直接归一化，Slice A 之前的 generic V1 内容确定性升级；损坏内容在升级器内 fail closed。
        return resumeDocumentConverter.upgradeLegacyDocument(content);
    }

    /**
     * 编辑文档的原始输入优先使用任务冻结的 resume_input_snapshot；
     * 历史回填任务可能没有快照，此时退回该任务 SOURCE 的冻结结构化内容。
     */
    private ResumeDocumentDTO documentFromFrozenSnapshot(String snapshot) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(snapshot);
            String schemaVersion = root == null ? null : root.path("schemaVersion").asText(null);
            if (ResumeDocumentDTO.SCHEMA_VERSION.equals(schemaVersion)) {
                return resumeDocumentConverter.upgradeLegacyDocument(snapshot);
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历内容格式不正确，请重新解析");
        }
        // 历史任务冻结的是旧 structured_json 候选；只读确定性投影，不能作为新任务输入。
        return resumeCanonicalDocumentService.buildFromStructuredJson(snapshot).document();
    }

    private String resolveFrozenSnapshot(EditableTaskContext context) {
        String snapshot = context.task().getResumeInputSnapshot();
        if (snapshot != null && !snapshot.isBlank()) {
            return snapshot;
        }
        String sourceContent = context.source().getStructuredContent();
        if (sourceContent == null || sourceContent.isBlank()) {
            throw new BusinessException(409, "简历内容尚未就绪，请先完成简历解析");
        }
        return sourceContent;
    }

    private String serialize(ResumeDocumentDTO document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "简历内容保存失败");
        }
    }

    private long revisionOf(ResumeVersion version) {
        long revision = version.getContentRevision() == null ? PRISTINE_REVISION : version.getContentRevision();
        if (revision < PRISTINE_REVISION) {
            throw new BusinessException(500, "简历内容版本号不正确");
        }
        return revision;
    }

    private void validateExpectedRevision(long expectedRevision) {
        if (expectedRevision < PRISTINE_REVISION || expectedRevision == Long.MAX_VALUE) {
            throw new BusinessException(400, "内容版本号不正确");
        }
    }

    private boolean isPdf(Resume resume) {
        return resume != null && resume.getFileType() != null
                && "PDF".equalsIgnoreCase(resume.getFileType().strip());
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new BusinessException(401, "请先登录");
        }
    }

    private record EditableTaskContext(
            OptimizationTask task, ResumeVersion source, ResumeVersion target, Resume resume) {
    }

    /** Server-resolved restore unit identity; the client names occurrences, never boundaries. */
    private record RestoreBoundary(
            WorkspaceSourceRestoreScope scope,
            String sectionId,
            String entryId,
            String bulletId,
            String contactBlockId,
            LinkedHashSet<String> occurrenceClosure) {

        static RestoreBoundary of(SourceBlock block) {
            return new RestoreBoundary(block.restoreScope(), block.sourceSectionId(), block.sourceEntryId(),
                    block.sourceBulletId(), block.id(), new LinkedHashSet<>());
        }

        boolean matches(SourceBlock block) {
            if (block.restoreScope() != scope) return false;
            return switch (scope) {
                case BULLET -> Objects.equals(sectionId, block.sourceSectionId())
                        && Objects.equals(entryId, block.sourceEntryId())
                        && Objects.equals(bulletId, block.sourceBulletId());
                case ENTRY, PROJECT_ENTRY -> Objects.equals(sectionId, block.sourceSectionId())
                        && Objects.equals(entryId, block.sourceEntryId());
                case CONTACT -> Objects.equals(contactBlockId, block.id());
                default -> false;
            };
        }

        RestoreBoundary withClosure(LinkedHashSet<String> closure) {
            return new RestoreBoundary(scope, sectionId, entryId, bulletId, contactBlockId, closure);
        }
    }
}
