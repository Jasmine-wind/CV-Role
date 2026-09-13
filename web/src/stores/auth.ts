import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login as loginApi } from '@/api/auth'
import { getCurrentUser } from '@/api/user'
import type { CurrentUser, LoginRequest } from '@/types/auth'
import { clearAuthToken, readAuthToken, writeAuthToken } from '@/utils/auth-token'
import {
  advanceAuthSessionGeneration,
  getAuthSessionGeneration,
} from '@/utils/authSessionGeneration'
import { clearJobComposerTemporaryStateForUser } from '@/utils/jobComposerPersistence'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(readAuthToken())
  const currentUser = ref<CurrentUser | null>(null)
  const loading = ref(false)

  const isAuthenticated = computed(() => Boolean(token.value))

  const setToken = (value: string) => {
    token.value = value
    writeAuthToken(value)
  }

  const clearAuth = () => {
    const userId = currentUser.value?.id
    if (userId) clearJobComposerTemporaryStateForUser(userId)
    advanceAuthSessionGeneration()
    token.value = ''
    currentUser.value = null
    loading.value = false
    clearAuthToken()
  }

  const login = async (payload: LoginRequest) => {
    const requestGeneration = advanceAuthSessionGeneration()
    loading.value = true

    try {
      const data = await loginApi(payload)
      if (requestGeneration !== getAuthSessionGeneration()) {
        throw new Error('登录状态已变化，请重新登录')
      }

      setToken(data.token)
      currentUser.value = {
        id: data.userId,
        username: data.username,
        email: data.email,
        nickname: data.nickname,
        createdAt: '',
      }

      return data
    } finally {
      if (requestGeneration === getAuthSessionGeneration()) loading.value = false
    }
  }

  const fetchMe = async () => {
    if (!token.value) {
      currentUser.value = null
      return null
    }

    const requestGeneration = getAuthSessionGeneration()
    const requestToken = token.value
    loading.value = true

    try {
      const data = await getCurrentUser()
      if (
        requestGeneration !== getAuthSessionGeneration()
        || requestToken !== token.value
      ) {
        return null
      }

      currentUser.value = data
      return data
    } catch (error) {
      // A 401 advances the global fence in the request layer before this catch runs.
      // Token equality still proves this failure belongs to the same identity; a
      // later login has a different token and must not be cleared by this request.
      if (requestToken === token.value) clearAuth()
      throw error
    } finally {
      if (requestGeneration === getAuthSessionGeneration()) loading.value = false
    }
  }

  const updateCurrentUser = (user: CurrentUser) => {
    currentUser.value = user
  }

  const logout = () => {
    clearAuth()
  }

  return {
    token,
    currentUser,
    loading,
    isAuthenticated,
    login,
    logout,
    fetchMe,
    updateCurrentUser,
  }
})
