// @vitest-environment jsdom

import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import WorkspacePreviewExport from '@/components/workspace/WorkspacePreviewExport.vue'
import {
  deleteWorkspaceArtifact,
  downloadArtifactPdf,
  exportWorkspacePdf,
  listWorkspaceArtifacts,
  previewWorkspacePdf,
} from '@/api/workspace'

const { messageError, messageWarning, messageSuccess } = vi.hoisted(() => ({
  messageError: vi.fn(),
  messageWarning: vi.fn(),
  messageSuccess: vi.fn(),
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    error: messageError,
    warning: messageWarning,
    success: messageSuccess,
  },
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(undefined) },
}))

vi.mock('@/api/workspace', () => ({
  previewWorkspacePdf: vi.fn(),
  exportWorkspacePdf: vi.fn(),
  listWorkspaceArtifacts: vi.fn(),
  downloadArtifactPdf: vi.fn(),
  deleteWorkspaceArtifact: vi.fn(),
}))

const previewMock = vi.mocked(previewWorkspacePdf)
const exportMock = vi.mocked(exportWorkspacePdf)
const listMock = vi.mocked(listWorkspaceArtifacts)
const downloadMock = vi.mocked(downloadArtifactPdf)
const deleteMock = vi.mocked(deleteWorkspaceArtifact)

const staleHandler = vi.fn()
const pdfBlob = new Blob(['%PDF-1.7 component'], { type: 'application/pdf' })

const previewResult = (revision = 3) => ({
  blob: pdfBlob,
  contentRevision: revision,
  targetResumeVersionId: 99,
  templateVersion: '1',
  rendererVersion: 'typst-resume-renderer/1',
  previewReceipt: 'signed-receipt',
  preflight: {
    pageCount: 2,
    missingContact: false,
    pageLimitExceeded: false,
    overflowDetected: false,
  },
})

const artifact = {
  id: 11,
  optimizationTaskId: 42,
  templateId: 'classic' as const,
  templateVersion: '1',
  rendererVersion: 'typst-resume-renderer/1',
  contentRevision: 3,
  mimeType: 'application/pdf',
  fileSize: 1234,
  checksum: 'a'.repeat(64),
  status: 'READY' as const,
  pageCount: 2,
  missingContact: false,
  pageLimitExceeded: false,
  overflowDetected: false,
  fileName: 'resume.pdf',
  createdAt: '2026-08-21T00:00:00',
}

const deferred = <T>() => {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((promiseResolve) => {
    resolve = promiseResolve
  })
  return { promise, resolve }
}

const ButtonStub = {
  props: ['disabled', 'loading'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
}

const RadioGroupStub = {
  name: 'ElRadioGroup',
  props: ['modelValue'],
  emits: ['update:modelValue'],
  template: '<div><slot /></div>',
}

const mountComponent = () =>
  mount(WorkspacePreviewExport, {
    props: {
      optimizationTaskId: 42,
      revision: 3,
      status: 'saved',
      onStale: staleHandler,
    },
    global: {
      components: {
        ElButton: ButtonStub,
        ElRadioGroup: RadioGroupStub,
        ElRadioButton: { template: '<span><slot /></span>' },
        ElDialog: { template: '<div><slot /></div>' },
      },
    },
  })

const button = (wrapper: ReturnType<typeof mountComponent>, text: string) =>
  wrapper.findAll('button').find((item) => item.text().includes(text))!

describe('WorkspacePreviewExport', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    staleHandler.mockClear()
    listMock.mockResolvedValue([])
    downloadMock.mockResolvedValue(pdfBlob)
    deleteMock.mockResolvedValue(undefined)
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(() => 'blob:preview'),
      revokeObjectURL: vi.fn(),
    })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
  })

  it('allows export only after a valid preview and submits the signed receipt', async () => {
    previewMock.mockResolvedValue(previewResult())
    exportMock.mockResolvedValue(artifact)
    const wrapper = mountComponent()
    await flushPromises()

    expect(button(wrapper, '导出 PDF').attributes('disabled')).toBeDefined()
    await button(wrapper, '预览 PDF').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('实际 PDF：2 页')
    expect(button(wrapper, '导出 PDF').attributes('disabled')).toBeUndefined()
    await button(wrapper, '导出 PDF').trigger('click')
    await flushPromises()

    expect(exportMock).toHaveBeenCalledWith(42, {
      templateId: 'classic',
      expectedRevision: 3,
      previewReceipt: 'signed-receipt',
    })
  })

  it('shows contact, page-limit, and executable overflow warnings from real preflight', async () => {
    previewMock.mockResolvedValue({
      ...previewResult(),
      preflight: {
        pageCount: 3,
        missingContact: true,
        pageLimitExceeded: true,
        overflowDetected: true,
      },
    })
    const wrapper = mountComponent()
    await flushPromises()

    await button(wrapper, '预览 PDF').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('实际 PDF：3 页')
    expect(wrapper.text()).toContain('缺少联系方式')
    expect(wrapper.text()).toContain('超过建议的 2 页')
    expect(wrapper.text()).toContain('文字超出页面边界')
  })

  it('drops a late preview response after revision changes', async () => {
    const pending = deferred<ReturnType<typeof previewResult>>()
    previewMock.mockReturnValue(pending.promise)
    const wrapper = mountComponent()
    await flushPromises()

    await button(wrapper, '预览 PDF').trigger('click')
    await wrapper.setProps({ revision: 4 })
    pending.resolve(previewResult(3))
    await flushPromises()

    expect(wrapper.text()).not.toContain('实际 PDF')
    expect(button(wrapper, '导出 PDF').attributes('disabled')).toBeDefined()
  })

  it('invalidates preview on template, task, or save-status changes', async () => {
    previewMock.mockResolvedValue(previewResult())
    const wrapper = mountComponent()
    await flushPromises()
    await button(wrapper, '预览 PDF').trigger('click')
    await flushPromises()
    expect(button(wrapper, '导出 PDF').attributes('disabled')).toBeUndefined()

    wrapper.findComponent(RadioGroupStub).vm.$emit('update:modelValue', 'modern')
    await flushPromises()
    expect(button(wrapper, '导出 PDF').attributes('disabled')).toBeDefined()

    await wrapper.setProps({ optimizationTaskId: 43 })
    await wrapper.setProps({ status: 'conflict' })
    expect(button(wrapper, '导出 PDF').attributes('disabled')).toBeDefined()
  })

  it('emits stale and does not retain preview when server returns 409', async () => {
    const stale = Object.assign(new Error('预览已失效'), { code: 409 })
    previewMock.mockRejectedValue(stale)
    const wrapper = mountComponent()
    await flushPromises()

    await button(wrapper, '预览 PDF').trigger('click')
    await flushPromises()

    expect(staleHandler).toHaveBeenCalledTimes(1)
    expect(messageWarning).toHaveBeenCalledWith('预览已失效')
    expect(button(wrapper, '导出 PDF').attributes('disabled')).toBeDefined()
  })

  it('surfaces non-stale preview errors without enabling export', async () => {
    previewMock.mockRejectedValue(new Error('PDF response invalid'))
    const wrapper = mountComponent()
    await flushPromises()

    await button(wrapper, '预览 PDF').trigger('click')
    await flushPromises()

    expect(messageError).toHaveBeenCalledWith('PDF response invalid')
    expect(button(wrapper, '导出 PDF').attributes('disabled')).toBeDefined()
  })
})
