import axios from 'axios'
import type { AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios'
import type { ApiResult } from '@/types/auth'
import { clearAuthToken, readAuthToken } from '@/utils/auth-token'
import { advanceAuthSessionGeneration } from '@/utils/authSessionGeneration'
import { clearJobComposerTemporaryState } from '@/utils/jobComposerPersistence'
import { aiFailureMessages, presentAiFailure } from '@/utils/aiFailurePresentation'

export interface ApiError extends Error {
  code?: number
  failureCode?: string
  requestId?: string
}

interface ApiClient {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T>
}

const resolveApiBaseUrl = () => {
  const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
  const normalizedBaseUrl = configuredBaseUrl.trim().replace(/\/+$/, '')

  if (normalizedBaseUrl === '/api') {
    return ''
  }

  if (normalizedBaseUrl.endsWith('/api')) {
    return normalizedBaseUrl.slice(0, -4)
  }

  return normalizedBaseUrl
}

const service = axios.create({
  baseURL: resolveApiBaseUrl(),
  timeout: 10000,
})

const redirectUnauthorized = () => {
  // Authentication failure invalidates every in-flight identity-bound callback
  // before navigation and removes recoverable JD/task state from this browser tab.
  advanceAuthSessionGeneration()
  clearJobComposerTemporaryState()
  clearAuthToken()

  if (window.location.pathname !== '/login') {
    const redirect = `${window.location.pathname}${window.location.search}`
    window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`
  }
}

// 只翻译完整匹配的 AI 错误标识；普通业务文案保持原样，业务码仍使用后端整数。
const isAiFailureCode = (message: string | undefined): message is keyof typeof aiFailureMessages =>
  Boolean(message && Object.prototype.hasOwnProperty.call(aiFailureMessages, message))

const isJsonContentType = (contentType: unknown) => {
  const normalized = String(contentType ?? '').toLowerCase()
  return normalized.includes('application/json') || normalized.includes('+json')
}

const resolveResponseMessage = (
  status: number | undefined,
  result: Partial<ApiResult<unknown>> | undefined,
  fallback: string,
  isAuthEndpoint = false,
) => {
  const code = typeof result?.code === 'number' ? result.code : status
  if (status === 401 || code === 401) {
    return '用户名或密码错误，请检查后重试。'
  }
  if (isAuthEndpoint && ((status ?? 0) >= 500 || (code ?? 0) >= 500)) {
    return '服务器暂时无法处理请求，请稍后重试'
  }

  // AI failure identifiers are a public business contract, including failures mapped to HTTP 502.
  if (isAiFailureCode(result?.message)) {
    return presentAiFailure(result.message, fallback)
  }
  if ((status ?? 0) >= 500 || (code ?? 0) >= 500) {
    return '服务器暂时无法处理请求，请稍后重试'
  }
  return result?.message || fallback
}

const createApiError = (
  status: number | undefined,
  result: Partial<ApiResult<unknown>> | undefined,
  fallback: string,
  headers?: AxiosResponse['headers'],
  isAuthEndpoint = false,
) => {
  const message = resolveResponseMessage(status, result, fallback, isAuthEndpoint)
  const apiError = new Error(message) as ApiError
  apiError.code = typeof result?.code === 'number' ? result.code : status
  if (isAiFailureCode(result?.message)) apiError.failureCode = result.message
  const headerRequestId = headers?.['x-request-id']
  const requestId = result?.requestId || (headerRequestId == null ? undefined : String(headerRequestId))
  if (requestId) apiError.requestId = requestId
  return apiError
}

const unwrapResponse = <T>(response: AxiosResponse<ApiResult<T>>) => {
  const result = response.data

  if (result.code !== 200) {
    throw createApiError(
      response.status,
      result,
      '请求失败',
      response.headers,
      response.config.url?.includes('/api/auth/') === true,
    )
  }

  return result.data
}

service.interceptors.request.use((config) => {
  const token = readAuthToken()

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

service.interceptors.response.use(
  undefined,
  async (error: AxiosError<ApiResult<unknown> | Blob>) => {
    // responseType: blob 也会把非 2xx JSON 错误读成 Blob，先还原再走同一套错误处理。
    if (error.response?.data instanceof Blob) {
      const blob = error.response.data
      if (isJsonContentType(error.response.headers['content-type']) || isJsonContentType(blob.type)) {
        try {
          error.response.data = JSON.parse(await blob.text()) as ApiResult<unknown>
        } catch {
          // 无法解析时保留 HTTP 状态和默认错误文案。
        }
      }
    }
    const normalizedError = error as AxiosError<ApiResult<unknown>>
    const status = normalizedError.response?.status
    const result = normalizedError.response?.data
    const code = result?.code

    if (status === 401 || code === 401) {
      redirectUnauthorized()
    }

    return Promise.reject(
      createApiError(
        status,
        result,
        resolveErrorMessage(normalizedError),
        normalizedError.response?.headers,
        normalizedError.config?.url?.includes('/api/auth/') === true,
      ),
    )
  },
)

const resolveErrorMessage = (error: AxiosError<ApiResult<unknown>>) => {
  const status = error.response?.status
  const code = error.response?.data?.code
  if (status === 401 || code === 401) {
    return '用户名或密码错误，请检查后重试。'
  }
  const serverMessage = error.response?.data?.message
  if (isAiFailureCode(serverMessage)) {
    return presentAiFailure(serverMessage, '请求失败')
  }
  if ((status !== undefined && status >= 500) || (code !== undefined && code >= 500)) {
    return '服务器暂时无法处理请求，请稍后重试'
  }
  if (serverMessage) {
    return serverMessage
  }

  if (error.code === 'ECONNABORTED') {
    return '请求超时，请稍后重试'
  }

  if (!error.response) {
    return '当前无法连接服务，请检查网络后重试'
  }

  if (error.response.status >= 500) {
    return '服务器暂时无法处理请求，请稍后重试'
  }

  return error.message || '请求失败'
}

const request: ApiClient = {
  get: <T>(url: string, config?: AxiosRequestConfig) => {
    return service.get<ApiResult<T>>(url, config).then(unwrapResponse)
  },
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => {
    return service.post<ApiResult<T>>(url, data, config).then(unwrapResponse)
  },
  patch: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => {
    return service.patch<ApiResult<T>>(url, data, config).then(unwrapResponse)
  },
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig) => {
    return service.put<ApiResult<T>>(url, data, config).then(unwrapResponse)
  },
  delete: <T>(url: string, config?: AxiosRequestConfig) => {
    return service.delete<ApiResult<T>>(url, config).then(unwrapResponse)
  },
}

export default request

/**
 * 携带 JWT 下载二进制内容（PDF Preview / Export）。
 * 后端业务错误使用与 Result.code 一致的非 2xx HTTP 状态；同时兼容旧服务可能返回的
 * HTTP 200 + Result JSON。成功响应必须按 Content-Type 区分真正的 PDF 与错误 JSON；
 * 非 2xx Blob 错误由响应拦截器还原并归一化。
 */
export interface DownloadedPdfResponse {
  blob: Blob
  headers: Record<string, string>
}

export const downloadPdfResponse = async (
  url: string,
  timeoutMs = 60000,
): Promise<DownloadedPdfResponse> => {
  const response = await service.get<Blob>(url, { responseType: 'blob', timeout: timeoutMs })

  const contentType = String(response.headers['content-type'] ?? '').toLowerCase()
  if (isJsonContentType(contentType) || isJsonContentType(response.data.type)) {
    let parsed: ApiResult<unknown> | null = null
    try {
      parsed = JSON.parse(await response.data.text()) as ApiResult<unknown>
    } catch {
      // 声明为 JSON 却无法解析：fail closed，绝不把坏字节交给预览或下载。
    }
    if (parsed?.code === 401) {
      redirectUnauthorized()
    }
    throw createApiError(
      response.status,
      parsed ?? undefined,
      '下载失败，请稍后重试',
      response.headers,
    )
  }
  if (!contentType.includes('application/pdf')) {
    throw new Error('下载响应不是有效 PDF')
  }
  const signature = new TextDecoder('ascii').decode(await response.data.slice(0, 5).arrayBuffer())
  if (signature !== '%PDF-') {
    throw new Error('下载响应不是有效 PDF')
  }
  const headers: Record<string, string> = {}
  for (const [name, value] of Object.entries(response.headers)) {
    if (value !== undefined && value !== null) headers[name.toLowerCase()] = String(value)
  }
  return { blob: response.data, headers }
}

export const downloadBlob = async (url: string, timeoutMs = 60000): Promise<Blob> =>
  (await downloadPdfResponse(url, timeoutMs)).blob
