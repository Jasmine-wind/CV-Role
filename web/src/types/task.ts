export type AsyncTaskStatus = 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | string

export interface AsyncTaskVO {
  taskId: number
  taskType: string
  bizType?: string | null
  bizId?: number | null
  status: AsyncTaskStatus
  progress: number
  message?: string | null
  resultType?: string | null
  resultId?: number | null
  resultSummary?: string | null
  errorCode?: string | null
  errorMessage?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface AsyncTaskSubmitResult {
  taskId: number
}
