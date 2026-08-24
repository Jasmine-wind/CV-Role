// @vitest-environment jsdom

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import ResumeEditor from '@/components/workspace/ResumeEditor.vue'
import type { ResumeDocument } from '@/types/resume-document'

const elementPlusStubs = vi.hoisted(() => ({
  ElInput: {
    name: 'ElInput',
    props: ['modelValue', 'type', 'placeholder', 'maxlength'],
    emits: ['update:modelValue'],
    template:
      '<textarea :value="modelValue" :placeholder="placeholder" @input="$emit(\'update:modelValue\', $event.target.value)"></textarea>',
  },
  ElButton: {
    props: ['disabled', 'loading'],
    template: '<button :disabled="disabled"><slot /></button>',
  },
  ElDropdown: { template: '<div><slot /></div>' },
  ElDropdownMenu: { template: '<div><slot /></div>' },
  ElDropdownItem: { template: '<div><slot /></div>' },
}))

vi.mock('element-plus', () => ({
  ElMessage: { warning: vi.fn(), error: vi.fn() },
  ...elementPlusStubs,
}))

// unplugin-vue-components 将模板组件从 element-plus/es 局部导入；两处均替换为稳定测试 stub。
vi.mock('element-plus/es', () => elementPlusStubs)

const makeDocument = (): ResumeDocument => ({
  schemaVersion: 'RESUME_DOCUMENT_V1',
  basics: {
    name: '张晓测试',
    contacts: [{ id: 'c1', label: '邮箱', value: 'test@example.com' }],
  },
  sections: [
    {
      id: 's1',
      kind: 'EXPERIENCE',
      title: '工作经历',
      entries: [
        {
          id: 'e1',
          heading: '后端开发工程师',
          meta: '2022 - 至今',
          bullets: [{ id: 'b1', text: '负责订单系统后端接口开发' }],
        },
      ],
    },
  ],
})

describe('ResumeEditor', () => {
  it('edits a bullet when the document prop is a reactive proxy', async () => {
    // 与 WorkspacePanel 一致：ref 深层响应式会把 document 变成 Proxy。
    // structuredClone 无法克隆 Proxy，编辑必须保持可用（Phase 8 浏览器回归缺陷）。
    const draft = ref<ResumeDocument>(makeDocument())
    const onChange = vi.fn()
    const wrapper = mount(ResumeEditor, {
      props: {
        document: draft.value,
        onChange,
      },
    })

    const bulletInput = wrapper
      .findAllComponents({ name: 'ElInput' })
      .find((item) => item.props('placeholder') === '要点内容')
    expect(bulletInput).toBeTruthy()
    const updateBullet = bulletInput!.vm.$.vnode.props?.['onUpdate:modelValue'] as
      | ((value: string) => void)
      | undefined
    expect(updateBullet).toBeTypeOf('function')

    updateBullet!('负责订单系统后端接口开发（测试补充）')
    await nextTick()

    expect(onChange).toHaveBeenCalledTimes(1)
    const changed = onChange.mock.lastCall![0] as ResumeDocument
    expect(changed.sections[0].entries[0].bullets[0].text).toBe(
      '负责订单系统后端接口开发（测试补充）',
    )
    // 发出的是纯对象，而不是响应式代理或原引用。
    expect(changed).not.toBe(draft.value)
  })

  it('preserves all supported RESUME_DOCUMENT_V1 values while cloning a reactive proxy', async () => {
    const draft = ref<ResumeDocument>({
      schemaVersion: 'RESUME_DOCUMENT_V1',
      basics: {
        name: null,
        contacts: [{ id: 'c1', label: '邮箱', value: 'test@example.com' }],
      },
      sections: [
        {
          id: 's1',
          kind: 'PROJECT',
          title: '项目经历',
          entries: [
            {
              id: 'e1',
              heading: null,
              meta: null,
              bullets: [{ id: 'b1', text: '原始要点' }],
            },
          ],
        },
      ],
    })
    const onChange = vi.fn()
    const wrapper = mount(ResumeEditor, { props: { document: draft.value, onChange } })
    const bulletInput = wrapper
      .findAllComponents({ name: 'ElInput' })
      .find((item) => item.props('placeholder') === '要点内容')

    const updateBullet = bulletInput!.vm.$.vnode.props?.['onUpdate:modelValue'] as
      | ((value: string) => void)
      | undefined
    expect(updateBullet).toBeTypeOf('function')

    updateBullet!('更新后的要点')
    await nextTick()

    const changed = onChange.mock.lastCall![0] as ResumeDocument
    expect(changed).toEqual({
      schemaVersion: 'RESUME_DOCUMENT_V1',
      basics: {
        name: null,
        contacts: [{ id: 'c1', label: '邮箱', value: 'test@example.com' }],
      },
      sections: [
        {
          id: 's1',
          kind: 'PROJECT',
          title: '项目经历',
          entries: [
            {
              id: 'e1',
              heading: null,
              meta: null,
              bullets: [{ id: 'b1', text: '更新后的要点' }],
            },
          ],
        },
      ],
    })
  })
})
