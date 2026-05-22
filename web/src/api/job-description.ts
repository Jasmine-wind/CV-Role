import request from '@/api/request'
import type { EmbeddingSummary } from '@/types/embedding'
import type { JobDescriptionDetail, JobDescriptionSubmitRequest } from '@/types/job-description'

const AI_PARSE_TIMEOUT_MS = 120000
const EMBEDDING_TIMEOUT_MS = 180000

export const submitJobDescription = (data: JobDescriptionSubmitRequest) => {
  return request.post<JobDescriptionDetail>('/api/job-descriptions', data)
}

export const getJobDescriptionList = () => {
  return request.get<JobDescriptionDetail[]>('/api/job-descriptions')
}

export const getJobDescriptionDetail = (id: number) => {
  return request.get<JobDescriptionDetail>(`/api/job-descriptions/${id}`)
}

export const deleteJobDescription = (id: number) => {
  return request.delete<void>(`/api/job-descriptions/${id}`)
}

export const parseJobDescription = (id: number) => {
  return request.post<JobDescriptionDetail>(`/api/job-descriptions/${id}/parse`, undefined, {
    timeout: AI_PARSE_TIMEOUT_MS,
  })
}

export const generateJobDescriptionEmbedding = (id: number) => {
  return request.post<EmbeddingSummary>(`/api/job-descriptions/${id}/embeddings`, undefined, {
    timeout: EMBEDDING_TIMEOUT_MS,
  })
}

export const getJobDescriptionEmbeddingSummary = (id: number) => {
  return request.get<EmbeddingSummary>(`/api/job-descriptions/${id}/embeddings`)
}
