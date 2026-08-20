import type { ResumeDocument } from '@/types/resume-document'

export interface WorkspaceContent {
  optimizationTaskId: number
  revision: number
  document: ResumeDocument
}

export interface WorkspaceSaveRequest {
  expectedRevision: number
  document: ResumeDocument
}

export interface WorkspaceSaveResult {
  saved: boolean
  conflict: boolean
  revision: number
  document: ResumeDocument | null
}

/**
 * 自动保存状态机：
 * dirty 编辑后未保存；saving 保存中（期间继续编辑仍为 dirty）；saved 最近一次保存成功；
 * failed 保存失败，本地草稿保留可重试；conflict 服务端已有更新版本，停止盲目重试。
 */
export type WorkspaceSaveStatus = 'dirty' | 'saving' | 'saved' | 'failed' | 'conflict'
