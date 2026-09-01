package com.winter.airesumeoptimizer.module.workspace.service;

import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;

/**
 * RESUME_DOCUMENT_V1 文档转换契约。
 * normalize 是唯一写入口径；历史 generic V1 文档只读升级为同一 V1 语义结构。
 */
public interface ResumeDocumentConverter {

    /** 归一化并校验用户提交/持久化内容；结构违反契约时拒绝而不是静默修正。 */
    ResumeDocumentDTO normalize(ResumeDocumentDTO document);

    /**
     * 读取持久化文档内容：V1 语义文档直接归一化，Slice A 之前的 generic V1 内容按确定性规则升级。
     * 无法安全升级时显式失败引导重新解析，不做降级产出。
     */
    ResumeDocumentDTO upgradeLegacyDocument(String persistedJson);
}
