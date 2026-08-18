import request from '@/api/request'
import type { AsyncTaskVO } from '@/types/task'
import type { ResumeListItem, ResumeUploadResult } from '@/types/resume'

export const uploadResume = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ResumeUploadResult>('/api/resumes', formData)
}

export const getResumeList = () => {
  return request.get<ResumeListItem[]>('/api/resumes')
}

export const requestResumePreparation = (id: number) => {
  return request.post<AsyncTaskVO>(`/api/resumes/${id}/preparation`, {})
}

export const deleteResume = (id: number) => {
  return request.delete<void>(`/api/resumes/${id}`)
}
