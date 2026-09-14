package com.winter.airesumeoptimizer.module.workspace.service;

import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;

/**
 * RESUME_DOCUMENT_V1 文档转换契约。
 * normalize 负责结构归一化；Workspace 写入必须经 normalizeWorkspaceSave 重建服务端可信 provenance。
 * 历史 generic V1 文档只读升级为同一 V1 语义结构。
 */
public interface ResumeDocumentConverter {

    /** 归一化并校验内部生成/持久化内容；结构违反契约时拒绝而不是静默修正。 */
    ResumeDocumentDTO normalize(ResumeDocumentDTO document);

    /**
     * Normalizes an untrusted Workspace edit while restoring provenance exclusively from the
     * task-frozen SOURCE. Existing stable IDs must retain their server-known parent lineage;
     * newly created IDs remain unreferenced.
     */
    ResumeDocumentDTO normalizeWorkspaceSave(
            ResumeDocumentDTO submitted,
            ResumeDocumentDTO currentTarget,
            ResumeDocumentDTO frozenSource);

    /**
     * 读取持久化文档内容：V1 语义文档直接归一化，Slice A 之前的 generic V1 内容按确定性规则升级。
     * 无法安全升级时显式失败引导重新解析，不做降级产出。
     */
    ResumeDocumentDTO upgradeLegacyDocument(String persistedJson);
}
