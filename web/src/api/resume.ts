import request from '@/api/request'
import type { ResumeDetail, ResumeListItem, ResumeUploadResult } from '@/types/resume'

export const uploadResume = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)

  return request.post<ResumeUploadResult>('/api/resumes', formData)
}

export const getResumeList = () => {
  return request.get<ResumeListItem[]>('/api/resumes')
}

export const getResumeDetail = (id: number) => {
  return request.get<ResumeDetail>(`/api/resumes/${id}`)
}
