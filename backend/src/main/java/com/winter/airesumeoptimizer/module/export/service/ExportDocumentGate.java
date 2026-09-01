package com.winter.airesumeoptimizer.module.export.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.winter.airesumeoptimizer.module.optimization.entity.OptimizationTask;
import com.winter.airesumeoptimizer.module.optimization.entity.ResumeVersion;
import com.winter.airesumeoptimizer.module.optimization.mapper.ResumeVersionMapper;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeQualityIssueDTO;
import com.winter.airesumeoptimizer.module.resume.entity.ResumeParseResult;
import com.winter.airesumeoptimizer.module.resume.enums.ResumeQualityStatus;
import com.winter.airesumeoptimizer.module.resume.mapper.ResumeParseResultMapper;
import com.winter.airesumeoptimizer.module.resume.service.ResumeDocumentQualityValidator;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentSectionDTO;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentContactType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Document Quality Gate（Slice A）：canonical 内容是否可信。
 * 与 PDF Quality Gate 分层：本门只看文档与解析质量状态，不看排版。
 */
@Component
public class ExportDocumentGate {

    /** 文档质量检查通过。 */
    public static final String STATUS_PASS = "PASS";
    /** 存在阻断项，禁止正式导出。 */
    public static final String STATUS_BLOCK = "BLOCK";

    public static final String CODE_DOCUMENT_NOT_CONFIRMED = "DOCUMENT_NOT_CONFIRMED";
    public static final String CODE_RESUME_QUALITY_FAILED = "RESUME_QUALITY_FAILED";
    public static final String CODE_RESUME_PARSE_PENDING = "RESUME_PARSE_PENDING";
    public static final String CODE_DUPLICATE_SECTION = "DUPLICATE_SECTION";
    public static final String CODE_SYSTEM_ARTIFACT_PRESENT = "SYSTEM_ARTIFACT_PRESENT";
    public static final String CODE_MISSING_TYPED_CONTACT = "MISSING_TYPED_CONTACT";

    private static final Set<String> SYSTEM_SECTION_TITLES = Set.of(
            "未识别章节", "其他原始内容", "原始简历内容");

    private final ResumeVersionMapper resumeVersionMapper;
    private final ResumeParseResultMapper resumeParseResultMapper;
    private final ResumeDocumentQualityValidator qualityValidator;

    public ExportDocumentGate(
            ResumeVersionMapper resumeVersionMapper,
            ResumeParseResultMapper resumeParseResultMapper,
            ResumeDocumentQualityValidator qualityValidator) {
        this.resumeVersionMapper = resumeVersionMapper;
        this.resumeParseResultMapper = resumeParseResultMapper;
        this.qualityValidator = qualityValidator;
    }

    /** 检查结果：是否阻断、阻断机器码、解析质量状态与是否处于待确认。 */
    public record GateResult(String status, String blockCode, String qualityStatus, boolean needsReview) {

        public boolean blocked() {
            return STATUS_BLOCK.equals(status);
        }
    }

    public GateResult check(Long userId, OptimizationTask task, ResumeDocumentDTO document) {
        String qualityStatus = resolveQualityStatus(userId, task);

        if (ResumeQualityStatus.QUALITY_PENDING.equals(qualityStatus)) {
            return new GateResult(STATUS_BLOCK, CODE_RESUME_PARSE_PENDING, qualityStatus, false);
        }
        if (ResumeQualityStatus.QUALITY_FAILED.equals(qualityStatus)) {
            return new GateResult(STATUS_BLOCK, CODE_RESUME_QUALITY_FAILED, qualityStatus, false);
        }
        if (ResumeQualityStatus.QUALITY_NEEDS_REVIEW.equals(qualityStatus)) {
            return new GateResult(STATUS_BLOCK, CODE_DOCUMENT_NOT_CONFIRMED, qualityStatus, true);
        }
        if (!ResumeQualityStatus.QUALITY_READY.equals(qualityStatus)) {
            return new GateResult(STATUS_BLOCK, CODE_DOCUMENT_NOT_CONFIRMED, qualityStatus, false);
        }
        if (hasUnresolvedItems(userId, task)) {
            return new GateResult(STATUS_BLOCK, CODE_DOCUMENT_NOT_CONFIRMED, qualityStatus, true);
        }

        String contentBlocker = checkDocumentContent(document);
        if (contentBlocker != null) {
            return new GateResult(STATUS_BLOCK, contentBlocker, qualityStatus, false);
        }
        String validatorBlocker = qualityValidator.validate(document, List.of()).issues().stream()
                .filter(issue -> ResumeQualityIssueDTO.SEVERITY_BLOCKER.equals(issue.getSeverity()))
                .map(ResumeQualityIssueDTO::getCode)
                .findFirst()
                .orElse(null);
        if (validatorBlocker != null) {
            return new GateResult(STATUS_BLOCK, validatorBlocker, qualityStatus, false);
        }
        return new GateResult(STATUS_PASS, null, qualityStatus, false);
    }

    /**
     * 任务 → SOURCE 版本 → 简历 → 解析质量状态。
     * 历史任务/历史行没有质量记录时按 READY 等价处理，保持既有行为。
     */
    private String resolveQualityStatus(Long userId, OptimizationTask task) {
        // 已完成任务的输入快照是冻结事实；当前 Resume 重新解析不能回写或阻断历史 Task。
        if (task != null
                && "SUCCESS".equals(task.getStatus())
                && task.getResumeInputSnapshot() != null
                && !task.getResumeInputSnapshot().isBlank()) {
            return ResumeQualityStatus.QUALITY_READY;
        }
        if (task == null || task.getSourceResumeVersionId() == null) {
            return ResumeQualityStatus.QUALITY_READY;
        }
        ResumeVersion source = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, task.getSourceResumeVersionId())
                .eq(ResumeVersion::getUserId, userId));
        if (source == null || source.getResumeId() == null) {
            return ResumeQualityStatus.QUALITY_READY;
        }
        ResumeParseResult parseResult = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, source.getResumeId()));
        if (parseResult == null || parseResult.getQualityStatus() == null) {
            return ResumeQualityStatus.QUALITY_READY;
        }
        return parseResult.getQualityStatus();
    }

    private boolean hasUnresolvedItems(Long userId, OptimizationTask task) {
        if (task != null
                && "SUCCESS".equals(task.getStatus())
                && task.getResumeInputSnapshot() != null
                && !task.getResumeInputSnapshot().isBlank()) {
            return false;
        }
        if (task == null || task.getSourceResumeVersionId() == null) {
            return false;
        }
        ResumeVersion source = resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersion>()
                .eq(ResumeVersion::getId, task.getSourceResumeVersionId())
                .eq(ResumeVersion::getUserId, userId));
        if (source == null || source.getResumeId() == null) {
            return false;
        }
        ResumeParseResult parseResult = resumeParseResultMapper.selectOne(new LambdaQueryWrapper<ResumeParseResult>()
                .eq(ResumeParseResult::getResumeId, source.getResumeId()));
        String unresolved = parseResult == null ? null : parseResult.getUnresolvedItems();
        return unresolved != null && !unresolved.isBlank() && !"[]".equals(unresolved.strip());
    }

    private String checkDocumentContent(ResumeDocumentDTO document) {
        if (document == null || document.getSections() == null || document.getSections().isEmpty()) {
            return CODE_DOCUMENT_NOT_CONFIRMED;
        }
        Set<String> titles = new HashSet<>();
        for (ResumeDocumentSectionDTO section : document.getSections()) {
            if (section == null || section.getTitle() == null) {
                continue;
            }
            String title = section.getTitle().strip();
            if (SYSTEM_SECTION_TITLES.contains(title)) {
                return CODE_SYSTEM_ARTIFACT_PRESENT;
            }
            if (!titles.add(title.toLowerCase(Locale.ROOT))) {
                return CODE_DUPLICATE_SECTION;
            }
        }
        if (!hasReachableContact(document)) {
            return CODE_MISSING_TYPED_CONTACT;
        }
        return null;
    }

    private boolean hasReachableContact(ResumeDocumentDTO document) {
        if (document.getBasics() == null || document.getBasics().getContacts() == null) {
            return false;
        }
        for (ResumeDocumentContactDTO contact : document.getBasics().getContacts()) {
            if (contact == null || contact.getValue() == null || contact.getValue().isBlank()) {
                continue;
            }
            ResumeDocumentContactType type = ResumeDocumentContactType.fromValue(contact.getType());
            if (type == ResumeDocumentContactType.PHONE
                    && ResumeDocumentQualityValidator.isValidPhone(contact.getValue())) {
                return true;
            }
            if (type == ResumeDocumentContactType.EMAIL
                    && ResumeDocumentQualityValidator.isValidEmail(contact.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** 生成供日志/响应使用的告警机器码列表（非阻断）。 */
    public List<String> warnings(ResumeDocumentDTO document) {
        List<String> warnings = new ArrayList<>();
        if (!hasReachableContact(document)) {
            warnings.add(CODE_MISSING_TYPED_CONTACT);
        }
        return warnings;
    }
}
