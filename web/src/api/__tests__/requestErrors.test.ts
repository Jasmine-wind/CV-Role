import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError, AxiosHeaders } from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'

const {
  adapter,
  authTokenState,
  readAuthToken,
  clearAuthToken,
  clearJobComposerTemporaryState,
  advanceAuthSessionGeneration,
} = vi.hoisted(() => ({
  adapter: vi.fn(),
  authTokenState: { value: '' },
  readAuthToken: vi.fn(),
  clearAuthToken: vi.fn(),
  clearJobComposerTemporaryState: vi.fn(),
  advanceAuthSessionGeneration: vi.fn(),
}))

vi.mock('axios', async (importOriginal) => {
  const actual = await importOriginal<typeof import('axios')>()
  return {
    ...actual,
    default: {
      ...actual.default,
      create: (config: Parameters<typeof actual.default.create>[0]) =>
        actual.default.create({ ...config, adapter }),
    },
  }
})
vi.mock('@/utils/auth-token', () => ({ readAuthToken, clearAuthToken }))
vi.mock('@/utils/jobComposerPersistence', () => ({ clearJobComposerTemporaryState }))
vi.mock('@/utils/authSessionGeneration', () => ({ advanceAuthSessionGeneration }))

import request, { downloadBlob } from '@/api/request'

const messages = [
  ['INVALID_CREDENTIAL', 'AI 密钥无效或未授权，请检查 AI 设置后重试。'],
  ['PROVIDER_UNAUTHORIZED', 'AI 密钥无效或未授权，请检查 AI 设置后重试。'],
  ['MODEL_NOT_FOUND', '当前 AI 模型不可用，请检查模型名称。'],
  ['RATE_LIMITED', 'AI 服务请求过于频繁，请稍后重试。'],
  ['TIMEOUT', 'AI 服务响应超时，请稍后重试。'],
  ['PROVIDER_UNAVAILABLE', 'AI 服务暂时不可用，请稍后重试。'],
  ['SCHEMA_INVALID', 'AI 返回内容暂时无法使用，请重新生成。'],
  ['REFUSAL', 'AI 未能在当前事实范围内生成建议，可以重新生成或手工编辑。'],
  ['UNSAFE_BASE_URL', 'AI 连接未通过安全检查，请检查 AI 设置或网络环境后重试。'],
  ['RESPONSE_TOO_LARGE', 'AI 返回内容过长，请重新生成。'],
  ['CREDENTIAL_CHANGED', 'AI 配置已经变化，请刷新后重试。'],
  ['CONFIGURATION_INVALID', 'AI 配置不完整，请检查 AI 设置。'],
  ['AI_CONFIGURATION_REQUIRED', '请先在 AI 设置中配置并启用自己的 API。'],
  ['INTERRUPTED', 'AI 请求已中断，请重新尝试。'],
] as const

function respond(data: unknown, status = 200, blob = false) {
  adapter.mockImplementationOnce((config: InternalAxiosRequestConfig) => {
    const response: AxiosResponse = {
      data: blob ? new Blob([JSON.stringify(data)], { type: 'application/json' }) : data,
      status,
      statusText: String(status),
      headers: new AxiosHeaders({ 'content-type': 'application/json;charset=UTF-8' }),
      config,
    }
    return status < 400
      ? Promise.resolve(response)
      : Promise.reject(new AxiosError('HTTP failure', undefined, config, undefined, response))
  })
}

beforeEach(() => {
  adapter.mockReset()
  authTokenState.value = 'existing-token'
  readAuthToken.mockReset()
  readAuthToken.mockImplementation(() => authTokenState.value)
  clearAuthToken.mockReset()
  clearAuthToken.mockImplementation(() => {
    authTokenState.value = ''
  })
  clearJobComposerTemporaryState.mockReset()
  advanceAuthSessionGeneration.mockReset()
  vi.stubGlobal('window', { location: { pathname: '/workspace', search: '?id=1', href: '' } })
})

describe.each(['wrapped', 'http', 'blob', 'http-blob'] as const)('%s AI errors', (path) => {
  it.each(messages)(
    'translates the exact %s identifier and keeps the integer code',
    async (identifier, message) => {
      const http = path.startsWith('http')
      const blob = path.includes('blob')
      const code = [
        'INVALID_CREDENTIAL',
        'AI_CONFIGURATION_REQUIRED',
        'CONFIGURATION_INVALID',
        'UNSAFE_BASE_URL',
      ].includes(identifier)
        ? 400
        : identifier === 'CREDENTIAL_CHANGED'
          ? 409
          : 502
      respond({ code, message: identifier }, http ? code : 200, blob)
      await expect(
        blob ? downloadBlob('/api/preview') : request.post('/api/ai/test'),
      ).rejects.toMatchObject({
        message,
        code,
      })
      expect(clearAuthToken).not.toHaveBeenCalled()
    },
  )

  it.each([
    '普通业务错误',
    'UNKNOWN_AI_FAILURE',
    ' TIMEOUT ',
    'TIMEOUT: details',
    'constructor',
    '__proto__',
  ])('preserves unknown/ordinary message %s', async (message) => {
    respond({ code: 409, message }, path.startsWith('http') ? 409 : 200, path.includes('blob'))
    await expect(
      path.includes('blob') ? downloadBlob('/api/preview') : request.get('/api/test'),
    ).rejects.toMatchObject({ message, code: 409 })
  })
})

describe('existing request error behavior', () => {
  it.each([false, true])('expires the current session for a protected HTTP 401 (blob=%s)', async (blob) => {
    respond({ code: 401, message: 'INVALID_CREDENTIAL' }, 401, blob)
    await expect(
      blob ? downloadBlob('/api/preview') : request.get('/api/test'),
    ).rejects.toMatchObject({
      message: '用户名或密码错误，请检查后重试。',
      code: 401,
    })
    expect(advanceAuthSessionGeneration).toHaveBeenCalledOnce()
    expect(clearJobComposerTemporaryState).toHaveBeenCalledOnce()
    expect(clearAuthToken).toHaveBeenCalledOnce()
    expect(authTokenState.value).toBe('')
    expect(window.location.href).toBe('/login?redirect=%2Fworkspace%3Fid%3D1')
  })

  it('leaves a real HTTP login credential failure to the login flow', async () => {
    respond({ code: 401, message: 'INVALID_CREDENTIAL' }, 401)

    await expect(request.post('/api/auth/login', {
      account: 'candidate',
      password: 'wrong-password',
    })).rejects.toMatchObject({
      message: '用户名或密码错误，请检查后重试。',
      code: 401,
    })

    expect(advanceAuthSessionGeneration).not.toHaveBeenCalled()
    expect(clearJobComposerTemporaryState).not.toHaveBeenCalled()
    expect(clearAuthToken).not.toHaveBeenCalled()
    expect(window.location.href).toBe('')
  })

  it('invalidates one session only once when protected requests fail concurrently', async () => {
    let releaseResponses!: () => void
    const responsesReleased = new Promise<void>((resolve) => {
      releaseResponses = resolve
    })

    adapter.mockImplementation(async (config: InternalAxiosRequestConfig) => {
      await responsesReleased
      const response: AxiosResponse = {
        data: { code: 401, message: 'UNAUTHORIZED' },
        status: 401,
        statusText: '401',
        headers: new AxiosHeaders({ 'content-type': 'application/json' }),
        config,
      }
      throw new AxiosError('HTTP failure', undefined, config, undefined, response)
    })

    const requests = [request.get('/api/resumes'), request.get('/api/tasks/recent')]
    await vi.waitFor(() => expect(adapter).toHaveBeenCalledTimes(2))
    releaseResponses()

    const results = await Promise.allSettled(requests)
    expect(results.every((result) => result.status === 'rejected')).toBe(true)
    expect(advanceAuthSessionGeneration).toHaveBeenCalledOnce()
    expect(clearJobComposerTemporaryState).toHaveBeenCalledOnce()
    expect(clearAuthToken).toHaveBeenCalledOnce()
    expect(window.location.href).toBe('/login?redirect=%2Fworkspace%3Fid%3D1')
  })

  it('does not let a stale session 401 clear a newer login', async () => {
    let releaseResponse!: () => void
    const responseReleased = new Promise<void>((resolve) => {
      releaseResponse = resolve
    })

    adapter.mockImplementationOnce(async (config: InternalAxiosRequestConfig) => {
      await responseReleased
      const response: AxiosResponse = {
        data: { code: 401, message: 'UNAUTHORIZED' },
        status: 401,
        statusText: '401',
        headers: new AxiosHeaders({ 'content-type': 'application/json' }),
        config,
      }
      throw new AxiosError('HTTP failure', undefined, config, undefined, response)
    })

    const staleRequest = request.get('/api/resumes')
    await vi.waitFor(() => expect(adapter).toHaveBeenCalledOnce())
    authTokenState.value = 'new-session-token'
    releaseResponse()

    await expect(staleRequest).rejects.toMatchObject({ code: 401 })
    expect(advanceAuthSessionGeneration).not.toHaveBeenCalled()
    expect(clearJobComposerTemporaryState).not.toHaveBeenCalled()
    expect(clearAuthToken).not.toHaveBeenCalled()
    expect(window.location.href).toBe('')
  })

  it.each([
    [503, 400],
    [400, 502],
  ])('does not expose an ordinary server message for HTTP %i / business %i', async (status, code) => {
    respond({ code, message: 'internal stack and SQL detail' }, status)
    await expect(request.get('/api/test')).rejects.toMatchObject({
      message: '服务器暂时无法处理请求，请稍后重试',
      code,
    })
  })

  it('keeps the request ID from a real HTTP error envelope', async () => {
    adapter.mockImplementationOnce((config: InternalAxiosRequestConfig) => {
      const response: AxiosResponse = {
        data: { code: 409, message: '内容已更新', requestId: 'request-body-id' },
        status: 409,
        statusText: '409',
        headers: new AxiosHeaders({
          'content-type': 'application/json',
          'x-request-id': 'request-header-id',
        }),
        config,
      }
      return Promise.reject(new AxiosError('HTTP failure', undefined, config, undefined, response))
    })

    await expect(request.get('/api/test')).rejects.toMatchObject({
      code: 409,
      message: '内容已更新',
      requestId: 'request-body-id',
    })
  })

  it('parses a Blob error by its own JSON type when the response header is absent', async () => {
    adapter.mockImplementationOnce((config: InternalAxiosRequestConfig) => {
      const response: AxiosResponse = {
        data: new Blob(
          [JSON.stringify({ code: 409, message: '内容已更新', requestId: 'blob-request-id' })],
          { type: 'application/problem+json' },
        ),
        status: 409,
        statusText: '409',
        headers: new AxiosHeaders(),
        config,
      }
      return Promise.reject(new AxiosError('HTTP failure', undefined, config, undefined, response))
    })

    await expect(downloadBlob('/api/preview')).rejects.toMatchObject({
      code: 409,
      message: '内容已更新',
      requestId: 'blob-request-id',
    })
  })

  it.each([
    [401, '用户名或密码错误，请检查后重试。'],
    [502, '服务器暂时无法处理请求，请稍后重试'],
  ])('preserves wrapped auth endpoint handling for %i', async (code, message) => {
    respond({ code, message: 'INVALID_CREDENTIAL' })
    await expect(request.post('/api/auth/login')).rejects.toMatchObject({ code, message })
  })

  it('uses HTTP status when there is no business code', async () => {
    respond({ message: 'MODEL_NOT_FOUND' }, 404)
    await expect(request.get('/api/test')).rejects.toMatchObject({
      code: 404,
      message: messages[2][1],
    })
  })

  it.each([
    ['ECONNABORTED', '请求超时，请稍后重试'],
    ['ERR_NETWORK', '当前无法连接服务，请检查网络后重试'],
  ])('preserves %s fallback', async (code, message) => {
    adapter.mockRejectedValueOnce(new AxiosError('internal details', code))
    await expect(request.get('/api/test')).rejects.toThrow(message)
  })

  it('still unwraps successful responses', async () => {
    respond({ code: 200, message: 'TIMEOUT', data: { id: 1 } })
    await expect(request.get('/api/test')).resolves.toEqual({ id: 1 })
  })
})
