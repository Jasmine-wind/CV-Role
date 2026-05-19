import type { AiJobMatchEvidence, AiJobMatchItem } from '@/types/ai-job-match'
import type { AiResumeSuggestionItem } from '@/types/ai-resume-suggestion'
import type { AiRewriteAcceptStatus, AiRewriteType } from '@/types/ai-rewrite-suggestion'

export interface JobOptimizationSuggestionSummary {
  totalCount: number
  highPriorityCount: number
  mediumPriorityCount: number
  lowPriorityCount: number
}

export interface JobOptimizationRewriteSuggestion {
  rewriteId: number
  rewriteType: AiRewriteType
  targetSection: string | null
  originalText: string | null
  rewrittenText: string | null
  rewriteReason: string | null
  caution: string | null
  acceptStatus: AiRewriteAcceptStatus
  aiResumeSuggestionId: number | null
  updatedAt: string | null
}

export interface JobOptimizationNextStep {
  key: string
  text: string
  source: string
  status: string
}

export interface JobOptimizationModelInfo {
  sourceType: string
  sourceId: number | null
  modelName: string | null
  promptVersion: string | null
  status: string | null
  updatedAt: string | null
}

export interface JobOptimizationWarning {
  code: string
  message: string
  source: string
}

export interface JobOptimizationReport {
  resumeId: number
  resumeName: string | null
  jobDescriptionId: number
  jobTitle: string | null
  matchScore: number | null
  matchLevel: 'HIGH' | 'MEDIUM' | 'LOW' | string | null
  strongMatches: AiJobMatchItem[]
  weakMatches: AiJobMatchItem[]
  missingSkills: AiJobMatchItem[]
  riskTips: string[]
  matchEvidence: AiJobMatchEvidence[]
  suggestionSummary: JobOptimizationSuggestionSummary
  highPrioritySuggestions: AiResumeSuggestionItem[]
  mediumPrioritySuggestions: AiResumeSuggestionItem[]
  lowPrioritySuggestions: AiResumeSuggestionItem[]
  rewriteSuggestions: JobOptimizationRewriteSuggestion[]
  acceptedRewriteSuggestions: JobOptimizationRewriteSuggestion[]
  pendingRewriteSuggestions: JobOptimizationRewriteSuggestion[]
  rejectedRewriteSuggestions: JobOptimizationRewriteSuggestion[]
  nextStepChecklist: JobOptimizationNextStep[]
  modelInfo: JobOptimizationModelInfo[]
  generatedAt: string | null
  warnings: JobOptimizationWarning[]
}
