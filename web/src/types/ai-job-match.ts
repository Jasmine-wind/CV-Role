export interface AiJobMatchRequest {
  jobDescriptionId: number
}

export interface AiJobMatchItem {
  item: string
  reason: string
}

export interface AiJobMatchWeakExperience {
  section: string
  issue: string
}

export interface AiJobMatchEvidence {
  source: 'resume' | 'job' | string
  content: string
}

export interface AiJobMatchResult {
  matchId: number
  resumeId: number
  jobDescriptionId: number
  overallScore: number | null
  strongMatches: AiJobMatchItem[]
  weakMatches: AiJobMatchItem[]
  missingSkills: AiJobMatchItem[]
  weakExperienceDescriptions: AiJobMatchWeakExperience[]
  evidence: AiJobMatchEvidence[]
  riskNotes: string[]
  modelName: string | null
  promptVersion: string | null
  matchStatus: 'PENDING' | 'SUCCESS' | 'FAILED' | string
  errorMessage: string | null
  updatedAt: string | null
}

export interface AiJobMatchTrigger {
  matchId: number
  resumeId: number
  jobDescriptionId: number
  overallScore: number | null
  matchStatus: 'PENDING' | 'SUCCESS' | 'FAILED' | string
  modelName: string | null
  promptVersion: string | null
  errorMessage: string | null
  updatedAt: string | null
}
