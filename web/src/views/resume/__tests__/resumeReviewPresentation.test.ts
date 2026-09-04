import { describe, expect, it } from 'vitest'
import type { ResumeReviewUnresolvedItem } from '@/api/resume'
import type { ReviewItemState } from '../resumeReviewPresentation'
import {
  entryDraftSummary,
  getInitialReviewItemId,
  getReviewCandidatePresentation,
  getReviewEntryTitle,
  getReviewItemNavigationPresentation,
  getReviewProgressLabel,
  groupReviewItems,
  selectReviewItemAfterResolve,
} from '../resumeReviewPresentation'

const state = (
  kind: string,
  id = 'item-1',
  draft: Record<string, unknown> = {},
): ReviewItemState => ({
  item: {
    id,
    kind,
    canonicalDraft: JSON.stringify(draft),
    reason: null,
  } as ResumeReviewUnresolvedItem,
  contact: {
    type: typeof draft.type === 'string' ? draft.type : 'EMAIL',
    value: typeof draft.value === 'string' ? draft.value : 'candidate@example.com',
  },
  entry: {
    kind: typeof draft.kind === 'string' ? draft.kind : undefined,
    organization: typeof draft.organization === 'string' ? draft.organization : null,
    school: typeof draft.school === 'string' ? draft.school : null,
    role: typeof draft.role === 'string' ? draft.role : null,
    degree: typeof draft.degree === 'string' ? draft.degree : null,
    major: typeof draft.major === 'string' ? draft.major : null,
    startDate: null,
    endDate: null,
    bullets: Array.isArray(draft.bullets) ? draft.bullets as ReviewItemState['entry']['bullets'] : [],
  },
  text: typeof draft.text === 'string' ? draft.text : '',
})

describe('resumeReviewPresentation', () => {
  it('selects the first unresolved item on open', () => {
    expect(getInitialReviewItemId([state('TEXT_FRAGMENT', 'first'), state('TEXT_FRAGMENT', 'second')])).toBe('first')
  })

  it('uses stable item ids instead of array indexes', () => {
    const items = [state('TEXT_FRAGMENT', 'stable-id')]
    expect(getInitialReviewItemId(items)).toBe('stable-id')
  })

  it('returns no active item for an empty review', () => {
    expect(getInitialReviewItemId([])).toBeNull()
  })

  it('keeps the active item when a different item was resolved', () => {
    const previous = [state('TEXT_FRAGMENT', 'one'), state('TEXT_FRAGMENT', 'two')]
    const next = [state('TEXT_FRAGMENT', 'one')]
    expect(selectReviewItemAfterResolve({
      previousItems: previous,
      nextItems: next,
      resolvedItemId: 'two',
      previousActiveItemId: 'one',
    })).toBe('one')
  })

  it('selects the item at the old position after the active item disappears', () => {
    const previous = [state('TEXT_FRAGMENT', 'one'), state('TEXT_FRAGMENT', 'two'), state('TEXT_FRAGMENT', 'three')]
    const next = [state('TEXT_FRAGMENT', 'one'), state('TEXT_FRAGMENT', 'three')]
    expect(selectReviewItemAfterResolve({
      previousItems: previous,
      nextItems: next,
      resolvedItemId: 'two',
      previousActiveItemId: 'two',
    })).toBe('three')
  })

  it('falls back to the last remaining item after the final item resolves', () => {
    const previous = [state('TEXT_FRAGMENT', 'one'), state('TEXT_FRAGMENT', 'two')]
    const next = [state('TEXT_FRAGMENT', 'one')]
    expect(selectReviewItemAfterResolve({
      previousItems: previous,
      nextItems: next,
      resolvedItemId: 'two',
      previousActiveItemId: 'two',
    })).toBe('one')
  })

  it('returns no active item after the last candidate resolves', () => {
    expect(selectReviewItemAfterResolve({
      previousItems: [state('TEXT_FRAGMENT')],
      nextItems: [],
      resolvedItemId: 'item-1',
      previousActiveItemId: 'item-1',
    })).toBeNull()
  })

  it('presents a name candidate without a delete action', () => {
    expect(getReviewCandidatePresentation(state('NAME_CANDIDATE'))).toMatchObject({
      title: '姓名',
      primaryAction: '使用这个姓名',
      canDelete: false,
    })
  })

  it('presents required contact with its constrained save action', () => {
    expect(getReviewCandidatePresentation(state('REQUIRED_CONTACT_CANDIDATE'))).toMatchObject({
      title: '必要联系方式',
      primaryAction: '保存这项联系方式',
      canDelete: false,
    })
  })

  it('presents ordinary contact with a non-destructive reject action', () => {
    expect(getReviewCandidatePresentation(state('CONTACT_CANDIDATE'))).toMatchObject({
      title: '联系方式',
      primaryAction: '保留这项联系方式',
      canDelete: true,
    })
  })

  it('presents a text fragment as unclassified content', () => {
    expect(getReviewCandidatePresentation(state('TEXT_FRAGMENT'))).toMatchObject({
      title: '未归类内容',
      primaryAction: '保留这段内容',
      canDelete: true,
    })
  })

  it('presents education entries with education copy', () => {
    expect(getReviewCandidatePresentation(state('ENTRY_CANDIDATE', 'education', { kind: 'EDUCATION' }))).toMatchObject({
      title: '教育经历',
      primaryAction: '保留这段教育经历',
    })
  })

  it('presents experience entries with work copy', () => {
    expect(getReviewCandidatePresentation(state('ENTRY_CANDIDATE', 'experience', { kind: 'EXPERIENCE' }))).toMatchObject({
      title: '工作经历',
      primaryAction: '保留这段工作经历',
    })
  })

  it('presents project entries with project copy', () => {
    expect(getReviewCandidatePresentation(state('ENTRY_CANDIDATE', 'project', { kind: 'PROJECT' }))).toMatchObject({
      title: '项目经历',
      primaryAction: '保留这段项目经历',
    })
  })

  it('uses a safe fallback for unknown candidate kinds', () => {
    expect(getReviewCandidatePresentation(state('UNKNOWN'))).toMatchObject({
      title: '待确认内容',
      primaryAction: '保留这项内容',
      canDelete: true,
    })
  })

  it('uses the backend reason when one is available', () => {
    const candidate = state('TEXT_FRAGMENT')
    candidate.item.reason = '后端提供的确认原因'
    expect(getReviewCandidatePresentation(candidate).description).toBe('后端提供的确认原因')
  })

  it('provides a safe fallback reason when the backend reason is empty', () => {
    expect(getReviewCandidatePresentation(state('TEXT_FRAGMENT')).description).toContain('无法安全判断')
  })

  it('maps entry kinds to user-facing section titles', () => {
    expect(getReviewEntryTitle('EDUCATION')).toBe('教育经历')
    expect(getReviewEntryTitle('EXPERIENCE')).toBe('工作经历')
    expect(getReviewEntryTitle('PROJECT')).toBe('项目经历')
    expect(getReviewEntryTitle('OTHER')).toBe('经历条目')
  })

  it('formats progress using the candidate count', () => {
    expect(getReviewProgressLabel(0, 3)).toBe('第 1 项 / 共 3 项')
  })

  it('keeps the existing entry summary fallback readable', () => {
    expect(entryDraftSummary(JSON.stringify({
      organization: '示例公司',
      role: '后端工程师',
      bullets: [{ text: '负责服务开发' }],
    }))).toEqual({
      title: '示例公司',
      details: ['后端工程师'],
      bullets: ['负责服务开发'],
    })
  })

  it('groups navigation presentation without changing item order', () => {
    const items = [
      state('ENTRY_CANDIDATE', 'experience', { kind: 'EXPERIENCE', organization: '示例公司', role: '后端工程师' }),
      state('CONTACT_CANDIDATE', 'email', { value: 'email@example.com' }),
      state('ENTRY_CANDIDATE', 'education', { kind: 'EDUCATION', school: '示例大学', degree: '本科' }),
      state('TEXT_FRAGMENT', 'fragment', { text: '负责跨团队推进' }),
      state('UNKNOWN_KIND', 'unknown', { text: '未知片段' }),
      state('CONTACT_CANDIDATE', 'phone', { value: '13800138000' }),
    ]

    expect(groupReviewItems(items).map((group) => [group.label, group.items.map((item) => item.state.item.id)])).toEqual([
      ['基本信息', ['email', 'phone']],
      ['经历内容', ['experience', 'education']],
      ['其他内容', ['fragment', 'unknown']],
    ])
    expect(getReviewItemNavigationPresentation(items[0]!).summary).toBe('示例公司 · 后端工程师')
    expect(getReviewItemNavigationPresentation(items[1]!).typeLabel).toBe('邮箱')
    expect(getReviewItemNavigationPresentation(items[2]!).summary).toBe('示例大学 · 本科')
    expect(getReviewItemNavigationPresentation(items[3]!).summary).toBe('负责跨团队推进')
    expect(getReviewItemNavigationPresentation(items[4]!).typeLabel).toBe('待确认内容')
  })
})
