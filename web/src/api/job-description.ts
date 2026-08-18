import request from '@/api/request'
import type { JobDescriptionDetail } from '@/types/job-description'

export const getJobDescriptionDetail = (id: number) => {
  return request.get<JobDescriptionDetail>(`/api/job-descriptions/${id}`)
}
