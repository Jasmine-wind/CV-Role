import request from '@/api/request'
import type { AiJobMatchResult } from '@/types/ai-job-match'

export const getAiJobMatch = (resumeId: number, jobDescriptionId: number) => {
  return request.get<AiJobMatchResult>(`/api/resumes/${resumeId}/ai-job-matches`, {
    params: {
      jobDescriptionId,
    },
  })
}
