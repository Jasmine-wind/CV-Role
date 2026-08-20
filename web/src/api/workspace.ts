import request from '@/api/request'
import type {
  BulletSuggestionRequest,
  BulletSuggestionResult,
  WorkspaceContent,
  WorkspaceSaveRequest,
  WorkspaceSaveResult,
} from '@/types/workspace'

/** AI 生成可能超过默认请求超时，单独放宽；服务端自身有 AI 调用超时兜底。 */
const BULLET_SUGGESTION_TIMEOUT_MS = 65000

export const getWorkspaceContent = (optimizationTaskId: number) => {
  return request.get<WorkspaceContent>(`/api/workspace/${optimizationTaskId}/content`)
}

export const saveWorkspaceContent = (
  optimizationTaskId: number,
  data: WorkspaceSaveRequest,
) => {
  return request.put<WorkspaceSaveResult>(`/api/workspace/${optimizationTaskId}/content`, data)
}

export const restorePreOptimizationContent = (
  optimizationTaskId: number,
  expectedRevision: number,
) => {
  return request.post<WorkspaceSaveResult>(
    `/api/workspace/${optimizationTaskId}/restore-pre-optimization`,
    { expectedRevision },
  )
}

export const requestBulletSuggestion = (
  optimizationTaskId: number,
  data: BulletSuggestionRequest,
) => {
  return request.post<BulletSuggestionResult>(
    `/api/workspace/${optimizationTaskId}/bullet-suggestion`,
    data,
    { timeout: BULLET_SUGGESTION_TIMEOUT_MS },
  )
}
