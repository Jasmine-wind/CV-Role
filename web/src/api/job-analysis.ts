import request from '@/api/request'
import type {
  JobAnalysisStartRequest,
  JobAnalysisStartResult,
  OptimizationAnalysisResult,
} from '@/types/job-analysis'

export const startJobAnalysis = (data: JobAnalysisStartRequest) => {
  return request.post<JobAnalysisStartResult>('/api/job-analyses', data)
}

export const retryJobAnalysis = (optimizationTaskId: number) => {
  return request.post<JobAnalysisStartResult>(
    `/api/optimization-tasks/${optimizationTaskId}/retry`,
  )
}

export const getOptimizationAnalysisResult = (optimizationTaskId: number) => {
  return request.get<OptimizationAnalysisResult>(
    `/api/optimization-tasks/${optimizationTaskId}/analysis-result`,
  )
}
