import { getTaskStatus } from '@/api/task'
import type { AsyncTaskVO } from '@/types/task'

export interface AsyncTaskPollingOptions {
  taskId: number
  intervalMs?: number
  timeoutMs?: number
  onUpdate?: (task: AsyncTaskVO) => void | Promise<void>
  onSuccess?: (task: AsyncTaskVO) => void | Promise<void>
  onFailed?: (task: AsyncTaskVO) => void | Promise<void>
  onCancelled?: (task: AsyncTaskVO) => void | Promise<void>
  onTimeout?: (task: AsyncTaskVO | null) => void | Promise<void>
  onError?: (error: unknown) => void | Promise<void>
}

export interface AsyncTaskPollingController {
  stop: () => void
}

const DEFAULT_INTERVAL_MS = 2000
const DEFAULT_TIMEOUT_MS = 180000

export const startAsyncTaskPolling = (options: AsyncTaskPollingOptions): AsyncTaskPollingController => {
  const intervalMs = options.intervalMs ?? DEFAULT_INTERVAL_MS
  const timeoutMs = options.timeoutMs ?? DEFAULT_TIMEOUT_MS
  const startedAt = Date.now()
  let stopped = false
  let timerId: number | null = null
  let lastTask: AsyncTaskVO | null = null

  const clearTimer = () => {
    if (timerId !== null) {
      window.clearTimeout(timerId)
      timerId = null
    }
  }

  const stop = () => {
    stopped = true
    clearTimer()
  }

  const scheduleNext = () => {
    clearTimer()
    timerId = window.setTimeout(runOnce, intervalMs)
  }

  const runOnce = async () => {
    if (stopped) {
      return
    }

    if (Date.now() - startedAt > timeoutMs) {
      stop()
      await options.onTimeout?.(lastTask)
      return
    }

    try {
      const task = await getTaskStatus(options.taskId)
      lastTask = task
      await options.onUpdate?.(task)

      if (task.status === 'SUCCESS') {
        stop()
        await options.onSuccess?.(task)
        return
      }

      if (task.status === 'FAILED') {
        stop()
        await options.onFailed?.(task)
        return
      }

      if (task.status === 'CANCELLED') {
        stop()
        await options.onCancelled?.(task)
        return
      }

      scheduleNext()
    } catch (error) {
      stop()
      await options.onError?.(error)
    }
  }

  void runOnce()

  return { stop }
}
