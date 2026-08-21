import { beforeEach, describe, expect, it, vi } from 'vitest'

const { responseMock } = vi.hoisted(() => ({ responseMock: vi.fn() }))

vi.mock('@/api/request', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
  downloadBlob: vi.fn(),
  downloadPdfResponse: responseMock,
}))

import { previewWorkspacePdf } from '@/api/workspace'

const blob = new Blob(['%PDF-1.7'], { type: 'application/pdf' })

describe('previewWorkspacePdf', () => {
  beforeEach(() => responseMock.mockReset())

  it('parses the complete server preview binding and preflight headers', async () => {
    responseMock.mockResolvedValue({
      blob,
      headers: {
        'x-content-revision': '3',
        'x-target-resume-version': '99',
        'x-template-version': '1',
        'x-renderer-version': 'typst-resume-renderer/1',
        'x-preview-receipt': 'signed-receipt',
        'x-resume-page-count': '2',
        'x-resume-missing-contact': 'true',
        'x-resume-page-limit-exceeded': 'false',
        'x-resume-overflow-detected': 'false',
      },
    })

    const result = await previewWorkspacePdf(42, 'classic', 3)

    expect(result).toEqual({
      blob,
      contentRevision: 3,
      targetResumeVersionId: 99,
      templateVersion: '1',
      rendererVersion: 'typst-resume-renderer/1',
      previewReceipt: 'signed-receipt',
      preflight: {
        pageCount: 2,
        missingContact: true,
        pageLimitExceeded: false,
        overflowDetected: false,
      },
    })
  })

  it('fails closed when the signed receipt or preflight metadata is missing', async () => {
    responseMock.mockResolvedValue({
      blob,
      headers: { 'x-content-revision': '3' },
    })

    await expect(previewWorkspacePdf(42, 'classic', 3)).rejects.toThrow(
      '预览响应缺少服务端绑定信息',
    )
  })
})
