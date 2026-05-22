import request from '@/api/request'
import type { EmbeddingSummary } from '@/types/embedding'
import type { AsyncTaskVO } from '@/types/task'
import type {
  ResumeAiAnalysis,
  ResumeAiAnalysisTrigger,
  ResumeDetail,
  ResumeListItem,
  ResumeParseOptions,
  ResumeParseResult,
  ResumeUploadResult,
} from '@/types/resume'

const AI_ANALYSIS_TIMEOUT_MS = 120000
const RESUME_PARSE_TIMEOUT_MS = 180000
const EMBEDDING_TIMEOUT_MS = 180000

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

export const parseResume = (id: number, options?: ResumeParseOptions) => {
  return request.post<ResumeParseResult>(`/api/resumes/${id}/parse`, options ?? {}, {
    timeout: RESUME_PARSE_TIMEOUT_MS,
  })
}

export const submitResumeParseTask = (id: number, options?: ResumeParseOptions) => {
  return request.post<AsyncTaskVO>(`/api/resumes/${id}/parse/tasks`, options ?? {})
}

export const getResumeParseResult = (id: number) => {
  return request.get<ResumeParseResult>(`/api/resumes/${id}/parse-result`)
}

export const analyzeResume = (id: number) => {
  return request.post<ResumeAiAnalysisTrigger>(`/api/resumes/${id}/ai-analysis`, undefined, {
    timeout: AI_ANALYSIS_TIMEOUT_MS,
  })
}

export const submitResumeDiagnosisTask = (id: number) => {
  return request.post<AsyncTaskVO>(`/api/resumes/${id}/diagnosis/tasks`)
}

export const getResumeAiAnalysis = (id: number) => {
  return request.get<ResumeAiAnalysis>(`/api/resumes/${id}/ai-analysis`)
}

export const submitResumeEmbeddingTask = (id: number) => {
  return request.post<AsyncTaskVO>(`/api/resumes/${id}/embeddings/tasks`)
}

export const generateResumeEmbedding = (id: number) => {
  return request.post<EmbeddingSummary>(`/api/resumes/${id}/embeddings`, undefined, {
    timeout: EMBEDDING_TIMEOUT_MS,
  })
}

export const getResumeEmbeddingSummary = (id: number) => {
  return request.get<EmbeddingSummary>(`/api/resumes/${id}/embeddings`)
}

export const deleteResume = (id: number) => {
  return request.delete<void>(`/api/resumes/${id}`)
}
