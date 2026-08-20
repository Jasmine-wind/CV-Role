package com.winter.airesumeoptimizer.module.workspace.service;

import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;

/**
 * Structured Resume Document 与既有解析快照之间的转换边界。
 * 编辑器、保存与后续渲染只面向 {@link ResumeDocumentDTO}，不直接依赖 V1 解析输出结构。
 */
public interface ResumeDocumentConverter {

    /**
     * 从任务冻结的 V1 简历解析快照生成可编辑文档。
     * 转换是确定性的，恢复“本次优化前版本”时按同一输入重新生成。
     */
    ResumeDocumentDTO fromParsedSnapshot(String structuredJson);

    /**
     * 归一化并校验用户提交的文档：补齐稳定 ID、裁剪文本、执行编辑上限。
     * 超限时抛出业务异常，不会落库。
     */
    ResumeDocumentDTO normalize(ResumeDocumentDTO document);
}
