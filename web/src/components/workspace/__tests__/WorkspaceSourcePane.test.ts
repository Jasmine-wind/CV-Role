// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkspaceSourcePane from '@/components/workspace/WorkspaceSourcePane.vue'
import type {
  WorkspaceSourceBlock,
  WorkspaceSourceReference,
  WorkspaceTargetMapping,
} from '@/types/workspace'

const { confirmMock, unconfirmMock } = vi.hoisted(() => ({
  confirmMock: vi.fn(),
  unconfirmMock: vi.fn(),
}))

vi.mock('@/api/workspace', () => ({
  getWorkspaceSourcePdf: vi.fn(),
  confirmWorkspaceSourceOmissions: confirmMock,
  unconfirmWorkspaceSourceOmissions: unconfirmMock,
}))

const block = (
  id: string,
  status: WorkspaceSourceBlock['status'],
  overrides: Partial<WorkspaceSourceBlock> = {},
): WorkspaceSourceBlock => ({
  id,
  order: Number(id.replace(/\D/g, '')) || 0,
  text: `原文 ${id}`,
  occurrenceIds: [id],
  sourceNodeType: null,
  sourceSectionKind: null,
  sourceSectionId: null,
  sourceEntryId: null,
  sourceBulletId: null,
  targetNodeIds: [],
  status,
  reliable: false,
  omissionConfirmed: false,
  omissionEligible: false,
  ...overrides,
})

const mapping = (targetNodeId: string, textChanged: boolean): WorkspaceTargetMapping => ({
  targetNodeId,
  nodeType: 'BULLET',
  sectionId: 's',
  entryId: 'e',
  bulletId: 'b',
  targetText: '当前内容',
  sourceOccurrenceIds: ['occ-1'],
  status: 'EXACT',
  reliable: true,
  textChanged,
  omissionConfirmed: false,
  omissionEligible: false,
})

const sourceWith = (
  sourceBlocks: WorkspaceSourceBlock[],
  overrides: Partial<WorkspaceSourceReference> = {},
): WorkspaceSourceReference => ({
  optimizationTaskId: 1,
  sourceResumeVersionId: 2,
  targetResumeVersionId: 3,
  targetRevision: 4,
  sourceFilename: 'synthetic.pdf',
  sourcePdfAvailable: false,
  sourceBlocks,
  mappings: [],
  fidelityIssues: [],
  statusCounts: { EXACT: 0, MERGED: 0, SPLIT: 0, UNMAPPED: 0, AMBIGUOUS: 0 },
  confirmedOmissionCount: 0,
  exportBlocked: false,
  ...overrides,
})

const mountPane = (source: WorkspaceSourceReference, props = {}) =>
  mount(WorkspaceSourcePane, {
    props: {
      optimizationTaskId: 1,
      source,
      loading: false,
      error: null,
      omissionDisabledReason: null,
      ...props,
    },
  })

const deferred = <T>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve
  })
  return { promise, resolve }
}

describe('WorkspaceSourcePane', () => {
  beforeEach(() => vi.clearAllMocks())

  it('navigates only a unique reliable occurrence and fails closed for ambiguity', async () => {
    const focusTarget = vi.fn()
    const source = sourceWith([
      block('occ-1', 'EXACT', {
        text: 'OmniGateway',
        targetNodeIds: ['section:s/entry:e/bullet:b'],
        reliable: true,
      }),
      block('occ-2', 'SPLIT', { text: '边界待确认', targetNodeIds: ['a', 'b'] }),
    ])
    const wrapper = mountPane(source, { onFocusTarget: focusTarget })

    const buttons = wrapper.findAll('.source-block-main')
    await buttons[0]!.trigger('click')
    expect(focusTarget).toHaveBeenCalledWith('section:s/entry:e/bullet:b')

    expect(buttons[1]!.attributes('disabled')).toBeDefined()
    await buttons[1]!.trigger('click')
    expect(focusTarget).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('尚未确认该原文对应位置')
  })

  it('maps lineage and text changes to user language without exposing internal enums', () => {
    const exactTarget = 'section:s/entry:e/bullet:b'
    const source = sourceWith(
      [
        block('occ-1', 'EXACT', { targetNodeIds: [exactTarget], reliable: true }),
        block('occ-2', 'MERGED'),
        block('occ-3', 'SPLIT', {
          targetNodeIds: ['section:s/entry:e/bullet:split'],
          reliable: true,
        }),
        block('occ-4', 'AMBIGUOUS'),
        block('occ-5', 'UNMAPPED'),
        block('occ-6', 'UNMAPPED', { omissionEligible: true }),
        block('occ-7', 'UNMAPPED', { omissionConfirmed: true }),
        block('occ-8', 'EXACT', { targetNodeIds: ['section:s/entry:e/bullet:plain'], reliable: true }),
      ],
      {
        mappings: [
          mapping(exactTarget, true),
          mapping('section:s/entry:e/bullet:split', true),
          mapping('section:s/entry:e/bullet:plain', false),
        ],
        confirmedOmissionCount: 1,
      },
    )
    const wrapper = mountPane(source)

    const statusOf = (id: string) =>
      wrapper.get(`[data-source-block-id="${id}"] .mapping-state`).text()
    expect(statusOf('occ-1')).toBe('已定位 · 内容已修改')
    expect(statusOf('occ-2')).toBe('合并来源')
    expect(statusOf('occ-3')).toBe('拆分来源 · 内容已修改')
    expect(statusOf('occ-4')).toBe('待确认')
    expect(statusOf('occ-5')).toBe('未映射')
    expect(statusOf('occ-6')).toBe('未映射')
    expect(statusOf('occ-7')).toBe('已确认省略')
    expect(statusOf('occ-8')).toBe('已定位')
    expect(wrapper.text()).toContain('已确认省略 1 段原文')
    for (const internalStatus of ['EXACT', 'MERGED', 'SPLIT', 'UNMAPPED', 'AMBIGUOUS']) {
      expect(wrapper.text()).not.toContain(internalStatus)
    }
  })

  it('confirms all eligible unmapped blocks in the same server-provided PROJECT entry once', async () => {
    const pending = deferred<{
      saved: boolean
      conflict: boolean
      revision: number
      document: null
    }>()
    confirmMock.mockReturnValueOnce(pending.promise)
    const projectBoundary = {
      sourceNodeType: 'ENTRY',
      sourceSectionKind: 'PROJECT',
      sourceSectionId: 'project-section',
      sourceEntryId: 'project-entry',
      omissionEligible: true,
    }
    const source = sourceWith([
      block('occ-1', 'UNMAPPED', { ...projectBoundary, occurrenceIds: ['occ-1', 'occ-1b'] }),
      block('occ-2', 'UNMAPPED', { ...projectBoundary, occurrenceIds: ['occ-2'] }),
    ])
    const omissionSaved = vi.fn()
    const omissionBusy = vi.fn()
    const wrapper = mountPane(source, {
      onOmissionSaved: omissionSaved,
      onOmissionBusy: omissionBusy,
    })

    const action = wrapper.get('button.omission-action')
    expect(action.text()).toBe('确认省略此项目对应的 2 段原文')
    await action.trigger('click')
    await action.trigger('click')

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(confirmMock).toHaveBeenCalledWith(1, {
      expectedRevision: 4,
      sourceOccurrenceIds: ['occ-1', 'occ-1b', 'occ-2'],
    })
    expect(action.attributes('disabled')).toBeDefined()
    expect(action.text()).toBe('正在确认…')

    const result = { saved: true, conflict: false, revision: 5, document: null } as const
    pending.resolve(result)
    await flushPromises()

    expect(omissionSaved).toHaveBeenCalledWith(4, result, true)
    expect(omissionBusy.mock.calls).toEqual([[true], [false]])
  })

  it('does not offer a project omission when any block in the frozen entry boundary is ineligible', () => {
    const projectBoundary = {
      sourceNodeType: 'ENTRY',
      sourceSectionKind: 'PROJECT',
      sourceSectionId: 'project-section',
      sourceEntryId: 'project-entry',
      omissionEligible: true,
    }
    const source = sourceWith([
      block('occ-1', 'UNMAPPED', projectBoundary),
      block('occ-2', 'EXACT', { ...projectBoundary, omissionEligible: false }),
    ])

    const wrapper = mountPane(source)

    expect(wrapper.findAll('button.omission-action')).toHaveLength(0)
  })

  it('reconciles a partially confirmed project as one whole-entry confirmation', async () => {
    confirmMock.mockResolvedValueOnce({ saved: true, conflict: false, revision: 5, document: null })
    const projectBoundary = {
      sourceNodeType: 'ENTRY',
      sourceSectionKind: 'PROJECT',
      sourceSectionId: 'project-section',
      sourceEntryId: 'project-entry',
      omissionEligible: true,
    }
    const source = sourceWith([
      block('occ-1', 'UNMAPPED', { ...projectBoundary, omissionConfirmed: true }),
      block('occ-2', 'UNMAPPED', projectBoundary),
    ])
    const wrapper = mountPane(source)

    expect(wrapper.findAll('button.omission-action')).toHaveLength(1)
    expect(wrapper.get('button.omission-action').text()).toBe('确认省略此项目对应的 2 段原文')
    await wrapper.get('button.omission-action').trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledWith(1, {
      expectedRevision: 4,
      sourceOccurrenceIds: ['occ-1', 'occ-2'],
    })
  })

  it('confirms every physical occurrence fragment of one server-provided source bullet', async () => {
    confirmMock.mockResolvedValueOnce({ saved: true, conflict: false, revision: 5, document: null })
    const bulletBoundary = {
      sourceNodeType: 'BULLET',
      sourceSectionKind: 'SUMMARY',
      sourceSectionId: 'summary-section',
      sourceEntryId: 'summary-entry',
      sourceBulletId: 'summary-bullet',
      omissionEligible: true,
    }
    const source = sourceWith([
      block('occ-1', 'UNMAPPED', { ...bulletBoundary }),
      block('occ-2', 'UNMAPPED', { ...bulletBoundary }),
      block('occ-3', 'UNMAPPED', { ...bulletBoundary }),
    ])
    const wrapper = mountPane(source)

    expect(wrapper.findAll('button.omission-action')).toHaveLength(1)
    await wrapper.get('button.omission-action').trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledWith(1, {
      expectedRevision: 4,
      sourceOccurrenceIds: ['occ-1', 'occ-2', 'occ-3'],
    })
  })

  it('keeps an invalidated in-flight omission locked until it settles and clears parent busy state', async () => {
    const pending = deferred<{
      saved: boolean
      conflict: boolean
      revision: number
      document: null
    }>()
    confirmMock.mockReturnValueOnce(pending.promise)
    const source = sourceWith([block('occ-1', 'UNMAPPED', { omissionEligible: true })])
    const omissionBusy = vi.fn()
    const wrapper = mountPane(source, { onOmissionBusy: omissionBusy })

    await wrapper.get('button.omission-action').trigger('click')
    await wrapper.setProps({ source: sourceWith(source.sourceBlocks, { targetRevision: 5 }) })
    expect(wrapper.get('button.omission-action').attributes('disabled')).toBeDefined()

    pending.resolve({ saved: false, conflict: true, revision: 5, document: null })
    await flushPromises()

    expect(omissionBusy.mock.calls).toEqual([[true], [false]])
    expect(wrapper.get('button.omission-action').attributes('disabled')).toBeUndefined()
  })

  it('disables omission while the editor is not fully saved', () => {
    const source = sourceWith([block('occ-1', 'UNMAPPED', { omissionEligible: true })])
    const wrapper = mountPane(source, {
      omissionDisabledReason: '请先完成当前简历保存，再确认省略。',
    })

    expect(wrapper.get('button.omission-action').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('请先完成当前简历保存，再确认省略。')
  })

  it('surfaces CAS conflicts and never reports them as a successful confirmation', async () => {
    confirmMock.mockResolvedValueOnce({
      saved: false,
      conflict: true,
      revision: 5,
      document: null,
    })
    const source = sourceWith([block('occ-1', 'UNMAPPED', { omissionEligible: true })])
    const omissionConcurrent = vi.fn()
    const omissionSaved = vi.fn()
    const wrapper = mountPane(source, {
      onOmissionConcurrent: omissionConcurrent,
      onOmissionSaved: omissionSaved,
    })

    await wrapper.get('button.omission-action').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('当前简历已有更新，本次操作未生效')
    expect(omissionConcurrent).toHaveBeenCalledTimes(1)
    expect(omissionSaved).not.toHaveBeenCalled()
  })

  it('allows a confirmed omission to be cancelled through the unconfirm CAS endpoint', async () => {
    unconfirmMock.mockResolvedValueOnce({
      saved: true,
      conflict: false,
      revision: 5,
      document: null,
    })
    const source = sourceWith([
      block('occ-1', 'UNMAPPED', { omissionConfirmed: true, occurrenceIds: ['occ-1', 'occ-2'] }),
    ])
    const omissionSaved = vi.fn()
    const wrapper = mountPane(source, { onOmissionSaved: omissionSaved })

    await wrapper.get('button.omission-action').trigger('click')
    await flushPromises()

    expect(unconfirmMock).toHaveBeenCalledWith(1, {
      expectedRevision: 4,
      sourceOccurrenceIds: ['occ-1', 'occ-2'],
    })
    expect(omissionSaved.mock.calls[0]?.[2]).toBe(false)
  })

  it('surfaces blocker count instead of hiding unmapped content', () => {
    const source = sourceWith([block('occ-2', 'UNMAPPED', { omissionEligible: true })], {
      exportBlocked: true,
      fidelityIssues: [
        {
          code: 'SOURCE_CONTENT_UNMAPPED',
          severity: 'BLOCKER',
          message: '冻结原文未映射',
          sourceOccurrenceIds: ['occ-2'],
          targetNodeIds: [],
        },
      ],
    })
    const wrapper = mountPane(source)

    expect(wrapper.text()).toContain('结构保真：1 项阻断')
    expect(wrapper.text()).toContain('冻结原文未映射')
  })
})
