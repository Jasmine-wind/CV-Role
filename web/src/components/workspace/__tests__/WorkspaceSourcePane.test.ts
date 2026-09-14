// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkspaceSourcePane from '@/components/workspace/WorkspaceSourcePane.vue'
import type {
  WorkspaceSourceBlock,
  WorkspaceSourceReference,
  WorkspaceTargetMapping,
} from '@/types/workspace'

vi.mock('@/api/workspace', () => ({
  getWorkspaceSourcePdf: vi.fn(),
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
  restoreScope: 'NONE',
  restoreEligible: false,
  restoreBlockedReason: null,
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
      sourceMutationDisabledReason: null,
      mutationBusy: false,
      mutationError: null,
      reviewRequestKey: 0,
      ...props,
    },
  })

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

  it('maps lineage and text changes to user language and hides the noise for plain mappings', () => {
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
    expect(statusOf('occ-1')).toBe('内容已修改')
    expect(statusOf('occ-2')).toBe('合并来源')
    expect(statusOf('occ-3')).toBe('拆分来源 · 内容已修改')
    expect(statusOf('occ-4')).toBe('需要核对')
    expect(statusOf('occ-5')).toBe('需要处理')
    expect(statusOf('occ-6')).toBe('需要处理')
    expect(statusOf('occ-7')).toBe('已确认省略')
    // 正常映射成功的块不再占位：没有需要用户处理的信息时不显示状态。
    expect(
      wrapper.find('[data-source-block-id="occ-8"] .mapping-state').exists(),
    ).toBe(false)
    expect(wrapper.text()).toContain('已确认省略 1 段原文')
    for (const internalStatus of ['EXACT', 'MERGED', 'SPLIT', 'UNMAPPED', 'AMBIGUOUS']) {
      expect(wrapper.text()).not.toContain(internalStatus)
    }
  })

  it('emits one omission intent for a complete Project boundary without touching any API', async () => {
    const projectBoundary = {
      sourceSectionKind: 'PROJECT',
      sourceSectionId: 'project-section',
      sourceEntryId: 'project-entry',
      omissionEligible: true,
    }
    const source = sourceWith([
      block('occ-1', 'UNMAPPED', {
        ...projectBoundary,
        sourceNodeType: 'ENTRY',
        occurrenceIds: ['occ-1', 'occ-1b'],
      }),
      block('occ-2', 'UNMAPPED', {
        ...projectBoundary,
        sourceNodeType: 'BULLET',
        sourceBulletId: 'project-bullet',
        occurrenceIds: ['occ-2'],
      }),
    ])
    const omissionRequested = vi.fn()
    const wrapper = mountPane(source, { onOmissionRequested: omissionRequested })

    const action = wrapper.get('button.omission-action')
    expect(action.text()).toBe('确认省略此项目对应的 2 段原文')
    await action.trigger('click')

    // Pane 只 emit 点击的 block；occurrence 边界由 Panel 用 fresh source-reference 解析。
    expect(omissionRequested).toHaveBeenCalledTimes(1)
    expect(omissionRequested).toHaveBeenCalledWith('occ-1', true)
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

  it('does not merge different project entries or non-project bullets into one action', () => {
    const source = sourceWith([
      block('project-a', 'UNMAPPED', {
        sourceNodeType: 'BULLET',
        sourceSectionKind: 'PROJECT',
        sourceSectionId: 'project-section',
        sourceEntryId: 'project-a-entry',
        sourceBulletId: 'a-bullet',
        omissionEligible: true,
      }),
      block('project-b', 'UNMAPPED', {
        sourceNodeType: 'ENTRY',
        sourceSectionKind: 'PROJECT',
        sourceSectionId: 'project-section',
        sourceEntryId: 'project-b-entry',
        omissionEligible: true,
      }),
      block('summary-a', 'UNMAPPED', {
        sourceNodeType: 'BULLET',
        sourceSectionKind: 'SUMMARY',
        sourceSectionId: 'summary-section',
        sourceEntryId: 'summary-entry',
        sourceBulletId: 'summary-a-bullet',
        omissionEligible: true,
      }),
      block('summary-b', 'UNMAPPED', {
        sourceNodeType: 'BULLET',
        sourceSectionKind: 'SUMMARY',
        sourceSectionId: 'summary-section',
        sourceEntryId: 'summary-entry',
        sourceBulletId: 'summary-b-bullet',
        omissionEligible: true,
      }),
    ])
    const wrapper = mountPane(source)

    expect(wrapper.findAll('button.omission-action')).toHaveLength(4)
  })

  it('reconciles a partially confirmed project as one whole-entry confirmation', () => {
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
  })

  it('cancels a confirmed omission through the same intent event', async () => {
    const source = sourceWith([
      block('occ-1', 'UNMAPPED', { omissionConfirmed: true, occurrenceIds: ['occ-1', 'occ-2'] }),
    ])
    const omissionRequested = vi.fn()
    const wrapper = mountPane(source, { onOmissionRequested: omissionRequested })

    const action = wrapper.get('button.omission-action')
    expect(action.text()).toBe('取消省略')
    await action.trigger('click')

    expect(omissionRequested).toHaveBeenCalledWith('occ-1', false)
  })

  it('shows and clears the in-flight label from the mutationBusy prop', async () => {
    const source = sourceWith([block('occ-1', 'UNMAPPED', { omissionEligible: true })])
    const wrapper = mountPane(source)

    const action = wrapper.get('button.omission-action')
    await action.trigger('click')
    // 点击后立即进入本地 pending 展示；busy 由父组件驱动。
    await wrapper.setProps({ mutationBusy: true })
    expect(action.text()).toBe('正在确认…')

    await wrapper.setProps({ mutationBusy: false })
    expect(action.text()).toBe('确认省略')
  })

  it('surfaces the parent mutation error next to the clicked block and disables actions on the shared gate', async () => {
    const source = sourceWith([block('occ-1', 'UNMAPPED', { omissionEligible: true })])
    const wrapper = mountPane(source, {
      mutationError: { blockId: 'occ-1', message: '存在编辑冲突，请核对后重试。' },
      sourceMutationDisabledReason: '保存失败，请先重试保存。',
    })

    expect(wrapper.get('.source-block .mutation-error').text()).toBe('存在编辑冲突，请核对后重试。')
    expect(wrapper.get('button.omission-action').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('保存失败，请先重试保存。')
  })

  it('gives each source and omission control a contextual accessible name and description', () => {
    const source = sourceWith([
      block('occ-1', 'UNMAPPED', { text: '重复原文', omissionEligible: true }),
      block('occ-2', 'UNMAPPED', {
        text: '项目原文',
        sourceNodeType: 'ENTRY',
        sourceSectionKind: 'PROJECT',
        sourceSectionId: 'project-section',
        sourceEntryId: 'project-entry',
        omissionEligible: true,
      }),
    ])
    const wrapper = mountPane(source, {
      sourceMutationDisabledReason: '保存失败，请先重试保存。',
    })

    const sourceNames = wrapper
      .findAll('button.source-block-main')
      .map((button) => button.attributes('aria-label'))
    expect(sourceNames[0]).toContain('第 2 段冻结原文：重复原文。需要处理')
    expect(sourceNames[1]).toContain('第 3 段冻结原文：项目原文。需要处理')
    expect(new Set(sourceNames).size).toBe(2)

    const omissionActions = wrapper.findAll('button.omission-action')
    expect(omissionActions[0]!.attributes('aria-label')).toContain(
      '确认省略：第 2 段冻结原文：重复原文',
    )
    expect(omissionActions[1]!.attributes('aria-label')).toContain(
      '确认省略此项目原文：第 3 段冻结原文：项目原文',
    )
    for (const action of omissionActions) {
      expect(action.attributes('disabled')).toBeDefined()
      for (const descriptionId of action.attributes('aria-describedby')!.split(' ')) {
        expect(wrapper.find(`#${descriptionId}`).exists()).toBe(true)
      }
    }
  })

  it('shows the blocker strip in user language and renders every blocker as an actionable issue card', () => {
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

    expect(wrapper.text()).toContain('还有 1 项内容需要确认')
    expect(wrapper.text()).toContain('不影响继续编辑和预览，处理完成后才能正式导出。')
    const card = wrapper.get('.issue-card')
    expect(card.attributes('data-issue-code')).toBe('SOURCE_CONTENT_UNMAPPED')
    expect(card.get('.issue-card-title').text()).toBe('原文内容未进入当前简历')
    expect(card.text()).toContain('这段内容存在于原始简历，但当前简历中找不到。')
    expect(card.text()).toContain('原文 occ-2')
    // 内部 code 不得进入用户可见文案
    expect(wrapper.text()).not.toContain('SOURCE_CONTENT_UNMAPPED')
  })

  it('offers restore and omission on a restorable bullet and emits intent-only events', async () => {
    const source = sourceWith(
      [
        block('occ-1', 'UNMAPPED', {
          sourceNodeType: 'BULLET',
          sourceSectionKind: 'SUMMARY',
          sourceSectionId: 'summary-section',
          sourceEntryId: 'summary-entry',
          sourceBulletId: 'summary-bullet',
          omissionEligible: true,
          restoreEligible: true,
          restoreScope: 'BULLET',
        }),
      ],
      {
        exportBlocked: true,
        fidelityIssues: [
          {
            code: 'SOURCE_CONTENT_UNMAPPED',
            severity: 'BLOCKER',
            message: '冻结原文未映射',
            sourceOccurrenceIds: ['occ-1'],
            targetNodeIds: [],
          },
        ],
      },
    )
    const restoreRequested = vi.fn()
    const wrapper = mountPane(source, { onRestoreRequested: restoreRequested })

    const card = wrapper.get('.issue-card')
    const cardRestore = card.get('button.issue-restore-action')
    expect(cardRestore.text()).toBe('恢复原文')
    expect(card.get('button.issue-omission-action').text()).toBe('确认省略')
    const inlineRestore = wrapper.get('.source-block button.restore-action')
    expect(inlineRestore.text()).toBe('恢复原文')

    await cardRestore.trigger('click')
    await inlineRestore.trigger('click')

    // 点击只 emit blockId；Panel 负责 single-flight 与 CAS revision。
    expect(restoreRequested).toHaveBeenCalledTimes(2)
    expect(restoreRequested).toHaveBeenCalledWith('occ-1')
  })

  it('labels a restorable Project boundary as restoring the whole project', () => {
    const projectBoundary = {
      sourceSectionKind: 'PROJECT',
      sourceSectionId: 'project-section',
      sourceEntryId: 'project-entry',
      omissionEligible: true,
      restoreEligible: true,
      restoreScope: 'PROJECT_ENTRY' as const,
    }
    const source = sourceWith(
      [
        block('occ-1', 'UNMAPPED', { ...projectBoundary, sourceNodeType: 'ENTRY' }),
        block('occ-2', 'UNMAPPED', {
          ...projectBoundary,
          sourceNodeType: 'BULLET',
          sourceBulletId: 'project-bullet',
        }),
      ],
      {
        exportBlocked: true,
        fidelityIssues: [
          {
            code: 'PROJECT_BOUNDARY_LOST',
            severity: 'BLOCKER',
            message: '项目边界丢失',
            sourceOccurrenceIds: ['occ-1', 'occ-2'],
            targetNodeIds: [],
          },
          {
            code: 'SOURCE_CONTENT_UNMAPPED',
            severity: 'BLOCKER',
            message: '冻结原文未映射',
            sourceOccurrenceIds: ['occ-1', 'occ-2'],
            targetNodeIds: [],
          },
        ],
      },
    )
    const wrapper = mountPane(source)

    const cards = wrapper.findAll('.issue-card')
    expect(cards).toHaveLength(2)
    for (const card of cards) {
      expect(card.get('button.issue-restore-action').text()).toBe('恢复整个项目')
      expect(card.get('button.issue-omission-action').text()).toBe('确认省略整个项目')
    }
    // 内联区域只在边界 leader block 上渲染一次恢复入口
    const inlineRestores = wrapper.findAll('.source-block button.restore-action')
    expect(inlineRestores).toHaveLength(1)
    expect(inlineRestores[0]!.text()).toBe('恢复整个项目')
  })

  it('offers only omission when the boundary is omission eligible but not restorable', () => {
    const source = sourceWith(
      [
        block('occ-1', 'UNMAPPED', {
          sourceNodeType: 'BULLET',
          sourceSectionKind: 'SUMMARY',
          sourceSectionId: 'summary-section',
          sourceEntryId: 'summary-entry',
          sourceBulletId: 'summary-bullet',
          omissionEligible: true,
          restoreScope: 'BULLET',
          restoreBlockedReason: 'BOUNDARY_HAS_OTHER_MAPPINGS',
        }),
      ],
      {
        exportBlocked: true,
        fidelityIssues: [
          {
            code: 'SOURCE_CONTENT_UNMAPPED',
            severity: 'BLOCKER',
            message: '冻结原文未映射',
            sourceOccurrenceIds: ['occ-1'],
            targetNodeIds: [],
          },
        ],
      },
    )
    const wrapper = mountPane(source)

    expect(wrapper.findAll('button.issue-restore-action')).toHaveLength(0)
    expect(wrapper.get('button.issue-omission-action').text()).toBe('确认省略')
  })

  it('explains when nothing can be handled automatically instead of offering fake buttons', () => {
    const source = sourceWith([block('occ-7', 'UNMAPPED')], {
      exportBlocked: true,
      fidelityIssues: [
        {
          code: 'SOURCE_CONTENT_UNMAPPED',
          severity: 'BLOCKER',
          message: '冻结原文未映射',
          sourceOccurrenceIds: ['occ-7'],
          targetNodeIds: [],
        },
      ],
    })
    const wrapper = mountPane(source)

    const card = wrapper.get('.issue-card')
    expect(card.text()).toContain('当前问题无法自动处理，请检查结构关系。')
    expect(card.findAll('button.issue-restore-action')).toHaveLength(0)
    expect(card.findAll('button.issue-omission-action')).toHaveLength(0)
    expect(wrapper.findAll('.source-block button.restore-action')).toHaveLength(0)
  })

  it('offers a locate action for ambiguous mappings without restore or omission', async () => {
    const source = sourceWith(
      [block('occ-9', 'AMBIGUOUS', { targetNodeIds: ['section:s/entry:e/bullet:x'] })],
      {
        exportBlocked: true,
        fidelityIssues: [
          {
            code: 'AMBIGUOUS_MAPPING',
            severity: 'BLOCKER',
            message: '目标内容包含未知或不一致的原文引用，必须人工核对。',
            sourceOccurrenceIds: ['occ-9'],
            targetNodeIds: ['section:s/entry:e/bullet:x'],
          },
        ],
      },
    )
    const locate = vi.fn()
    const wrapper = mountPane(source, { onLocateIssueTarget: locate })

    const card = wrapper.get('.issue-card')
    expect(card.get('.issue-card-title').text()).toBe('对应关系不明确')
    expect(card.text()).toContain('系统无法确认这段当前内容来自哪一段原文')
    expect(card.findAll('button.issue-restore-action')).toHaveLength(0)
    expect(card.findAll('button.issue-omission-action')).toHaveLength(0)
    await card.get('button.issue-locate-action').trigger('click')
    expect(locate).toHaveBeenCalledWith('section:s/entry:e/bullet:x')
  })

  it('offers one indexed locate action per duplicate target', () => {
    const source = sourceWith([block('occ-8', 'SPLIT', { targetNodeIds: ['a', 'b'] })], {
      exportBlocked: true,
      fidelityIssues: [
        {
          code: 'DUPLICATE_MAPPING',
          severity: 'BLOCKER',
          message: '同一原文被重复写入多个目标位置，导出已阻止。',
          sourceOccurrenceIds: ['occ-8'],
          targetNodeIds: ['a', 'b'],
        },
      ],
    })
    const wrapper = mountPane(source)

    const card = wrapper.get('.issue-card')
    expect(card.get('.issue-card-title').text()).toBe('同一段原文出现了多份')
    const buttons = card.findAll('button.issue-locate-action')
    expect(buttons.map((button) => button.text())).toEqual(['定位第 1 处', '定位第 2 处'])
  })

  it('explains manifest problems as not solvable inside the current task', async () => {
    const source = sourceWith([], {
      exportBlocked: true,
      fidelityIssues: [
        {
          code: 'SOURCE_MANIFEST_INVALID',
          severity: 'BLOCKER',
          message: '冻结原文清单不完整，无法安全建立定位关系。',
          sourceOccurrenceIds: [],
          targetNodeIds: [],
        },
      ],
    })
    const restart = vi.fn()
    const wrapper = mountPane(source, { onRestartUpload: restart })

    const card = wrapper.get('.issue-card')
    expect(card.get('.issue-card-title').text()).toBe('原文校验数据异常')
    expect(card.text()).toContain('该问题无法通过编辑简历内容解决')
    expect(card.findAll('button.issue-restore-action')).toHaveLength(0)
    expect(card.findAll('button.issue-omission-action')).toHaveLength(0)
    await card.get('button.issue-restart-action').trigger('click')
    expect(restart).toHaveBeenCalledTimes(1)
  })

  it('opens the issue area when the resolver requests review', async () => {
    const source = sourceWith([block('occ-5', 'UNMAPPED')], {
      exportBlocked: true,
      fidelityIssues: [
        {
          code: 'SOURCE_CONTENT_UNMAPPED',
          severity: 'BLOCKER',
          message: '冻结原文未映射',
          sourceOccurrenceIds: [],
          targetNodeIds: [],
        },
      ],
    })
    const wrapper = mountPane(source)

    const details = wrapper.get('details')
    ;(details.element as HTMLDetailsElement).open = false
    await details.trigger('toggle')
    expect(wrapper.get('details').attributes('open')).toBeUndefined()

    await wrapper.setProps({ reviewRequestKey: 1, selectedOccurrenceIds: [] })
    await flushPromises()

    expect(wrapper.get('details').attributes('open')).toBeDefined()
  })
})
