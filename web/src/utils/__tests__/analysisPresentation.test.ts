import { describe, expect, it } from 'vitest'
import type { EvidenceRequirementItem } from '@/types/evidence-analysis'
import { sortEvidenceRequirements } from '@/utils/analysisPresentation'

const requirement = (
  id: number,
  importance: string,
  matchLevel: string,
): EvidenceRequirementItem => ({
  evidenceRequirementId: id,
  requirementText: `要求 ${id}`,
  importance,
  matchLevel,
  conclusion: null,
  suggestion: null,
  evidences: [],
})

describe('analysisPresentation', () => {
  it('sorts requirements by fixed action priority and keeps JD order within a bucket', () => {
    const sorted = sortEvidenceRequirements([
      requirement(1, 'BONUS', 'NO_EVIDENCE'),
      requirement(3, 'BONUS', 'PARTIAL_EVIDENCE'),
      requirement(4, 'REQUIRED', 'NO_EVIDENCE'),
      requirement(5, 'REQUIRED', 'PARTIAL_EVIDENCE'),
      requirement(6, 'REQUIRED', 'PARTIAL_EVIDENCE'),
      requirement(7, 'BONUS', 'MATCHED'),
      requirement(8, 'BONUS', 'PARTIAL_EVIDENCE'),
      requirement(2, 'REQUIRED', 'MATCHED'),
    ])

    expect(sorted.map((item) => item.evidenceRequirementId)).toEqual([5, 6, 4, 3, 8, 1, 7, 2])
  })

  it('keeps unknown API values at the end instead of silently treating them as a match', () => {
    const sorted = sortEvidenceRequirements([
      requirement(1, 'REQUIRED', 'UNEXPECTED'),
      requirement(2, 'REQUIRED', 'NO_EVIDENCE'),
    ])

    expect(sorted.map((item) => item.evidenceRequirementId)).toEqual([2, 1])
  })
})
