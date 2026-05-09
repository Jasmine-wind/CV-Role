import request from '@/api/request'
import type { HistoryDetail, HistoryPage } from '@/types/history'

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
