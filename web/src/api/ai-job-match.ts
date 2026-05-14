import request from '@/api/request'
import type { AiJobMatchRequest, AiJobMatchResult, AiJobMatchTrigger } from '@/types/ai-job-match'

const AI_JOB_MATCH_TIMEOUT_MS = 120000

export const triggerAiJobMatch = (resumeId: number, data: AiJobMatchRequest) => {
  return request.post<AiJobMatchTrigger>(`/api/resumes/${resumeId}/ai-job-matches`, data, {
    timeout: AI_JOB_MATCH_TIMEOUT_MS,
  })
}

export const getAiJobMatches = (resumeId: number) => {
  return request.get<AiJobMatchResult[]>(`/api/resumes/${resumeId}/ai-job-matches`)
}

export const getAiJobMatch = (resumeId: number, jobDescriptionId: number) => {
  return request.get<AiJobMatchResult>(`/api/resumes/${resumeId}/ai-job-matches`, {
    params: {
      jobDescriptionId,
    },
  })
}
