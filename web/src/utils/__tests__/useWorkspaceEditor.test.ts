import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ResumeDocument } from '@/types/resume-document'
import { useWorkspaceEditor } from '@/utils/useWorkspaceEditor'
import {
  getWorkspaceContent,
  restorePreOptimizationContent,
  saveWorkspaceContent,
} from '@/api/workspace'

vi.mock('@/api/workspace', () => ({
  getWorkspaceContent: vi.fn(),
  saveWorkspaceContent: vi.fn(),
  restorePreOptimizationContent: vi.fn(),
}))

const getContentMock = vi.mocked(getWorkspaceContent)
const saveContentMock = vi.mocked(saveWorkspaceContent)
const restoreContentMock = vi.mocked(restorePreOptimizationContent)

const document = (text: string): ResumeDocument => ({
  schemaVersion: 'RESUME_DOCUMENT_V1',
  basics: { name: '张三', contacts: [] },
  sections: [
    {
      id: 's-1',
      kind: 'EXPERIENCE',
      title: '工作经历',
      entries: [
        {
          id: 'e-1',
          heading: '某公司',
          meta: null,
          bullets: [{ id: 'b-1', text }],
        },
      ],
    },
  ],
})

const deferred = <T>() => {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

const settle = async () => {
  await Promise.resolve()
  await Promise.resolve()
}

describe('useWorkspaceEditor', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    getContentMock.mockResolvedValue({
      optimizationTaskId: 10,
      revision: 0,
      document: document('初始内容'),
    })
  })

  it('CAS-saves a pristine revision before allowing render', async () => {
    saveContentMock.mockResolvedValueOnce({
      saved: true,
      conflict: false,
      revision: 1,
      document: document('初始内容'),
    })
    const editor = useWorkspaceEditor(10)
    await editor.load()

    await expect(editor.ensurePersistedForRender()).resolves.toBe(true)

    expect(saveContentMock).toHaveBeenCalledWith(10, {
      expectedRevision: 0,
      document: document('初始内容'),
    })
    expect(editor.revision.value).toBe(1)
    expect(editor.status.value).toBe('saved')
  })

  it('does not allow render when the pristine CAS save conflicts', async () => {
    saveContentMock.mockResolvedValueOnce({ saved: false, conflict: true, revision: 1, document: null })
    const editor = useWorkspaceEditor(10)
    await editor.load()

    await expect(editor.ensurePersistedForRender()).resolves.toBe(false)

    expect(editor.status.value).toBe('conflict')
    expect(editor.revision.value).toBe(0)
  })

  it('does not let an old save response mark a newer draft as saved', async () => {
    const first = deferred<Awaited<ReturnType<typeof saveWorkspaceContent>>>()
    saveContentMock
      .mockReturnValueOnce(first.promise)
      .mockResolvedValueOnce({ saved: true, conflict: false, revision: 2, document: document('第二次编辑') })
    const editor = useWorkspaceEditor(10)
    await editor.load()

    editor.applyDocument(document('第一次编辑'))
    await vi.advanceTimersByTimeAsync(800)
    editor.applyDocument(document('第二次编辑'))
    first.resolve({ saved: true, conflict: false, revision: 1, document: document('第一次编辑') })
    await settle()

    expect(editor.status.value).toBe('dirty')
    expect(editor.draft.value).toEqual(document('第二次编辑'))
    await vi.advanceTimersByTimeAsync(800)
    await settle()

    expect(saveContentMock).toHaveBeenLastCalledWith(10, {
      expectedRevision: 1,
      document: document('第二次编辑'),
    })
    expect(editor.status.value).toBe('saved')
  })

  it('retries a failed save with the latest draft rather than the failed snapshot', async () => {
    saveContentMock
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({ saved: true, conflict: false, revision: 1, document: document('最新草稿') })
    const editor = useWorkspaceEditor(10)
    await editor.load()

    editor.applyDocument(document('失败请求快照'))
    await vi.advanceTimersByTimeAsync(800)
    await settle()
    expect(editor.status.value).toBe('failed')

    editor.applyDocument(document('最新草稿'))
    await editor.retrySave()

    expect(saveContentMock).toHaveBeenLastCalledWith(10, {
      expectedRevision: 0,
      document: document('最新草稿'),
    })
    expect(editor.status.value).toBe('saved')
  })

  it('uses a refetched revision for overwrite and still surfaces a second conflict', async () => {
    saveContentMock
      .mockResolvedValueOnce({ saved: false, conflict: true, revision: 1, document: null })
      .mockResolvedValueOnce({ saved: false, conflict: true, revision: 2, document: null })
    getContentMock
      .mockResolvedValueOnce({ optimizationTaskId: 10, revision: 0, document: document('初始内容') })
      .mockResolvedValueOnce({ optimizationTaskId: 10, revision: 1, document: document('另一端内容') })
    const editor = useWorkspaceEditor(10)
    await editor.load()

    editor.applyDocument(document('本地草稿'))
    await vi.advanceTimersByTimeAsync(800)
    await settle()
    expect(editor.status.value).toBe('conflict')

    await editor.overwriteWithLocalDraft()

    expect(saveContentMock).toHaveBeenLastCalledWith(10, {
      expectedRevision: 1,
      document: document('本地草稿'),
    })
    expect(editor.status.value).toBe('conflict')
    expect(editor.draft.value).toEqual(document('本地草稿'))
  })

  it('keeps conflict recovery available when overwrite revision refetch fails', async () => {
    saveContentMock.mockResolvedValueOnce({ saved: false, conflict: true, revision: 1, document: null })
    getContentMock
      .mockResolvedValueOnce({ optimizationTaskId: 10, revision: 0, document: document('初始内容') })
      .mockRejectedValueOnce(new Error('refetch failed'))
    const editor = useWorkspaceEditor(10)
    await editor.load()

    editor.applyDocument(document('本地草稿'))
    await vi.advanceTimersByTimeAsync(800)
    await settle()

    await expect(editor.overwriteWithLocalDraft()).rejects.toThrow('refetch failed')
    expect(editor.status.value).toBe('conflict')
    expect(editor.conflictRevision.value).toBe(1)
    expect(editor.draft.value).toEqual(document('本地草稿'))
  })

  it('undoes and redoes in-session edits, clears redo after a new edit, and autosaves', async () => {
    saveContentMock.mockResolvedValue({
      saved: true,
      conflict: false,
      revision: 1,
      document: document('新分支编辑'),
    })
    const editor = useWorkspaceEditor(10)
    await editor.load()

    editor.applyDocument(document('编辑后'))
    editor.undo()
    expect(editor.draft.value).toEqual(document('初始内容'))
    expect(editor.canRedo.value).toBe(true)
    editor.redo()
    expect(editor.draft.value).toEqual(document('编辑后'))
    editor.undo()
    editor.applyDocument(document('新分支编辑'))
    expect(editor.canRedo.value).toBe(false)
    await vi.advanceTimersByTimeAsync(800)
    await settle()

    expect(saveContentMock).toHaveBeenCalledTimes(1)
    expect(saveContentMock).toHaveBeenCalledWith(10, {
      expectedRevision: 0,
      document: document('新分支编辑'),
    })
  })

  it('does not let continuous edits postpone the final save past max-wait', async () => {
    saveContentMock.mockResolvedValue({
      saved: true,
      conflict: false,
      revision: 1,
      document: document('第6次编辑'),
    })
    const editor = useWorkspaceEditor(10)
    await editor.load()

    for (let index = 1; index <= 6; index += 1) {
      editor.applyDocument(document(`第${index}次编辑`))
      await vi.advanceTimersByTimeAsync(700)
    }
    await settle()

    expect(saveContentMock).toHaveBeenCalledTimes(1)
    expect(saveContentMock).toHaveBeenCalledWith(10, {
      expectedRevision: 0,
      document: document('第6次编辑'),
    })
  })

  it('isolates a disposed task session from late responses and a new task session', async () => {
    const oldSave = deferred<Awaited<ReturnType<typeof saveWorkspaceContent>>>()
    getContentMock.mockImplementation(async (taskId) => ({
      optimizationTaskId: taskId,
      revision: 0,
      document: document(taskId === 10 ? '任务A' : '任务B'),
    }))
    saveContentMock.mockReturnValue(oldSave.promise)
    const editorA = useWorkspaceEditor(10)
    await editorA.load()
    editorA.applyDocument(document('任务A编辑'))
    await vi.advanceTimersByTimeAsync(800)

    editorA.dispose()
    const editorB = useWorkspaceEditor(20)
    await editorB.load()
    oldSave.resolve({ saved: true, conflict: false, revision: 1, document: document('任务A编辑') })
    await settle()

    expect(saveContentMock).toHaveBeenCalledWith(10, expect.anything())
    expect(editorB.revision.value).toBe(0)
    expect(editorB.draft.value).toEqual(document('任务B'))
    expect(editorB.status.value).toBe('saved')
  })

  it('adopts the canonical restored document and keeps the previous draft in undo history', async () => {
    restoreContentMock.mockResolvedValue({
      saved: true,
      conflict: false,
      revision: 1,
      document: document('冻结基线'),
    })
    const editor = useWorkspaceEditor(10)
    await editor.load()
    editor.applyDocument(document('待恢复草稿'))

    expect(await editor.restorePreOptimization()).toBe('saved')
    expect(editor.revision.value).toBe(1)
    expect(editor.draft.value).toEqual(document('冻结基线'))
    expect(editor.status.value).toBe('saved')
    editor.undo()
    expect(editor.draft.value).toEqual(document('待恢复草稿'))
    expect(editor.status.value).toBe('dirty')
  })

  it('preserves the local draft when restore detects a conflict', async () => {
    restoreContentMock.mockResolvedValue({ saved: false, conflict: true, revision: 2, document: null })
    const editor = useWorkspaceEditor(10)
    await editor.load()
    editor.applyDocument(document('本地待恢复草稿'))

    expect(await editor.restorePreOptimization()).toBe('conflict')
    expect(editor.conflictRevision.value).toBe(2)
    expect(editor.draft.value).toEqual(document('本地待恢复草稿'))
    expect(editor.status.value).toBe('conflict')
  })

  it('cancels the pending timer for restore and preserves edits made while restore is pending', async () => {
    const restore = deferred<Awaited<ReturnType<typeof restorePreOptimizationContent>>>()
    restoreContentMock.mockReturnValue(restore.promise)
    saveContentMock.mockResolvedValue({
      saved: true,
      conflict: false,
      revision: 2,
      document: document('恢复期间的新编辑'),
    })
    const editor = useWorkspaceEditor(10)
    await editor.load()

    editor.applyDocument(document('恢复前草稿'))
    const restorePromise = editor.restorePreOptimization()
    editor.applyDocument(document('恢复期间的新编辑'))
    await vi.advanceTimersByTimeAsync(800)
    expect(saveContentMock).not.toHaveBeenCalled()

    restore.resolve({ saved: true, conflict: false, revision: 1, document: document('冻结基线') })
    expect(await restorePromise).toBe('saved')
    expect(editor.draft.value).toEqual(document('恢复期间的新编辑'))
    expect(editor.status.value).toBe('dirty')

    await vi.advanceTimersByTimeAsync(800)
    await settle()
    expect(saveContentMock).toHaveBeenCalledWith(10, {
      expectedRevision: 1,
      document: document('恢复期间的新编辑'),
    })
  })
})
