export interface JobDescriptionSubmitRequest {
  title: string
  rawText: string
}

export type JobDescriptionSourceType = 'USER_INPUT' | 'PRESET' | 'CRAWLED' | string

export interface JobDescriptionDetail {
  id: number
  title: string
  sourceType: JobDescriptionSourceType | null
  rawText: string
  parseStatus: 'PENDING' | 'SUCCESS' | 'FAILED' | string
  structuredContent: string | null
  modelName: string | null
  promptVersion: string | null
  errorMessage: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface JobDescriptionStructuredContent {
  jobTitle: string
  requiredSkills: string[]
  bonusSkills: string[]
  experienceSignals: string[]
  responsibilities: string[]
  keywords: string[]
  summary: string
}
