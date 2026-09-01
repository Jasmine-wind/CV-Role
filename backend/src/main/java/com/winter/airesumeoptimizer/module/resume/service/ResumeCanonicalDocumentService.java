package com.winter.airesumeoptimizer.module.resume.service;

import com.winter.airesumeoptimizer.module.resume.dto.ResumeStructuredContentDTO;
import com.winter.airesumeoptimizer.module.resume.dto.ResumeUnresolvedItemDTO;
import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;
import java.util.List;

/**
 * 候选解析 → canonical 交付文档的可信边界（Slice A）。
 *
 * <p>解析产出的结构化内容只是候选，不是事实；本服务把候选投影成通过结构白名单的
 * RESUME_DOCUMENT_V1 语义文档，并把无法可靠判定归属的内容显式收集为未决候选项，
 * 而不是机械追加到正式文档。调用方负责把通过质量门的文档唯一物化到 SOURCE
 * resume_versions.structured_content。
 */
public interface ResumeCanonicalDocumentService {

    /** 构建结果：canonical 文档 + 未决候选项（审查态数据，不是简历内容）。 */
    record BuildResult(ResumeDocumentDTO document, List<ResumeUnresolvedItemDTO> unresolvedItems) {
    }

    BuildResult build(ResumeStructuredContentDTO structuredContent);

    /** 兼容入口：从持久化的候选解析 JSON 构建（历史行按需投影使用）。 */
    BuildResult buildFromStructuredJson(String structuredJson);
}
