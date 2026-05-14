import request from '@/api/request'
import type {
  AiRewriteAcceptStatusUpdateRequest,
  AiRewriteSuggestionRequest,
  AiRewriteSuggestionResult,
} from '@/types/ai-rewrite-suggestion'

const AI_REWRITE_SUGGESTION_TIMEOUT_MS = 120000

export const triggerAiRewriteSuggestion = (resumeId: number, data: AiRewriteSuggestionRequest) => {
  return request.post<AiRewriteSuggestionResult>(`/api/resumes/${resumeId}/rewrite-suggestions`, data, {
    timeout: AI_REWRITE_SUGGESTION_TIMEOUT_MS,
  })
}

export const getAiRewriteSuggestions = (
  resumeId: number,
  params?: {
    rewriteType?: string
    acceptStatus?: string
  },
) => {
  return request.get<AiRewriteSuggestionResult[]>(`/api/resumes/${resumeId}/rewrite-suggestions`, {
    params,
  })
}

export const updateAiRewriteAcceptStatus = (rewriteId: number, data: AiRewriteAcceptStatusUpdateRequest) => {
  return request.patch<AiRewriteSuggestionResult>(`/api/rewrite-suggestions/${rewriteId}/accept-status`, data)
}
