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
  jobId: number
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
