export type EvidenceMatchLevel = 'MATCHED' | 'EXPRESSION_GAP' | 'NO_EVIDENCE'

export type RequirementImportance = 'REQUIRED' | 'BONUS'

export interface RequirementEvidenceItem {
  requirementEvidenceId: number
  sectionLabel: string | null
  evidenceText: string
  expressionStatus: 'ADEQUATE' | 'WEAK' | string
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
  expressionGapCount: number
  noEvidenceCount: number
  requirements: EvidenceRequirementItem[]
}
