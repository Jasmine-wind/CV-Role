import request from '@/api/request'
import type { JobDescriptionDetail, JobDescriptionSubmitRequest } from '@/types/job-description'

const AI_PARSE_TIMEOUT_MS = 120000

export const submitJobDescription = (data: JobDescriptionSubmitRequest) => {
  return request.post<JobDescriptionDetail>('/api/job-descriptions', data)
}

export const getJobDescriptionList = () => {
  return request.get<JobDescriptionDetail[]>('/api/job-descriptions')
}

export const getJobDescriptionDetail = (id: number) => {
  return request.get<JobDescriptionDetail>(`/api/job-descriptions/${id}`)
}

export const parseJobDescription = (id: number) => {
  return request.post<JobDescriptionDetail>(`/api/job-descriptions/${id}/parse`, undefined, {
    timeout: AI_PARSE_TIMEOUT_MS,
  })
}
