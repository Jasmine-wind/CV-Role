import { beforeEach, describe, expect, it, vi } from 'vitest'

// vi.mock 会被提升到文件顶部，共享 mock 必须用 vi.hoisted 声明。
const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }))

// request.ts 在模块加载时创建 axios 实例并注册拦截器；这里只替换实例能力。
vi.mock('axios', () => ({
  default: {
    create: () => ({
      get: getMock,
      interceptors: {
        request: { use: vi.fn() },
        response: { use: vi.fn() },
      },
    }),
  },
}))

import { downloadBlob } from '@/api/request'

const pdfBlob = new Blob(['%PDF-1.7 test'], { type: 'application/pdf' })

const response = (contentType: string, data: Blob) => ({
  data,
  headers: { 'content-type': contentType },
})

describe('downloadBlob', () => {
  beforeEach(() => {
    getMock.mockReset()
  })

  it('返回真实 PDF 字节', async () => {
    getMock.mockResolvedValueOnce(response('application/pdf', pdfBlob))

    const blob = await downloadBlob('/api/workspace/1/preview.pdf?expectedRevision=0')

    expect(blob).toBe(pdfBlob)
  })

  it('HTTP 200 包装的业务错误 JSON 必须抛出并携带业务码', async () => {
    const errorBody = new Blob(
      [JSON.stringify({ code: 409, message: '简历内容已更新，预览已失效，请刷新后重试' })],
      { type: 'application/json' },
    )
    getMock.mockResolvedValueOnce(response('application/json;charset=UTF-8', errorBody))

    const error = await downloadBlob('/api/workspace/1/preview.pdf?expectedRevision=0')
      .then(() => null)
      .catch((thrown: unknown) => thrown)

    expect(error).toBeInstanceOf(Error)
    expect((error as Error).message).toBe('简历内容已更新，预览已失效，请刷新后重试')
    expect((error as Error & { code?: number }).code).toBe(409)
  })

  it('编译失败等业务错误同样不得被当作 PDF 返回', async () => {
    const errorBody = new Blob(
      [JSON.stringify({ code: 500, message: '简历排版编译失败，请检查内容后重试' })],
      { type: 'application/json' },
    )
    getMock.mockResolvedValueOnce(response('application/json', errorBody))

    await expect(
      downloadBlob('/api/workspace/1/preview.pdf?expectedRevision=0'),
    ).rejects.toThrow('简历排版编译失败，请检查内容后重试')
  })

  it('声明为 JSON 但无法解析时 fail closed', async () => {
    const brokenBody = new Blob(['not-json'], { type: 'application/json' })
    getMock.mockResolvedValueOnce(response('application/json', brokenBody))

    await expect(
      downloadBlob('/api/workspace/1/preview.pdf?expectedRevision=0'),
    ).rejects.toThrow('下载失败，请稍后重试')
  })

  it('即使业务码为 200，JSON 也不能伪装成 PDF', async () => {
    const jsonBody = new Blob([JSON.stringify({ code: 200, data: null })], {
      type: 'application/json',
    })
    getMock.mockResolvedValueOnce(response('application/json', jsonBody))

    await expect(downloadBlob('/api/workspace/1/preview.pdf')).rejects.toThrow(
      '下载失败，请稍后重试',
    )
  })

  it('拒绝非 PDF Content-Type 与伪造的 PDF 字节', async () => {
    getMock.mockResolvedValueOnce(response('text/html', new Blob(['<html>error</html>'])))
    await expect(downloadBlob('/api/workspace/1/preview.pdf')).rejects.toThrow(
      '下载响应不是有效 PDF',
    )

    getMock.mockResolvedValueOnce(response('application/pdf', new Blob(['not-pdf'])))
    await expect(downloadBlob('/api/workspace/1/preview.pdf')).rejects.toThrow(
      '下载响应不是有效 PDF',
    )
  })
})
