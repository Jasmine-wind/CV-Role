export interface ResumeListItem {
  id: number
  originalFilename: string
  fileType: string
  fileSize: number
  uploadStatus: string
  parseStatus: 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED' | string
  /** Slice A 交付质量状态：READY / NEEDS_REVIEW / FAILED / PENDING。 */
  qualityStatus: 'READY' | 'NEEDS_REVIEW' | 'FAILED' | 'PENDING' | string | null
  /** Whether the current parse has a materialized canonical SOURCE version. */
  canonicalReady?: boolean
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
