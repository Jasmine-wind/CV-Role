import request from '@/api/request'
import type {
  AiResumeSuggestionRequest,
  AiResumeSuggestionResult,
  AiResumeSuggestionTrigger,
} from '@/types/ai-resume-suggestion'

const AI_RESUME_SUGGESTION_TIMEOUT_MS = 120000

export const triggerAiResumeSuggestion = (resumeId: number, data: AiResumeSuggestionRequest) => {
  return request.post<AiResumeSuggestionTrigger>(`/api/resumes/${resumeId}/ai-suggestions`, data, {
    timeout: AI_RESUME_SUGGESTION_TIMEOUT_MS,
  })
}

export const getAiResumeSuggestions = (resumeId: number) => {
  return request.get<AiResumeSuggestionResult[]>(`/api/resumes/${resumeId}/ai-suggestions`)
}

export const getAiResumeSuggestionByJobDescription = (resumeId: number, jobDescriptionId: number) => {
  return request.get<AiResumeSuggestionResult>(`/api/resumes/${resumeId}/ai-suggestions`, {
    params: {
      jobDescriptionId,
    },
  })
}

export const getAiResumeSuggestionByMatchResult = (resumeId: number, aiJobMatchResultId: number) => {
  return request.get<AiResumeSuggestionResult>(`/api/resumes/${resumeId}/ai-suggestions`, {
    params: {
      aiJobMatchResultId,
    },
  })
}
