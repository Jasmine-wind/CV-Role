export interface JobListItem {
  id: number
  title: string
  companyName: string
  jobCategory: string
  location: string
  requiredSkills: string[]
  status: string
}

export interface JobDetail {
  id: number
  title: string
  companyName: string
  jobCategory: string
  location: string
  description: string
  requirements: string
  requiredSkills: string[]
  updatedAt: string | null
}

export interface JobMatchSuggestion {
  type: string
  priority: string
  title: string
  content: string
  relatedItem: string
}

export interface JobMatchResult {
  matchId: number
  resumeId: number
  jobId: number
  jobTitle: string
  companyName: string
  matchScore: number
  matchedItems: string[]
  missingItems: string[]
  matchReason: string
  suggestions: JobMatchSuggestion[]
  updatedAt: string | null
}
