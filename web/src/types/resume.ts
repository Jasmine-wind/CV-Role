export interface ResumeListItem {
  id: number
  originalFilename: string
  fileType: string
  fileSize: number
  uploadStatus: string
  parseStatus: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | string
  parseErrorMessage: string | null
  createdAt: string
}

export interface ResumeUploadResult {
  id: number
  originalFilename: string
  fileType: string
  fileSize: number
  uploadStatus: string
  parseStatus: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | string
  preparationTaskId: number | null
  createdAt: string
}
