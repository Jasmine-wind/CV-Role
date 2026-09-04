// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ResumeReviewProgress from '../ResumeReviewProgress.vue'
import type { ReviewItemState } from '@/views/resume/resumeReviewPresentation'

const makeState = (
  id: string,
  kind: string,
  values: { text?: string; contact?: string; entryKind?: string; organization?: string; role?: string } = {},
): ReviewItemState => ({
  item: { id, kind, canonicalDraft: '{}', reason: null },
  contact: { type: 'EMAIL', value: values.contact ?? '' },
  entry: {
    kind: values.entryKind,
    organization: values.organization ?? null,
    role: values.role ?? null,
    school: null,
    degree: null,
    major: null,
    startDate: null,
    endDate: null,
    bullets: [],
  },
  text: values.text ?? '',
})

describe('ResumeReviewProgress', () => {
  it('renders 30+ candidates in presentation groups while preserving source indexes', () => {
    const items = [
      makeState('name', 'NAME_CANDIDATE', { text: '林然' }),
      ...Array.from({ length: 12 }, (_, index) => makeState(`entry-${index}`, 'ENTRY_CANDIDATE', {
        entryKind: 'EXPERIENCE',
        organization: `示例公司 ${index + 1}`,
        role: '后端工程师',
      })),
      makeState('email', 'CONTACT_CANDIDATE', { contact: 'candidate@example.com' }),
      ...Array.from({ length: 19 }, (_, index) => makeState(`fragment-${index}`, 'TEXT_FRAGMENT', {
        text: `未归类内容 ${index + 1}`,
      })),
      makeState('unknown', 'UNEXPECTED_KIND', { text: '未知片段' }),
    ]
    const original = JSON.parse(JSON.stringify(items))
    const wrapper = mount(ResumeReviewProgress, {
      props: { items, activeItemId: 'fragment-18' },
    })

    expect(wrapper.get('nav').attributes('aria-label')).toBe('待确认内容')
    expect(wrapper.get('.resume-review-index summary').text()).toContain('查看全部待确认内容')
    expect(wrapper.findAll('.resume-review-index-item')).toHaveLength(34)
    expect(wrapper.findAll('.resume-review-index-group')).toHaveLength(3)
    expect(wrapper.find('.resume-review-index-group').text()).toContain('姓名')
    expect(wrapper.findAll('.resume-review-index-group').at(1)?.text()).toContain('经历内容')
    expect(wrapper.findAll('.resume-review-index-group').at(2)?.text()).toContain('其他内容')
    expect(wrapper.find('.resume-review-index-item[aria-current="step"]').text()).toContain('未归类内容')
    expect(wrapper.find('.resume-review-index-item[aria-current="step"]').attributes('aria-label')).toContain('第 33 项 / 共 34 项')
    expect(items).toEqual(original)
  })

  it('emits the original candidate id and keeps the active candidate discoverable', async () => {
    const items = [
      makeState('entry-1', 'ENTRY_CANDIDATE', { entryKind: 'PROJECT', organization: '推荐系统' }),
      makeState('fragment-1', 'TEXT_FRAGMENT', { text: '负责跨团队推进' }),
    ]
    const onSelect = vi.fn()
    const wrapper = mount(ResumeReviewProgress, {
      props: { items, activeItemId: 'entry-1', onSelect },
    })

    const active = wrapper.get('.resume-review-index-item[aria-current="step"]')
    expect(active.attributes('aria-label')).toContain('项目经历')
    const index = wrapper.get('.resume-review-index')
    ;(index.element as HTMLDetailsElement).open = true
    await index.trigger('toggle')
    const fragmentButton = wrapper.get('button[aria-label*="未归类内容"]')
    await fragmentButton.trigger('click')
    expect(onSelect).toHaveBeenCalledWith('fragment-1')
  })

  it('uses a safe unknown-kind group and fallback summary', () => {
    const wrapper = mount(ResumeReviewProgress, {
      props: {
        items: [makeState('unknown', 'FUTURE_KIND')],
        activeItemId: 'unknown',
      },
    })

    expect(wrapper.get('.resume-review-index-group-header h3').text()).toBe('其他内容')
    expect(wrapper.get('.resume-review-index-item').text()).toContain('待确认内容')
    expect(wrapper.get('.resume-review-index-item').text()).toContain('需要你确认这项内容')
  })
})
