// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import JobAnalysisView from '@/views/job/JobAnalysisView.vue'
import { getOptimizationAnalysisResult } from '@/api/job-analysis'
import type { EvidenceRequirementItem } from '@/types/evidence-analysis'

const push = vi.fn()
const elementPlusStubs = vi.hoisted(() => ({
  ElButton: {
    name: 'ElButton',
    props: ['disabled', 'loading', 'type', 'size'],
    emits: ['click'],
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
  },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { optimizationTaskId: '7' } }),
  useRouter: () => ({ push }),
}))

vi.mock('element-plus', () => elementPlusStubs)
vi.mock('element-plus/es', () => elementPlusStubs)
vi.mock('@/api/job-analysis', () => ({
  getOptimizationAnalysisResult: vi.fn(),
}))

const resultMock = vi.mocked(getOptimizationAnalysisResult)

const requirement = (
  id: number,
  importance: string,
  matchLevel: string,
): EvidenceRequirementItem => ({
  evidenceRequirementId: id,
  requirementText: `要求 ${id}`,
  importance,
  matchLevel,
  conclusion: matchLevel === 'MATCHED' ? '已有支持' : '需要核对',
  suggestion: matchLevel === 'PARTIAL_EVIDENCE' ? '核对已有表达' : null,
  evidences:
    matchLevel === 'NO_EVIDENCE'
      ? []
      : [
          {
            requirementEvidenceId: id,
            sectionLabel: '工作经历',
            evidenceText: `材料 ${id}`,
            supportLevel: matchLevel === 'MATCHED' ? 'SUFFICIENT' : 'PARTIAL',
          },
        ],
})

const result = (requirements: EvidenceRequirementItem[]) => ({
  optimizationTaskId: 7,
  sourceResumeVersionId: 1,
  targetResumeVersionId: 2,
  jobTargetId: 3,
  status: 'SUCCESS',
  jobTitle: '后端工程师',
  resumeName: '我的简历',
  analysisMode: 'EVIDENCE' as const,
  evidenceAnalysis: {
    evidenceAnalysisId: 4,
    matchedCount: requirements.filter((item) => item.matchLevel === 'MATCHED').length,
    partialEvidenceCount: requirements.filter((item) => item.matchLevel === 'PARTIAL_EVIDENCE')
      .length,
    noEvidenceCount: requirements.filter((item) => item.matchLevel === 'NO_EVIDENCE').length,
    requirements,
  },
  legacyAnalysis: null,
})

const mountView = async (analysisResult: ReturnType<typeof result>) => {
  resultMock.mockResolvedValue(analysisResult)
  const wrapper = mount(JobAnalysisView, {
    global: {
      stubs: {
        PageHeader: {
          props: ['title', 'description'],
          template: '<header><h2>{{ title }}</h2><slot name="actions" /></header>',
        },
        ErrorState: { template: '<div><slot /></div>' },
        SkeletonBlock: { template: '<div />' },
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('JobAnalysisView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    push.mockClear()
  })

  it('shows the fixed action order, expands only the first priority item, and keeps strengths readable', async () => {
    const wrapper = await mountView(
      result([
        requirement(1, 'BONUS', 'NO_EVIDENCE'),
        requirement(2, 'REQUIRED', 'MATCHED'),
        requirement(3, 'BONUS', 'PARTIAL_EVIDENCE'),
        requirement(4, 'REQUIRED', 'NO_EVIDENCE'),
        requirement(5, 'REQUIRED', 'PARTIAL_EVIDENCE'),
      ]),
    )

    const priorityItems = wrapper.findAll('.analysis-priority .analysis-item')
    expect(priorityItems.map((item) => item.find('strong').text())).toEqual([
      '要求 5',
      '要求 4',
      '要求 3',
      '要求 1',
    ])
    expect(priorityItems[0]?.find('.analysis-item-detail').exists()).toBe(true)
    expect(priorityItems.slice(1).some((item) => item.find('.analysis-item-detail').exists())).toBe(
      false,
    )
    expect(wrapper.findAll('.analysis-strengths .analysis-item-detail')).toHaveLength(0)
    expect(wrapper.find('.analysis-strengths .item-toggle-label').text()).toBe('查看依据')

    const noEvidenceItem = priorityItems[1]
    await noEvidenceItem?.find('.analysis-item-toggle').trigger('click')
    expect(noEvidenceItem?.find('.analysis-item-detail').text()).toContain('手动补充')
    expect(noEvidenceItem?.find('.analysis-item-detail').text()).not.toContain('进入编辑器')
    expect(noEvidenceItem?.find('.analysis-item-detail').text()).not.toContain('需要核对')

    await wrapper.find('.analysis-strengths .analysis-item-toggle').trigger('click')
    expect(wrapper.find('.analysis-strengths .analysis-item-detail').text()).toContain('材料 2')
    expect(wrapper.find('.analysis-strengths .analysis-conclusion').exists()).toBe(false)
    expect(wrapper.text()).toContain('当前材料未体现')
  })

  it('uses a compact quiet line rather than a large empty card when no priority exists', async () => {
    const wrapper = await mountView(result([requirement(1, 'REQUIRED', 'MATCHED')]))

    expect(wrapper.find('.analysis-priority .analysis-list').exists()).toBe(false)
    expect(wrapper.find('.analysis-priority .section-toggle').exists()).toBe(false)
    expect(wrapper.text()).toContain('当前没有需要优先处理的岗位要求。')
    expect(wrapper.find('.analysis-priority .ui-empty-state').exists()).toBe(false)
  })
})
