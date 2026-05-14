export interface AiRewriteSuggestionRequest {
  rewriteType: string
  targetSection: string
  originalText: string
  jobDescriptionId?: number
  aiJobMatchResultId?: number
  aiResumeSuggestionId?: number
}

export interface AiRewriteAcceptStatusUpdateRequest {
  acceptStatus: 'ACCEPTED' | 'REJECTED'
}

export type AiRewriteSuggestionStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | string
export type AiRewriteAcceptStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | string
export type AiRewriteType = 'PROJECT' | 'SKILL' | 'INTERNSHIP' | 'SUMMARY' | 'EDUCATION' | 'OTHER' | string

export interface AiRewriteSuggestionResult {
  rewriteId: number
  resumeId: number
  jobDescriptionId: number | null
  aiJobMatchResultId: number | null
  aiResumeSuggestionId: number | null
  rewriteType: AiRewriteType
  targetSection: string
  originalText: string
  rewrittenText: string | null
  rewriteReason: string | null
  caution: string | null
  acceptStatus: AiRewriteAcceptStatus
  rewriteStatus: AiRewriteSuggestionStatus
  modelName: string | null
  promptVersion: string | null
  errorMessage: string | null
  updatedAt: string | null
}
