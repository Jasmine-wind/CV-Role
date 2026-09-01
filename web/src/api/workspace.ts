import request, { downloadBlob, downloadPdfResponse } from '@/api/request'
import type {
  BulletSuggestionRequest,
  BulletSuggestionResult,
  ExportArtifact,
  ResumeTemplateId,
  WorkspaceContent,
  WorkspaceExportRequest,
  WorkspacePreviewPdf,
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

/**
 * PDF 预览：只渲染服务端已保存内容；expectedRevision 不一致时服务端拒绝，
 * 防止把过期 Preview 当作当前版本。渲染为同步编译，放宽超时。
 */
export const previewWorkspacePdf = (
  optimizationTaskId: number,
  templateId: ResumeTemplateId,
  expectedRevision: number,
) => {
  const params = new URLSearchParams({ templateId, expectedRevision: String(expectedRevision) })
  return downloadPdfResponse(
    `/api/workspace/${optimizationTaskId}/preview.pdf?${params.toString()}`,
  ).then(({ blob, headers }): WorkspacePreviewPdf => {
    const required = (name: string) => {
      const value = headers[name]
      if (!value) throw new Error('预览响应缺少服务端绑定信息，请重试')
      return value
    }
    const integer = (name: string) => {
      const value = Number(required(name))
      if (!Number.isInteger(value) || value < 0) throw new Error('预览响应版本信息无效')
      return value
    }
    const flag = (name: string) => required(name) === 'true'
    return {
      blob,
      contentRevision: integer('x-content-revision'),
      targetResumeVersionId: integer('x-target-resume-version'),
      templateVersion: required('x-template-version'),
      rendererVersion: required('x-renderer-version'),
      previewReceipt: required('x-preview-receipt'),
      preflight: {
        pageCount: integer('x-resume-page-count'),
        missingContact: flag('x-resume-missing-contact'),
        pageLimitExceeded: flag('x-resume-page-limit-exceeded'),
        overflowDetected: flag('x-resume-overflow-detected'),
        orphanFinalPage: flag('x-resume-orphan-final-page'),
        readabilityTooSmall: flag('x-resume-readability-too-small'),
        needsReview: flag('x-resume-needs-review'),
      },
    }
  })
}

/** 导出 PDF：编译、存储与记录全部成功后返回导出物。 */
export const exportWorkspacePdf = (
  optimizationTaskId: number,
  data: WorkspaceExportRequest,
) => {
  return request.post<ExportArtifact>(`/api/workspace/${optimizationTaskId}/export`, data, {
    timeout: BULLET_SUGGESTION_TIMEOUT_MS,
  })
}

export const listWorkspaceArtifacts = (optimizationTaskId: number) => {
  return request.get<ExportArtifact[]>(`/api/workspace/${optimizationTaskId}/artifacts`)
}

export const downloadArtifactPdf = (artifactId: number) => {
  return downloadBlob(`/api/workspace/artifacts/${artifactId}/download`)
}

export const deleteWorkspaceArtifact = (artifactId: number) => {
  return request.delete<void>(`/api/workspace/artifacts/${artifactId}`)
}
