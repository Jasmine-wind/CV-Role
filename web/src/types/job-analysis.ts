import type { AiJobMatchResult } from '@/types/ai-job-match'

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

export interface OptimizationAnalysisResult {
  optimizationTaskId: number
  sourceResumeVersionId: number
  targetResumeVersionId: number
  jobTargetId: number
  status: string
  jobTitle: string
  resumeName: string
  analysis: AiJobMatchResult
}
