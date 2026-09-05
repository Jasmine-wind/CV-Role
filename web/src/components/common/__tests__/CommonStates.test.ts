// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ErrorState from '@/components/common/ErrorState.vue'
import PageHeader from '@/components/common/PageHeader.vue'

describe('shared page primitives', () => {
  it('exposes a page-level title as an h1', () => {
    const wrapper = mount(PageHeader, {
      props: { title: '我的简历', description: '管理已确认的简历材料。' },
    })

    expect(wrapper.find('h1').text()).toBe('我的简历')
    expect(wrapper.find('h2').exists()).toBe(false)
  })

  it('announces task errors while keeping the recovery action available', () => {
    const wrapper = mount(ErrorState, {
      props: { title: '加载失败', description: '请重试。', actionText: '重新加载' },
      global: {
        stubs: {
          ElButton: { template: '<button><slot /></button>' },
        },
      },
    })

    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.get('button').text()).toBe('重新加载')
  })
})
