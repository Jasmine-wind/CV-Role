import { describe, expect, it } from 'vitest'
import { resolveWorkspaceEvidenceAnchor, retainWorkspaceEvidenceAnchor } from './workspaceEvidenceAnchor'
import type { EvidenceRequirementItem, RequirementEvidenceItem } from '@/types/evidence-analysis'
import type {
  ResumeDocument,
  ResumeDocumentBullet,
  ResumeDocumentSection,
} from '@/types/resume-document'

const makeSection = (
  id: string,
  title: string,
  bullets: ResumeDocumentBullet[],
): ResumeDocumentSection => ({
  id,
  kind: 'EXPERIENCE',
  title,
  entries: [
    {
      id: `entry-${id}`,
      organization: null,
      role: null,
      school: null,
      degree: null,
      major: null,
      startDate: null,
      endDate: null,
      location: null,
      group: null,
      skillItems: null,
      bullets,
    },
  ],
})

const makeDocument = (
  bullets: ResumeDocumentBullet[] = [
    { id: 'b1', text: '原始证据' },
    { id: 'b2', text: '其他内容' },
  ],
  sections?: ResumeDocumentSection[],
): ResumeDocument => ({
  schemaVersion: 'RESUME_DOCUMENT_V1',
  basics: { name: 'A', contacts: [] },
  sections: sections ?? [makeSection('experience', '工作经历', bullets)],
})

const evidence = (id: number, quote: string, sectionLabel = '工作经历'): RequirementEvidenceItem => ({
  requirementEvidenceId: id,
  sectionLabel,
  evidenceText: quote,
  supportLevel: 'SUFFICIENT',
})

const makeRequirement = (
  id = 3,
  evidences: RequirementEvidenceItem[] = [evidence(1, '原始证据')],
): EvidenceRequirementItem => ({
  evidenceRequirementId: id,
  requirementText: '要求',
  importance: 'REQUIRED',
  matchLevel: 'MATCHED',
  conclusion: null,
  suggestion: null,
  evidences,
})

const anchorFor = (
  requirement = makeRequirement(),
  document = makeDocument(),
  requirementEvidenceId = 1,
) => resolveWorkspaceEvidenceAnchor(requirement, document, requirementEvidenceId)

describe('workspace evidence anchor', () => {
  it('resolves the specifically selected evidence instead of the first row', () => {
    const requirement = makeRequirement(3, [
      evidence(11, '原始证据'),
      evidence(12, '其他内容'),
    ])

    expect(anchorFor(requirement, makeDocument(), 12)).toEqual({
      requirementId: 3,
      requirementEvidenceId: 12,
      sectionId: 'experience',
      bulletId: 'b2',
    })
  })

  it('resolves and retains stable bullet identity after text changes', () => {
    const anchor = anchorFor()
    expect(anchor).toEqual({
      requirementId: 3,
      requirementEvidenceId: 1,
      sectionId: 'experience',
      bulletId: 'b1',
    })
    expect(
      retainWorkspaceEvidenceAnchor(anchor, makeDocument([{ id: 'b1', text: '完全不同的描述' }])),
    ).toEqual(anchor)
  })

  it('falls back to the section when the bullet is deleted', () => {
    const anchor = anchorFor()
    expect(retainWorkspaceEvidenceAnchor(anchor, makeDocument([]))).toEqual({
      ...anchor,
      bulletId: null,
    })
  })

  it('keeps identities when sections are reordered or the bullet moves with its entry', () => {
    const original = makeSection('experience-a', '工作经历', [{ id: 'b1', text: '原始证据' }])
    const other = makeSection('experience-b', '工作经历', [{ id: 'b2', text: '其他内容' }])
    const base = makeDocument([], [original, other])
    const anchor = anchorFor(makeRequirement(), base)
    const moved = makeSection('experience-b', '工作经历', [
      { id: 'b2', text: '其他内容' },
      { id: 'b1', text: '编辑后的证据' },
    ])

    expect(retainWorkspaceEvidenceAnchor(anchor, makeDocument([], [moved, makeSection('experience-a', '工作经历', [])]))).toEqual({
      ...anchor,
      sectionId: 'experience-b',
    })
  })

  it('keeps section only when repeated quote text is ambiguous', () => {
    const anchor = anchorFor(
      makeRequirement(3, [evidence(1, '重复内容')]),
      makeDocument([
        { id: 'b1', text: '重复内容' },
        { id: 'b2', text: '重复内容' },
      ]),
    )
    expect(anchor).toEqual({
      requirementId: 3,
      requirementEvidenceId: 1,
      sectionId: 'experience',
      bulletId: null,
    })
  })

  it('does not pick a section when repeated labels and text are ambiguous', () => {
    const sections = [
      makeSection('experience-a', '工作经历', [{ id: 'b1', text: '重复内容' }]),
      makeSection('experience-b', '工作经历', [{ id: 'b2', text: '重复内容' }]),
    ]
    expect(
      anchorFor(makeRequirement(3, [evidence(1, '重复内容')]), makeDocument([], sections)),
    ).toEqual({
      requirementId: 3,
      requirementEvidenceId: 1,
      sectionId: null,
      bulletId: null,
    })
  })

  it('fails closed when the selected stable evidence identifier is unavailable', () => {
    expect(anchorFor(makeRequirement(), makeDocument(), 999)).toEqual({
      requirementId: 3,
      requirementEvidenceId: 999,
      sectionId: null,
      bulletId: null,
    })
  })

  it('clears both document anchors when the section and bullet are deleted', () => {
    const anchor = anchorFor()
    expect(retainWorkspaceEvidenceAnchor(anchor, { ...makeDocument(), sections: [] })).toEqual({
      ...anchor,
      sectionId: null,
      bulletId: null,
    })
  })
})
