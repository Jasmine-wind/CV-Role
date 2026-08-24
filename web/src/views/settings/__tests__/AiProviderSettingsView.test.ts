// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AiProviderSettingsView from '@/views/settings/AiProviderSettingsView.vue'
import {
  disableAiProvider,
  enableAiProvider,
  getAiProviderSettings,
} from '@/api/ai-provider'
import type { AiProviderCredential } from '@/api/ai-provider'

const { messageError, messageSuccess } = vi.hoisted(() => ({
  messageError: vi.fn(),
  messageSuccess: vi.fn(),
}))

const elementPlusStubs = vi.hoisted(() => ({
  ElButton: {
    name: 'ElButton',
    props: ['disabled', 'loading'],
    emits: ['click'],
    template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
  },
  ElAlert: { template: '<div><slot /></div>' },
  ElForm: { template: '<form><slot /></form>' },
  ElFormItem: { template: '<div><slot /></div>' },
  ElInput: { props: ['modelValue'], template: '<input :value="modelValue" />' },
  ElTag: { template: '<span><slot /></span>' },
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: messageError, success: messageSuccess },
  ElMessageBox: { confirm: vi.fn() },
  ...elementPlusStubs,
}))

vi.mock('element-plus/es', () => ({
  ...elementPlusStubs,
  ElMessage: { error: messageError, success: messageSuccess },
  ElMessageBox: { confirm: vi.fn() },
}))

vi.mock('@/api/ai-provider', () => ({
  deleteAiProvider: vi.fn(),
  disableAiProvider: vi.fn(),
  enableAiProvider: vi.fn(),
  getAiProviderSettings: vi.fn(),
  saveAiProviderSettings: vi.fn(),
  testAiProvider: vi.fn(),
}))

const getSettingsMock = vi.mocked(getAiProviderSettings)
const enableMock = vi.mocked(enableAiProvider)
const disableMock = vi.mocked(disableAiProvider)

const credential = (status: 'ACTIVE' | 'DISABLED'): AiProviderCredential => ({
  providerType: 'OPENAI_COMPATIBLE',
  baseUrl: 'https://api.example.com/v1',
  model: 'gate-model',
  config: {},
  status,
  configured: true,
  apiKeyConfigured: true,
  maskedApiKey: 'sk-***',
})

const mountLoaded = async (settings: AiProviderCredential) => {
  getSettingsMock.mockResolvedValue(settings)
  const wrapper = mount(AiProviderSettingsView)
  await flushPromises()
  return wrapper
}

const button = (wrapper: Awaited<ReturnType<typeof mountLoaded>>, text: string) =>
  wrapper.findAll('button').find((item) => item.text().includes(text))!

describe('AiProviderSettingsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reports activation after enabling instead of reading the post-update status backwards', async () => {
    enableMock.mockResolvedValue(credential('ACTIVE'))
    const wrapper = await mountLoaded(credential('DISABLED'))

    await button(wrapper, '启用').trigger('click')
    await flushPromises()

    expect(enableMock).toHaveBeenCalledOnce()
    expect(messageSuccess).toHaveBeenCalledWith('已启用你的 API 密钥')
  })

  it('reports deactivation after disabling instead of reading the post-update status backwards', async () => {
    disableMock.mockResolvedValue(credential('DISABLED'))
    const wrapper = await mountLoaded(credential('ACTIVE'))

    await button(wrapper, '停用').trigger('click')
    await flushPromises()

    expect(disableMock).toHaveBeenCalledOnce()
    expect(messageSuccess).toHaveBeenCalledWith('已停用你的 API 密钥，新任务将使用系统提供的 AI')
  })
})
