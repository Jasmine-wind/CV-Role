import request from '@/api/request'
import type { AsyncTaskVO } from '@/types/task'

export const getTaskStatus = (taskId: number) => {
  return request.get<AsyncTaskVO>(`/api/tasks/${taskId}`)
}
