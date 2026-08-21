package com.winter.airesumeoptimizer.module.export.service;

import com.winter.airesumeoptimizer.module.export.dto.WorkspaceExportRequestDTO;
import com.winter.airesumeoptimizer.module.export.vo.ExportArtifactVO;
import java.util.List;

/**
 * Phase 6 Preview / Export 边界。
 *
 * <p>唯一入口身份仍是 optimizationTaskId：服务端从任务解析 TARGET 岗位版本并只读取
 * 已持久化的 structured_content 与服务端 revision。前端不能指定可渲染的 ResumeVersion，
 * 也不能用草稿、SOURCE、输入快照或证据分析作为渲染输入；expectedRevision 与服务端
 * 不一致时一律拒绝，防止静默渲染旧版本。
 */
public interface WorkspaceExportService {

    /**
     * PDF 预览：同步渲染当前服务端已保存的 TARGET 内容。
     * Preview 与导出共享同一 Renderer、模板版本与字体环境。
     */
    RenderedPdf preview(Long userId, Long optimizationTaskId, String templateId, Long expectedRevision);

    /**
     * 生成可下载导出物：必须验证最近 Preview 的服务端签名 receipt 对 user / task /
     * TARGET / revision / template+version / renderer / PDF checksum 的完整绑定；随后 PDF 编译成功 +
     * 存储成功 + 数据库记录成功才返回。数据库记录失败必须补偿删除已写入的存储对象。
     */
    ExportArtifactVO export(Long userId, Long optimizationTaskId, WorkspaceExportRequestDTO request);

    /** 列出当前任务的导出物；只返回属于当前用户的记录。 */
    List<ExportArtifactVO> listArtifacts(Long userId, Long optimizationTaskId);

    /** 下载导出物：校验 current_user + artifact 归属，读取时复核内容校验和。 */
    ArtifactDownload loadArtifact(Long userId, Long artifactId);

    /** 删除导出物：先移除数据库记录使其不可访问，再尽力删除存储对象。 */
    void deleteArtifact(Long userId, Long artifactId);
}
