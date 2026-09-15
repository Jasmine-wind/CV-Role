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
 * Technical Export Gate：只裁决“这份 document 是否技术上可以渲染 / 导出”。
 *
 * <p>产品原则：系统负责发现问题和提醒用户，用户负责决定是否修改。未确认候选、质量校验告警、
 * 重复章节、系统兜底章节、缺少联系方式、Structure Fidelity issue 等内容质量问题只作为
 * needsReview 提醒透传，不再阻止岗位分析、编辑、Preview 或正式导出。只有解析 PENDING /
 * FAILED、没有任何可渲染 document 这类“操作无法完成”的情况才阻断。
 */
@Component
public class ExportDocumentGate {

    /** 文档技术上可导出。 */
    public static final String STATUS_PASS = "PASS";
    /** 存在技术阻断项，操作无法完成；内容质量问题不会置位。 */
    public static final String STATUS_BLOCK = "BLOCK";

    /** 没有可渲染的 document，无法形成任何输出。 */
    public static final String CODE_DOCUMENT_NOT_CONFIRMED = "DOCUMENT_NOT_CONFIRMED";
    public static final String CODE_RESUME_QUALITY_FAILED = "RESUME_QUALITY_FAILED";
    public static final String CODE_RESUME_PARSE_PENDING = "RESUME_PARSE_PENDING";

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

    /** 检查结果：是否技术阻断、阻断机器码、解析质量状态与是否存在建议检查项。 */
    public record GateResult(String status, String blockCode, String qualityStatus, boolean needsReview) {

        public boolean blocked() {
            return STATUS_BLOCK.equals(status);
        }
    }

    public GateResult check(Long userId, OptimizationTask task, ResumeDocumentDTO document) {
        String qualityStatus = resolveQualityStatus(userId, task);

        // 解析 PENDING / FAILED：文档还没准备好或无法形成，操作技术上无法完成。
        if (ResumeQualityStatus.QUALITY_PENDING.equals(qualityStatus)) {
            return new GateResult(STATUS_BLOCK, CODE_RESUME_PARSE_PENDING, qualityStatus, false);
        }
        if (ResumeQualityStatus.QUALITY_FAILED.equals(qualityStatus)) {
            return new GateResult(STATUS_BLOCK, CODE_RESUME_QUALITY_FAILED, qualityStatus, false);
        }
        if (document == null) {
            // 没有可渲染 document：无法形成任何输出，属于“系统无法完成这个操作”。
            return new GateResult(STATUS_BLOCK, CODE_DOCUMENT_NOT_CONFIRMED, qualityStatus, false);
        }

        // 以下全部是 advisory：未确认候选、质量校验、重复章节、系统兜底章节、缺少联系方式
        // 只合并为一个 needsReview 提醒，由 UI 负责具体文案；不再影响 blocked。
        boolean needsReview = hasUnresolvedItems(userId, task)
                || hasContentConcerns(document)
                || hasBlockerQualityIssues(document);
        return new GateResult(STATUS_PASS, null, qualityStatus, needsReview);
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

    /** 内容层面的建议检查项：全部 advisory，只用于 needsReview。 */
    private boolean hasContentConcerns(ResumeDocumentDTO document) {
        if (document.getSections() == null || document.getSections().isEmpty()) {
            return true;
        }
        Set<String> titles = new HashSet<>();
        for (ResumeDocumentSectionDTO section : document.getSections()) {
            if (section == null || section.getTitle() == null) {
                continue;
            }
            String title = section.getTitle().strip();
            if (SYSTEM_SECTION_TITLES.contains(title)) {
                return true;
            }
            if (!titles.add(title.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return !hasReachableContact(document);
    }

    private boolean hasBlockerQualityIssues(ResumeDocumentDTO document) {
        return qualityValidator.validate(document, List.of()).issues().stream()
                .anyMatch(issue -> ResumeQualityIssueDTO.SEVERITY_BLOCKER.equals(issue.getSeverity()));
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
        if (document != null && !hasReachableContact(document)) {
            warnings.add("MISSING_CONTACT");
        }
        return warnings;
    }
}
