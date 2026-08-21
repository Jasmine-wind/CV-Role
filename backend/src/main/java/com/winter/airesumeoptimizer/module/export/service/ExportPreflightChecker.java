package com.winter.airesumeoptimizer.module.export.service;

import com.winter.airesumeoptimizer.infra.render.PdfLayoutInspection;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentBasicsDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentContactDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Structured Resume + 最终 PDF 的统一轻量导出检查。 */
@Component
public class ExportPreflightChecker {

    /** Phase 6 默认简历建议不超过两页；只告警，不自动删改或阻断用户内容。 */
    public static final int RECOMMENDED_MAX_PAGE_COUNT = 2;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^[+()\\-\\s0-9]{7,}$");
    private static final Pattern URL = Pattern.compile("^(https?://|www\\.).+", Pattern.CASE_INSENSITIVE);

    public ExportPreflight check(ResumeDocumentDTO document, PdfLayoutInspection layout) {
        boolean missingContact = !hasNonBlankContact(document);
        boolean pageLimitExceeded = layout.pageCount() > RECOMMENDED_MAX_PAGE_COUNT;
        boolean overflowDetected = layout.overflowDetected();
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
        return new ExportPreflight(
                layout.pageCount(), missingContact, pageLimitExceeded, overflowDetected, List.copyOf(warnings));
    }

    private boolean hasNonBlankContact(ResumeDocumentDTO document) {
        ResumeDocumentBasicsDTO basics = document == null ? null : document.getBasics();
        if (basics == null || basics.getContacts() == null) {
            return false;
        }
        for (ResumeDocumentContactDTO contact : basics.getContacts()) {
            if (contact == null || contact.getValue() == null || contact.getValue().isBlank()) {
                continue;
            }
            String label = contact.getLabel() == null
                    ? ""
                    : contact.getLabel().strip().toLowerCase(Locale.ROOT);
            String value = contact.getValue().strip();
            if (label.contains("邮箱")
                    || label.contains("email")
                    || label.contains("电话")
                    || label.contains("手机")
                    || label.contains("phone")
                    || label.contains("mobile")
                    || label.contains("微信")
                    || label.contains("wechat")
                    || label.contains("linkedin")
                    || label.contains("领英")
                    || label.contains("github")
                    || label.contains("网站")
                    || label.contains("website")
                    || EMAIL.matcher(value).matches()
                    || PHONE.matcher(value).matches()
                    || URL.matcher(value).matches()) {
                return true;
            }
        }
        return false;
    }
}
