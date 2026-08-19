export type EvidenceMatchLevel = 'MATCHED' | 'PARTIAL_EVIDENCE' | 'NO_EVIDENCE'

export type RequirementImportance = 'REQUIRED' | 'BONUS'

export interface RequirementEvidenceItem {
  requirementEvidenceId: number
  sectionLabel: string | null
  evidenceText: string
  supportLevel: 'SUFFICIENT' | 'PARTIAL' | string
}

export interface EvidenceRequirementItem {
  evidenceRequirementId: number
  requirementText: string
  importance: RequirementImportance | string
  matchLevel: EvidenceMatchLevel | string
  conclusion: string | null
  suggestion: string | null
  evidences: RequirementEvidenceItem[]
}

export interface EvidenceAnalysisResult {
  evidenceAnalysisId: number
  matchedCount: number
  partialEvidenceCount: number
  noEvidenceCount: number
  requirements: EvidenceRequirementItem[]
}
