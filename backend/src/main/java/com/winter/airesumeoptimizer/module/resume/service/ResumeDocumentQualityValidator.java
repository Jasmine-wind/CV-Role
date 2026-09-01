package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeQualityIssueDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeUnresolvedItemDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import java.util.List;
import java.util.regex.Pattern;

/**
 * canonical 简历文档的确定性质量验证（Slice A）。
 * 只做代码级检测，不依赖 LLM：能确定则接受，不确定转未决项，明显错误阻止 READY。
 */
public interface ResumeDocumentQualityValidator {

    Pattern PHONE_FORMAT = Pattern.compile("^\\+?[0-9][0-9\\s\\-()]{5,19}$");
    Pattern EMAIL_FORMAT = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** 验证结果：问题清单与由此推导的质量状态。 */
    record ValidationResult(String qualityStatus, List<ResumeQualityIssueDTO> issues) {
    }

    ValidationResult validate(ResumeDocumentDTO document, List<ResumeUnresolvedItemDTO> unresolvedItems);

    static boolean isValidPhone(String value) {
        return value != null && PHONE_FORMAT.matcher(value.strip()).matches();
    }

    static boolean isValidEmail(String value) {
        return value != null && EMAIL_FORMAT.matcher(value.strip()).matches();
    }
}
