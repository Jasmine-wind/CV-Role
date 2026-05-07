export interface ResumeListItem {
  id: number
  originalFilename: string
  fileType: string
  fileSize: number
  uploadStatus: string
  createdAt: string
}

export interface ResumeDetail {
  id: number
  originalFilename: string
  fileType: string
  fileSize: number
  uploadStatus: string
  createdAt: string
  updatedAt: string
}

export interface ResumeUploadResult {
  id: number
  originalFilename: string
  fileType: string
  fileSize: number
  objectKey: string
  uploadStatus: string
  createdAt: string
}

export interface ResumeParseResult {
  resumeId: number
  parseStatus: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | string
  extractedText: string | null
  structuredJson: string | null
  errorMessage: string | null
  updatedAt: string | null
}

export interface ResumeStructuredContent {
  name?: string | null
  phone?: string | null
  email?: string | null
  education?: string[] | null
  skills?: string[] | null
  projects?: string[] | null
  internships?: string[] | null
  rawText?: string | null
}

export interface ResumeAiAnalysis {
  resumeId: number
  analysisStatus: 'PENDING' | 'SUCCESS' | 'FAILED' | string
  score: number | null
  strengths?: string[] | null
  problems?: string[] | null
  suggestionsSummary?: string[] | null
  modelName: string | null
  promptVersion: string | null
  errorMessage: string | null
  updatedAt: string | null
}

export interface ResumeAiAnalysisTrigger {
  resumeId: number
  analysisStatus: 'PENDING' | 'SUCCESS' | 'FAILED' | string
  score: number | null
  modelName: string | null
  promptVersion: string | null
  errorMessage: string | null
  updatedAt: string | null
}
