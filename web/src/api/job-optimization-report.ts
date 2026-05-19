import request from '@/api/request'
import type { JobOptimizationReport } from '@/types/job-optimization-report'

export const getJobOptimizationReport = (resumeId: number, jobDescriptionId: number) => {
  return request.get<JobOptimizationReport>(`/api/resumes/${resumeId}/job-optimization-report`, {
    params: {
      jobDescriptionId,
    },
  })
}
