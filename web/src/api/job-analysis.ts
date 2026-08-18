import request from '@/api/request'
import type { JobAnalysisStartRequest, JobAnalysisStartResult } from '@/types/job-analysis'

export const startJobAnalysis = (data: JobAnalysisStartRequest) => {
  return request.post<JobAnalysisStartResult>('/api/job-analyses', data)
}

export const retryJobAnalysis = (resumeId: number, jobDescriptionId: number) => {
  return request.post<JobAnalysisStartResult>(`/api/job-analyses/${jobDescriptionId}/retry`, undefined, {
    params: { resumeId },
  })
}
