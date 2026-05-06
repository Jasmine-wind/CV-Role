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
