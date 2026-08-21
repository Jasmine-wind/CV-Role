package com.winter.airesumeoptimizer.infra.render;

import com.winter.airesumeoptimizer.module.workspace.dto.ResumeDocumentDTO;

/**
 * 简历 PDF 渲染 seam：Structured Resume JSON → 确定性映射 → 版本化模板 → Typst 编译 → PDF。
 *
 * <p>实现不承担业务判断：输入必须是已经持久化并通过 Schema 校验的结构化简历文档，
 * 调用方负责内容归属与 revision 一致性。Preview 与 Export 必须共享同一实现、
 * 同一模板版本与同一字体环境，保证所见即所得。
 */
public interface ResumePdfRenderer {

    /** 渲染器实现版本，记录进 ExportArtifact 以便复现历史导出。 */
    String RENDERER_VERSION = "typst-resume-renderer/1";

    /**
     * 同步渲染 PDF。
     *
     * @throws ResumeRenderException 文档无法映射、模板缺失、编译失败或超时；
     *         失败不产生任何中间产物，临时文件必须被清理。
     */
    ResumePdfRenderResult render(ResumeDocumentDTO document, ResumeTemplateId template);
}
