export interface AiResumeSuggestionRequest {
  jobDescriptionId: number
  aiJobMatchResultId?: number
}

export type AiResumeSuggestionStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | string
export type AiResumeSuggestionType =
  | 'SKILL_GAP'
  | 'EXPERIENCE_WEAKNESS'
  | 'PROJECT_DESCRIPTION'
  | 'HIGHLIGHT_STRENGTH'
  | 'STRUCTURE'
  | 'GENERAL'
  | string
export type AiResumeSuggestionPriority = 'HIGH' | 'MEDIUM' | 'LOW' | string

export interface AiResumeSuggestionItem {
  type: AiResumeSuggestionType
  priority: AiResumeSuggestionPriority
  targetSection: string | null
  issue: string
  suggestion: string
  evidence: string[]
  caution: string | null
  relatedItems: string[]
}

export interface AiResumeSuggestionTrigger {
  suggestionId: number
  resumeId: number
  jobDescriptionId: number
  aiJobMatchResultId: number
  suggestionStatus: AiResumeSuggestionStatus
  suggestionCount: number
  errorMessage: string | null
  updatedAt: string | null
}

export interface AiResumeSuggestionResult {
  suggestionId: number
  resumeId: number
  jobDescriptionId: number
  aiJobMatchResultId: number
  suggestionStatus: AiResumeSuggestionStatus
  suggestions: AiResumeSuggestionItem[]
  modelName: string | null
  promptVersion: string | null
  errorMessage: string | null
  updatedAt: string | null
}
