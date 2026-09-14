// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import WorkspaceSourcePane from '@/components/workspace/WorkspaceSourcePane.vue'
import type { WorkspaceSourceReference } from '@/types/workspace'

const source: WorkspaceSourceReference = {
  optimizationTaskId: 1,
  sourceResumeVersionId: 2,
  targetResumeVersionId: 3,
  targetRevision: 4,
  sourceFilename: 'synthetic.pdf',
  sourcePdfAvailable: false,
  sourceBlocks: [
    { id: 'occ-1', order: 0, text: 'OmniGateway', occurrenceIds: ['occ-1'], targetNodeIds: ['section:s/entry:e/bullet:b'], status: 'EXACT', reliable: true },
    { id: 'occ-2', order: 1, text: '边界待确认', occurrenceIds: ['occ-2'], targetNodeIds: ['a', 'b'], status: 'SPLIT', reliable: false },
  ],
  mappings: [],
  fidelityIssues: [{ code: 'SOURCE_CONTENT_SPLIT', severity: 'WARNING', message: '请核对边界', sourceOccurrenceIds: ['occ-2'], targetNodeIds: ['a', 'b'] }],
  statusCounts: { EXACT: 1, MERGED: 0, SPLIT: 1, UNMAPPED: 0, AMBIGUOUS: 0 },
  exportBlocked: false,
}

describe('WorkspaceSourcePane', () => {
  it('navigates only a unique reliable occurrence and fails closed for ambiguity', async () => {
    const focusTarget = vi.fn()
    const wrapper = mount(WorkspaceSourcePane, {
      props: { optimizationTaskId: 1, source, loading: false, error: null, onFocusTarget: focusTarget },
    })

    const buttons = wrapper.findAll('.source-block button')
    await buttons[0]!.trigger('click')
    expect(focusTarget).toHaveBeenCalledWith('section:s/entry:e/bullet:b')

    expect(buttons[1]!.attributes('disabled')).toBeDefined()
    await buttons[1]!.trigger('click')
    expect(focusTarget).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('尚未确认该原文对应位置')
  })

  it('surfaces blocker count instead of hiding unmapped content', () => {
    const wrapper = mount(WorkspaceSourcePane, {
      props: {
        optimizationTaskId: 1,
        source: {
          ...source,
          exportBlocked: true,
          fidelityIssues: [{ code: 'SOURCE_CONTENT_UNMAPPED', severity: 'BLOCKER', message: '冻结原文未映射', sourceOccurrenceIds: ['occ-2'], targetNodeIds: [] }],
        },
        loading: false,
        error: null,
      },
    })

    expect(wrapper.text()).toContain('结构保真：1 项阻断')
    expect(wrapper.text()).toContain('冻结原文未映射')
  })
})
