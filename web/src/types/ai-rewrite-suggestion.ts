export interface AiRewriteSuggestionRequest {
  rewriteType: string
  targetSection: string
  originalText: string
  sourceText?: string
  jobDescriptionId?: number
  aiJobMatchResultId?: number
  matchId?: number
  aiResumeSuggestionId?: number
  suggestionId?: number
  rewriteGoal?: string
  jobKeywords?: string[]
  tone?: string
  lengthLimit?: number
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

export interface RewriteSourceRef {
  startLine: number | null
  endLine: number | null
  text: string | null
}

export interface RecommendedRewriteSection {
  sectionType: string
  sectionTitle: string
  sourceText: string
  reason: string
  confidence: number
  matchedKeywords: string[]
  sourceRef: RewriteSourceRef | null
}

export interface RewriteContext {
  suggestionId: number
  suggestionIndex: number | null
  resumeId: number
  jobDescriptionId: number
  matchId: number | null
  suggestionTitle: string | null
  suggestionText: string | null
  suggestionReason: string | null
  recommendedSections: RecommendedRewriteSection[]
  jobKeywords: string[]
  rewriteGoals: string[]
  defaultRewriteGoal: string
  tones: string[]
}
