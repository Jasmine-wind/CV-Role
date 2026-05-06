import request from '@/api/request'
import type { ResumeDetail, ResumeListItem, ResumeParseResult, ResumeUploadResult } from '@/types/resume'

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

export const parseResume = (id: number) => {
  return request.post<ResumeParseResult>(`/api/resumes/${id}/parse`)
}

export const getResumeParseResult = (id: number) => {
  return request.get<ResumeParseResult>(`/api/resumes/${id}/parse-result`)
}

export const deleteResume = (id: number) => {
  return request.delete<void>(`/api/resumes/${id}`)
}
