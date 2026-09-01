package com.winter.airesumeoptimizer.module.export.service;

import java.util.List;

/** 导出前轻量检查；warnings 使用稳定机器码，前端负责文案。 */
public record ExportPreflight(
        int pageCount,
        boolean missingContact,
        boolean pageLimitExceeded,
        boolean overflowDetected,
        boolean orphanFinalPage,
        boolean readabilityTooSmall,
        boolean needsReview,
        List<String> warnings) {

    /** Slice A 之前的构造兼容入口；旧测试/历史调用没有字体检查事实。 */
    public ExportPreflight(
            int pageCount,
            boolean missingContact,
            boolean pageLimitExceeded,
            boolean overflowDetected,
            boolean orphanFinalPage,
            boolean needsReview,
            List<String> warnings) {
        this(pageCount, missingContact, pageLimitExceeded, overflowDetected,
                orphanFinalPage, false, needsReview, warnings);
    }
}
