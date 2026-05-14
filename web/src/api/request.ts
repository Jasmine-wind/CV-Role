import axios from 'axios'
import type { AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios'
import type { ApiResult } from '@/types/auth'
import { clearAuthToken, readAuthToken } from '@/utils/auth-token'

interface ApiClient {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T>
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T>
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T>
}

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  timeout: 10000,
})

const unwrapResponse = <T>(response: AxiosResponse<ApiResult<T>>) => {
  const result = response.data

  if (result.code !== 200) {
    throw new Error(result.message || '请求失败')
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

service.interceptors.response.use(undefined, (error: AxiosError<ApiResult<unknown>>) => {
  const status = error.response?.status
  const code = error.response?.data?.code
  const message = resolveErrorMessage(error)

  if (status === 401 || code === 401) {
    clearAuthToken()

    if (window.location.pathname !== '/login') {
      const redirect = `${window.location.pathname}${window.location.search}`
      window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`
    }
  }

  return Promise.reject(new Error(message))
})

const resolveErrorMessage = (error: AxiosError<ApiResult<unknown>>) => {
  const serverMessage = error.response?.data?.message
  if (serverMessage) {
    return serverMessage
  }

  if (error.code === 'ECONNABORTED') {
    return '请求超时，请稍后重试'
  }

  if (!error.response) {
    return '无法连接后端服务，请确认后端已启动'
  }

  if (error.response.status >= 500) {
    return '服务器处理失败，请稍后重试'
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
