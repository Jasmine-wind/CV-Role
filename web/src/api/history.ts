import request from '@/api/request'
import type { AiResultDetail, AiResultPage, HistoryDetail, HistoryPage } from '@/types/history'

export const getHistoryPage = (page = 1, size = 10) => {
  return request.get<HistoryPage>('/api/history', {
    params: {
      page,
      size,
    },
  })
}

export const getHistoryDetail = (resumeId: number) => {
  return request.get<HistoryDetail>(`/api/history/${resumeId}`)
}

export interface AiResultQueryParams {
  resultType?: string
  resumeId?: number
  jobDescriptionId?: number
  status?: string
  page?: number
  size?: number
}

export const getAiResultPage = (params: AiResultQueryParams = {}) => {
  return request.get<AiResultPage>('/api/ai-results', {
    params,
  })
}

export const getAiResultDetail = (resultType: string, recordId: number) => {
  return request.get<AiResultDetail>(`/api/ai-results/${resultType}/${recordId}`)
}
