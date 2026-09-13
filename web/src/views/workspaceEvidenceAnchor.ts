import type { EvidenceRequirementItem } from '@/types/evidence-analysis'
import type { ResumeDocument, ResumeDocumentSection } from '@/types/resume-document'

export interface WorkspaceEvidenceAnchor {
  requirementId: number
  requirementEvidenceId: number
  sectionId: string | null
  bulletId: string | null
}

const normalize = (value: string | null | undefined) =>
  (value ?? '').replace(/\s+/g, '').toLocaleLowerCase()

const sectionKindLabels: Record<string, string[]> = {
  EXPERIENCE: ['工作经历', '实习经历', 'experience'],
  PROJECT: ['项目经历', '项目经验', 'project'],
  EDUCATION: ['教育经历', '教育背景', 'education'],
  SKILL: ['技能', '专业技能', 'skills', 'skill'],
  SUMMARY: ['个人简介', '简介', 'summary'],
}

const sectionMatchesLabel = (section: ResumeDocumentSection, label: string) => {
  const title = normalize(section.title)
  return (
    title === label ||
    (sectionKindLabels[section.kind] ?? []).some((kind) => normalize(kind) === label)
  )
}

/**
 * Resolve only the Evidence row the user activated. The current Evidence API does not expose a
 * ResumeDocument element/source-occurrence identity, so requirementEvidenceId selects the row and
 * section label + a unique quote match is the conservative fallback. Ambiguous text never picks an
 * arbitrary bullet.
 */
export const resolveWorkspaceEvidenceAnchor = (
  requirement: EvidenceRequirementItem,
  document: ResumeDocument | null,
  requirementEvidenceId: number,
): WorkspaceEvidenceAnchor => {
  const emptyAnchor: WorkspaceEvidenceAnchor = {
    requirementId: requirement.evidenceRequirementId,
    requirementEvidenceId,
    sectionId: null,
    bulletId: null,
  }
  const evidence = requirement.evidences.find(
    (candidate) => candidate.requirementEvidenceId === requirementEvidenceId,
  )
  const label = normalize(evidence?.sectionLabel)
  if (!evidence || !document || !label) return emptyAnchor

  const sections = document.sections.filter((candidate) => sectionMatchesLabel(candidate, label))
  if (sections.length === 0) return emptyAnchor

  const quote = normalize(evidence.evidenceText)
  if (!quote) {
    return sections.length === 1 ? { ...emptyAnchor, sectionId: sections[0]!.id } : emptyAnchor
  }

  const bullets = sections.flatMap((section) =>
    section.entries.flatMap((entry) =>
      entry.bullets.map((bullet) => ({ sectionId: section.id, bullet })),
    ),
  )
  const exact = bullets.filter(({ bullet }) => normalize(bullet.text) === quote)
  const matches = exact.length
    ? exact
    : bullets.filter(({ bullet }) => normalize(bullet.text).includes(quote))

  if (matches.length === 1) {
    return {
      ...emptyAnchor,
      sectionId: matches[0]!.sectionId,
      bulletId: matches[0]!.bullet.id,
    }
  }
  return sections.length === 1 ? { ...emptyAnchor, sectionId: sections[0]!.id } : emptyAnchor
}

/** Keep established document identities through text edits and reorder; deletion clears safely. */
export const retainWorkspaceEvidenceAnchor = (
  anchor: WorkspaceEvidenceAnchor,
  document: ResumeDocument | null,
): WorkspaceEvidenceAnchor => {
  if (!document) return anchor

  if (anchor.bulletId) {
    for (const section of document.sections) {
      const bulletExists = section.entries.some((entry) =>
        entry.bullets.some((bullet) => bullet.id === anchor.bulletId),
      )
      if (bulletExists) return { ...anchor, sectionId: section.id }
    }
  }

  if (!anchor.sectionId) return anchor.bulletId ? { ...anchor, bulletId: null } : anchor
  const sectionExists = document.sections.some((candidate) => candidate.id === anchor.sectionId)
  if (!sectionExists) return { ...anchor, sectionId: null, bulletId: null }
  return anchor.bulletId ? { ...anchor, bulletId: null } : anchor
}
