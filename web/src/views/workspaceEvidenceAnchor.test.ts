import { describe, expect, it } from 'vitest'
import { resolveWorkspaceEvidenceAnchor, retainWorkspaceEvidenceAnchor } from './workspaceEvidenceAnchor'
import type { EvidenceRequirementItem } from '@/types/evidence-analysis'
import type { ResumeDocument, ResumeDocumentBullet } from '@/types/resume-document'

const makeDocument = (bullets: ResumeDocumentBullet[] = [{ id: 'b1', text: '原始证据' }, { id: 'b2', text: '其他内容' }]): ResumeDocument => ({
  schemaVersion: 'RESUME_DOCUMENT_V1',
  basics: { name: 'A', contacts: [] },
  sections: [{
    id: 'experience', kind: 'EXPERIENCE', title: '工作经历', entries: [{
      id: 'entry', organization: null, role: null, school: null, degree: null, major: null,
      startDate: null, endDate: null, location: null, group: null, skillItems: null, bullets,
    }],
  }],
})
const makeRequirement = (id = 3, quote = '原始证据'): EvidenceRequirementItem => ({
  evidenceRequirementId: id, requirementText: '要求', importance: 'REQUIRED', matchLevel: 'MATCHED',
  conclusion: null, suggestion: null,
  evidences: [{ requirementEvidenceId: 1, sectionLabel: '工作经历', evidenceText: quote, supportLevel: 'SUFFICIENT' }],
})

describe('workspace evidence anchor', () => {
  it('resolves and retains a bullet identity after text changes', () => {
    const anchor = resolveWorkspaceEvidenceAnchor(makeRequirement(), makeDocument())
    expect(anchor).toEqual({ requirementId: 3, sectionId: 'experience', bulletId: 'b1' })
    expect(retainWorkspaceEvidenceAnchor(anchor, makeDocument([{ id: 'b1', text: '完全不同的描述' }]))).toEqual(anchor)
  })
  it('falls back to section when the bullet is deleted', () => {
    const anchor = resolveWorkspaceEvidenceAnchor(makeRequirement(), makeDocument())
    expect(retainWorkspaceEvidenceAnchor(anchor, makeDocument([]))).toEqual({ ...anchor, bulletId: null })
  })
  it('keeps the anchor when sections and entries are reordered', () => {
    const base = makeDocument()
    const section = base.sections[0]!
    const anchor = resolveWorkspaceEvidenceAnchor(makeRequirement(), base)
    const reordered: ResumeDocument = { ...base, sections: [{ ...section, entries: [...section.entries].reverse() }] }
    expect(retainWorkspaceEvidenceAnchor(anchor, reordered)).toEqual(anchor)
  })
  it('keeps section only for ambiguous quotes', () => {
    const anchor = resolveWorkspaceEvidenceAnchor(makeRequirement(3, '内容'), makeDocument([{ id: 'b1', text: '内容一' }, { id: 'b2', text: '内容二' }]))
    expect(anchor).toEqual({ requirementId: 3, sectionId: 'experience', bulletId: null })
  })
  it('clears both anchors when the section is deleted', () => {
    const anchor = resolveWorkspaceEvidenceAnchor(makeRequirement(), makeDocument())
    expect(retainWorkspaceEvidenceAnchor(anchor, { ...makeDocument(), sections: [] })).toEqual({ ...anchor, sectionId: null, bulletId: null })
  })
})
