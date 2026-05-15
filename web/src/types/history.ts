export interface HistoryListItem {
  recordId: number
  resumeId: number
  resumeName: string
  fileType: string
  fileSize: number
  uploadStatus: string
  uploadTime: string
  parseStatus: string
  analysisStatus: string
  analysisScore: number | null
  latestJobId: number | null
  latestJobDescriptionId: number | null
  latestMatchSource: 'JOB' | 'AI_JOB_DESCRIPTION' | string | null
  latestJobTitle: string | null
  latestCompanyName: string | null
  latestMatchScore: number | null
  updatedAt: string | null
}

export interface HistoryPage {
  records: HistoryListItem[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface HistoryResume {
  resumeId: number
  resumeName: string
  fileType: string
  fileSize: number
  uploadStatus: string
  uploadTime: string
}

export interface HistoryParseResult {
  parseStatus: string
  extractedTextPreview: string | null
  parseErrorMessage: string | null
  parseUpdatedAt: string | null
}

export interface HistoryAiAnalysis {
  analysisStatus: string
  analysisScore: number | null
  strengthsPreview: string | null
  problemsPreview: string | null
  suggestionsSummary: string | null
  analysisErrorMessage: string | null
  analysisUpdatedAt: string | null
}

export interface HistoryMatchResult {
  matchId: number
  jobId: number | null
  jobDescriptionId: number | null
  matchSource: 'JOB' | 'AI_JOB_DESCRIPTION' | string | null
  jobTitle: string | null
  companyName: string | null
  jobCategory: string | null
  matchScore: number | null
  matchReason: string | null
  suggestionsPreview: string | null
  matchUpdatedAt: string | null
}

export interface HistoryDetail {
  recordId: number
  resumeId: number
  resume: HistoryResume
  parseResult: HistoryParseResult
  aiAnalysis: HistoryAiAnalysis
  latestMatch: HistoryMatchResult | null
  matchResults: HistoryMatchResult[]
  updatedAt: string | null
}

export type AiResultType =
  | 'RESUME_DIAGNOSIS'
  | 'TARGET_JOB_PARSE'
  | 'MATCH_ANALYSIS'
  | 'JOB_OPTIMIZATION_SUGGESTION'
  | 'LOCAL_REWRITE'
  | string

export interface AiResultRecord {
  recordId: number
  resultType: AiResultType
  title: string
  summary: string | null
  status: string
  resumeId: number | null
  resumeName: string | null
  jobDescriptionId: number | null
  jobTitle: string | null
  modelName: string | null
  promptVersion: string | null
  errorMessage: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface AiResultPage {
  records: AiResultRecord[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface AiResultDetail {
  recordId: number
  resultType: AiResultType
  title: string
  status: string
  content: Record<string, unknown>
  resumeId: number | null
  resumeName: string | null
  jobDescriptionId: number | null
  jobTitle: string | null
  modelName: string | null
  promptVersion: string | null
  errorMessage: string | null
  createdAt: string | null
  updatedAt: string | null
}
