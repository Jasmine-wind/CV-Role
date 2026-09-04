import type { AiJobMatchResult } from '@/types/ai-job-match'
import type { EvidenceAnalysisResult } from '@/types/evidence-analysis'

export interface JobAnalysisStartRequest {
  resumeId: number
  jobDescription: string
}

export interface JobAnalysisStartResult {
  taskId: number
  optimizationTaskId: number
  sourceResumeVersionId: number
  targetResumeVersionId: number
  jobTargetId: number
}

export interface ActiveJobAnalysis extends JobAnalysisStartResult {
  startedAt: string
}

export type OptimizationAnalysisMode = 'EVIDENCE' | 'LEGACY_COMPAT'

export interface OptimizationAnalysisResult {
  optimizationTaskId: number
  /** Present for formal tasks; omitted by historical compatibility fixtures. */
  resumeId?: number
  sourceResumeVersionId: number
  /** Canonical document frozen on this task's SOURCE version; absent for legacy or unavailable content. */
  sourceCanonicalDocument?: string | null
  targetResumeVersionId: number
  jobTargetId: number
  status: string
  jobTitle: string
  resumeName: string
  analysisMode: OptimizationAnalysisMode
  evidenceAnalysis: EvidenceAnalysisResult | null
  legacyAnalysis: AiJobMatchResult | null
}
