package com.winter.airesumeoptimizer.module.export.service;

import com.winter.airesumeoptimizer.infra.render.PdfLayoutInspection;
import com.winter.airesumeoptimizer.module.resume.service.ResumeDocumentQualityValidator;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import com.winter.airesumeoptimizer.module.workspace.enums.ResumeDocumentContactType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Structured Resume + 最终 PDF 的统一轻量导出检查。
 * Slice A 起联系方式按显式类型判断；末页行数与实际 glyph 占用参与孤立/稀疏末页检测；
 * 本检查只产出事实与告警机器码，阻断裁决在 Document/PDF Quality Gate。
 */
@Component
public class ExportPreflightChecker {

    /** Phase 6 默认简历建议不超过两页；只告警，不自动删改或阻断用户内容。 */
    public static final int RECOMMENDED_MAX_PAGE_COUNT = 2;

    /** 页数 ≥2 且末页非空文本行少于此值判定为明确孤立末页。 */
    public static final int ORPHAN_FINAL_PAGE_MIN_LINES = 3;

    /** 实际 PDF 末页文字包围盒的最低占用比例；旧测试桩未提供时不启用该判断。 */
    public static final float MIN_FINAL_PAGE_CONTENT_RATIO = 0.20f;

    /** 正文/联系人最低可读字号；0 表示旧测试桩没有提供字号事实。 */
    public static final float MIN_READABLE_FONT_SIZE_PT = 8.5f;

    public ExportPreflight check(ResumeDocumentDTO document, PdfLayoutInspection layout, boolean needsReview) {
        boolean missingContact = !hasReachableContact(document);
        boolean pageLimitExceeded = layout.pageCount() > RECOMMENDED_MAX_PAGE_COUNT;
        boolean overflowDetected = layout.overflowDetected();
        boolean sparseFinalPage = layout.finalPageContentRatio() >= 0.0f
                && layout.finalPageContentRatio() < MIN_FINAL_PAGE_CONTENT_RATIO;
        // A legacy two-/three-argument inspection has no line or ratio fact. Do not turn that
        // compatibility sentinel into an invented orphan; a real inspector always supplies
        // ratio >= 0, including 0 for a blank/invisible final page.
        boolean finalPageLineCountAvailable = layout.finalPageLineCount() > 0
                || layout.finalPageContentRatio() >= 0.0f;
        boolean orphanFinalPage = layout.pageCount() >= 2
                && ((finalPageLineCountAvailable
                && layout.finalPageLineCount() < ORPHAN_FINAL_PAGE_MIN_LINES) || sparseFinalPage);
        boolean readabilityTooSmall = layout.minimumFontSizeInPt() > 0
                && layout.minimumFontSizeInPt() < MIN_READABLE_FONT_SIZE_PT;
        List<String> warnings = new ArrayList<>();
        if (missingContact) {
            warnings.add("MISSING_CONTACT");
        }
        if (pageLimitExceeded) {
            warnings.add("PAGE_LIMIT_EXCEEDED");
        }
        if (overflowDetected) {
            warnings.add("CONTENT_OUT_OF_PAGE_BOUNDS");
        }
        if (orphanFinalPage) {
            warnings.add("ORPHAN_FINAL_PAGE");
        }
        if (readabilityTooSmall) {
            warnings.add("READABILITY_TOO_SMALL");
        }
        return new ExportPreflight(
                layout.pageCount(),
                missingContact,
                pageLimitExceeded,
                overflowDetected,
                orphanFinalPage,
                readabilityTooSmall,
                needsReview,
                List.copyOf(warnings));
    }

    /** 是否存在格式正确的电话或邮箱；类型来自文档而不是 label 猜测。 */
    private boolean hasReachableContact(ResumeDocumentDTO document) {
        ResumeDocumentBasicsDTO basics = document == null ? null : document.getBasics();
        if (basics == null || basics.getContacts() == null) {
            return false;
        }
        for (ResumeDocumentContactDTO contact : basics.getContacts()) {
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
}
