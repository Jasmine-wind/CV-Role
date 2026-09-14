// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import BulletSuggestionCard from '@/components/workspace/BulletSuggestionCard.vue'

const ElButton = {
  props: ['disabled'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}

describe('BulletSuggestionCard', () => {
  it('uses a technical failure message without presenting content changes as errors', () => {
    const wrapper = mount(BulletSuggestionCard, {
      props: {
        mode: 'rejected',
        originalText: '参与 Redis 优化。',
        rejectCode: 'CONTROL_CHARACTER',
        rejectMessage: 'AI 改写包含不可见格式控制字符',
      },
      global: { stubs: { ElButton } },
    })

    expect(wrapper.text()).toContain('这条建议暂时不可用')
    expect(wrapper.text()).toContain('处理说明')
    expect(wrapper.text()).toContain('重新生成建议')
    expect(wrapper.text()).toContain('继续手工编辑')
    expect(wrapper.text()).not.toContain('没有通过事实校验')
  })

  it('shows a neutral low-value result without diff or apply actions', () => {
    const wrapper = mount(BulletSuggestionCard, {
      props: {
        mode: 'rejected',
        originalText: '设计统一订单状态机，落地分布式事务方案',
        rejectCode: 'LOW_VALUE_CHANGE',
        rejectMessage: '这次改写只产生了很轻微的表达变化，没有足够价值，建议保留原文。',
      },
      global: { stubs: { ElButton } },
    })

    expect(wrapper.text()).toContain('这条内容暂时不需要改')
    expect(wrapper.text()).toContain('原文已经比较清楚')
    expect(wrapper.text()).not.toContain('没有通过事实校验')
    expect(wrapper.text()).not.toContain('采纳')
    expect(wrapper.text()).toContain('换个方向')
    expect(wrapper.get('.bullet-suggestion').classes()).toContain('is-low-value')
  })

  it('shows a content review advisory while keeping the candidate applyable', async () => {
    const apply = vi.fn()
    const wrapper = mount(BulletSuggestionCard, {
      props: {
        mode: 'ready',
        originalText: '负责订单服务开发',
        suggestedText: '负责订单服务开发，并使用 Kafka 处理异步消息',
        reason: '补充 Kafka 相关技术描述；原文未包含该信息，请确认真实性。',
        reviewCode: 'NEW_TECHNOLOGY',
        reviewMessage: '建议包含原文未写明的信息，请确认这些内容确实属于你的真实经历。',
        onApply: apply,
      },
      global: { stubs: { ElButton } },
    })

    expect(wrapper.text()).toContain('新增技术或能力信息')
    expect(wrapper.text()).toContain('不代表系统已验证内容真实性')
    expect(wrapper.text()).toContain('负责订单服务开发，并使用 Kafka 处理异步消息')
    expect(wrapper.text()).toContain('差异')
    expect(wrapper.get('button').text()).toBe('采纳此建议')
    await wrapper.get('button').trigger('click')
    expect(apply).toHaveBeenCalledOnce()
    expect(wrapper.text()).not.toContain('没有通过事实校验')
  })

  it('hides Apply when the candidate is stale', () => {
    const wrapper = mount(BulletSuggestionCard, {
      props: {
        mode: 'stale',
        originalText: '负责订单服务开发',
        suggestedText: '负责订单服务开发，并使用 Kafka 处理异步消息',
        reason: '补充技术描述，请确认真实性。',
      },
      global: { stubs: { ElButton } },
    })

    expect(wrapper.text()).toContain('这条建议已失效')
    expect(wrapper.findAll('.suggestion-actions button').map((button) => button.text())).toEqual([
      '重新生成',
      '关闭',
    ])
  })

  it('shows original, suggested expression, deterministic diff and apply action together', async () => {
    const wrapper = mount(BulletSuggestionCard, {
      props: {
        mode: 'ready',
        originalText: '参与 Redis 缓存优化，完善监控与故障排查流程。',
        suggestedText: '参与 Redis 缓存优化，并完善监控与故障排查流程。',
        reason: '只补充连接词。',
      },
      global: { stubs: { ElButton } },
    })

    expect(wrapper.text()).toContain('当前版本')
    expect(wrapper.text()).toContain('建议版本')
    expect(wrapper.text()).toContain('差异')
    expect(wrapper.find('.diff-added').text()).toContain('并')
    expect(wrapper.get('button').text()).toBe('采纳此建议')
  })

  it.each([
    ['NEW_QUANTITATIVE_CLAIM', '新增数字或量化描述'],
    ['NEW_TECHNOLOGY', '新增技术或能力信息'],
    ['NEW_ENTITY', '新增实体信息'],
    ['RESPONSIBILITY_ESCALATION', '职责或参与程度升级'],
    ['NEW_ACHIEVEMENT', '新增成果或效果描述'],
    ['NEW_SCOPE_OR_TIME', '新增或改变范围、时间'],
    ['UNDETERMINED', '变化内容需要核对'],
  ])('maps %s to a concrete review advisory', (reviewCode, expectedTitle) => {
    const wrapper = mount(BulletSuggestionCard, {
      props: {
        mode: 'ready',
        originalText: '参与订单服务开发',
        suggestedText: '主导订单服务开发并提升性能 30%',
        reason: '调整职责与成果表达。',
        reviewCode,
      },
      global: { stubs: { ElButton } },
    })

    expect(wrapper.text()).toContain(expectedTitle)
    expect(wrapper.text()).toContain('系统')
    expect(wrapper.get('button').text()).toBe('采纳此建议')
    expect(wrapper.get('button').text()).not.toContain('确认')
  })

  it('does not invent a reassuring reason when the API reason is missing', () => {
    const wrapper = mount(BulletSuggestionCard, {
      props: {
        mode: 'ready',
        originalText: '参与订单服务开发',
        suggestedText: '负责订单服务开发',
        reason: null,
      },
      global: { stubs: { ElButton } },
    })

    expect(wrapper.text()).toContain('改写原因未提供')
    expect(wrapper.text()).not.toContain('保留原有事实')
  })
})
