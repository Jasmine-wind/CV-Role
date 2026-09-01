import request from '@/api/request'
import type { AsyncTaskVO } from '@/types/task'
import type { ResumeListItem, ResumeUploadResult } from '@/types/resume'
import type { ResumeDocumentEntry } from '@/types/resume-document'

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

/** 待确认候选项（Slice A）：canonicalDraft 为 canonical 片段 JSON 字符串。 */
export interface ResumeReviewUnresolvedItem {
  id: string
  kind: 'CONTACT_CANDIDATE' | 'REQUIRED_CONTACT_CANDIDATE' | 'NAME_CANDIDATE' | 'ENTRY_CANDIDATE' | 'TEXT_FRAGMENT' | string
  canonicalDraft: string
  reason: string | null
  sourceRef?: string | null
}

export interface ResumeReviewVO {
  resumeId: number
  qualityStatus: string
  qualityIssues: string | null
  unresolvedItems: string | null
  canonicalDocument: string | null
}

export interface ResumeReviewResolveRequest {
  itemId: string
  action: 'ACCEPT' | 'DELETE'
  name?: string
  contactType?: string
  contactLabel?: string
  contactValue?: string
  text?: string
  targetSectionId?: string
  entry?: Partial<ResumeDocumentEntry>
}

export const getResumeReview = (id: number) => {
  return request.get<ResumeReviewVO>(`/api/resumes/${id}/review`)
}

export const resolveResumeReview = (id: number, payload: ResumeReviewResolveRequest) => {
  return request.post<ResumeReviewVO>(`/api/resumes/${id}/review/resolve`, payload)
}
