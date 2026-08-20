import request from '@/api/request'
import type {
  WorkspaceContent,
  WorkspaceSaveRequest,
  WorkspaceSaveResult,
} from '@/types/workspace'

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
