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

export type BulletSuggestIntent =
  | 'JOB_TARGETED'
  | 'SIMPLIFY'
  | 'TECHNICAL_DEPTH'
  | 'HIGHLIGHT_OUTCOME'
  | 'CUSTOM'

/**
 * 单 Bullet 改写建议请求。生命周期绑定字段：
 * requestId（客户端生成，乱序/并发判别）、baseRevision（发起时的服务端版本号）、
 * bulletId（用户明确选中的要点）、originalTextHash（原文 SHA-256，人工编辑判别）。
 */
export interface BulletSuggestionRequest {
  requestId: string
  bulletId: string
  baseRevision: number
  originalText: string
  originalTextHash: string
  intent: BulletSuggestIntent
  userInstruction?: string | null
}

export type BulletSuggestionState = 'READY' | 'REJECTED'

/**
 * 建议只存在于当前会话：服务端不落库，Apply / Reject / Regenerate 都在前端完成；
 * Apply 只替换对应 Bullet 文本并走既有 Undo / dirty / Auto Save / CAS。
 */
export interface BulletSuggestionResult {
  requestId: string
  state: BulletSuggestionState
  baseRevision: number
  bulletId: string
  originalText: string
  suggestedText: string | null
  reason: string | null
  rejectCode: string | null
  rejectMessage: string | null
  modelName: string | null
}
