package com.winter.airesumeoptimizer.module.workspace.service;

import com.winter.airesumeoptimizer.module.workspace.dto.WorkspaceContentSaveRequestDTO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentSaveResultVO;
import com.winter.airesumeoptimizer.module.workspace.vo.WorkspaceContentVO;

/**
 * Optimization Workspace 内容边界。
 *
 * <p>Workspace 的唯一入口身份是 optimizationTaskId；SOURCE / TARGET / JobTarget 全部由任务在服务端解析，
 * 调用方不能指定可写的 ResumeVersion。只有当前任务的 TARGET 岗位版本可被编辑，
 * SOURCE、resume_input_snapshot 与正式证据分析永远只读。
 */
public interface WorkspaceContentService {

    /**
     * 读取当前任务 TARGET 岗位版本的可编辑文档与服务端内容版本号。
     *
     * <p>revision 为 0 表示 TARGET 尚未被编辑过，此时按任务冻结的解析快照确定性地生成编辑文档；
     * revision 大于 0 时直接返回已持久化的编辑文档。刷新或重新进入只恢复最后成功持久化的服务端内容。
     */
    WorkspaceContentVO getContent(Long userId, Long optimizationTaskId);

    /**
     * 以 expectedRevision 乐观并发控制整体替换 TARGET 内容。
     *
     * <p>仅当 expectedRevision 等于服务端当前 revision 时原子写入并递增；
     * 否则返回冲突结果与当前服务端 revision，不覆盖任何更新的版本。
     */
    WorkspaceContentSaveResultVO saveContent(Long userId, Long optimizationTaskId, WorkspaceContentSaveRequestDTO request);

    /**
     * 恢复本次优化前版本：基于任务冻结的 resume_input_snapshot 重新生成编辑文档，
     * 作为 TARGET 的新 revision 写入，遵循与保存完全相同的 expectedRevision 并发规则。
     * 不回写 SOURCE、快照或证据分析。
     */
    WorkspaceContentSaveResultVO restorePreOptimizationContent(
            Long userId, Long optimizationTaskId, Long expectedRevision);
}
