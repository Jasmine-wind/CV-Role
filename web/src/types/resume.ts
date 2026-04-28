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
