import type { EvidenceRequirementItem } from '@/types/evidence-analysis'
import type { ResumeDocument } from '@/types/resume-document'

export interface WorkspaceEvidenceAnchor {
  requirementId: number
  sectionId: string | null
  bulletId: string | null
}

const normalize = (value: string | null | undefined) => (value ?? '').replace(/\s+/g, '').toLocaleLowerCase()
const sectionKindLabels: Record<string, string[]> = {
  EXPERIENCE: ['工作经历', '实习经历', 'experience'],
  PROJECT: ['项目经历', '项目经验', 'project'],
  EDUCATION: ['教育经历', '教育背景', 'education'],
  SKILL: ['技能', '专业技能', 'skills', 'skill'],
  SUMMARY: ['个人简介', '简介', 'summary'],
}

export const resolveWorkspaceEvidenceAnchor = (
  requirement: EvidenceRequirementItem,
  document: ResumeDocument | null,
): WorkspaceEvidenceAnchor => {
  const evidence = requirement.evidences[0]
  const label = normalize(evidence?.sectionLabel)
  const section = document?.sections.find((candidate) => {
    const title = normalize(candidate.title)
    return Boolean(label) && (title === label || (sectionKindLabels[candidate.kind] ?? []).some((kind) => normalize(kind) === label))
  })
  if (!section) return { requirementId: requirement.evidenceRequirementId, sectionId: null, bulletId: null }
  const quote = normalize(evidence?.evidenceText)
  if (!quote) return { requirementId: requirement.evidenceRequirementId, sectionId: section.id, bulletId: null }
  const bullets = section.entries.flatMap((entry) => entry.bullets)
  const exact = bullets.filter((bullet) => normalize(bullet.text) === quote)
  const matches = exact.length ? exact : bullets.filter((bullet) => normalize(bullet.text).includes(quote))
  return { requirementId: requirement.evidenceRequirementId, sectionId: section.id, bulletId: matches.length === 1 ? matches[0]?.id ?? null : null }
}

export const retainWorkspaceEvidenceAnchor = (
  anchor: WorkspaceEvidenceAnchor,
  document: ResumeDocument | null,
): WorkspaceEvidenceAnchor => {
  if (!document || !anchor.sectionId) return anchor
  const section = document.sections.find((candidate) => candidate.id === anchor.sectionId)
  if (!section) return { ...anchor, sectionId: null, bulletId: null }
  const bulletExists = section.entries.some((entry) => entry.bullets.some((bullet) => bullet.id === anchor.bulletId))
  return bulletExists || !anchor.bulletId ? anchor : { ...anchor, bulletId: null }
}
