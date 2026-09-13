import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { AsyncTaskVO } from '@/types/task'

const { getTaskStatus } = vi.hoisted(() => ({ getTaskStatus: vi.fn() }))
vi.mock('@/api/task', () => ({ getTaskStatus }))

import { startAsyncTaskPolling } from '../asyncTaskPolling'

const task = (status: string): AsyncTaskVO => ({
  taskId: 1,
  taskType: 'JOB_ANALYSIS',
  status,
  progress: 0,
})

beforeEach(() => {
  getTaskStatus.mockReset()
})

describe('async task polling cancellation fence', () => {
  it('aborts an in-flight request and ignores its late response after stop', async () => {
    let resolveRequest!: (value: AsyncTaskVO) => void
    getTaskStatus.mockReturnValue(new Promise<AsyncTaskVO>((resolve) => {
      resolveRequest = resolve
    }))
    const onUpdate = vi.fn()
    const onSuccess = vi.fn()
    const onError = vi.fn()

    const controller = startAsyncTaskPolling({ taskId: 1, onUpdate, onSuccess, onError })
    const signal = getTaskStatus.mock.calls[0]?.[1] as AbortSignal
    expect(signal.aborted).toBe(false)

    controller.stop()
    expect(signal.aborted).toBe(true)
    resolveRequest(task('SUCCESS'))
    await Promise.resolve()
    await Promise.resolve()

    expect(onUpdate).not.toHaveBeenCalled()
    expect(onSuccess).not.toHaveBeenCalled()
    expect(onError).not.toHaveBeenCalled()
  })

  it('does not deliver success when identity teardown happens during onUpdate', async () => {
    let releaseUpdate!: () => void
    const updatePending = new Promise<void>((resolve) => {
      releaseUpdate = resolve
    })
    getTaskStatus.mockResolvedValue(task('SUCCESS'))
    const onSuccess = vi.fn()
    const controller = startAsyncTaskPolling({
      taskId: 1,
      onUpdate: () => updatePending,
      onSuccess,
    })

    await Promise.resolve()
    controller.stop()
    releaseUpdate()
    await Promise.resolve()
    await Promise.resolve()

    expect(onSuccess).not.toHaveBeenCalled()
  })
})
