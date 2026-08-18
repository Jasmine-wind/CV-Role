export interface JobAnalysisStartRequest {
  resumeId: number
  jobDescription: string
}

export interface JobAnalysisStartResult {
  taskId: number
  resumeId: number
  jobDescriptionId: number
}

export interface ActiveJobAnalysis extends JobAnalysisStartResult {
  startedAt: string
}
