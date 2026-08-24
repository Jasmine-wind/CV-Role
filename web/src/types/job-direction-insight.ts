export type EvidenceCoverageLevel = 'MATCHED' | 'PARTIAL_EVIDENCE' | 'NO_EVIDENCE'

export interface JobDirectionEvidence {
  requirementEvidenceId: number
  sectionLabel: string | null
  evidenceText: string
  supportLevel: string
}

export interface JobDirectionRequirementSource {
  optimizationTaskId: number
  evidenceRequirementId: number
  requirementText: string
  matchLevel: EvidenceCoverageLevel
  evidences: JobDirectionEvidence[]
}

export interface JobDirectionRequirement {
  label: string
  occurrenceCount: number
  sampleSize: number
  matchedCount: number
  partialEvidenceCount: number
  noEvidenceCount: number
  sources: JobDirectionRequirementSource[]
}

export interface JobDirectionCohort {
  resumeId: number
  resumeName: string
  sampleSize: number
  minimumSampleSize: number
  windowStart: string
  newestAnalysisAt: string
  commonRequirements: JobDirectionRequirement[]
}

export interface JobDirectionInsights {
  cohorts: JobDirectionCohort[]
}
