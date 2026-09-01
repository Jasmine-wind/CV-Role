import type { EvidenceRequirementItem } from '@/types/evidence-analysis'

export const isKnownRequirementImportance = (value: string) =>
  value === 'REQUIRED' || value === 'BONUS'

export const isKnownRequirementMatchLevel = (value: string) =>
  value === 'MATCHED' || value === 'PARTIAL_EVIDENCE' || value === 'NO_EVIDENCE'

/**
 * Analysis / Workspace 共用的用户优先级：先处理已有部分证据的必需项，
 * 再看没有证据的必需项，随后处理加分项，最后集中展示所有已有优势；同级保持 JD 原始顺序。
 */
export const evidenceRequirementRank = (item: EvidenceRequirementItem) => {
  if (item.importance === 'REQUIRED' && item.matchLevel === 'PARTIAL_EVIDENCE') return 0
  if (item.importance === 'REQUIRED' && item.matchLevel === 'NO_EVIDENCE') return 1
  if (item.importance === 'BONUS' && item.matchLevel === 'PARTIAL_EVIDENCE') return 2
  if (item.importance === 'BONUS' && item.matchLevel === 'NO_EVIDENCE') return 3
  if (item.matchLevel === 'MATCHED') return 4
  return 5
}

export const sortEvidenceRequirements = (requirements: EvidenceRequirementItem[]) =>
  requirements
    .map((item, index) => ({ item, index }))
    .sort(
      (left, right) =>
        evidenceRequirementRank(left.item) - evidenceRequirementRank(right.item) ||
        left.index - right.index,
    )
    .map(({ item }) => item)
