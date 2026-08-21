package com.winter.airesumeoptimizer.module.export.service;

import java.util.List;

/** 导出前轻量检查；warnings 使用稳定机器码，前端负责用户文案。 */
public record ExportPreflight(
        int pageCount,
        boolean missingContact,
        boolean pageLimitExceeded,
        boolean overflowDetected,
        List<String> warnings) {
}
