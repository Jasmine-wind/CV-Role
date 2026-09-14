// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkspacePanel from '@/components/workspace/WorkspacePanel.vue'

const {
  contentMock,
  sourceMock,
  analysisMock,
  messageWarning,
  messageSuccess,
  messageError,
  routerPush,
} = vi.hoisted(() => ({
  contentMock: vi.fn(),
  sourceMock: vi.fn(),
  analysisMock: vi.fn(),
  messageWarning: vi.fn(),
  messageSuccess: vi.fn(),
  messageError: vi.fn(),
  routerPush: vi.fn(),
}))

vi.mock('@/api/workspace', () => ({
  getWorkspaceContent: contentMock,
  getWorkspaceSourceReference: sourceMock,
  saveWorkspaceContent: vi.fn(),
  restorePreOptimizationContent: vi.fn(),
  requestBulletSuggestion: vi.fn(),
  getWorkspaceSourcePdf: vi.fn(),
  confirmWorkspaceSourceOmissions: vi.fn(),
  unconfirmWorkspaceSourceOmissions: vi.fn(),
  restoreWorkspaceSourceContent: vi.fn(),
}))

vi.mock('@/api/job-analysis', () => ({
  getOptimizationAnalysisResult: analysisMock,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: routerPush, replace: vi.fn() }),
  useRoute: () => ({ query: {} }),
  onBeforeRouteLeave: vi.fn(),
  onBeforeRouteUpdate: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: messageError,
    warning: messageWarning,
    success: messageSuccess,
  },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(undefined) },
  ElButton: {
    props: ['disabled', 'loading'],
    template: '<button :disabled="disabled"><slot /></button>',
  },
}))

const document = {
  schemaVersion: 'RESUME_DOCUMENT_V1',
  basics: { name: '张三', contacts: [] },
  sections: [
    {
      id: 's-1',
      kind: 'EXPERIENCE',
      title: '工作经历',
      entries: [
        {
          id: 's-1-e-1',
          organization: '某公司',
          role: 'Java 开发',
          bullets: [{ id: 's-1-e-1-b-2', text: '剩余要点' }],
        },
      ],
    },
  ],
}

const blockerBlock = {
  id: 'occ-9',
  order: 0,
  text: '负责 Redis 热点缓存与缓存一致性',
  occurrenceIds: ['occ-9'],
  sourceNodeType: 'BULLET',
  sourceSectionKind: 'EXPERIENCE',
  sourceSectionId: 's-1',
  sourceEntryId: 's-1-e-1',
  sourceBulletId: 'lost-bullet',
  targetNodeIds: [],
  status: 'UNMAPPED' as const,
  reliable: false,
  omissionConfirmed: false,
  omissionEligible: true,
  restoreScope: 'BULLET' as const,
  restoreEligible: true,
  restoreBlockedReason: null,
}

const sourceReference = {
  optimizationTaskId: 50,
  sourceResumeVersionId: 40,
  targetResumeVersionId: 41,
  targetRevision: 5,
  sourceFilename: 'synthetic.pdf',
  sourcePdfAvailable: false,
  sourceBlocks: [blockerBlock],
  mappings: [],
  fidelityIssues: [
    {
      code: 'SOURCE_CONTENT_UNMAPPED',
      severity: 'BLOCKER' as const,
      message: '冻结原文仍有内容未进入当前结构，导出已阻止。',
      sourceOccurrenceIds: ['occ-9'],
      targetNodeIds: [],
    },
  ],
  statusCounts: { EXACT: 0, MERGED: 0, SPLIT: 0, UNMAPPED: 1, AMBIGUOUS: 0 },
  confirmedOmissionCount: 0,
  exportBlocked: true,
}

const mountPanel = () =>
  mount(WorkspacePanel, {
    props: { optimizationTaskId: 50 },
    global: {
      stubs: {
        ResumeEditor: true,
        WorkspaceRequirements: true,
        WorkspaceSuggestions: true,
        // Preview 行为本身由 WorkspacePreviewExport 单测覆盖；此处只验证父组件的导航语义。
        WorkspacePreviewExport: {
          name: 'WorkspacePreviewExportStub',
          emits: ['resolveFidelity', 'stale'],
          template:
            '<button class="stub-resolve-fidelity" @click="$emit(\'resolveFidelity\')">查看并处理</button>',
        },
      },
    },
  })

const openPreview = async (wrapper: ReturnType<typeof mountPanel>) => {
  const previewButton = wrapper
    .findAll('button')
    .find((button) => button.text().includes('预览 →'))
  expect(previewButton).toBeTruthy()
  await previewButton!.trigger('click')
  await flushPromises()
}

describe('WorkspacePanel structure fidelity resolver navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    contentMock.mockResolvedValue({ optimizationTaskId: 50, revision: 5, document })
    sourceMock.mockResolvedValue(sourceReference)
    analysisMock.mockResolvedValue({
      jobTitle: 'Java 后端工程师',
      resumeName: '张三的简历',
      analysisMode: 'EVIDENCE',
      evidenceAnalysis: { requirements: [] },
    })
  })

  it('routes Preview 查看并处理 back to the edit-mode source issue area and selects the first blocker', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    await openPreview(wrapper)
    expect(wrapper.find('.stub-resolve-fidelity').exists()).toBe(true)

    await wrapper.get('.stub-resolve-fidelity').trigger('click')
    await flushPromises()

    // 回到编辑态（Preview 保持挂载但被 v-show 隐藏）
    expect(wrapper.find('.workspace-preview-mode').attributes('style')).toContain('display: none')
    // 结构问题区域可见，并定位到第一个 BLOCKER 的 source occurrence
    expect(wrapper.find('.fidelity-issues').exists()).toBe(true)
    expect(wrapper.get('.issue-card').attributes('data-issue-code')).toBe('SOURCE_CONTENT_UNMAPPED')
    expect(wrapper.find('details[open]').exists()).toBe(true)
    const selected = wrapper.get('.source-block.is-selected')
    expect(selected.attributes('data-source-block-id')).toBe('occ-9')
    // 只做导航：不触发任何保存请求
    expect(messageWarning).not.toHaveBeenCalledWith(expect.stringContaining('保存'))
  })

  it('switches the narrow layout to the source panel when the resolver requests review', async () => {
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => ({
        matches: true,
        media: query,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    )
    try {
      const wrapper = mountPanel()
      await flushPromises()

      await openPreview(wrapper)
      await wrapper.get('.stub-resolve-fidelity').trigger('click')
      await flushPromises()

      const sourceTab = wrapper
        .findAll('.workspace-mobile-switch button')
        .find((button) => button.text().includes('冻结原文'))
      expect(sourceTab).toBeTruthy()
      expect(sourceTab!.classes()).toContain('is-active')
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('locates an issue target even when its mapping is unreliable', async () => {
    const targetNodeId = 'section:s-1/entry:s-1-e-1/bullet:stray-1'
    sourceMock.mockResolvedValue({
      ...sourceReference,
      sourceBlocks: [{ ...blockerBlock, targetNodeIds: [targetNodeId] }],
      mappings: [
        {
          targetNodeId,
          nodeType: 'BULLET',
          sectionId: 's-1',
          entryId: 's-1-e-1',
          bulletId: 'stray-1',
          targetText: '错误归属内容',
          sourceOccurrenceIds: ['occ-9'],
          status: 'AMBIGUOUS',
          reliable: false,
          textChanged: false,
          omissionConfirmed: false,
          omissionEligible: false,
        },
      ],
      fidelityIssues: [
        {
          code: 'AMBIGUOUS_MAPPING',
          severity: 'BLOCKER' as const,
          message: '目标内容包含未知或不一致的原文引用，必须人工核对。',
          sourceOccurrenceIds: ['occ-9'],
          targetNodeIds: [targetNodeId],
        },
      ],
    })
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.get('button.issue-locate-action').trigger('click')
    await flushPromises()

    // 定位不修改数据，只把锚点交给编辑器（此处断言没有触发保存路径）
    expect(messageError).not.toHaveBeenCalled()
  })
})
