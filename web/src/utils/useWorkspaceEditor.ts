import { computed, ref } from 'vue'
import {
  getWorkspaceContent,
  restorePreOptimizationContent,
  saveWorkspaceContent,
} from '@/api/workspace'
import type { ResumeDocument } from '@/types/resume-document'
import type {
  WorkspaceContent,
  WorkspaceSaveRequest,
  WorkspaceSaveResult,
  WorkspaceSaveStatus,
} from '@/types/workspace'

const DEFAULT_SAVE_DEBOUNCE_MS = 800
const DEFAULT_SAVE_MAX_WAIT_MS = 4000
const MAX_UNDO_ENTRIES = 50

export interface WorkspaceEditorApi {
  getContent: (optimizationTaskId: number) => Promise<WorkspaceContent>
  saveContent: (
    optimizationTaskId: number,
    request: WorkspaceSaveRequest,
  ) => Promise<WorkspaceSaveResult>
  restoreContent: (
    optimizationTaskId: number,
    expectedRevision: number,
  ) => Promise<WorkspaceSaveResult>
}

export interface WorkspaceEditorOptions {
  api?: WorkspaceEditorApi
  saveDebounceMs?: number
  saveMaxWaitMs?: number
}

const defaultApi: WorkspaceEditorApi = {
  getContent: getWorkspaceContent,
  saveContent: saveWorkspaceContent,
  restoreContent: restorePreOptimizationContent,
}

/**
 * Workspace 编辑会话状态机。
 *
 * revision 始终是最后一次成功持久化的服务端版本号。所有写请求串行执行；
 * 保存或恢复期间产生的新编辑通过 draftSequence 识别，旧响应只能更新 revision，
 * 不能把更新后的草稿错标为 saved。冲突保留本地草稿并停止自动重试。
 */
export function useWorkspaceEditor(
  optimizationTaskId: number,
  options: WorkspaceEditorOptions = {},
) {
  const api = options.api ?? defaultApi
  const saveDebounceMs = options.saveDebounceMs ?? DEFAULT_SAVE_DEBOUNCE_MS
  const saveMaxWaitMs = options.saveMaxWaitMs ?? DEFAULT_SAVE_MAX_WAIT_MS

  const loading = ref(false)
  const loadError = ref<string | null>(null)
  const revision = ref<number | null>(null)
  const draft = ref<ResumeDocument | null>(null)
  const status = ref<WorkspaceSaveStatus>('saved')
  const conflictRevision = ref<number | null>(null)
  const saveError = ref<string | null>(null)

  const undoStack = ref<ResumeDocument[]>([])
  const redoStack = ref<ResumeDocument[]>([])
  /** 草稿变更序号：任何用户侧草稿变化（编辑/Undo/Redo/恢复/采纳服务端）都递增，供 AI 建议判别失效。 */
  const mutationSequence = ref(0)
  const canUndo = computed(() => undoStack.value.length > 0)
  const canRedo = computed(() => redoStack.value.length > 0)
  const hasUnsavedChanges = computed(
    () =>
      status.value === 'dirty' ||
      status.value === 'saving' ||
      status.value === 'failed' ||
      conflictRevision.value !== null,
  )

  let debounceTimer: ReturnType<typeof setTimeout> | null = null
  let maxWaitTimer: ReturnType<typeof setTimeout> | null = null
  let pendingSince: number | null = null
  let saving = false
  let disposed = false
  let draftSequence = 0

  // Vue ref 会把文档变成深层 Proxy，structuredClone 不能复制 Proxy；文档是纯 JSON 数据。
  const clone = (document: ResumeDocument): ResumeDocument =>
    JSON.parse(JSON.stringify(document)) as ResumeDocument

  const clearSaveTimers = () => {
    if (debounceTimer) {
      clearTimeout(debounceTimer)
      debounceTimer = null
    }
    if (maxWaitTimer) {
      clearTimeout(maxWaitTimer)
      maxWaitTimer = null
    }
  }

  const load = async () => {
    loading.value = true
    loadError.value = null
    try {
      const content = await api.getContent(optimizationTaskId)
      if (disposed) return
      revision.value = content.revision
      draft.value = clone(content.document)
      draftSequence = 0
      mutationSequence.value = 0
      undoStack.value = []
      redoStack.value = []
      conflictRevision.value = null
      saveError.value = null
      pendingSince = null
      status.value = 'saved'
    } catch (error) {
      if (!disposed) {
        loadError.value = error instanceof Error ? error.message : '工作区内容加载失败'
      }
    } finally {
      if (!disposed) loading.value = false
    }
  }

  const pushUndo = (snapshot: ResumeDocument) => {
    undoStack.value.push(clone(snapshot))
    if (undoStack.value.length > MAX_UNDO_ENTRIES) undoStack.value.shift()
  }

  const scheduleSave = () => {
    if (disposed || saving || conflictRevision.value !== null) return
    if (pendingSince === null) pendingSince = Date.now()

    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
      debounceTimer = null
      void flush()
    }, saveDebounceMs)

    if (!maxWaitTimer) {
      const elapsed = Date.now() - pendingSince
      maxWaitTimer = setTimeout(() => {
        maxWaitTimer = null
        void flush()
      }, Math.max(0, saveMaxWaitMs - elapsed))
    }
  }

  const flush = async () => {
    if (disposed || saving || conflictRevision.value !== null) return
    if (!draft.value || revision.value === null) return

    clearSaveTimers()
    const snapshot = clone(draft.value)
    const snapshotSequence = draftSequence
    const expectedRevision = revision.value
    pendingSince = null
    saving = true
    status.value = 'saving'

    try {
      const result = await api.saveContent(optimizationTaskId, {
        expectedRevision,
        document: snapshot,
      })
      if (disposed) return
      if (!result.saved) {
        conflictRevision.value = result.revision
        status.value = 'conflict'
        return
      }

      revision.value = result.revision
      conflictRevision.value = null
      saveError.value = null
      if (draftSequence === snapshotSequence) {
        draft.value = clone(result.document ?? snapshot)
        status.value = 'saved'
      } else {
        status.value = 'dirty'
        pendingSince = Date.now()
      }
    } catch (error) {
      if (!disposed) {
        saveError.value = error instanceof Error ? error.message : '保存失败，请稍后重试'
        status.value = 'failed'
      }
    } finally {
      saving = false
      if (!disposed && status.value === 'dirty') scheduleSave()
    }
  }

  const markDirty = () => {
    draftSequence += 1
    mutationSequence.value = draftSequence
    if (conflictRevision.value !== null) {
      status.value = 'conflict'
      return
    }
    status.value = 'dirty'
    if (pendingSince === null) pendingSince = Date.now()
    if (!saving) scheduleSave()
  }

  /** 编辑器产生新文档：形成新的 dirty 草稿并安排自动保存。 */
  const applyDocument = (next: ResumeDocument) => {
    if (!draft.value || disposed) return
    pushUndo(draft.value)
    redoStack.value = []
    draft.value = clone(next)
    markDirty()
  }

  const undo = () => {
    const previous = undoStack.value.pop()
    if (!previous || !draft.value || disposed) return
    redoStack.value.push(clone(draft.value))
    draft.value = previous
    markDirty()
  }

  const redo = () => {
    const next = redoStack.value.pop()
    if (!next || !draft.value || disposed) return
    pushUndo(draft.value)
    draft.value = next
    markDirty()
  }

  /** failed 后保留当前草稿；重试始终读取调用时的最新 draft。 */
  const retrySave = async () => {
    if (disposed || saving) return
    saveError.value = null
    status.value = 'dirty'
    if (pendingSince === null) pendingSince = Date.now()
    await flush()
  }

  /** 显式覆盖仍先读取最新 revision，再执行普通 expectedRevision 条件保存。 */
  const overwriteWithLocalDraft = async () => {
    if (disposed || saving || !draft.value) return
    try {
      const latest = await api.getContent(optimizationTaskId)
      if (disposed) return
      revision.value = latest.revision
      conflictRevision.value = null
      saveError.value = null
      status.value = 'dirty'
      pendingSince = Date.now()
      await flush()
    } catch (error) {
      if (!disposed) {
        saveError.value = error instanceof Error ? error.message : '保存失败，请稍后重试'
        // revision 重新读取失败时仍处于原冲突，必须保留冲突处置入口；
        // 条件保存自身失败时 conflictRevision 已清除，才进入普通 failed/retry。
        status.value = conflictRevision.value !== null ? 'conflict' : 'failed'
      }
      throw error
    }
  }

  /** 放弃本地草稿前重新读取服务端；读取期间的新编辑不会被晚到响应覆盖。 */
  const adoptServerVersion = async () => {
    if (disposed || saving || !draft.value) return
    clearSaveTimers()
    const sequenceBeforeLoad = draftSequence
    const latest = await api.getContent(optimizationTaskId)
    if (disposed) return
    revision.value = latest.revision
    if (draftSequence !== sequenceBeforeLoad) {
      conflictRevision.value = latest.revision
      status.value = 'conflict'
      return
    }
    pushUndo(draft.value)
    redoStack.value = []
    draft.value = clone(latest.document)
    draftSequence += 1
    mutationSequence.value = draftSequence
    conflictRevision.value = null
    saveError.value = null
    pendingSince = null
    status.value = 'saved'
  }

  /** 恢复走同一串行/CAS通道；恢复期间的新编辑保留并基于新 revision 继续保存。 */
  const restorePreOptimization = async (): Promise<'saved' | 'conflict' | 'failed'> => {
    if (disposed || saving || revision.value === null || !draft.value) return 'failed'

    clearSaveTimers()
    const expectedRevision = revision.value
    const sequenceBeforeRestore = draftSequence
    const draftBeforeRestore = clone(draft.value)
    pendingSince = null
    saving = true
    status.value = 'saving'

    try {
      const result = await api.restoreContent(optimizationTaskId, expectedRevision)
      if (disposed) return 'failed'
      if (!result.saved || !result.document) {
        conflictRevision.value = result.revision
        status.value = 'conflict'
        return 'conflict'
      }

      revision.value = result.revision
      conflictRevision.value = null
      saveError.value = null
      if (draftSequence === sequenceBeforeRestore) {
        pushUndo(draftBeforeRestore)
        redoStack.value = []
        draft.value = clone(result.document)
        draftSequence += 1
        mutationSequence.value = draftSequence
        status.value = 'saved'
        return 'saved'
      }

      status.value = 'dirty'
      pendingSince = Date.now()
      return 'saved'
    } catch (error) {
      if (!disposed) {
        saveError.value = error instanceof Error ? error.message : '恢复失败，请稍后重试'
        status.value = 'failed'
      }
      return 'failed'
    } finally {
      saving = false
      if (!disposed && status.value === 'dirty') scheduleSave()
    }
  }

  const dispose = () => {
    disposed = true
    clearSaveTimers()
  }

  return {
    loading,
    loadError,
    revision,
    draft,
    status,
    conflictRevision,
    saveError,
    mutationSequence,
    canUndo,
    canRedo,
    hasUnsavedChanges,
    load,
    applyDocument,
    undo,
    redo,
    retrySave,
    overwriteWithLocalDraft,
    adoptServerVersion,
    restorePreOptimization,
    flushNow: flush,
    dispose,
  }
}
