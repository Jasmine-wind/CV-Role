import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { LoginResponse } from '@/types/auth'

const {
  loginApi,
  getCurrentUser,
  tokenState,
  generationState,
  readAuthToken,
  writeAuthToken,
  clearAuthToken,
  advanceAuthSessionGeneration,
  getAuthSessionGeneration,
  clearJobComposerTemporaryStateForUser,
} = vi.hoisted(() => ({
  loginApi: vi.fn(),
  getCurrentUser: vi.fn(),
  tokenState: { value: '' },
  generationState: { value: 0 },
  readAuthToken: vi.fn(),
  writeAuthToken: vi.fn(),
  clearAuthToken: vi.fn(),
  advanceAuthSessionGeneration: vi.fn(),
  getAuthSessionGeneration: vi.fn(),
  clearJobComposerTemporaryStateForUser: vi.fn(),
}))

vi.mock('@/api/auth', () => ({ login: loginApi }))
vi.mock('@/api/user', () => ({ getCurrentUser }))
vi.mock('@/utils/auth-token', () => ({ readAuthToken, writeAuthToken, clearAuthToken }))
vi.mock('@/utils/authSessionGeneration', () => ({
  advanceAuthSessionGeneration,
  getAuthSessionGeneration,
}))
vi.mock('@/utils/jobComposerPersistence', () => ({ clearJobComposerTemporaryStateForUser }))

import { useAuthStore } from '@/stores/auth'

const loginResponse = (token: string, userId = 7): LoginResponse => ({
  userId,
  username: `candidate-${userId}`,
  email: `candidate-${userId}@example.invalid`,
  nickname: `Candidate ${userId}`,
  token,
  tokenType: 'Bearer',
  expiresIn: 3600,
})

beforeEach(() => {
  setActivePinia(createPinia())
  tokenState.value = ''
  generationState.value = 0
  loginApi.mockReset()
  getCurrentUser.mockReset()
  readAuthToken.mockReset()
  readAuthToken.mockImplementation(() => tokenState.value)
  writeAuthToken.mockReset()
  writeAuthToken.mockImplementation((token: string) => {
    tokenState.value = token
  })
  clearAuthToken.mockReset()
  clearAuthToken.mockImplementation(() => {
    tokenState.value = ''
  })
  advanceAuthSessionGeneration.mockReset()
  advanceAuthSessionGeneration.mockImplementation(() => {
    generationState.value += 1
    return generationState.value
  })
  getAuthSessionGeneration.mockReset()
  getAuthSessionGeneration.mockImplementation(() => generationState.value)
  clearJobComposerTemporaryStateForUser.mockReset()
})

describe('auth store login state', () => {
  it('ends loading after bad credentials and can immediately log in again', async () => {
    loginApi
      .mockRejectedValueOnce(new Error('用户名或密码错误，请检查后重试。'))
      .mockResolvedValueOnce(loginResponse('valid-token'))
    const store = useAuthStore()

    await expect(store.login({ account: 'candidate', password: 'wrong-password' }))
      .rejects.toThrow('用户名或密码错误')
    expect(store.loading).toBe(false)
    expect(store.isAuthenticated).toBe(false)

    await expect(store.login({ account: 'candidate', password: 'safe-password' }))
      .resolves.toMatchObject({ token: 'valid-token' })
    expect(store.loading).toBe(false)
    expect(store.token).toBe('valid-token')
    expect(store.currentUser?.id).toBe(7)
  })

  it('ends loading after a network failure', async () => {
    loginApi.mockRejectedValueOnce(new Error('当前无法连接服务，请检查网络后重试'))
    const store = useAuthStore()

    await expect(store.login({ account: 'candidate', password: 'safe-password' }))
      .rejects.toThrow('当前无法连接服务')

    expect(store.loading).toBe(false)
    expect(store.isAuthenticated).toBe(false)
  })

  it('does not let an older login response overwrite a newer session', async () => {
    let resolveOldLogin!: (value: LoginResponse) => void
    const oldLoginResponse = new Promise<LoginResponse>((resolve) => {
      resolveOldLogin = resolve
    })
    loginApi
      .mockReturnValueOnce(oldLoginResponse)
      .mockResolvedValueOnce(loginResponse('new-session-token', 9))
    const store = useAuthStore()

    const oldLogin = store.login({ account: 'old-user', password: 'safe-password' })
    const newLogin = store.login({ account: 'new-user', password: 'safe-password' })
    await expect(newLogin).resolves.toMatchObject({ token: 'new-session-token' })
    resolveOldLogin(loginResponse('old-session-token', 8))
    await expect(oldLogin).rejects.toThrow('登录状态已变化，请重新登录')

    expect(store.loading).toBe(false)
    expect(store.token).toBe('new-session-token')
    expect(store.currentUser?.id).toBe(9)
    expect(writeAuthToken).toHaveBeenCalledOnce()
  })
})
